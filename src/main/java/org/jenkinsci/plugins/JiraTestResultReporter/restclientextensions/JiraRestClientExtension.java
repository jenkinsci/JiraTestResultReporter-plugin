/**
 Copyright 2015 Andrei Tuicu

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.jenkinsci.plugins.JiraTestResultReporter.restclientextensions;

import com.atlassian.httpclient.api.HttpClient;
import com.atlassian.jira.rest.client.internal.async.AbstractAsynchronousRestClient;
import com.atlassian.jira.rest.client.internal.json.GenericJsonArrayParser;
import com.atlassian.util.concurrent.Promise;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;

/**
 * Created by tuicu.
 * Extension of the Jira REST Client for querying statuses with status category information and delete issues
 */
public class JiraRestClientExtension extends AbstractAsynchronousRestClient {

    private final URI baseUri;

    public JiraRestClientExtension(URI serverUri, HttpClient client) {
        super(client);
        this.baseUri = UriBuilder.fromUri(serverUri).path("/rest/api/latest").build(new Object[0]);
    }

    public Promise<Iterable<FullStatus>> getStatuses() {
        final UriBuilder uriBuilder = UriBuilder.fromUri(this.baseUri);
        uriBuilder.path("status");
        return getAndParse(uriBuilder.build(), new GenericJsonArrayParser<>(new FullStatusJsonParser()));
    }

    public Promise<Void> deteleIssue(String issueKey) {
        UriBuilder uriBuilder = UriBuilder.fromUri(this.baseUri);
        uriBuilder.path("issue").path(issueKey);
        return this.delete(uriBuilder.build(new Object[0]));
    }
}
