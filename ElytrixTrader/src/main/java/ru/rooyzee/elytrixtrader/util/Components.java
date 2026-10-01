package ru.rooyzee.elytrixtrader.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public final class Components {

    private static final String[] NAMED = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
            "yellow", "white"
    };

    private Components() {
    }

    public static Component colored(String legacyText) {
        if (legacyText == null || legacyText.isEmpty()) {
            return Component.empty();
        }
        try {
            return GsonComponentSerializer.gson().deserialize(toJson(legacyText));
        } catch (Throwable throwable) {
            return Component.text(ColorUtil.strip(legacyText));
        }
    }

    public static String toJson(String legacyText) {
        String source = legacyText == null ? "" : legacyText;
        JsonArray parts = new JsonArray();
        StringBuilder buffer = new StringBuilder();
        String color = null;
        boolean bold = false;
        boolean italic = false;
        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;
        char[] chars = source.toCharArray();
        for (int index = 0; index < chars.length; index++) {
            char current = chars[index];
            if (current == '&' && index + 7 <= chars.length && chars[index + 1] == '#') {
                boolean valid = true;
                StringBuilder hex = new StringBuilder();
                for (int c = 0; c < 6; c++) {
                    char ch = chars[index + 2 + c];
                    if ("0123456789abcdefABCDEF".indexOf(ch) < 0) {
                        valid = false;
                        break;
                    }
                    hex.append(Character.toLowerCase(ch));
                }
                if (valid) {
                    flush(parts, buffer, color, bold, italic, underlined, strikethrough, obfuscated);
                    buffer.setLength(0);
                    color = "#" + hex;
                    index += 7;
                    continue;
                }
            }
            if ((current == '§' || current == '&') && index + 1 < chars.length) {
                char code = Character.toLowerCase(chars[index + 1]);
                if ("0123456789abcdef".indexOf(code) >= 0) {
                    flush(parts, buffer, color, bold, italic, underlined, strikethrough, obfuscated);
                    buffer.setLength(0);
                    color = NAMED[Integer.parseInt(String.valueOf(code), 16)];
                    bold = false;
                    italic = false;
                    underlined = false;
                    strikethrough = false;
                    obfuscated = false;
                    index++;
                    continue;
                }
                if (code == 'l' || code == 'o' || code == 'n' || code == 'm') {
                    flush(parts, buffer, color, bold, italic, underlined, strikethrough, obfuscated);
                    buffer.setLength(0);
                    bold = code == 'l' || bold;
                    italic = code == 'o' || italic;
                    underlined = code == 'n' || underlined;
                    strikethrough = code == 'm' || strikethrough;
                    index++;
                    continue;
                }
                if (code == 'k') {
                    flush(parts, buffer, color, bold, italic, underlined, strikethrough, obfuscated);
                    buffer.setLength(0);
                    obfuscated = true;
                    index++;
                    continue;
                }
                if (code == 'r') {
                    flush(parts, buffer, color, bold, italic, underlined, strikethrough, obfuscated);
                    buffer.setLength(0);
                    color = null;
                    bold = false;
                    italic = false;
                    underlined = false;
                    strikethrough = false;
                    obfuscated = false;
                    index++;
                    continue;
                }
                if (code == 'x' && index + 13 < chars.length) {
                    StringBuilder hex = new StringBuilder();
                    boolean valid = true;
                    for (int cursor = 0; cursor < 6; cursor++) {
                        char marker = chars[index + 2 + cursor * 2];
                        char value = chars[index + 3 + cursor * 2];
                        if (marker != '§' && marker != '&') {
                            valid = false;
                            break;
                        }
                        if ("0123456789abcdefABCDEF".indexOf(value) < 0) {
                            valid = false;
                            break;
                        }
                        hex.append(Character.toLowerCase(value));
                    }
                    if (valid) {
                        flush(parts, buffer, color, bold, italic, underlined, strikethrough, obfuscated);
                        buffer.setLength(0);
                        color = "#" + hex;
                        bold = false;
                        italic = false;
                        underlined = false;
                        strikethrough = false;
                        obfuscated = false;
                        index += 13;
                        continue;
                    }
                }
            }
            buffer.append(current);
        }
        flush(parts, buffer, color, bold, italic, underlined, strikethrough, obfuscated);
        if (parts.size() == 0) {
            JsonObject empty = new JsonObject();
            empty.addProperty("text", "");
            return empty.toString();
        }
        if (parts.size() == 1) {
            return parts.get(0).toString();
        }
        JsonObject root = new JsonObject();
        root.addProperty("text", "");
        root.add("extra", parts);
        return root.toString();
    }

    private static void flush(JsonArray parts, StringBuilder buffer, String color, boolean bold, boolean italic,
                              boolean underlined, boolean strikethrough, boolean obfuscated) {
        if (buffer.length() == 0) {
            return;
        }
        JsonObject part = new JsonObject();
        part.addProperty("text", buffer.toString());
        if (color != null) {
            part.addProperty("color", color);
        }
        if (bold) {
            part.addProperty("bold", true);
        }
        if (italic) {
            part.addProperty("italic", true);
        }
        if (underlined) {
            part.addProperty("underlined", true);
        }
        if (strikethrough) {
            part.addProperty("strikethrough", true);
        }
        if (obfuscated) {
            part.addProperty("obfuscated", true);
        }
        parts.add(part);
    }
}
