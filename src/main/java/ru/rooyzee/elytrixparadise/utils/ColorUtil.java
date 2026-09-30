package ru.rooyzee.elytrixparadise.utils;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorUtil() {}

    /**
     * Translates alternate color codes and hex codes (&#RRGGBB) into Minecraft chat color format.
     */
    public static String colorize(String text) {
        if (text == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hexCode = matcher.group(1);
            try {
                matcher.appendReplacement(buffer, ChatColor.of("#" + hexCode).toString());
            } catch (Exception e) {
                matcher.appendReplacement(buffer, matcher.group(0));
            }
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Colorizes a list of strings.
     */
    public static List<String> colorize(List<String> list) {
        if (list == null) return new ArrayList<>();
        List<String> result = new ArrayList<>(list.size());
        for (String line : list) {
            result.add(colorize(line));
        }
        return result;
    }

    /**
     * Strips color codes from text.
     */
    public static String stripColor(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(colorize(text));
    }

    /**
     * Sends action bar message to a player in a cross-compatible way (Spigot & Paper 1.16.5 - 1.20+).
     */
    public static void sendActionBar(Player player, String message) {
        if (player == null || message == null) return;
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colorize(message)));
    }

    /**
     * Sends broadcast message ONLY to online players (not spamming console logs).
     */
    public static void broadcastToPlayers(String message) {
        if (message == null) return;
        String colored = colorize(message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(colored);
        }
    }

    /**
     * Formats seconds into H:MM:SS or M:SS format (e.g. 2:59:30 or 2:59).
     */
    public static String formatTimeShort(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        if (totalSeconds >= 3600) {
            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            int seconds = totalSeconds % 60;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Formats seconds into Russian pretty format (e.g. 2ч 59м or 4м 30с).
     */
    public static String formatTimePretty(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        if (totalSeconds >= 3600) {
            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            return String.format("%dч %02dм", hours, minutes);
        }
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%dм %02dс", minutes, seconds);
    }
}
