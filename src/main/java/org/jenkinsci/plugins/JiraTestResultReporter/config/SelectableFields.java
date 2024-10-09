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
package org.jenkinsci.plugins.JiraTestResultReporter.config;

import com.atlassian.jira.rest.client.api.IdentifiableEntity;
import com.atlassian.jira.rest.client.api.NamedEntity;
import com.atlassian.jira.rest.client.api.domain.CustomFieldOption;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import hudson.EnvVars;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Descriptor;
import hudson.tasks.test.TestResult;
import hudson.util.ListBoxModel;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.JiraTestResultReporter.JiraTestDataPublisher;
import org.jenkinsci.plugins.JiraTestResultReporter.JiraUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Created by tuicu.
 * Class for fields with a single, predefined value (ex. Priority)
 */
public class SelectableFields extends AbstractFields {
    public static final long serialVersionUID = 7570781225890517710L;
    private String fieldKey;
    private String value;
    private transient FieldInput fieldInput;

    /**
     * Constructor
     * @param fieldKey
     * @param value
     */
    @DataBoundConstructor
    public SelectableFields(String fieldKey, String value) {
        this.fieldKey = fieldKey;
        this.value = value;
        fieldInput = new FieldInput(fieldKey, ComplexIssueInputFieldValue.with("id", value));
    }

    /**
     * Getter for the field key
     * @return
     */
    public String getFieldKey() {
        return fieldKey;
    }

    /**
     * Getter for the field value
     * @return
     */
    public String getValue() {
        return value;
    }

    @Override
    public Object readResolve() {
        fieldInput = new FieldInput(fieldKey, ComplexIssueInputFieldValue.with("id", value));
        return this;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " #" + fieldKey + " : " + value + "#";
    }

    /**
     * Getter for the FieldInput object
     * @param test
     * @param envVars
     * @return
     */
    @Override
    public FieldInput getFieldInput(TestResult test, EnvVars envVars) {
        return fieldInput;
    }

    /**
     * Descriptor, required for the hetero-list
     */
    @Symbol("jiraSelectableField")
    @Extension
    public static class SelectableFieldsDescriptor extends Descriptor<AbstractFields> {
        @Override
        public String getDisplayName() {
            return "Selectable Field";
        }

        /**
         * Method for filling the field keys selectable
         * @param projectKey
         * @param issueType
         * @return
         */
        public ListBoxModel doFillFieldKeyItems(
                @QueryParameter @RelativePath("..") String projectKey,
                @QueryParameter @RelativePath("..") String issueType) {
            JiraTestDataPublisher.JiraTestDataPublisherDescriptor jiraDescriptor = JiraUtils.getJiraDescriptor();
            try {
                return jiraDescriptor.getCacheEntry(projectKey, issueType).getSelectableFieldBox();
            } catch (NullPointerException e) {
                return new ListBoxModel();
            }
        }

        /**
         * Method for filling the selectable with the allowed values
         * @param projectKey
         * @param issueType
         * @param fieldKey
         * @return
         */
        public ListBoxModel doFillValueItems(
                @QueryParameter @RelativePath("..") String projectKey,
                @QueryParameter @RelativePath("..") String issueType,
                @QueryParameter String fieldKey) {
            ListBoxModel listBox = new ListBoxModel();
            JiraTestDataPublisher.JiraTestDataPublisherDescriptor jiraDescriptor = JiraUtils.getJiraDescriptor();
            try {
                Iterable<Object> values = jiraDescriptor
                        .getCacheEntry(projectKey, issueType)
                        .getFieldInfoMap()
                        .get(fieldKey)
                        .getAllowedValues();
                for (Object o : values) {
                    if (o instanceof CustomFieldOption) {
                        CustomFieldOption option = (CustomFieldOption) o;
                        listBox.add(option.getValue(), option.getId().toString());
                    } else if (o instanceof IdentifiableEntity && o instanceof NamedEntity) {
                        listBox.add(
                                ((NamedEntity) o).getName(),
                                ((IdentifiableEntity<Long>) o).getId().toString());
                    }
                }
                return listBox;
            } catch (NullPointerException e) {
                return listBox;
            }
        }
    }
}
