package org.jenkinsci.plugins.JiraTestResultReporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.atlassian.jira.rest.client.api.IdentifiableEntity;
import com.atlassian.jira.rest.client.api.NamedEntity;
import com.atlassian.jira.rest.client.api.domain.BasicComponent;
import com.atlassian.jira.rest.client.api.domain.CustomFieldOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;

class MetadataCacheTest {

    @Test
    void returnsCachedEntryAndRemovesIt() {
        MetadataCache cache = new MetadataCache();
        MetadataCache.CacheEntry expected = new MetadataCache.CacheEntry(Collections.emptyList());
        cache.fieldConfigCache.put("PROJ", new HashMap<>(Map.of("100", expected)));

        assertSame(expected, cache.getCacheEntry("PROJ", "100"));

        cache.removeCacheEntry("PROJ", "100");

        assertFalse(cache.fieldConfigCache.get("PROJ").containsKey("100"));
        cache.removeCacheEntry("MISSING", "100");
    }

    @Test
    void convertsJsonAllowedValuesToUiCompatibleTypes() throws Exception {
        MetadataCache.CacheEntry cacheEntry = new MetadataCache.CacheEntry(Collections.emptyList());
        JSONObject fields = new JSONObject();
        fields.put(
                "components",
                new JSONObject()
                        .put("name", "Components")
                        .put("schema", new JSONObject().put("type", "array").put("items", "component"))
                        .put(
                                "allowedValues",
                                new JSONArray()
                                        .put(new JSONObject()
                                                .put("self", "https://jira.example/rest/api/2/component/1")
                                                .put("id", "1")
                                                .put("name", "Startup")
                                                .put("description", "Startup component"))));
        fields.put(
                "customfield_10000",
                new JSONObject()
                        .put("name", "Custom option")
                        .put("schema", new JSONObject().put("type", "option"))
                        .put(
                                "allowedValues",
                                new JSONArray()
                                        .put(new JSONObject().put("id", "2").put("value", "Enabled"))));
        fields.put(
                "priority",
                new JSONObject()
                        .put("name", "Priority")
                        .put("schema", new JSONObject().put("type", "priority"))
                        .put(
                                "allowedValues",
                                new JSONArray()
                                        .put(new JSONObject().put("id", "3").put("name", "High"))));

        cacheEntry.populateFromJsonFields(fields);

        Object component = cacheEntry
                .getFieldInfoMap()
                .get("components")
                .getAllowedValues()
                .iterator()
                .next();
        BasicComponent basicComponent = assertInstanceOf(BasicComponent.class, component);
        assertEquals("1", basicComponent.getId().toString());
        assertEquals("Startup", basicComponent.getName());

        Object customOption = cacheEntry
                .getFieldInfoMap()
                .get("customfield_10000")
                .getAllowedValues()
                .iterator()
                .next();
        CustomFieldOption option = assertInstanceOf(CustomFieldOption.class, customOption);
        assertEquals("2", option.getId().toString());
        assertEquals("Enabled", option.getValue());

        Object priority = cacheEntry
                .getFieldInfoMap()
                .get("priority")
                .getAllowedValues()
                .iterator()
                .next();
        assertInstanceOf(IdentifiableEntity.class, priority);
        assertInstanceOf(NamedEntity.class, priority);
        assertEquals("3", ((IdentifiableEntity<?>) priority).getId().toString());
        assertEquals("High", ((NamedEntity) priority).getName());
    }

    @Test
    void parsesCreatemetaFieldArrayResponse() throws Exception {
        JSONArray fields = new JSONArray().put(field("summary", "Summary"));

        MetadataCache.CacheEntry entry = parseCreatemeta(fields.toString());

        assertEquals("Summary", entry.getFieldInfoMap().get("summary").getName());
        assertEquals(1, entry.getStringFieldBox().size());
    }

    @Test
    void parsesCreatemetaValuesFieldListResponse() throws Exception {
        JSONObject response = new JSONObject()
                .put(
                        "values",
                        new JSONArray()
                                .put(new JSONObject(field("summary", "Summary").toString()).put("fieldId", "summary")));

        MetadataCache.CacheEntry entry = parseCreatemeta(response.toString());

        assertEquals("Summary", entry.getFieldInfoMap().get("summary").getName());
    }

    @Test
    void parsesCreatemetaProjectAndIssueTypeResponse() throws Exception {
        JSONObject fields = new JSONObject().put("summary", field("summary", "Summary"));
        JSONObject response = new JSONObject()
                .put(
                        "values",
                        new JSONArray()
                                .put(new JSONObject()
                                        .put("key", "PROJ")
                                        .put(
                                                "issuetypes",
                                                new JSONArray()
                                                        .put(new JSONObject()
                                                                .put("id", "100")
                                                                .put("fields", fields)))));

        MetadataCache.CacheEntry entry = parseCreatemeta(response.toString());

        assertEquals("Summary", entry.getFieldInfoMap().get("summary").getName());
    }

    @Test
    void returnsEmptyEntryWhenCreatemetaHasNoValues() throws Exception {
        MetadataCache.CacheEntry entry =
                parseCreatemeta(new JSONObject().put("values", new JSONArray()).toString());

        assertEquals(0, entry.getFieldInfoMap().size());
    }

    private JSONObject field(String fieldId, String name) throws Exception {
        return new JSONObject()
                .put("fieldId", fieldId)
                .put("name", name)
                .put("schema", new JSONObject().put("type", "string"));
    }

    private MetadataCache.CacheEntry parseCreatemeta(String json) throws Exception {
        java.lang.reflect.Method parser = MetadataCache.class.getDeclaredMethod(
                "parseCreateMetadataFromJsonAndBuildCacheEntry", String.class, String.class, String.class);
        parser.setAccessible(true);
        return (MetadataCache.CacheEntry) parser.invoke(new MetadataCache(), json, "PROJ", "100");
    }
}
