package org.jenkinsci.plugins.JiraTestResultReporter.config;

import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.UserRestClient;
import com.atlassian.jira.rest.client.api.domain.User;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import hudson.EnvVars;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Descriptor;
import hudson.tasks.test.TestResult;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.codehaus.plexus.util.StringUtils;
import org.jenkinsci.plugins.JiraTestResultReporter.JiraTestDataPublisher;
import org.jenkinsci.plugins.JiraTestResultReporter.JiraUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Created by tuicu on 17/08/16.
 * Class for fields that accept user values
 */
public class UserFields extends AbstractFields {
    private final String fieldKey;
    private final String value;
    private transient User user;

    /**
     * Constructor
     * @param fieldKey fieldKey
     * @param value value
     */
    @DataBoundConstructor
    public UserFields(String fieldKey, String value) {
        this.fieldKey = fieldKey;
        this.value = value;
        this.user = JiraUtils.getJiraDescriptor().getRestClient().getUserClient().getUser(value).claim();
    }

    /**
     * Getter for the FieldInput object
     * @param test the TestResult
     * @param envVars the EnvVars
     * @return FieldInput
     */
    @Override
    public FieldInput getFieldInput(TestResult test, EnvVars envVars) {
        return new FieldInput(fieldKey, ComplexIssueInputFieldValue.with("name", user.getName()));
    }


    @Override
    public Object readResolve() {
        this.user = JiraUtils.getJiraDescriptor().getRestClient().getUserClient().getUser(value).claim();
        return this;
    }

    /**
     * Getter for the field key
     * @return field key
     */
    public String getFieldKey() { return fieldKey; }

    /**
     * Getter for value
     * @return value
     */
    public String getValue() { return value; }

    @Extension
    public static class UserFieldsDescriptor extends Descriptor<AbstractFields> {
        @Override
        public String getDisplayName() {
            return "User Field";
        }

        /**
         * Method for filling the field keys selectable
         * @param projectKey projectKey
         * @param issueType issueType
         * @return ListBoxModel
         */
        public ListBoxModel doFillFieldKeyItems(@QueryParameter @RelativePath("..") String projectKey,
                                                @QueryParameter @RelativePath("..") String issueType) {
            JiraTestDataPublisher.JiraTestDataPublisherDescriptor jiraDescriptor = JiraUtils.getJiraDescriptor();
            try {
                return jiraDescriptor.getCacheEntry(projectKey, issueType).getUserFieldBox();
            }
            catch (NullPointerException e) {
                return new ListBoxModel();
            }
        }

        /**
         * Validation for the specified user name
         * @param value username provided in the form
         * @return FormValidation
         */
        public FormValidation doCheckValue(@QueryParameter String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error("You need to specify a user");
            }

            UserRestClient userRestClient = JiraUtils.getJiraDescriptor().getRestClient().getUserClient();
            try {
                User user = userRestClient.getUser(value).claim();
                return FormValidation.ok();
            } catch (RestClientException e) {
                return FormValidation.error(JiraUtils.getErrorMessage(e, "\n"));
            }
        }

    }
}
