/**
 Copyright 2015 Andrei Tuicu

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.jenkinsci.plugins.JiraTestResultReporter;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.util.ErrorCollection;
import com.atlassian.util.concurrent.Promise;

import hudson.EnvVars;
import hudson.model.AbstractProject;
import hudson.tasks.test.TestResult;
import jenkins.model.Jenkins;

import org.jenkinsci.plugins.JiraTestResultReporter.JiraTestDataPublisher.JiraTestDataPublisherDescriptor;
import org.jenkinsci.plugins.JiraTestResultReporter.config.AbstractFields;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by tuicu.
 */
public class JiraUtils {
    private static final Logger LOGGER = Logger.getLogger("JiraIssuePlugin.log");

    /**
     * Constructs the URL for an issue, given the server url and the issue key
     * @param serverURL the server URL
     * @param issueKey the issuekey
     * @return constructed URL for an issue.
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

    public static void logWarning(String message, Exception e) { LOGGER.log(Level.WARNING, message, e);}
    /**
     * Static getter for the JiraTestDataPublisherDescriptor singleton instance
     * @return JiraTestDataPublisherDescriptor singleton instance
     */
    public static JiraTestDataPublisher.JiraTestDataPublisherDescriptor getJiraDescriptor() {
        return (JiraTestDataPublisher.JiraTestDataPublisherDescriptor) Jenkins.getInstance().getDescriptor(JiraTestDataPublisher.class);
    }

    /**
     * Form a single string from the messages returned in a RestClientException
     * @param e a RestClientException
     * @param newLine string representing the new line
     * @return error message
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

    public static IssueInput createIssueInput(AbstractProject project, TestResult test, EnvVars envVars) {
        final IssueInputBuilder newIssueBuilder = new IssueInputBuilder(
                JobConfigMapping.getInstance().getProjectKey(project),
                JobConfigMapping.getInstance().getIssueType(project));
        //first use the templates and then override them if other configs exist
        for(AbstractFields f : JiraTestDataPublisher.JiraTestDataPublisherDescriptor.TEMPLATES) {
            newIssueBuilder.setFieldInput(f.getFieldInput(test, envVars));
        }
        for (AbstractFields f : JobConfigMapping.getInstance().getConfig(project)) {
            newIssueBuilder.setFieldInput(f.getFieldInput(test, envVars));
        }
        return newIssueBuilder.build();
    }

    public static String createIssue(IssueInput issueInput) {
        final IssueRestClient issueClient = JiraUtils.getJiraDescriptor().getRestClient().getIssueClient();
        Promise<BasicIssue> issuePromise = issueClient.createIssue(issueInput);
        return issuePromise.claim().getKey();
    }
    
    /**
     * To prevent the creation of duplicates lets see if we can find a pre-existing issue.
     * It is a duplicate if it has the same summary and is open in the project.
     * @param project the project
     * @param test the test
     * @param envVars the environment variables
     * @return a SearchResult. Empty SearchResult means nothing was found.
     */
    public static SearchResult findIssues(AbstractProject project, TestResult test, EnvVars envVars, IssueInput issueInput)
    {
        String projectKey = JobConfigMapping.getInstance().getProjectKey(project);
        FieldInput fi = JiraTestDataPublisher.JiraTestDataPublisherDescriptor.TEMPLATES.get(0).getFieldInput(test, envVars);
        String jql = String.format("status != \"closed\" and project = \"%s\" and text ~ \"%s\"", projectKey, escapeJQL(issueInput.getField(fi.getId()).getValue().toString()));
        
        final Set<String > fields = new HashSet<>();
        
        fields.add("summary");
        fields.add("issuetype");
        fields.add("created");
        fields.add("updated");
        fields.add("project");
        fields.add("status");
        
        log(jql);
        JiraTestDataPublisherDescriptor desc = JiraUtils.getJiraDescriptor();
        JiraRestClient restClient = desc.getRestClient();
        SearchRestClient searchClient = restClient.getSearchClient();
        Promise<SearchResult> searchJqlPromise = searchClient.searchJql(jql, 50, 0, fields);
        return searchJqlPromise.claim();
    }
    
    
    /**
     * To Prevent the number of open bugs logged for the day by that user.
     * if limit is reached, no more bugs for the project are created for the day.
     * @param project the project
     * @param test the test
     * @param username to retrieve the bugs based on user
     * @return a SearchResult. Empty SearchResult means nothing was found.
     */
    
    public static int bugsPerDay(AbstractProject project, TestResult test,String username)
    {
        String projectKey = JobConfigMapping.getInstance().getProjectKey(project);
        String jql = String.format("project = \"%s\" and Created >= startOfDay() and creator= \"%s\"",projectKey,username);
        log(jql);
        
        final Set<String > fields = new HashSet<>();
        
        fields.add("summary");
        fields.add("issuetype");
        fields.add("created");
        fields.add("updated");
        fields.add("project");
        fields.add("status");
   
        Promise<SearchResult> searchJqlPromise = JiraUtils.getJiraDescriptor().getRestClient().getSearchClient().searchJql(jql, 50, 0, fields);
        return searchJqlPromise.claim().getTotal();
    }
    
    /**
     * Escape the JQL query of special characters.
     *
     * Currently:
     *  + - & | ! ( ) { } [ ] ^ ~ * ? \ :
     *
     * Reference:
     *  https://confluence.atlassian.com/jiracoreserver073/search-syntax-for-text-fields-861257223.html
     *
     * @param jql the JQL query.
     * @return the JQL query with special chars escaped.
     */
    static String escapeJQL(String jql)
    {
        return jql.replaceAll("'","\\'")
                .replaceAll("\"","\\\"")
                .replaceAll("\\+", "\\\\+")
                .replaceAll("-", "\\\\-")
                .replaceAll("&", "\\\\&")
                .replaceAll("\\|", "\\\\|")
                .replaceAll("!", "\\\\!")
                .replaceAll("\\(", "\\\\(")
                .replaceAll("\\)", "\\\\)")
                .replaceAll("\\{", "\\\\{")
                .replaceAll("}", "\\\\}")
                .replaceAll("\\[", "\\\\[")
                .replaceAll("]", "\\\\]")
                .replaceAll("\\^", "\\\\^")
                .replaceAll("~", "\\\\~")
                .replaceAll("\\*", "\\\\*")
                .replaceAll("\\?", "\\\\\\?")
                .replaceAll("\\\\","\\\\\\\\")
                .replaceAll(":", "\\\\\\\\:");
    }
}
