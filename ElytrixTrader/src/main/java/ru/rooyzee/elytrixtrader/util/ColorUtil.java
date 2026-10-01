package ru.rooyzee.elytrixtrader.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern HEX_IN_TEXT = Pattern.compile("(?i)(?:&#|#)([0-9a-fA-F]{6})");
    private static final String CODES = "0123456789abcdefklmnorx";

    private ColorUtil() {
    }

    public static String translate(String input) {
        if (input == null) {
            return "";
        }
        String hexFixed = fixHex(input);
        char[] chars = hexFixed.toCharArray();
        StringBuilder builder = new StringBuilder(chars.length);
        for (int index = 0; index < chars.length; index++) {
            char current = chars[index];
            if ((current == '&' || current == '§') && index + 1 < chars.length) {
                char next = Character.toLowerCase(chars[index + 1]);
                if (CODES.indexOf(next) >= 0) {
                    builder.append('§').append(next);
                    index++;
                    continue;
                }
            }
            builder.append(current);
        }
        return builder.toString();
    }

    public static List<String> translate(List<String> input) {
        List<String> translated = new ArrayList<>();
        if (input == null) {
            return translated;
        }
        for (String line : input) {
            translated.add(translate(line));
        }
        return translated;
    }

    public static String fixHex(String input) {
        Matcher matcher = HEX_IN_TEXT.matcher(input);
        StringBuffer builder = new StringBuffer(input.length());
        while (matcher.find()) {
            String hex = matcher.group(1).toLowerCase(Locale.ROOT);
            StringBuilder legacy = new StringBuilder("§x");
            for (char color : hex.toCharArray()) {
                legacy.append('§').append(color);
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(legacy.toString()));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    public static String strip(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.stripColor(input);
    }

    public static String plain(String input) {
        if (input == null) {
            return "";
        }
        return strip(fixHex(input));
    }

    public static String forTitle(String input) {
        if (input == null) {
            return "";
        }
        return translate(HEX_IN_TEXT.matcher(input).replaceAll("&f"));
    }

    public static String single(String input) {
        if (input == null) {
            return "";
        }
        String[] lines = input.split("\n");
        return lines.length == 0 ? input : lines[0];
    }

    public static Material material(String name, Material fallback) {
        if (name == null || name.trim().isEmpty()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        if (material == null) {
            return fallback;
        }
        return material;
    }
}
