package ru.rooyzee.elytrixtrader.util;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import ru.rooyzee.elytrixtrader.Main;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%[A-Za-z0-9_.\\-]+%");

    private Text() {
    }

    public static String apply(String text, OfflinePlayer player) {
        return apply(text, player, null);
    }

    public static String apply(String text, OfflinePlayer player, Map<String, String> placeholders) {
        if (text == null) {
            return "";
        }
        String result = placeholders == null || placeholders.isEmpty() ? text : fill(text, placeholders);
        result = applyPapi(result, player);
        return ColorUtil.translate(result);
    }

    public static String fill(String text, Map<String, String> placeholders) {
        if (text == null) {
            return "";
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }

    public static List<String> applyList(List<String> lines, OfflinePlayer player) {
        return applyList(lines, player, null);
    }

    public static List<String> applyList(List<String> lines, OfflinePlayer player, Map<String, String> placeholders) {
        List<String> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        for (String line : lines) {
            result.add(apply(line, player, placeholders));
        }
        return result;
    }

    public static String applyPapi(String text, OfflinePlayer player) {
        if (text == null || player == null) {
            return text;
        }
        Main plugin = Main.instance;
        if (plugin == null || !plugin.placeholderApiEnabled()) {
            return text;
        }
        if (!PLACEHOLDER_PATTERN.matcher(text).find()) {
            return text;
        }
        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable ignored) {
            return text;
        }
    }

    public static String capitalize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String cleared = input.replace('_', ' ').trim();
        if (cleared.isEmpty()) {
            return cleared;
        }
        Matcher matcher = Pattern.compile("[A-Za-zА-Яа-яЁё]+").matcher(cleared);
        StringBuilder builder = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            builder.append(cleared, last, matcher.start());
            String word = matcher.group();
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1).toLowerCase());
            }
            last = matcher.end();
        }
        builder.append(cleared.substring(last));
        return builder.toString();
    }
}
