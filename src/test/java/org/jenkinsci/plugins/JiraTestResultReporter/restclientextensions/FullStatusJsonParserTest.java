package org.jenkinsci.plugins.JiraTestResultReporter.restclientextensions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;

class FullStatusJsonParserTest {

    @Test
    void parsesStatusAndItsCategory() throws Exception {
        JSONObject statusJson = new JSONObject()
                .put("self", "https://jira.example/rest/api/2/status/1")
                .put("id", "1")
                .put("name", "Open")
                .put("description", "The issue is open")
                .put("iconUrl", "https://jira.example/images/open.png")
                .put(
                        "statusCategory",
                        new JSONObject()
                                .put("self", "https://jira.example/rest/api/2/statuscategory/2")
                                .put("id", "2")
                                .put("name", "To Do")
                                .put("key", "new")
                                .put("colorName", "blue-gray"));

        FullStatus status = new FullStatusJsonParser().parse(statusJson);

        assertEquals(1L, status.getId());
        assertEquals("Open", status.getName());
        assertEquals(URI.create("https://jira.example/rest/api/2/status/1"), status.getSelf());
        assertEquals(URI.create("https://jira.example/images/open.png"), status.getIconUrl());
        assertEquals("blue-gray", status.getColorName());
    }

    @Test
    void parsesStatusCategoryWithoutOptionalId() throws Exception {
        JSONObject categoryJson = new JSONObject()
                .put("self", "https://jira.example/rest/api/2/statuscategory/2")
                .put("key", "new")
                .put("colorName", "blue-gray");

        StatusCategory category = new StatusCategoryJsonParser().parse(categoryJson);

        assertEquals(URI.create("https://jira.example/rest/api/2/statuscategory/2"), category.getSelf());
        assertNull(category.getId());
        assertEquals("new", category.getKey());
        assertEquals("blue-gray", category.getColorName());
    }
}
