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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

/**
 * Converts string field values to Atlassian Document Format (ADF) for Jira Cloud REST API v3.
 *
 * <h2>ADF Requirement Rules</h2>
 * <p>
 * In Jira Cloud REST API v3, only rich-text/multi-line fields require ADF format:
 * </p>
 * <ul>
 *   <li><b>Standard fields:</b> description, environment, comment (body array), worklog (comment field)</li>
 *   <li><b>Custom fields:</b> Those with schema.custom ending in ":textarea" (multi-line text)</li>
 * </ul>
 *
 * <h2>Fields That Never Use ADF</h2>
 * <ul>
 *   <li>summary - plain text only (the ticket title)</li>
 *   <li>labels - array of strings</li>
 *   <li>components - structured objects</li>
 *   <li>Dropdowns/Select Lists - e.g., priority, status</li>
 *   <li>Custom fields with ":textfield" - single-line plain text</li>
 * </ul>
 *
 * <h2>Verification Method</h2>
 * <p>
 * To verify if a field requires ADF, examine its response from an existing ticket using API v3:
 * <code>GET /rest/api/3/issue/{issueIdOrKey}</code>
 * </p>
 * <p>
 * Any field returning a nested JSON with <code>"type": "doc"</code> and <code>"version": 1</code>
 * is an ADF field.
 * </p>
 */
public class AdfFieldConverter {

    // Cache of rich-text fields per project+issueType to avoid repeated API calls
    private static final Map<String, Set<String>> richTextFieldCache = new ConcurrentHashMap<>();

    /**
     * Converts a FieldInput to use ADF format if it's a rich-text field.
     * Queries Jira metadata to determine which fields need ADF conversion.
     *
     * @param fieldInput The original field input
     * @param project The Jenkins project (for metadata lookup)
     * @return FieldInput with ADF conversion applied if field needs it, otherwise unchanged
     */
    public static FieldInput convertIfNeeded(FieldInput fieldInput, Job<?, ?> project) {
        String fieldKey = fieldInput.getId();
        Object value = fieldInput.getValue();

        // Only convert string values
        if (!(value instanceof String)) {
            return fieldInput;
        }

        // Check if this field requires ADF by querying metadata
        if (requiresADF(fieldKey, project)) {
            String textValue = (String) value;
            return new FieldInput(fieldKey, convertToADF(textValue));
        }

        return fieldInput;
    }

    /**
     * Checks if a field requires ADF format by querying Jira metadata.
     * First tries via jira-rest-client, falls back to direct HTTP call if that fails.
     *
     * @param fieldKey The field key
     * @param project The Jenkins project
     * @return true if the field requires ADF, false otherwise
     */
    private static boolean requiresADF(String fieldKey, Job<?, ?> project) {
        String projectKey = JobConfigMapping.getInstance().getProjectKey(project);
        Long issueType = JobConfigMapping.getInstance().getIssueType(project);
        String cacheKey = projectKey + ":" + issueType;

        // Check cache first
        Set<String> richTextFields = richTextFieldCache.get(cacheKey);
        if (richTextFields != null) {
            return richTextFields.contains(fieldKey);
        }

        // Try to get metadata via jira-rest-client
        boolean metadataFailed = false;
        try {
            JiraTestDataPublisher.JiraTestDataPublisherDescriptor descriptor = JiraUtils.getJiraDescriptor();
            MetadataCache.CacheEntry cacheEntry = descriptor.getCacheEntry(projectKey, issueType.toString());

            if (cacheEntry != null) {
                Map<String, CimFieldInfo> fieldInfoMap = cacheEntry.getFieldInfoMap();
                if (fieldInfoMap != null) {
                    richTextFields = extractRichTextFields(fieldInfoMap);
                    richTextFieldCache.put(cacheKey, richTextFields);
                    return richTextFields.contains(fieldKey);
                }
            }
            // If we got here, cacheEntry or fieldInfoMap was null - metadata unavailable
            metadataFailed = true;
        } catch (IllegalArgumentException e) {
            // Known issue: jira-rest-client 7.0.1 doesn't support COPY operation from API v3
            // Silently fall back to direct API call
            metadataFailed = true;
        } catch (Exception e) {
            // Silently fall back to direct API call
            metadataFailed = true;
        }

        // Fall back to direct HTTP call if metadata was unavailable
        if (metadataFailed) {
            richTextFields = getStringFieldsViaDirectCall(projectKey, issueType.toString());
            if (richTextFields != null && !richTextFields.isEmpty()) {
                richTextFieldCache.put(cacheKey, richTextFields);
                return richTextFields.contains(fieldKey);
            }
        }

        // If all else fails, return false (don't convert)
        JiraUtils.logWarning("Could not determine if field '" + fieldKey
                + "' requires ADF - not converting (this may cause 400 errors)");
        return false;
    }

    /**
     * Extracts rich-text field keys from CimFieldInfo map (metadata from jira-rest-client).
     * Identifies fields that require ADF format based on their schema type.
     *
     * @param fieldInfoMap Map of field ID to CimFieldInfo
     * @return Set of field IDs that require ADF format
     */
    private static Set<String> extractRichTextFields(Map<String, CimFieldInfo> fieldInfoMap) {
        Set<String> richTextFields = new HashSet<>();
        for (Map.Entry<String, CimFieldInfo> entry : fieldInfoMap.entrySet()) {
            String fieldId = entry.getKey();
            CimFieldInfo fieldInfo = entry.getValue();

            // Standard fields that always require ADF in API v3
            if ("description".equals(fieldId)
                    || "environment".equals(fieldId)
                    || "comment".equals(fieldId)
                    || "worklog".equals(fieldId)) {
                richTextFields.add(fieldId);
                continue;
            }

            // Fields with schema type "doc" are ADF fields
            if (fieldInfo.getSchema() != null
                    && "doc".equals(fieldInfo.getSchema().getType())) {
                richTextFields.add(fieldId);
            }
        }
        return richTextFields;
    }

    /**
     * Gets all ADF-required fields by making a direct HTTP call to Jira API /rest/api/3/field.
     *
     * <p>In Jira Cloud REST API v3, rich-text fields require ADF format:</p>
     * <ul>
     *   <li>Standard fields: description, environment, comment, worklog</li>
     *   <li>Custom fields with schema.custom ending in ":textarea" (multi-line text)</li>
     * </ul>
     *
     * <p>Fields that do NOT require ADF:</p>
     * <ul>
     *   <li>summary (plain text title)</li>
     *   <li>Custom fields with ":textfield" (single-line text)</li>
     *   <li>labels, components, dropdowns, etc.</li>
     * </ul>
     *
     * @param projectKey The Jira project key (unused, kept for potential future filtering)
     * @param issueType The issue type ID (unused, kept for potential future filtering)
     * @return Set of field IDs that require ADF format, or null if the API call fails
     */
    private static Set<String> getStringFieldsViaDirectCall(String projectKey, String issueType) {
        HttpURLConnection connection = null;
        try {
            JiraTestDataPublisher.JiraTestDataPublisherDescriptor descriptor = JiraUtils.getJiraDescriptor();
            String jiraUrl = descriptor.getJiraUrl();
            if (jiraUrl.endsWith("/")) {
                jiraUrl = jiraUrl.substring(0, jiraUrl.length() - 1);
            }

            // Use /rest/api/3/field to get all field definitions
            String url = jiraUrl + "/rest/api/3/field";

            connection = (HttpURLConnection) new URI(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            // Add authentication
            String username = descriptor.getUsername();
            String password = descriptor.getPassword().getPlainText();
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", "Basic " + encodedAuth);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                return parseAdfFieldsFromJson(response.toString());
            } else {
                // Read error response body for debugging
                String errorBody = "";
                try {
                    BufferedReader errorReader = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8));
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    errorReader.close();
                    errorBody = errorResponse.toString();
                } catch (Exception ignored) {
                }
                JiraUtils.logWarning("Failed to get field metadata via direct API call, status: " + responseCode
                        + ", body: " + errorBody);
            }
        } catch (Exception e) {
            JiraUtils.logWarning("Error making direct API call for field metadata: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    /**
     * Parses JSON response from /rest/api/3/field to extract ADF-required fields.
     *
     * In Jira Cloud REST API v3, ADF is required for rich-text/multi-line fields:
     * - Standard fields: description, environment, comment (body array), worklog (comment field)
     * - Custom fields where schema.custom ends with ":textarea" (multi-line text fields)
     *
     * Fields that never use ADF:
     * - summary (plain text only)
     * - Custom fields with ":textfield" (single-line text)
     * - labels, components, priority, status, and other dropdowns/select lists
     */
    private static Set<String> parseAdfFieldsFromJson(String jsonResponse) {
        Set<String> adfFields = new HashSet<>();
        try {
            JSONArray fields = new JSONArray(jsonResponse);

            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                String fieldId = field.getString("id");

                if (!field.has("schema")) {
                    continue;
                }

                JSONObject schema = field.getJSONObject("schema");

                // Standard rich-text fields that always require ADF in API v3
                // These support rich formatting (bold, bullets, etc.)
                if ("description".equals(fieldId)
                        || "environment".equals(fieldId)
                        || "comment".equals(fieldId)
                        || "worklog".equals(fieldId)) {
                    adfFields.add(fieldId);
                    continue;
                }

                // Custom fields: check if it's a textarea (multi-line text = ADF)
                // Single-line textfields use plain strings, multi-line textareas use ADF
                if (schema.has("custom")) {
                    String customType = schema.getString("custom");
                    if (customType.endsWith(":textarea")) {
                        adfFields.add(fieldId);
                    }
                }
            }
        } catch (Exception e) {
            JiraUtils.logWarning("Error parsing field metadata JSON: " + e.getMessage());
        }
        return adfFields;
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
