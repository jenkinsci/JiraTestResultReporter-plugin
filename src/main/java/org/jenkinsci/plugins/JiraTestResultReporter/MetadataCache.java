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

import com.atlassian.jira.rest.client.api.GetCreateIssueMetadataOptions;
import com.atlassian.jira.rest.client.api.IdentifiableEntity;
import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.MetadataRestClient;
import com.atlassian.jira.rest.client.api.NamedEntity;
import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.BasicComponent;
import com.atlassian.jira.rest.client.api.domain.CimFieldInfo;
import com.atlassian.jira.rest.client.api.domain.CimIssueType;
import com.atlassian.jira.rest.client.api.domain.CimProject;
import com.atlassian.jira.rest.client.api.domain.CustomFieldOption;
import com.atlassian.jira.rest.client.api.domain.FieldSchema;
import com.atlassian.jira.rest.client.api.domain.ServerInfo;
import hudson.util.ListBoxModel;
import io.atlassian.util.concurrent.Promise;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * Created by tuicu.
 * Cache for requests made about metadata required for configuring fields in the job configuration page (config.jelly)
 */
public class MetadataCache {
    HashMap<String, HashMap<String, CacheEntry>> fieldConfigCache = new HashMap<String, HashMap<String, CacheEntry>>();

    public static class CacheEntry {
        private Map<String, CimFieldInfo> fieldInfoMap;
        private ListBoxModel stringFieldBox;
        private ListBoxModel selectableFieldBox;
        private ListBoxModel stringArrayFieldBox;
        private ListBoxModel selectableArrayFieldBox;
        private ListBoxModel userFieldBox;

        /**
         * Constructor
         * @param metadata from the request
         */
        public CacheEntry(Iterable<CimProject> metadata) {
            stringFieldBox = new ListBoxModel();
            selectableFieldBox = new ListBoxModel();
            stringArrayFieldBox = new ListBoxModel();
            selectableArrayFieldBox = new ListBoxModel();
            userFieldBox = new ListBoxModel();
            fieldInfoMap = new HashMap<String, CimFieldInfo>();

            for (CimProject project : metadata) {
                for (CimIssueType cimIssueType : project.getIssueTypes()) {
                    fieldInfoMap = cimIssueType.getFields();
                    Set<Map.Entry<String, CimFieldInfo>> entrySet = fieldInfoMap.entrySet();
                    for (Map.Entry<String, CimFieldInfo> entry : entrySet) {
                        // listInfo(entry);
                        if (entry.getValue().getSchema().getType().equals("string")
                                && entry.getValue().getAllowedValues() == null) {
                            stringFieldBox.add(
                                    new ListBoxModel.Option(entry.getValue().getName(), entry.getKey(), false));
                        } else if (!entry.getValue().getSchema().getType().equals("array")
                                && entry.getValue().getAllowedValues() != null) {
                            selectableFieldBox.add(
                                    new ListBoxModel.Option(entry.getValue().getName(), entry.getKey(), false));
                        } else if (entry.getValue().getSchema().getType().equals("array")
                                && entry.getValue().getAllowedValues() == null) {
                            stringArrayFieldBox.add(
                                    new ListBoxModel.Option(entry.getValue().getName(), entry.getKey(), false));
                        } else if (entry.getValue().getSchema().getType().equals("array")
                                && entry.getValue().getAllowedValues() != null) {
                            selectableArrayFieldBox.add(
                                    new ListBoxModel.Option(entry.getValue().getName(), entry.getKey(), false));
                        } else if (entry.getValue().getSchema().getType().equals("user")) {
                            userFieldBox.add(
                                    new ListBoxModel.Option(entry.getValue().getName(), entry.getKey(), false));
                        }
                    }
                    break; // the request is made for just one issue type
                }
                break; // the request is made for just one project
            }
        }

        /**
         * Populate field boxes from JSON field information (used for JIRA 8.4+ compatibility via direct API calls)
         */
        void populateFromJsonFields(JSONObject fields) throws JSONException {
            java.util.Iterator<?> fieldKeys = fields.keys();

            while (fieldKeys.hasNext()) {
                String fieldKey = String.valueOf(fieldKeys.next());
                JSONObject field = fields.getJSONObject(fieldKey);

                String fieldName = field.optString("name", fieldKey);
                JSONObject schema = field.optJSONObject("schema");

                if (schema == null) {
                    continue;
                }

                String schemaType = schema.optString("type", "");
                JSONArray allowedValues = field.optJSONArray("allowedValues");
                FieldSchema fieldSchema = new FieldSchema(
                        schemaType,
                        schema.optString("items", null),
                        schema.optString("system", null),
                        schema.optString("custom", null),
                        schema.has("customId") ? Long.valueOf(schema.optLong("customId")) : null);
                List<Object> allowedValueList = new ArrayList<Object>();
                if (allowedValues != null) {
                    for (int index = 0; index < allowedValues.length(); index++) {
                        allowedValueList.add(convertAllowedValue(fieldKey, schema, allowedValues.get(index)));
                    }
                }
                fieldInfoMap.put(
                        fieldKey,
                        new CimFieldInfo(
                                fieldKey,
                                field.optBoolean("required", false),
                                fieldName,
                                fieldSchema,
                                Collections.emptySet(),
                                allowedValues == null ? null : allowedValueList,
                                null));

                // Categorize field based on schema type and allowed values
                if ("string".equals(schemaType) && allowedValues == null) {
                    stringFieldBox.add(new ListBoxModel.Option(fieldName, fieldKey, false));
                } else if (!"array".equals(schemaType) && allowedValues != null) {
                    selectableFieldBox.add(new ListBoxModel.Option(fieldName, fieldKey, false));
                } else if ("array".equals(schemaType) && allowedValues == null) {
                    stringArrayFieldBox.add(new ListBoxModel.Option(fieldName, fieldKey, false));
                } else if ("array".equals(schemaType) && allowedValues != null) {
                    selectableArrayFieldBox.add(new ListBoxModel.Option(fieldName, fieldKey, false));
                } else if ("user".equals(schemaType)) {
                    userFieldBox.add(new ListBoxModel.Option(fieldName, fieldKey, false));
                }
            }
        }

        private Object convertAllowedValue(String fieldKey, JSONObject schema, Object allowedValue)
                throws JSONException {
            if (!(allowedValue instanceof JSONObject)) {
                return allowedValue;
            }

            JSONObject value = (JSONObject) allowedValue;
            String id = value.optString("id", null);
            if (id == null) {
                return allowedValue;
            }

            if ("components".equals(fieldKey) || "component".equals(schema.optString("items"))) {
                return new BasicComponent(
                        toUri(value.optString("self", null)),
                        Long.valueOf(id),
                        value.optString("name", id),
                        value.optString("description", null));
            }

            if (value.has("value")) {
                return new CustomFieldOption(
                        Long.valueOf(id), toUri(value.optString("self", null)), value.optString("value"), null, null);
            }

            String name = value.optString("name", null);
            return name == null ? allowedValue : new JsonNamedEntity(id, name);
        }

        private URI toUri(String uri) {
            return uri == null || uri.isEmpty() ? null : URI.create(uri);
        }

        private static class JsonNamedEntity implements IdentifiableEntity<String>, NamedEntity {
            private final String id;
            private final String name;

            JsonNamedEntity(String id, String name) {
                this.id = id;
                this.name = name;
            }

            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getName() {
                return name;
            }
        }

        public ListBoxModel getStringFieldBox() {
            return stringFieldBox;
        }

        public ListBoxModel getSelectableFieldBox() {
            return selectableFieldBox;
        }

        public ListBoxModel getStringArrayFieldBox() {
            return stringArrayFieldBox;
        }

        public ListBoxModel getSelectableArrayFieldBox() {
            return selectableArrayFieldBox;
        }

        public ListBoxModel getUserFieldBox() {
            return userFieldBox;
        }

        public Map<String, CimFieldInfo> getFieldInfoMap() {
            return fieldInfoMap;
        }

        private void listInfo(Map.Entry<String, CimFieldInfo> entry) {
            System.out.println(entry.getValue().getName() + " :: "
                    + entry.getValue().getSchema().getType());
            Iterable<?> allowedValues = entry.getValue().getAllowedValues();
            if (allowedValues != null) {
                for (Object o : allowedValues) {
                    System.out.println("\t" + o);
                }
            }
        }
    }

    /**
     * Method for removing the cache entry
     * @param projectKey
     * @param issueType
     */
    public void removeCacheEntry(String projectKey, String issueType) {
        if (fieldConfigCache.containsKey(projectKey)
                && fieldConfigCache.get(projectKey).containsKey(issueType)) {
            synchronized (fieldConfigCache.get(projectKey)) {
                fieldConfigCache.get(projectKey).remove(issueType);
            }
        }
    }

    /**
     * Getter for a cache entry, it will first look in the map too see if there is an entry associated with the
     * arguments, if not it will make the request for the metadata, create the entry, store it in the map and return it
     * @param projectKey
     * @param issueType
     * @return
     */
    public CacheEntry getCacheEntry(String projectKey, String issueType) {
        CacheEntry cacheEntry;
        try {
            cacheEntry = fieldConfigCache.get(projectKey).get(issueType);
            if (cacheEntry == null) {
                fieldConfigCache.get(projectKey).remove(issueType);
            } else {
                return cacheEntry;
            }
        } catch (NullPointerException e) {
            // Absent project key or issue type
        }

        if (!fieldConfigCache.containsKey(projectKey)) {
            synchronized (fieldConfigCache) {
                if (!fieldConfigCache.containsKey(projectKey)) {
                    fieldConfigCache.put(projectKey, new HashMap<String, CacheEntry>());
                }
            }
        }

        HashMap<String, CacheEntry> issueTypeToFields = fieldConfigCache.get(projectKey);
        cacheEntry = issueTypeToFields.get(issueType);
        if (cacheEntry == null) {
            synchronized (issueTypeToFields) {
                if (!issueTypeToFields.containsKey(issueType)) {
                    Iterable<CimProject> metadata = null;

                    // Try the library method first (for backward compatibility with older JIRA versions)
                    try {
                        IssueRestClient issueRestClient =
                                JiraUtils.getJiraDescriptor().getRestClient().getIssueClient();
                        metadata = issueRestClient
                                .getCreateIssueMetadata(new GetCreateIssueMetadataOptions(
                                        Collections.singletonList(
                                                GetCreateIssueMetadataOptions.EXPAND_PROJECTS_ISSUETYPES_FIELDS),
                                        null,
                                        Collections.singletonList(Long.parseLong(issueType)),
                                        Collections.singletonList(projectKey),
                                        null))
                                .claim();
                    } catch (RestClientException e) {
                        MetadataRestClient client =
                                JiraUtils.getJiraDescriptor().getRestClient().getMetadataClient();
                        Promise<ServerInfo> serverInfoPromise = client.getServerInfo();
                        ServerInfo serverInfo = serverInfoPromise.claim();
                        JiraUtils.log("ERROR: RestClientException for getCacheEntry projectKey:" + projectKey
                                + " issueType:" + issueType + " JIRA Version:" + serverInfo.getVersion());
                        // Fall back to direct REST API call for JIRA 8.4+ compatibility (issue #218)
                        JiraUtils.log("getCreateIssueMetadata call failed, attempting direct REST API call");
                        try {
                            cacheEntry = getCreateIssueMetadataCacheEntryViaDirectCall(projectKey, issueType);
                            if (cacheEntry != null) {
                                issueTypeToFields.put(issueType, cacheEntry);
                                return cacheEntry;
                            }
                        } catch (Exception directCallException) {
                            JiraUtils.logError("Direct API call also failed", directCallException);
                        }
                        JiraUtils.logError("ERROR: RestClientException in getCacheEntry", e);
                        return null;
                    } catch (IllegalArgumentException e) {
                        // Known issue: jira-rest-client doesn't support COPY operation from Jira API v3
                        // This is expected and handled by fallback to direct API calls in AdfFieldConverter
                        if (e.getMessage() != null && e.getMessage().contains("StandardOperation.COPY")) {
                            JiraUtils.log(
                                    "Metadata parsing failed (unsupported COPY operation), using fallback method");
                            return null;
                        }
                        // For other IllegalArgumentExceptions, log as error
                        JiraUtils.logError("ERROR: Invalid argument", e);
                        return null;
                    } catch (Exception e) {
                        JiraUtils.logError("ERROR: Unknown error", e);
                        return null;
                    }

                    cacheEntry = new CacheEntry(metadata);
                    issueTypeToFields.put(issueType, cacheEntry);
                    return cacheEntry;
                }
            }
            cacheEntry = issueTypeToFields.get(issueType);
        }

        return cacheEntry;
    }

    /**
     * Retrieves create issue metadata using direct REST API calls to the scoped endpoint
     * and constructs a CacheEntry directly from the JSON response.
     * This is used as a fallback when the jira-rest-client library method fails (e.g., JIRA 8.4+).
     *
     * The scoped endpoint format is: /rest/api/{version}/issue/createmeta?projectKeys={projectKey}&issueTypeIds={issueTypeId}&expand=projects.issuetypes.fields
     *
     * @param projectKey The JIRA project key
     * @param issueTypeId The issue type ID
     * @return A populated CacheEntry with field information, or null if the call fails
     */
    private CacheEntry getCreateIssueMetadataCacheEntryViaDirectCall(String projectKey, String issueTypeId)
            throws Exception {
        HttpURLConnection connection = null;
        try {
            JiraTestDataPublisher.JiraTestDataPublisherDescriptor descriptor = JiraUtils.getJiraDescriptor();
            String jiraUrl = descriptor.getJiraUrl();
            if (jiraUrl.endsWith("/")) {
                jiraUrl = jiraUrl.substring(0, jiraUrl.length() - 1);
            }

            // Determine API version to use (v3 or 'latest')
            String apiVersion = descriptor.getUseLatestRestApi() ? "latest" : "3";

            // Use scoped API endpoint for getting create metadata
            String encodedProjectKey = URLEncoder.encode(projectKey, StandardCharsets.UTF_8.name());
            String url = jiraUrl + "/rest/api/" + apiVersion + "/issue/createmeta/"
                    + encodedProjectKey
                    + "/issuetypes/" + issueTypeId;

            JiraUtils.log("Calling scoped createmeta endpoint for JIRA 8.4+ compatibility");

            connection = (HttpURLConnection) new URI(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            // Add authentication
            String username = descriptor.getUsername();
            String password = descriptor.getPassword().getPlainText();
            if (password != null && !password.isEmpty()) {
                String auth = username + ":" + password;
                String encodedAuth =
                        java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                if (descriptor.getUseBearerAuth()) {
                    connection.setRequestProperty("Authorization", "Bearer " + password);
                } else {
                    connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
                }
            }

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

                // Parse JSON response and build CacheEntry directly
                return parseCreateMetadataFromJsonAndBuildCacheEntry(response.toString(), projectKey, issueTypeId);
            } else {
                String errorBody = "";
                try {
                    BufferedReader errorReader = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8));
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine);
                    }
                    errorReader.close();
                    errorBody = errorResponse.toString();
                } catch (Exception e) {
                    // Ignore error reading response body
                }
                String errorMsg = "Scoped createmeta endpoint returned HTTP " + responseCode;
                if (!errorBody.isEmpty()) {
                    errorMsg += ": " + errorBody;
                }
                JiraUtils.log(errorMsg);
                throw new Exception(errorMsg);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Parses JSON response from the scoped /rest/api/<version>/issue/createmeta endpoint and builds
     * a CacheEntry object directly from the field information.
     *
     * @param jsonResponse The JSON response from the API
     * @param projectKey The project key being queried
     * @param issueTypeId The issue type ID being queried
     * @return A populated CacheEntry with field information
     */
    private CacheEntry parseCreateMetadataFromJsonAndBuildCacheEntry(
            String jsonResponse, String projectKey, String issueTypeId) throws JSONException {
        // Create a CacheEntry with empty field boxes - we'll populate them manually
        CacheEntry cacheEntry = new CacheEntry(Collections.emptyList());

        String trimmedResponse = jsonResponse.trim();
        if (trimmedResponse.startsWith("[")) {
            JSONArray fields = new JSONArray(trimmedResponse);
            JSONObject fieldsById = new JSONObject();
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                String fieldId = field.optString("fieldId");
                if (!fieldId.isEmpty()) {
                    fieldsById.put(fieldId, field);
                }
            }
            cacheEntry.populateFromJsonFields(fieldsById);
            JiraUtils.log("Successfully populated CacheEntry from scoped API from first branch. Found "
                    + fieldsById.length() + " fields");
            return cacheEntry;
        }

        JSONObject responseObject = new JSONObject(trimmedResponse);
        JSONArray values = responseObject.optJSONArray("values");

        if (values == null || values.length() == 0) {
            JiraUtils.log("No values found in metadata response from scoped API");
            return cacheEntry;
        }

        JSONObject firstValue = values.optJSONObject(0);
        if (firstValue != null && !firstValue.optString("fieldId").isEmpty()) {
            JSONObject fieldsById = new JSONObject();
            for (int i = 0; i < values.length(); i++) {
                JSONObject field = values.getJSONObject(i);
                String fieldId = field.optString("fieldId");
                if (!fieldId.isEmpty()) {
                    fieldsById.put(fieldId, field);
                }
            }
            cacheEntry.populateFromJsonFields(fieldsById);
            JiraUtils.log("Successfully populated CacheEntry from scoped API from second branch. Found "
                    + fieldsById.length() + " fields");
            return cacheEntry;
        }

        // Find the matching project and issue type, then extract fields
        for (int i = 0; i < values.length(); i++) {
            JSONObject project = values.getJSONObject(i);
            if (projectKey.equals(project.optString("key"))) {
                JSONArray issueTypes = project.optJSONArray("issuetypes");
                if (issueTypes != null) {
                    for (int j = 0; j < issueTypes.length(); j++) {
                        JSONObject issueType = issueTypes.getJSONObject(j);
                        String id = issueType.optString("id");
                        if (issueTypeId.equals(id)) {
                            JSONObject fields = issueType.optJSONObject("fields");
                            if (fields != null) {
                                cacheEntry.populateFromJsonFields(fields);
                                JiraUtils.log(
                                        "Successfully populated CacheEntry from scoped API from third branch. Found "
                                                + fields.length() + " fields");
                                return cacheEntry;
                            }
                        }
                    }
                }
            }
        }

        JiraUtils.log("Could not find matching project/issue type in metadata response");
        return cacheEntry;
    }

    /**
     * Method for printing the metadata
     * @param entry
     */
    private void listInfo(Map.Entry<String, CimFieldInfo> entry) {
        System.out.println(entry.getValue().getName() + " :: "
                + entry.getValue().getSchema().getType());
        Iterable<?> allowedValues = entry.getValue().getAllowedValues();
        if (allowedValues != null) {
            for (Object o : allowedValues) {
                System.out.println("\t" + o);
            }
        }
    }
}
