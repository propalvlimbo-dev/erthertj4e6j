package ru.rooyzee.elytrixtrader.config;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixtrader.util.Cfg;
import ru.rooyzee.elytrixtrader.util.ColorUtil;
import ru.rooyzee.elytrixtrader.util.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Messages {

    private final ConfigurationSection root;
    private final String prefix;

    public Messages(YamlConfiguration config) {
        this.root = config;
        String raw = Cfg.str(config, "prefix", "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7»");
        // Пробел после » обязателен
        this.prefix = raw == null || raw.isEmpty() ? "" : (raw.endsWith(" ") ? raw : raw + " ");
    }

    public String prefix() {
        return prefix;
    }

    public List<String> lines(String key) {
        List<String> result = new ArrayList<>();
        if (root == null || key == null) {
            return result;
        }
        Object raw = root.get(key);
        if (raw instanceof List) {
            for (Object entry : (List<?>) raw) {
                if (entry != null) {
                    result.add(String.valueOf(entry));
                }
            }
            return result;
        }
        if (raw instanceof String) {
            String value = (String) raw;
            if (value.contains("\n")) {
                for (String line : value.split("\n")) {
                    result.add(line);
                }
                return result;
            }
            result.add(value);
            return result;
        }
        return result;
    }

    public String raw(String key) {
        List<String> lines = lines(key);
        return lines.isEmpty() ? "" : String.join("\n", lines);
    }

    public String format(String key, OfflinePlayer player, Map<String, String> placeholders) {
        String template = raw(key);
        if (template.isEmpty()) {
            return "";
        }
        Map<String, String> map = placeholders == null ? new LinkedHashMap<>() : new LinkedHashMap<>(placeholders);
        map.putIfAbsent("prefix", prefix);
        return Text.apply(template, player, map);
    }

    public List<String> formatList(String key, OfflinePlayer player, Map<String, String> placeholders) {
        List<String> result = new ArrayList<>();
        String formatted = format(key, player, placeholders);
        if (formatted.isEmpty()) {
            return result;
        }
        for (String line : formatted.split("\n")) {
            result.add(line);
        }
        return result;
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, null, null);
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        send(sender, key, null, placeholders);
    }

    public void send(CommandSender sender, String key, OfflinePlayer player, Map<String, String> placeholders) {
        if (sender == null) {
            return;
        }
        String message = format(key, player, placeholders);
        if (message.isEmpty()) {
            return;
        }
        for (String line : message.split("\n")) {
            sender.sendMessage(line);
        }
    }

    public boolean contains(String key) {
        return root != null && root.contains(key);
    }

    public String fallback(String key, String def) {
        String value = raw(key);
        return value.isEmpty() ? ColorUtil.translate(def) : value;
    }
}
