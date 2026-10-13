package ru.rooyzee.elytrixtrader.persistence;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixtrader.Main;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownStore {

    private final Main plugin;
    private final File file;
    private final Map<UUID, Map<String, Long>> expirations = new ConcurrentHashMap<>();
    private final boolean persist;

    public CooldownStore(Main plugin, boolean persist) {
        this.plugin = plugin;
        this.persist = persist;
        this.file = new File(plugin.getDataFolder(), "cooldowns.yml");
    }

    public void load() {
        expirations.clear();
        if (!persist || !file.exists()) {
            return;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        long now = System.currentTimeMillis();
        for (String uuid : configuration.getKeys(false)) {
            UUID player;
            try {
                player = UUID.fromString(uuid);
            } catch (IllegalArgumentException exception) {
                continue;
            }
            ConfigurationSection section = configuration.getConfigurationSection(uuid);
            if (section == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
                if (!(entry.getValue() instanceof Number)) {
                    continue;
                }
                long expires = ((Number) entry.getValue()).longValue();
                if (expires > now) {
                    expirations.computeIfAbsent(player, key -> new ConcurrentHashMap<>()).put(entry.getKey(), expires);
                }
            }
        }
    }

    public void save() {
        if (!persist) {
            return;
        }
        YamlConfiguration configuration = new YamlConfiguration();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<String, Long>> playerEntry : expirations.entrySet()) {
            for (Map.Entry<String, Long> entry : playerEntry.getValue().entrySet()) {
                if (entry.getValue() > now) {
                    configuration.set(playerEntry.getKey() + "." + entry.getKey(), entry.getValue());
                }
            }
        }
        try {
            file.getParentFile().mkdirs();
            configuration.save(file);
        } catch (IOException exception) {
        }
    }

    public long remaining(UUID player, String key) {
        Map<String, Long> map = expirations.get(player);
        if (map == null) {
            return 0L;
        }
        Long expires = map.get(key);
        if (expires == null) {
            return 0L;
        }
        long left = expires - System.currentTimeMillis();
        if (left <= 0L) {
            map.remove(key);
            return 0L;
        }
        return (long) Math.ceil(left / 1000.0D);
    }

    public void apply(UUID player, String key, int seconds) {
        if (seconds <= 0) {
            return;
        }
        expirations.computeIfAbsent(player, id -> new ConcurrentHashMap<>())
                .put(key, System.currentTimeMillis() + seconds * 1000L);
    }

    public void clear(UUID player) {
        expirations.remove(player);
    }
}
