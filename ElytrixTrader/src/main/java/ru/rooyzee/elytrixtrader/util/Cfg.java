package ru.rooyzee.elytrixtrader.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Cfg {

    private Cfg() {
    }

    public static String str(ConfigurationSection section, String path, String def) {
        if (section == null || !section.isString(path) && !section.contains(path)) {
            return def;
        }
        String value = section.getString(path, def);
        return value == null ? def : value;
    }

    public static int num(ConfigurationSection section, String path, int def) {
        if (section == null || !section.contains(path)) {
            return def;
        }
        Object raw = section.get(path);
        if (raw instanceof String) {
            return Numbers.parseSeconds((String) raw, def);
        }
        return section.getInt(path, def);
    }

    public static double dbl(ConfigurationSection section, String path, double def) {
        if (section == null || !section.contains(path)) {
            return def;
        }
        Object raw = section.get(path);
        if (raw instanceof String) {
            return Numbers.parseDouble((String) raw, def);
        }
        return section.getDouble(path, def);
    }

    public static boolean bool(ConfigurationSection section, String path, boolean def) {
        if (section == null || !section.contains(path)) {
            return def;
        }
        Object raw = section.get(path);
        if (raw instanceof String) {
            String value = ((String) raw).trim().toLowerCase();
            if (value.equals("true") || value.equals("yes") || value.equals("on") || value.equals("1")) {
                return true;
            }
            if (value.equals("false") || value.equals("no") || value.equals("off") || value.equals("0")) {
                return false;
            }
            return def;
        }
        return section.getBoolean(path, def);
    }

    public static List<String> list(ConfigurationSection section, String path) {
        List<String> result = new ArrayList<>();
        if (section == null || !section.contains(path)) {
            return result;
        }
        Object raw = section.get(path);
        if (raw instanceof String) {
            result.add((String) raw);
            return result;
        }
        for (String value : section.getStringList(path)) {
            result.add(value);
        }
        return result;
    }

    public static List<String> orEmpty(ConfigurationSection section, String path, List<String> fallback) {
        List<String> values = list(section, path);
        return values.isEmpty() ? fallback : values;
    }

    public static ConfigurationSection section(Object raw) {
        if (raw instanceof ConfigurationSection) {
            return (ConfigurationSection) raw;
        }
        if (!(raw instanceof Map)) {
            return null;
        }
        MemoryConfiguration root = new MemoryConfiguration();
        ConfigurationSection created = root.createSection("data");
        fill(created, (Map<?, ?>) raw);
        return created;
    }

    private static void fill(ConfigurationSection target, Map<?, ?> source) {
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map) {
                ConfigurationSection child = target.createSection(key);
                fill(child, (Map<?, ?>) value);
            } else {
                target.set(key, value);
            }
        }
    }

    public static List<ConfigurationSection> entries(ConfigurationSection section, String path) {
        List<ConfigurationSection> result = new ArrayList<>();
        if (section == null || !section.contains(path)) {
            return result;
        }
        Object raw = section.get(path);
        if (raw instanceof List) {
            for (Object entry : (List<?>) raw) {
                ConfigurationSection child = section(entry);
                if (child != null) {
                    result.add(child);
                }
            }
            return result;
        }
        ConfigurationSection single = section(raw);
        if (single != null) {
            result.add(single);
        }
        return result;
    }

    public static List<ConfigurationSection> sections(ConfigurationSection section, String path) {
        List<ConfigurationSection> result = new ArrayList<>();
        if (section == null || !section.contains(path)) {
            return result;
        }
        Object raw = section.get(path);
        if (!(raw instanceof ConfigurationSection)) {
            return result;
        }
        ConfigurationSection target = (ConfigurationSection) raw;
        for (String key : target.getKeys(false)) {
            ConfigurationSection child = target.getConfigurationSection(key);
            if (child != null) {
                result.add(child);
            }
        }
        if (result.isEmpty()) {
            result.add(target);
        }
        return result;
    }

    public static int parseSecondsOr(ConfigurationSection section, String path, int def) {
        if (section == null || !section.contains(path)) {
            return def;
        }
        Object raw = section.get(path);
        if (raw instanceof String) {
            return Numbers.parseSeconds((String) raw, def);
        }
        return section.getInt(path, def);
    }

    public static double parseDoubleSafe(String input) {
        return Numbers.parseDouble(input, Double.NaN);
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
