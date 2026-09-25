package org.jenkinsci.plugins.JiraTestResultReporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.atlassian.jira.rest.client.api.IdentifiableEntity;
import com.atlassian.jira.rest.client.api.NamedEntity;
import com.atlassian.jira.rest.client.api.domain.BasicComponent;
import com.atlassian.jira.rest.client.api.domain.CustomFieldOption;
import java.util.Collections;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;

class MetadataCacheTest {

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
}
