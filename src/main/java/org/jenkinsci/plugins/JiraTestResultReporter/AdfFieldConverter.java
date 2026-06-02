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

import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import hudson.model.Job;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts string field values to Atlassian Document Format (ADF) for Jira API v3.
 * API v3 requires string fields to use ADF format for proper rendering.
 */
public class AdfFieldConverter {

    /**
     * Converts a FieldInput to use ADF format if it's a string value.
     * All string fields are converted to ADF format for Jira API v3 compatibility.
     *
     * @param fieldInput The original field input
     * @param project The Jenkins project (unused, kept for compatibility)
     * @return FieldInput with ADF conversion applied if value is a string, otherwise unchanged
     */
    public static FieldInput convertIfNeeded(FieldInput fieldInput, Job<?, ?> project) {
        String fieldKey = fieldInput.getId();
        Object value = fieldInput.getValue();

        // Only convert string values to ADF
        if (value instanceof String) {
            String textValue = (String) value;
            return new FieldInput(fieldKey, convertToADF(textValue));
        }

        return fieldInput;
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
