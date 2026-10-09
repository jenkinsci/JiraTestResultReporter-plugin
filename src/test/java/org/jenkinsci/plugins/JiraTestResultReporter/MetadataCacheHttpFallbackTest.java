package org.jenkinsci.plugins.JiraTestResultReporter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import hudson.util.Secret;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class MetadataCacheHttpFallbackTest {

    @Test
    void requestsScopedMetadataWithConfiguredApiVersionAndBearerAuth(JenkinsRule jenkins) throws Exception {
        List<String> paths = new ArrayList<>();
        List<String> authorizationHeaders = new ArrayList<>();
        byte[] responseBody = new JSONObject()
                .put(
                        "values",
                        new JSONArray()
                                .put(new JSONObject()
                                        .put("fieldId", "summary")
                                        .put("name", "Summary")
                                        .put("schema", new JSONObject().put("type", "string"))))
                .toString()
                .getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getRawPath());
            authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(responseBody);
            }
        });
        server.start();

        try {
            JiraTestDataPublisher.JiraTestDataPublisherDescriptor descriptor =
                    (JiraTestDataPublisher.JiraTestDataPublisherDescriptor)
                            Jenkins.get().getDescriptor(JiraTestDataPublisher.class);
            descriptor.setJiraUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            descriptor.setUsername("api-user");
            descriptor.setPassword(Secret.fromString("api-token"));
            descriptor.setUseBearerAuth(true);

            MetadataCache cache = new MetadataCache();
            descriptor.setUseLatestRestApi(false);
            MetadataCache.CacheEntry v3Entry = invokeDirectCall(cache);
            descriptor.setUseLatestRestApi(true);
            MetadataCache.CacheEntry latestEntry = invokeDirectCall(cache);

            assertEquals("Summary", v3Entry.getFieldInfoMap().get("summary").getName());
            assertEquals("Summary", latestEntry.getFieldInfoMap().get("summary").getName());
            assertEquals(
                    List.of(
                            "/rest/api/3/issue/createmeta/PROJ/issuetypes/100",
                            "/rest/api/latest/issue/createmeta/PROJ/issuetypes/100"),
                    paths);
            assertEquals(List.of("Bearer api-token", "Bearer api-token"), authorizationHeaders);
        } finally {
            server.stop(0);
        }
    }

    private MetadataCache.CacheEntry invokeDirectCall(MetadataCache cache) throws Exception {
        Method directCall = MetadataCache.class.getDeclaredMethod(
                "getCreateIssueMetadataCacheEntryViaDirectCall", String.class, String.class);
        directCall.setAccessible(true);
        return (MetadataCache.CacheEntry) directCall.invoke(cache, "PROJ", "100");
    }
}
