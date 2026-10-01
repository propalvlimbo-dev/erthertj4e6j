package ru.rooyzee.elytrixtrader.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class JsonUtil {

    private JsonUtil() {
    }

    @SuppressWarnings("deprecation")
    public static JsonObject parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            JsonElement element = new JsonParser().parse(json);
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            return element.getAsJsonObject();
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static String text(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsString();
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static JsonObject object(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(key);
    }
}
