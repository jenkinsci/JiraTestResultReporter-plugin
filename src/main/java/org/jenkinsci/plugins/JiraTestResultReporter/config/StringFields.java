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

import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import hudson.EnvVars;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Descriptor;
import hudson.tasks.test.TestResult;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.JiraTestResultReporter.JiraTestDataPublisher.JiraTestDataPublisherDescriptor;
import org.jenkinsci.plugins.JiraTestResultReporter.JiraUtils;
import org.jenkinsci.plugins.JiraTestResultReporter.VariableExpander;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Created by tuicu.
 * Class for fields that accept single string values
 */
public class StringFields extends AbstractFields {
    public static final long serialVersionUID = 4298649925601364399L;
    private static final ListBoxModel DEFAULT_MODEL;

    private String fieldKey;
    private String value;

    static {
        DEFAULT_MODEL = new ListBoxModel();
        DEFAULT_MODEL.add(new ListBoxModel.Option("Summary", "summary", false));
        DEFAULT_MODEL.add(new ListBoxModel.Option("Description", "description", false));
    }

    /**
     * Constructor
     * @param fieldKey
     * @param value
     */
    @DataBoundConstructor
    public StringFields(String fieldKey, String value) {
        this.fieldKey = fieldKey;
        this.value = value;
    }

    /**
     * Getter for the field key
     * @return
     */
    public String getFieldKey() {
        return fieldKey;
    }

    /**
     * Getter for value
     * @return
     */
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " #" + fieldKey + " : " + value + "#";
    }

    /**
     * Converts plain text to Atlassian Document Format (ADF) required by Jira API v3
     * @param text Plain text to convert
     * @return ADF structure as ComplexIssueInputFieldValue
     */
    private static ComplexIssueInputFieldValue convertToADF(String text) {
        // Create text node
        Map<String, Object> textNode = new HashMap<>();
        textNode.put("type", "text");
        textNode.put("text", text);

        // Create paragraph with text content
        Map<String, Object> paragraph = new HashMap<>();
        paragraph.put("type", "paragraph");
        List<ComplexIssueInputFieldValue> paragraphContent = new ArrayList<>();
        paragraphContent.add(new ComplexIssueInputFieldValue(textNode));
        paragraph.put("content", paragraphContent);

        // Create document with paragraph
        Map<String, Object> doc = new HashMap<>();
        doc.put("version", 1);
        doc.put("type", "doc");
        List<ComplexIssueInputFieldValue> docContent = new ArrayList<>();
        docContent.add(new ComplexIssueInputFieldValue(paragraph));
        doc.put("content", docContent);

        return new ComplexIssueInputFieldValue(doc);
    }

    /**
     * Getter for the FieldInput object
     * @param test
     * @param envVars
     * @return
     */
    @Override
    public FieldInput getFieldInput(TestResult test, EnvVars envVars) {
        String expandedValue = VariableExpander.expandVariables(test, envVars, value);
        return new FieldInput(fieldKey, convertToADF(expandedValue));
    }

    @Override
    public Object readResolve() {
        return this;
    }

    /**
     * Descriptor, required for the hetero-list
     */
    @Symbol("jiraStringField")
    @Extension
    public static class StringFieldsDescriptor extends Descriptor<AbstractFields> {

        @Override
        public String getDisplayName() {
            return "String Field";
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
            JiraTestDataPublisherDescriptor jiraDescriptor = JiraUtils.getJiraDescriptor();
            if (projectKey.equals("") || issueType.equals("")) {
                return DEFAULT_MODEL;
            }
            try {
                return jiraDescriptor.getCacheEntry(projectKey, issueType).getStringFieldBox();
            } catch (NullPointerException e) {
                return DEFAULT_MODEL;
            }
        }
    }
}
