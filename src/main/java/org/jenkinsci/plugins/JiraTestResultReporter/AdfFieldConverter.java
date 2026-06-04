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
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

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
     * Converts Jira wiki markup text to Atlassian Document Format (ADF).
     *
     * <p>Supported wiki markup:</p>
     * <ul>
     *   <li>Headings: h1. h2. h3. h4. h5. h6.</li>
     *   <li>Code blocks: {code}...{code}, {noformat}...{noformat}</li>
     *   <li>Bullet lists: * item</li>
     *   <li>Numbered lists: # item</li>
     *   <li>Text formatting: *bold*, _italic_, {{monospace}}, -strikethrough-, +underline+, ^superscript^, ~subscript~</li>
     *   <li>Links: [text|url], [url]</li>
     *   <li>Line breaks: \n</li>
     * </ul>
     *
     * @param text Wiki markup text to convert
     * @return ADF structure as ComplexIssueInputFieldValue
     */
    public static ComplexIssueInputFieldValue convertToADF(String text) {
        List<ComplexIssueInputFieldValue> contentNodes = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];

            // Handle headings (h1. through h6.)
            if (line.matches("^h[1-6]\\.\\s+.*")) {
                int level = Character.getNumericValue(line.charAt(1));
                String headingText = line.substring(4).trim();
                contentNodes.add(createHeading(level, headingText));
                i++;
                continue;
            }

            // Handle code blocks {code} or {noformat}
            if (line.trim().matches("^\\{(code|noformat)(:[^}]*)?\\}\\s*$")) {
                StringBuilder codeContent = new StringBuilder();
                String blockType = line.trim().matches("^\\{code.*") ? "code" : "noformat";
                i++; // Move past opening tag

                // Collect lines until closing tag
                while (i < lines.length && !lines[i].trim().matches("^\\{" + blockType + "\\}\\s*$")) {
                    if (codeContent.length() > 0) {
                        codeContent.append("\n");
                    }
                    codeContent.append(lines[i]);
                    i++;
                }
                i++; // Move past closing tag

                contentNodes.add(createCodeBlock(codeContent.toString()));
                continue;
            }

            // Handle bullet lists (*)
            if (line.matches("^\\*+\\s+.*")) {
                List<String> listItems = new ArrayList<>();
                while (i < lines.length && lines[i].matches("^\\*+\\s+.*")) {
                    listItems.add(lines[i].replaceFirst("^\\*+\\s+", ""));
                    i++;
                }
                contentNodes.add(createBulletList(listItems));
                continue;
            }

            // Handle numbered lists (#)
            if (line.matches("^#+\\s+.*")) {
                List<String> listItems = new ArrayList<>();
                while (i < lines.length && lines[i].matches("^#+\\s+.*")) {
                    listItems.add(lines[i].replaceFirst("^#+\\s+", ""));
                    i++;
                }
                contentNodes.add(createOrderedList(listItems));
                continue;
            }

            // Handle horizontal rule (----)
            if (line.trim().matches("^-{4,}$")) {
                contentNodes.add(createRule());
                i++;
                continue;
            }

            // Handle empty lines (create paragraph separator, but skip consecutive empties)
            if (line.trim().isEmpty()) {
                i++;
                continue;
            }

            // Handle regular paragraphs (collect consecutive non-special lines)
            StringBuilder paragraphText = new StringBuilder();
            while (i < lines.length
                    && !lines[i].trim().isEmpty()
                    && !lines[i].matches("^h[1-6]\\.\\s+.*")
                    && !lines[i].trim().matches("^\\{(code|noformat)(:[^}]*)?\\}\\s*$")
                    && !lines[i].matches("^[\\*#]+\\s+.*")
                    && !lines[i].trim().matches("^-{4,}$")) {
                if (paragraphText.length() > 0) {
                    paragraphText.append("\n");
                }
                paragraphText.append(lines[i]);
                i++;
            }

            if (paragraphText.length() > 0) {
                contentNodes.add(createParagraph(paragraphText.toString()));
            }
        }

        // If no content was parsed, create a single empty paragraph
        if (contentNodes.isEmpty()) {
            contentNodes.add(createParagraph(""));
        }

        // Create document with all content nodes
        Map<String, Object> doc = new HashMap<>();
        doc.put("version", 1);
        doc.put("type", "doc");
        doc.put("content", contentNodes);

        return new ComplexIssueInputFieldValue(doc);
    }

    /**
     * Creates an ADF heading node.
     */
    private static ComplexIssueInputFieldValue createHeading(int level, String text) {
        Map<String, Object> heading = new HashMap<>();
        heading.put("type", "heading");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("level", level);
        heading.put("attrs", new ComplexIssueInputFieldValue(attrs));
        heading.put("content", parseInlineContent(text));
        return new ComplexIssueInputFieldValue(heading);
    }

    /**
     * Creates an ADF code block node.
     */
    private static ComplexIssueInputFieldValue createCodeBlock(String code) {
        Map<String, Object> codeBlock = new HashMap<>();
        codeBlock.put("type", "codeBlock");
        List<ComplexIssueInputFieldValue> content = new ArrayList<>();
        Map<String, Object> textNode = new HashMap<>();
        textNode.put("type", "text");
        textNode.put("text", code);
        content.add(new ComplexIssueInputFieldValue(textNode));
        codeBlock.put("content", content);
        return new ComplexIssueInputFieldValue(codeBlock);
    }

    /**
     * Creates an ADF paragraph node with inline formatting.
     */
    private static ComplexIssueInputFieldValue createParagraph(String text) {
        Map<String, Object> paragraph = new HashMap<>();
        paragraph.put("type", "paragraph");
        paragraph.put("content", parseInlineContent(text));
        return new ComplexIssueInputFieldValue(paragraph);
    }

    /**
     * Creates an ADF bullet list node.
     */
    private static ComplexIssueInputFieldValue createBulletList(List<String> items) {
        Map<String, Object> bulletList = new HashMap<>();
        bulletList.put("type", "bulletList");
        List<ComplexIssueInputFieldValue> listItems = new ArrayList<>();
        for (String item : items) {
            listItems.add(createListItem(item));
        }
        bulletList.put("content", listItems);
        return new ComplexIssueInputFieldValue(bulletList);
    }

    /**
     * Creates an ADF ordered list node.
     */
    private static ComplexIssueInputFieldValue createOrderedList(List<String> items) {
        Map<String, Object> orderedList = new HashMap<>();
        orderedList.put("type", "orderedList");
        List<ComplexIssueInputFieldValue> listItems = new ArrayList<>();
        for (String item : items) {
            listItems.add(createListItem(item));
        }
        orderedList.put("content", listItems);
        return new ComplexIssueInputFieldValue(orderedList);
    }

    /**
     * Creates an ADF list item node.
     */
    private static ComplexIssueInputFieldValue createListItem(String text) {
        Map<String, Object> listItem = new HashMap<>();
        listItem.put("type", "listItem");
        List<ComplexIssueInputFieldValue> content = new ArrayList<>();
        Map<String, Object> paragraph = new HashMap<>();
        paragraph.put("type", "paragraph");
        paragraph.put("content", parseInlineContent(text));
        content.add(new ComplexIssueInputFieldValue(paragraph));
        listItem.put("content", content);
        return new ComplexIssueInputFieldValue(listItem);
    }

    /**
     * Creates an ADF horizontal rule node.
     */
    private static ComplexIssueInputFieldValue createRule() {
        Map<String, Object> rule = new HashMap<>();
        rule.put("type", "rule");
        return new ComplexIssueInputFieldValue(rule);
    }

    /**
     * Parses inline wiki markup formatting within text.
     * Supports: *bold*, _italic_, {{monospace}}, -strikethrough-, +underline+,
     * ^superscript^, ~subscript~, [text|url], [url]
     */
    private static List<ComplexIssueInputFieldValue> parseInlineContent(String text) {
        List<ComplexIssueInputFieldValue> content = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return content;
        }

        StringBuilder currentText = new StringBuilder();
        int i = 0;

        while (i < text.length()) {
            char c = text.charAt(i);

            // Handle links [text|url] or [url]
            if (c == '[') {
                int closeBracket = text.indexOf(']', i);
                if (closeBracket != -1) {
                    // Flush current text
                    if (currentText.length() > 0) {
                        content.add(createTextNode(currentText.toString()));
                        currentText.setLength(0);
                    }

                    String linkContent = text.substring(i + 1, closeBracket);
                    String[] parts = linkContent.split("\\|", 2);
                    if (parts.length == 2) {
                        // [text|url]
                        content.add(createLink(parts[0], parts[1]));
                    } else {
                        // [url]
                        content.add(createLink(parts[0], parts[0]));
                    }
                    i = closeBracket + 1;
                    continue;
                }
            }

            // Handle inline formatting marks
            InlineFormat format = detectInlineFormat(text, i);
            if (format != null) {
                // Flush current text
                if (currentText.length() > 0) {
                    content.add(createTextNode(currentText.toString()));
                    currentText.setLength(0);
                }

                // Find closing delimiter
                int closePos = findClosingDelimiter(text, i + format.delimiter.length(), format.closingDelimiter);
                if (closePos != -1) {
                    String formattedText = text.substring(i + format.delimiter.length(), closePos);
                    content.add(createFormattedText(formattedText, format.markType));
                    i = closePos + format.closingDelimiter.length();
                    continue;
                }
            }

            // Regular character
            currentText.append(c);
            i++;
        }

        // Flush remaining text
        if (currentText.length() > 0) {
            content.add(createTextNode(currentText.toString()));
        }

        // Return empty text node if no content
        if (content.isEmpty()) {
            content.add(createTextNode(""));
        }

        return content;
    }

    /**
     * Detects inline formatting at the current position.
     */
    private static InlineFormat detectInlineFormat(String text, int pos) {
        // Check two-character delimiters first
        if (pos + 1 < text.length()) {
            String twoChar = text.substring(pos, pos + 2);
            if ("{{".equals(twoChar)) {
                return new InlineFormat("{{", "}}", "code");
            }
        }

        // Check single-character delimiters
        if (pos < text.length()) {
            char c = text.charAt(pos);
            switch (c) {
                case '*':
                    return new InlineFormat("*", "*", "strong");
                case '_':
                    return new InlineFormat("_", "_", "em");
                case '-':
                    return new InlineFormat("-", "-", "strike");
                case '+':
                    return new InlineFormat("+", "+", "underline");
                case '^':
                    return new InlineFormat("^", "^", "sup");
                case '~':
                    return new InlineFormat("~", "~", "sub");
            }
        }
        return null;
    }

    /**
     * Finds the closing delimiter for inline formatting.
     */
    private static int findClosingDelimiter(String text, int startPos, String delimiter) {
        int pos = text.indexOf(delimiter, startPos);
        // Make sure there's actual content between delimiters
        if (pos != -1 && pos > startPos) {
            return pos;
        }
        return -1;
    }

    /**
     * Creates a text node with formatting marks.
     */
    private static ComplexIssueInputFieldValue createFormattedText(String text, String markType) {
        Map<String, Object> textNode = new HashMap<>();
        textNode.put("type", "text");
        textNode.put("text", text);
        List<ComplexIssueInputFieldValue> marks = new ArrayList<>();
        Map<String, Object> mark = new HashMap<>();
        mark.put("type", markType);
        marks.add(new ComplexIssueInputFieldValue(mark));
        textNode.put("marks", marks);
        return new ComplexIssueInputFieldValue(textNode);
    }

    /**
     * Creates a plain text node.
     */
    private static ComplexIssueInputFieldValue createTextNode(String text) {
        Map<String, Object> textNode = new HashMap<>();
        textNode.put("type", "text");
        textNode.put("text", text);
        return new ComplexIssueInputFieldValue(textNode);
    }

    /**
     * Creates a link node.
     */
    private static ComplexIssueInputFieldValue createLink(String text, String url) {
        Map<String, Object> textNode = new HashMap<>();
        textNode.put("type", "text");
        textNode.put("text", text);
        List<ComplexIssueInputFieldValue> marks = new ArrayList<>();
        Map<String, Object> linkMark = new HashMap<>();
        linkMark.put("type", "link");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("href", url);
        linkMark.put("attrs", new ComplexIssueInputFieldValue(attrs));
        marks.add(new ComplexIssueInputFieldValue(linkMark));
        textNode.put("marks", marks);
        return new ComplexIssueInputFieldValue(textNode);
    }

    /**
     * Helper class to hold inline format information.
     */
    private static class InlineFormat {
        String delimiter;
        String closingDelimiter;
        String markType;

        InlineFormat(String delimiter, String closingDelimiter, String markType) {
            this.delimiter = delimiter;
            this.closingDelimiter = closingDelimiter;
            this.markType = markType;
        }
    }
}
