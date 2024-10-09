/**
 * Copyright 2015 Andrei Tuicu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.plugins.JiraTestResultReporter;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.util.ErrorCollection;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Job;
import hudson.plugins.junitattachments.AttachmentPublisher;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.test.TestResult;
import io.atlassian.util.concurrent.Promise;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.JiraTestResultReporter.config.AbstractFields;

/**
 * Created by tuicu.
 */
public class JiraUtils {
    private static final Logger LOGGER = Logger.getLogger("JiraIssuePlugin.log");

    /**
     * Constructs the URL for an issue, given the server url and the issue key
     * @param serverURL
     * @param issueKey
     * @return
     */
    public static String getIssueURL(String serverURL, String issueKey) {
        return serverURL + (serverURL.charAt(serverURL.length() - 1) == '/' ? "" : "/") + "browse/" + issueKey;
    }

    public static void log(String message) {
        LOGGER.log(Level.INFO, message);
    }

    public static void logError(String message, Exception e) {
        LOGGER.log(Level.SEVERE, message, e);
    }

    public static void logError(String message) {
        LOGGER.log(Level.SEVERE, message);
    }

    public static void logWarning(String message) {
        LOGGER.log(Level.WARNING, message);
    }

    public static void logWarning(String message, Exception e) {
        LOGGER.log(Level.WARNING, message, e);
    }
    /**
     * Static getter for the JiraTestDataPublisherDescriptor singleton instance
     * @return
     */
    public static JiraTestDataPublisher.JiraTestDataPublisherDescriptor getJiraDescriptor() {
        return (JiraTestDataPublisher.JiraTestDataPublisherDescriptor)
                Jenkins.getInstance().getDescriptor(JiraTestDataPublisher.class);
    }

    /**
     * Form a single string from the messages returned in a RestClientException
     * @param e a RestClientException
     * @param newLine string representing the new line
     * @return
     */
    public static String getErrorMessage(RestClientException e, String newLine) {
        StringBuilder errorMessages = new StringBuilder();
        for (ErrorCollection errorCollection : e.getErrorCollections()) {
            if (errorMessages.length() != 0) {
                errorMessages.append(newLine);
            }
            errorMessages.append("Error ").append(errorCollection.getStatus());
            for (String message : errorCollection.getErrorMessages()) {
                errorMessages.append(newLine).append(message);
            }

            for (Map.Entry<String, String> entry : errorCollection.getErrors().entrySet()) {
                errorMessages.append(newLine).append(entry.getValue());
            }
        }
        return errorMessages.toString();
    }

    public static String createIssue(Job job, EnvVars envVars, CaseResult test) throws RestClientException {
        return createIssue(job, job, envVars, test, JiraIssueTrigger.JOB, Collections.emptyList());
    }

    public static boolean cleanJobCacheFile(List<CaseResult> testCaseResults, Job testJob) {
        List<String> testNames = testCaseResults.stream()
                .filter(CaseResult::isFailed)
                .map(CaseResult::getId)
                .collect(Collectors.toList());
        HashMap<String, String> keysToCheck = new HashMap<>();
        List<String> jiraIds = new ArrayList<>();
        for (String test : testNames) {
            if (TestToIssueMapping.getInstance().getTestIssueKey(testJob, test) != null) {
                String jiraId = TestToIssueMapping.getInstance().getTestIssueKey(testJob, test);
                jiraIds.add(jiraId);
                keysToCheck.put(jiraId, test);
            }
        }
        if (keysToCheck.isEmpty()) {
            return false;
        }

        SearchResult searchResult = JiraUtils.findUnresolvedJiraIssues(String.join(",", jiraIds));
        if (searchResult != null && searchResult.getTotal() > 0) {
            for (Issue issue : searchResult.getIssues()) {
                String testKey = issue.getKey();
                String testId = keysToCheck.get(testKey);
                synchronized (testId) {
                    TestToIssueMapping.getInstance().removeTestToIssueMapping(testJob, testId, testKey);
                }
            }
        }
        return true;
    }

    public static String createIssue(
            Job job, Job project, EnvVars envVars, CaseResult test, JiraIssueTrigger trigger, List<String> attachments)
            throws RestClientException {
        synchronized (test.getId()) { // avoid creating duplicated issues
            if (TestToIssueMapping.getInstance().getTestIssueKey(job, test.getId()) != null) {
                return null;
            }
            IssueInput issueInput = JiraUtils.createIssueInput(project, test, envVars, trigger);
            SearchResult searchResult = JiraUtils.findIssues(project, test, envVars, issueInput);
            if (searchResult != null && searchResult.getTotal() > 0) {
                boolean duplicate = false;
                FieldInput fi = JiraTestDataPublisher.JiraTestDataPublisherDescriptor.templates
                        .get(0)
                        .getFieldInput(test, envVars);
                String text = issueInput.getField(fi.getId()).getValue().toString();
                for (Issue issue : searchResult.getIssues()) {
                    if (issue.getSummary().equals(text)) {
                        JiraUtils.log(String.format(
                                "Ignoring creating issue '%s' as it would be a duplicate. (from Jira server)", text));
                        duplicate = true;
                        TestToIssueMapping.getInstance().addTestToIssueMapping(job, test.getId(), issue.getKey());
                    }
                }
                if (duplicate) {
                    return null;
                }
            }
            String issueKey = JiraUtils.createIssueInput(issueInput, test, attachments);
            TestToIssueMapping.getInstance().addTestToIssueMapping(job, test.getId(), issueKey);
            return issueKey;
        }
    }

    /**
     * Given a test case result, it searchs for all the issue keys related with it
     * from the local issue map or from the Jira server
     * @param job
     * @param envVars
     * @param test
     * @return related issue keys from issue map or from Jira server
     * @throws RestClientException
     */
    public static Set<String> searchIssueKeys(Job job, EnvVars envVars, CaseResult test) throws RestClientException {
        synchronized (test.getId()) {
            Set<String> issueKeys = new HashSet<>();
            String issueKey = TestToIssueMapping.getInstance().getTestIssueKey(job, test.getId());
            if (StringUtils.isNotBlank(issueKey)) {
                issueKeys.add(issueKey);
                return issueKeys;
            }

            IssueInput issueInput = JiraUtils.createIssueInput(job, test, envVars, JiraIssueTrigger.JOB);
            SearchResult searchResult = JiraUtils.findIssues(job, test, envVars, issueInput);
            if (searchResult != null && searchResult.getTotal() > 0) {
                for (Issue issue : searchResult.getIssues()) {
                    issueKeys.add(issue.getKey());
                }
            }
            return issueKeys;
        }
    }

    private static IssueInput createIssueInput(
            Job project, TestResult test, EnvVars envVars, JiraIssueTrigger trigger) {
        final IssueInputBuilder newIssueBuilder = new IssueInputBuilder(
                JobConfigMapping.getInstance().getProjectKey(project),
                JobConfigMapping.getInstance().getIssueType(project));
        // first use the templates and then override them if other configs exist and it is requested from a job
        // execution (not UI badge)
        for (AbstractFields f : JiraTestDataPublisher.JiraTestDataPublisherDescriptor.templates) {
            newIssueBuilder.setFieldInput(f.getFieldInput(test, envVars));
        }

        if (trigger.equals(JiraIssueTrigger.JOB)) {
            for (AbstractFields f : JobConfigMapping.getInstance().getConfig(project)) {
                newIssueBuilder.setFieldInput(f.getFieldInput(test, envVars));
            }
        }
        return newIssueBuilder.build();
    }

    private static String createIssueInput(IssueInput issueInput, CaseResult test, List<String> attachments) {
        final IssueRestClient issueClient =
                JiraUtils.getJiraDescriptor().getRestClient().getIssueClient();
        Promise<BasicIssue> issuePromise = issueClient.createIssue(issueInput);
        String key = issuePromise.claim().getKey();
        Issue issue = issueClient.getIssue(key).claim();
        URI attachmentsUri = issue.getAttachmentsUri();
        if (StringUtils.isNotBlank(test.getStderr())) {
            issueClient
                    .addAttachment(
                            attachmentsUri,
                            new ByteArrayInputStream(test.getStderr().getBytes(StandardCharsets.UTF_8)),
                            "stderr.out")
                    .claim();
        }
        if (StringUtils.isNotBlank(test.getStdout())) {
            issueClient
                    .addAttachment(
                            attachmentsUri,
                            new ByteArrayInputStream(test.getStdout().getBytes(StandardCharsets.UTF_8)),
                            "stdout.out")
                    .claim();
        }
        if (StringUtils.isNotBlank(test.getErrorStackTrace())) {
            issueClient
                    .addAttachment(
                            attachmentsUri,
                            new ByteArrayInputStream(test.getErrorStackTrace().getBytes(StandardCharsets.UTF_8)),
                            "stacktrace.out")
                    .claim();
        }
        if (StringUtils.isNotBlank(test.getErrorDetails())) {
            issueClient
                    .addAttachment(
                            attachmentsUri,
                            new ByteArrayInputStream(test.getErrorDetails().getBytes(StandardCharsets.UTF_8)),
                            "details.out")
                    .claim();
        }

        if (!attachments.isEmpty()) {
            FilePath attachmentStorage = AttachmentPublisher.getAttachmentPath(test.getRun());
            try {
                if (attachmentStorage.exists()) {
                    FilePath attachmentPath =
                            AttachmentPublisher.getAttachmentPath(attachmentStorage, test.getClassName());
                    for (FilePath file : attachmentPath.list()) {
                        if (attachments.contains(file.getName())) {
                            issueClient
                                    .addAttachment(attachmentsUri, file.read(), file.getName())
                                    .claim();
                        }
                    }
                }
            } catch (IOException | InterruptedException e) {
                JiraUtils.log(String.format(
                        "Unable to access attachment storage for %s:%s",
                        test.getName(), test.getRun().getId()));
            }
        }
        return key;
    }

    /**
     * To prevent the creation of duplicates lets see if we can find a pre-existing issue.
     * It is a duplicate if it has the same summary and is open in the project.
     * @param project the project
     * @param test the test
     * @param envVars the environment variables
     * @return a SearchResult. Empty SearchResult means nothing was found.
     */
    public static SearchResult findIssues(Job project, TestResult test, EnvVars envVars, IssueInput issueInput)
            throws RestClientException {
        String projectKey = JobConfigMapping.getInstance().getProjectKey(project);
        FieldInput fi = JiraTestDataPublisher.JiraTestDataPublisherDescriptor.templates
                .get(0)
                .getFieldInput(test, envVars);
        String jql = String.format(
                "resolution = \"unresolved\" and project = \"%s\" and text ~ \"%s\"",
                projectKey, escapeJQL(issueInput.getField(fi.getId()).getValue().toString()));

        return getSearchResult(jql);
    }

    private static SearchResult findUnresolvedJiraIssues(String keys) throws RestClientException {
        String jql = String.format("key in (%s) and resolution != \"unresolved\" ", keys);
        return getSearchResult(jql);
    }

    private static SearchResult getSearchResult(String jql) {
        final Set<String> fields = new HashSet<>();
        fields.add("issueKey");
        fields.add("summary");
        fields.add("issuetype");
        fields.add("created");
        fields.add("updated");
        fields.add("project");
        fields.add("status");
        JiraUtils.log(jql);

        Promise<SearchResult> searchJqlPromise =
                JiraUtils.getJiraDescriptor().getRestClient().getSearchClient().searchJql(jql, 50, 0, fields);
        return searchJqlPromise.claim();
    }

    /**
     * Escape the JQL query of special characters.
     *
     * Based on https://jira.atlassian.com/browse/JRASERVER-25092, currently supported:
     *  + - & | ~ *
     *
     * Also supported ? although JRSERVER-25092 defines the opposite
     *
     * Unsupported:
     *  ! ( ) { } ^ \ /
     *
     * Provides special support for parameterized tests by ignoring the parameter in [ ]
     *
     * @param jql the JQL query.
     * @return the JQL query with special chars escaped.
     */
    static String escapeJQL(String jql) {
        String result = jql.replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\\+", "\\\\+")
                .replace("-", "\\\\-")
                .replace("&", "\\\\&")
                .replace("\\|", "\\\\|")
                .replace("~", "\\\\~")
                .replace("\\*", "\\\\*")
                .replace("?", "\\\\?"); // Although ? char is still not supported based on JRASERVER-25092

        if (result.contains("[")) {
            result = result.substring(0, result.lastIndexOf("[")); // let's remove the parameter part
        }
        return result;
    }
}
