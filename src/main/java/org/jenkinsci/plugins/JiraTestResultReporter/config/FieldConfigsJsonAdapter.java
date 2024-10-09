package org.jenkinsci.plugins.JiraTestResultReporter.config;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

/**
 * Created by Andrei Tuicu on 11/13/2015.
 */
public class FieldConfigsJsonAdapter implements JsonSerializer<AbstractFields>, JsonDeserializer<AbstractFields> {

    @Override
    public JsonElement serialize(AbstractFields field, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject result = new JsonObject();
        result.add("clazz", new JsonPrimitive(field.getClass().getSimpleName()));
        result.add("properties", jsonSerializationContext.serialize(field, field.getClass()));
        return result;
    }

    @Override
    public AbstractFields deserialize(
            JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)
            throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        String clazz = jsonObject.get("clazz").getAsString();
        try {
            AbstractFields field = jsonDeserializationContext.deserialize(
                    jsonObject.get("properties"),
                    Class.forName("org.jenkinsci.plugins.JiraTestResultReporter.config." + clazz));
            return (AbstractFields) field.readResolve();
        } catch (ClassNotFoundException e) {
            throw new JsonParseException("Unknown element type:" + clazz, e);
        }
    }
}
