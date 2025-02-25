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
import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.CimFieldInfo;
import com.atlassian.jira.rest.client.api.domain.CimIssueType;
import com.atlassian.jira.rest.client.api.domain.CimProject;
import hudson.util.ListBoxModel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
            selectableFieldBox = new ListBoxModel();
            selectableArrayFieldBox = new ListBoxModel();
            userFieldBox = new ListBoxModel();

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
                    IssueRestClient issueRestClient =
                            JiraUtils.getJiraDescriptor().getRestClient().getIssueClient();
                    Iterable<CimProject> metadata;
                    try {
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
                        // likely issue https://github.com/jenkinsci/JiraTestResultReporter-plugin/issues/218
                        // support for jira newer than 8.4 is not impl yet
                        JiraUtils.log("ERROR: RestClientException for getCacheEntry projectKey:" + projectKey
                                + " issueType:" + issueType);
                        JiraUtils.logError("ERROR: RestClientException error", e);
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
