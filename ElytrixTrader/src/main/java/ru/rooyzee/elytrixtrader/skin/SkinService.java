package ru.rooyzee.elytrixtrader.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.util.JsonUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SkinService {

    private static final String PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final SkinProfile MISSING = new SkinProfile("missing", null, null);

    private final Main plugin;
    private final Map<String, SkinProfile> cache = new ConcurrentHashMap<>();

    public SkinService(Main plugin) {
        this.plugin = plugin;
    }

    public void request(String name, Consumer<SkinProfile> callback) {
        String key = name == null ? "" : name.trim();
        if (key.isEmpty()) {
            callback.accept(null);
            return;
        }
        SkinProfile cached = cache.get(key.toLowerCase(Locale.ROOT));
        if (cached != null && fresh(cached)) {
            callback.accept(cached.isValid() ? cached : null);
            return;
        }
        SkinProfile fromPlayer = fromPlayer(key);
        if (fromPlayer != null) {
            cache.put(key.toLowerCase(Locale.ROOT), fromPlayer);
            callback.accept(fromPlayer);
            return;
        }
        if (!plugin.config().asyncSkins()) {
            SkinProfile profile = lookup(key);
            callback.accept(profile == null || !profile.isValid() ? null : profile);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            SkinProfile profile = lookup(key);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(profile == null || !profile.isValid() ? null : profile));
        });
    }

    private boolean fresh(SkinProfile profile) {
        int seconds = plugin.config().skinCacheSeconds();
        if (seconds <= 0) {
            return false;
        }
        return System.currentTimeMillis() - profile.requestedAt() < seconds * 1000L;
    }

    public SkinProfile fromPlayer(String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            return null;
        }
        try {
            Object playerProfile = target.getClass().getMethod("getPlayerProfile").invoke(target);
            if (playerProfile != null) {
                java.util.Collection<?> properties = (java.util.Collection<?>) playerProfile.getClass().getMethod("getProperties").invoke(playerProfile);
                if (properties != null) {
                    for (Object prop : properties) {
                        String propName = (String) prop.getClass().getMethod("getName").invoke(prop);
                        if ("textures".equals(propName)) {
                            String value = (String) prop.getClass().getMethod("getValue").invoke(prop);
                            String sig = null;
                            try {
                                sig = (String) prop.getClass().getMethod("getSignature").invoke(prop);
                            } catch (Throwable ignored) {
                            }
                            if (value != null && !value.isEmpty()) {
                                return new SkinProfile(name, value, sig);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object gameProfile = target.getClass().getMethod("getProfile").invoke(target);
            if (gameProfile != null) {
                Object propertyMap = gameProfile.getClass().getMethod("getProperties").invoke(gameProfile);
                if (propertyMap != null) {
                    @SuppressWarnings("unchecked")
                    java.util.Collection<?> textures = (java.util.Collection<?>) propertyMap.getClass().getMethod("get", Object.class).invoke(propertyMap, "textures");
                    if (textures != null) {
                        for (Object property : textures) {
                            if (property == null) continue;
                            String value = null;
                            String signature = null;
                            try {
                                value = (String) property.getClass().getMethod("getValue").invoke(property);
                            } catch (Throwable ignored) {
                            }
                            try {
                                signature = (String) property.getClass().getMethod("getSignature").invoke(property);
                            } catch (Throwable ignored) {
                            }
                            if (value != null && !value.isEmpty()) {
                                return new SkinProfile(name, value, signature);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private SkinProfile lookup(String name) {
        try {
            if (name.contains(":")) {
                String[] parts = name.split(":", 2);
                if (parts[0].length() > 50) {
                    SkinProfile result = new SkinProfile(name, parts[0], parts[1]);
                    cache.put(name.toLowerCase(Locale.ROOT), result);
                    return result;
                }
            } else if (name.length() > 100 && name.startsWith("eyJ")) {
                SkinProfile result = new SkinProfile(name, name, null);
                cache.put(name.toLowerCase(Locale.ROOT), result);
                return result;
            }

            String profileJson = get(PROFILE_URL + name);
            JsonObject profile = JsonUtil.parse(profileJson);
            String id = JsonUtil.text(profile, "id");
            if (id == null || id.isEmpty()) {
                cache.put(name.toLowerCase(Locale.ROOT), MISSING);
                return MISSING;
            }
            String sessionJson = get(SESSION_URL + id + "?unsigned=false");
            JsonObject session = JsonUtil.parse(sessionJson);
            if (session != null && session.has("properties") && session.get("properties").isJsonArray()) {
                JsonArray properties = session.getAsJsonArray("properties");
                for (int i = 0; i < properties.size(); i++) {
                    JsonElement element = properties.get(i);
                    if (element != null && element.isJsonObject()) {
                        JsonObject prop = element.getAsJsonObject();
                        if ("textures".equals(JsonUtil.text(prop, "name"))) {
                            String value = JsonUtil.text(prop, "value");
                            String signature = JsonUtil.text(prop, "signature");
                            if (value != null && !value.isEmpty()) {
                                SkinProfile result = new SkinProfile(name, value, signature);
                                cache.put(name.toLowerCase(Locale.ROOT), result);
                                return result;
                            }
                        }
                    }
                }
            }
            cache.put(name.toLowerCase(Locale.ROOT), MISSING);
            return MISSING;
        } catch (Throwable throwable) {
            if (plugin.config().debug()) {
            }
            cache.put(name.toLowerCase(Locale.ROOT), MISSING);
            return MISSING;
        }
    }

    private String get(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestProperty("User-Agent", "ElytrixTrader/1.0");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(6000);
        connection.setRequestMethod("GET");
        StringBuilder builder = new StringBuilder();
        try (InputStream stream = connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
            if (stream == null) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }
        } finally {
            connection.disconnect();
        }
        return builder.toString();
    }

    public void clear() {
        cache.clear();
    }
}
