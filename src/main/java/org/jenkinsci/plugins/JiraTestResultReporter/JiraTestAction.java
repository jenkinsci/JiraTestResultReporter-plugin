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
import com.atlassian.jira.rest.client.api.domain.Issue;
import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestAction;
import hudson.util.FormValidation;
import io.atlassian.util.concurrent.Promise;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.JiraTestResultReporter.restclientextensions.FullStatus;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.bind.JavaScriptMethod;

/**
 * Created by tuicu.
 */
public class JiraTestAction extends TestAction implements ExtensionPoint, Describable<JiraTestAction> {

    private String issueKey = null;
    private CaseResult test;
    private JiraTestData testData;
    private String issueStatus;
    private String statusColor;
    private String issueSummary;
    private Job job; // the same as project if it's not a matrix build
    private Job project;

    /**
     * Getter for issue status, called from issueStatus.jelly
     * @return string representing the issue status
     */
    public String getIssueStatus() {
        return issueStatus;
    }

    /**
     * Getter for issue color, called from issueStatus.jelly
     * @return tring representing the issue color
     */
    public String getStatusColor() {
        return statusColor;
    }

    /**
     * Getter for environment variables
     * @return environment variables map
     */
    public EnvVars getEnvVars() {
        return testData.getEnvVars();
    }

    /**
     * Getter for the JUnit test associated with this TestAction
     * @return the test object
     */
    public CaseResult getTest() {
        return test;
    }

    /**
     * Constructor
     * @param testData
     * @param test the JUnit test associated with this TestAction
     */
    public JiraTestAction(JiraTestData testData, CaseResult test) {
        project = initProject();
        if (project instanceof MatrixProject) {
            job = (Job) Jenkins.get().getItemByFullName(testData.getEnvVars().get("JOB_NAME"));
        } else {
            job = project;
        }

        if (project == null || job == null) {
            // fix for interaction with Test stability history plugin
            return;
        }

        this.testData = testData;
        this.test = test;
        for (String key : JiraUtils.searchIssueKeys(job, testData.getEnvVars(), test)) {
            issueKey = key;
            IssueRestClient issueRestClient =
                    JiraUtils.getJiraDescriptor().getRestClient().getIssueClient();
            try {
                Issue issue = issueRestClient.getIssue(key).claim();
                issueStatus = issue.getStatus().getName();
                issueSummary = issue.getSummary();
                JiraTestDataPublisher.JiraTestDataPublisherDescriptor jiraDescriptor = JiraUtils.getJiraDescriptor();
                if (jiraDescriptor.getStatusesMap() != null) {
                    FullStatus status = jiraDescriptor.getStatusesMap().get(issueStatus);
                    statusColor = status != null ? status.getColorName() : null;
                }
            } catch (Exception e) {
                JiraUtils.logError("The issue might be deleted, or there is no internet connection, etc.", e);
            }
        }
    }

    /**
     * Method for initializing the project. Used in constructor only.
     * @return
     */
    private Job initProject() {
        if (Stapler.getCurrentRequest2() == null) {
            return null;
        }

        List<Ancestor> ancestors = Stapler.getCurrentRequest2().getAncestors();
        for (Ancestor ancestor : ancestors) {
            if (ancestor.getObject() instanceof AbstractProject) {
                return (AbstractProject) ancestor.getObject();
            }
        }

        Job lastAncestor = null;
        for (Ancestor ancestor : ancestors) {
            if (ancestor.getObject() instanceof Job) {
                lastAncestor = (Job) ancestor.getObject();
            }
        }
        return lastAncestor;
    }

    /**
     * Getter for the issue key, called from badge.jelly
     * @return
     */
    public String getIssueKey() {
        return issueKey;
    }

    /**
     * Getter for the issue URL, called from badge.jelly
     * @return
     */
    public String getIssueUrl() {
        return JiraUtils.getIssueURL(JiraUtils.getJiraDescriptor().getJiraUrl(), issueKey);
    }

    /**
     * Getter to find is the test is failing
     */
    public boolean isTestFailing() {
        return test.isFailed();
    }

    public String getIssueSummary() {
        return issueSummary;
    }

    /**
     * Method for linking an issue to this test, called from badge.jelly
     * @param issueKey the key of the issue (ex. TST-256)
     * @return null if everything was Ok, an object with the error message if not
     */
    @JavaScriptMethod
    public FormValidation setIssueKey(String issueKey) {
        synchronized (test.getId()) {
            if (TestToIssueMapping.getInstance().getTestIssueKey(job, test.getId()) != null) {
                return null;
            }
            if (isValidIssueKey(issueKey)) {
                this.issueKey = issueKey;
                TestToIssueMapping.getInstance().addTestToIssueMapping(job, test.getId(), issueKey);
                return null;
            }
            return FormValidation.error("Not a valid issue key");
        }
    }

    /**
     * Method for unlinking the issue associated with this test
     */
    @JavaScriptMethod
    public void clearIssueKey() {
        TestToIssueMapping.getInstance().removeTestToIssueMapping(job, test.getId(), issueKey);
        issueKey = null;
    }

    /**
     * Getter for the icon file name
     * @return null
     */
    @Override
    public String getIconFileName() {
        return null;
    }

    /**
     * Getter for the url name
     * @return class' simple name
     */
    @Override
    public String getUrlName() {
        return getClass().getSimpleName();
    }

    /**
     * Getter for the url name
     * @return class' simple name
     */
    @Override
    public String getDisplayName() {
        return getClass().getSimpleName();
    }

    /**
     * Getter for the descriptor
     * @return descriptor instance
     */
    @Override
    public Descriptor<JiraTestAction> getDescriptor() {
        return (JiraTestActionDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * Descriptor for JiraTestAction
     */
    @Extension
    public static class JiraTestActionDescriptor extends Descriptor<JiraTestAction> {

        @Override
        public String getDisplayName() {
            return clazz.getSimpleName();
        }
    }

    /**
     * Method for creating an issue in jira, called from badge.jelly
     * @return a {@link FormValidation} with the result of the creation operation
     */
    @JavaScriptMethod
    public FormValidation createIssue() {
        try {
            String id = JiraUtils.createIssue(
                    job, project, testData.getEnvVars(), test, JiraIssueTrigger.UI, Collections.emptyList());
            return StringUtils.isBlank(id) ? FormValidation.error("Duplicate already exists") : setIssueKey(id);
        } catch (RestClientException e) {
            JiraUtils.logError("Error when creating issue", e);
            return FormValidation.error(JiraUtils.getErrorMessage(e, "\n"));
        }
    }

    /**
     * Method for checking if a issue key is valid
     * @param issueKey
     */
    public boolean isValidIssueKey(String issueKey) {
        if (JobConfigMapping.getInstance()
                        .getIssueKeyPattern(project)
                        .matcher(issueKey)
                        .matches()
                == false) {
            return false;
        }
        IssueRestClient restClient =
                JiraUtils.getJiraDescriptor().getRestClient().getIssueClient();
        try {
            Promise<Issue> issuePromise = restClient.getIssue(issueKey);
            issuePromise.claim();
        } catch (RestClientException e) {
            JiraUtils.logError("Error when validating issue", e);
            return false;
        }
        return true;
    }
}
