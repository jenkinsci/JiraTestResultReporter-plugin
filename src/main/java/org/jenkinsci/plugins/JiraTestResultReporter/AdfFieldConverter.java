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

import com.atlassian.jira.rest.client.api.domain.CimFieldInfo;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import hudson.model.Job;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts field values to Atlassian Document Format (ADF) when required by Jira API v3.
 * Rich text fields need ADF format, which this class handles automatically based on field metadata.
 */
public class AdfFieldConverter {

    /**
     * Converts a FieldInput to use ADF format if the field requires it.
     * Checks field metadata to determine if ADF conversion is needed.
     *
     * @param fieldInput The original field input
     * @param project The Jenkins project (used to lookup field metadata)
     * @return FieldInput with ADF conversion applied if needed, otherwise unchanged
     */
    public static FieldInput convertIfNeeded(FieldInput fieldInput, Job<?, ?> project) {
        String fieldKey = fieldInput.getId();
        Object value = fieldInput.getValue();

        // Only convert string values
        if (!(value instanceof String)) {
            return fieldInput;
        }

        String textValue = (String) value;

        // Check if this field needs ADF by looking at field metadata
        if (requiresADF(fieldKey, project)) {
            return new FieldInput(fieldKey, convertToADF(textValue));
        }

        return fieldInput;
    }

    /**
     * Checks if a field requires ADF format based on its schema in Jira.
     *
     * @param fieldKey The field key
     * @param project The Jenkins project
     * @return true if the field requires ADF, false otherwise
     */
    private static boolean requiresADF(String fieldKey, Job<?, ?> project) {
        try {
            JiraTestDataPublisher.JiraTestDataPublisherDescriptor descriptor = JiraUtils.getJiraDescriptor();
            String projectKey = JobConfigMapping.getInstance().getProjectKey(project);
            Long issueType = JobConfigMapping.getInstance().getIssueType(project);

            MetadataCache.CacheEntry cacheEntry = descriptor.getCacheEntry(projectKey, issueType.toString());
            if (cacheEntry == null) {
                return false;
            }

            Map<String, CimFieldInfo> fieldInfoMap = cacheEntry.getFieldInfoMap();
            if (fieldInfoMap == null) {
                return false;
            }

            CimFieldInfo fieldInfo = fieldInfoMap.get(fieldKey);
            if (fieldInfo == null || fieldInfo.getSchema() == null) {
                return false;
            }

            // Fields with schema type "doc" are ADF/rich-text fields in API v3
            String schemaType = fieldInfo.getSchema().getType();
            return "doc".equals(schemaType);

        } catch (Exception e) {
            // If we can't determine, assume no ADF needed (safer for compatibility)
            JiraUtils.log("Unable to determine if field '" + fieldKey + "' requires ADF: " + e.getMessage());
            return false;
        }
    }

    /**
     * Converts plain text to Atlassian Document Format (ADF).
     *
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
}
