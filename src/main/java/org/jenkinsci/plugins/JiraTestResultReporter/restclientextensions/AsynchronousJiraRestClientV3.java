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
package org.jenkinsci.plugins.JiraTestResultReporter.restclientextensions;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.MetadataRestClient;
import com.atlassian.jira.rest.client.api.ProjectRestClient;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.SessionRestClient;
import com.atlassian.jira.rest.client.api.UserRestClient;
import com.atlassian.jira.rest.client.internal.async.AsynchronousCloudSearchRestClient;
import com.atlassian.jira.rest.client.internal.async.AsynchronousIssueRestClient;
import com.atlassian.jira.rest.client.internal.async.AsynchronousMetadataRestClient;
import com.atlassian.jira.rest.client.internal.async.AsynchronousProjectRestClient;
import com.atlassian.jira.rest.client.internal.async.AsynchronousSessionRestClient;
import com.atlassian.jira.rest.client.internal.async.AsynchronousUserRestClient;
import com.atlassian.jira.rest.client.internal.async.DisposableHttpClient;
import jakarta.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;

/**
 * Minimal custom AsynchronousJiraRestClient that uses Jira REST API v3 instead of the deprecated 'latest' version.
 * This implementation only includes the REST clients actually used by the JiraTestResultReporter plugin.
 *
 * The standard jira-rest-client 7.0.1 hardcodes /rest/api/latest which has been removed by Jira Cloud.
 */
public class AsynchronousJiraRestClientV3 implements JiraRestClient {

    private final IssueRestClient issueRestClient;
    private final SearchRestClient searchRestClient;
    private final MetadataRestClient metadataRestClient;
    private final ProjectRestClient projectRestClient;
    private final UserRestClient userRestClient;
    private final SessionRestClient sessionRestClient;
    private final DisposableHttpClient httpClient;

    public AsynchronousJiraRestClientV3(
            URI serverUri, DisposableHttpClient httpClient, String latestRestApiVersionString) {
        // Use API v3 or 'latest' based on user setting from latestRestApiVersionString
        final URI baseUri = UriBuilder.fromUri(serverUri)
                .path("/rest/api/" + latestRestApiVersionString)
                .build(new Object[0]);
        this.httpClient = httpClient;

        this.metadataRestClient = new AsynchronousMetadataRestClient(baseUri, httpClient);
        this.sessionRestClient = new AsynchronousSessionRestClient(serverUri, httpClient);
        this.issueRestClient =
                new AsynchronousIssueRestClient(baseUri, httpClient, sessionRestClient, metadataRestClient);
        this.searchRestClient = new AsynchronousCloudSearchRestClient(baseUri, httpClient);
        this.projectRestClient = new AsynchronousProjectRestClient(baseUri, httpClient);
        this.userRestClient = new AsynchronousUserRestClient(baseUri, httpClient);
    }

    @Override
    public IssueRestClient getIssueClient() {
        return issueRestClient;
    }

    @Override
    public SearchRestClient getSearchClient() {
        return searchRestClient;
    }

    @Override
    public MetadataRestClient getMetadataClient() {
        return metadataRestClient;
    }

    @Override
    public ProjectRestClient getProjectClient() {
        return projectRestClient;
    }

    @Override
    public UserRestClient getUserClient() {
        return userRestClient;
    }

    @Override
    public SessionRestClient getSessionClient() {
        return sessionRestClient;
    }

    // Unused clients - throw UnsupportedOperationException
    @Override
    public com.atlassian.jira.rest.client.api.ComponentRestClient getComponentClient() {
        throw new UnsupportedOperationException("ComponentRestClient not implemented - not used by this plugin");
    }

    @Override
    public com.atlassian.jira.rest.client.api.VersionRestClient getVersionRestClient() {
        throw new UnsupportedOperationException("VersionRestClient not implemented - not used by this plugin");
    }

    @Override
    public com.atlassian.jira.rest.client.api.ProjectRolesRestClient getProjectRolesRestClient() {
        throw new UnsupportedOperationException("ProjectRolesRestClient not implemented - not used by this plugin");
    }

    @Override
    public com.atlassian.jira.rest.client.api.AuditRestClient getAuditRestClient() {
        throw new UnsupportedOperationException("AuditRestClient not implemented - not used by this plugin");
    }

    @Override
    public com.atlassian.jira.rest.client.api.MyPermissionsRestClient getMyPermissionsRestClient() {
        throw new UnsupportedOperationException("MyPermissionsRestClient not implemented - not used by this plugin");
    }

    @Override
    public com.atlassian.jira.rest.client.api.GroupRestClient getGroupClient() {
        throw new UnsupportedOperationException("GroupRestClient not implemented - not used by this plugin");
    }

    @Override
    public com.atlassian.jira.rest.client.api.EmailRestClient getEmailRestClient() {
        throw new UnsupportedOperationException("EmailRestClient not implemented - not used by this plugin");
    }

    @Override
    public void close() throws IOException {
        // HttpClient cleanup is handled externally
    }
}
