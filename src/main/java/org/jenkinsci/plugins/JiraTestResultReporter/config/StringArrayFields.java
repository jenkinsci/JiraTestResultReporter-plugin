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

import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import hudson.EnvVars;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Descriptor;
import hudson.tasks.test.TestResult;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.JiraTestResultReporter.JiraTestDataPublisher;
import org.jenkinsci.plugins.JiraTestResultReporter.JiraUtils;
import org.jenkinsci.plugins.JiraTestResultReporter.VariableExpander;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Created by tuicu.
 * Class for fields that accept multiple string values
 */
public class StringArrayFields extends AbstractFields {
    public static final long serialVersionUID = -8871121603596592222L;
    private String fieldKey;
    private List<Entry> values;

    /**
     * Constructor
     * @param fieldKey
     * @param values
     */
    @DataBoundConstructor
    public StringArrayFields(String fieldKey, List<Entry> values) {
        this.fieldKey = fieldKey;
        this.values = values;
    }

    /**
     * Getter for the field key
     * @return
     */
    public String getFieldKey() {
        return fieldKey;
    }

    /**
     * Getter for the field
     * @return
     */
    public List<Entry> getValues() {
        return values;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " #" + fieldKey + " : " + values.toString() + "#";
    }

    /**
     * Getter for the FieldInput object
     * @param test
     * @param envVars
     * @return
     */
    @Override
    public FieldInput getFieldInput(TestResult test, EnvVars envVars) {
        ArrayList<String> stringList = new ArrayList<String>();
        for (Entry v : values) {
            stringList.add(VariableExpander.expandVariables(test, envVars, v.getValue()));
        }
        FieldInput fieldInput = new FieldInput(fieldKey, stringList);
        return fieldInput;
    }

    @Override
    public Object readResolve() {
        return this;
    }

    /**
     * Descriptor, required for the hetero-list
     */
    @Symbol("jiraStringArrayField")
    @Extension
    public static class StringArrayFieldsDescriptor extends Descriptor<AbstractFields> {

        @Override
        public String getDisplayName() {
            return "String Array Field";
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
                return jiraDescriptor.getCacheEntry(projectKey, issueType).getStringArrayFieldBox();
            } catch (NullPointerException e) {
                return new ListBoxModel();
            }
        }
    }
}
