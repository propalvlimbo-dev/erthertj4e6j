package ru.rooyzee.elytrixairdrop.utils;

import net.md_5.bungee.api.ChatColor;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String colorize(String text) {
        if (text == null) return "";
        Matcher m = HEX.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, ChatColor.of("#" + m.group(1)).toString());
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public static List<String> colorize(List<String> list) {
        List<String> out = new ArrayList<>();
        for (String s : list) out.add(colorize(s));
        return out;
    }
}