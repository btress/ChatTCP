package org.example.ungdungchat.shared.protocol;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.Instant;

public class InstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
    @Override
    public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src != null ? src.toString() : "");
    }

    @Override
    public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        if (json == null || json.isJsonNull() || json.getAsString().isBlank()) return null;
        try {
            return Instant.parse(json.getAsString());
        } catch (Exception e) {
            return null;
        }
    }
}
