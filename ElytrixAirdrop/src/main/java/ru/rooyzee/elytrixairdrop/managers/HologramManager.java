package ru.rooyzee.elytrixairdrop.managers;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.utils.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HologramManager {

    private final Main plugin;
    /** Skip DHAPI.setHologramLines when content is unchanged — major tick savings. */
    private final Map<String, String> lastContent = new ConcurrentHashMap<>();

    public HologramManager(Main plugin) {
        this.plugin = plugin;
    }

    public String createHologram(Location loc, List<String> lines) {
        String name = "airdrop_" + Long.toHexString(System.currentTimeMillis());
        List<String> colored = colorize(lines);
        try {
            DHAPI.createHologram(name, loc.clone(), colored);
            lastContent.put(name, join(colored));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create hologram " + name + ": " + e.getMessage());
        }
        return name;
    }

    public void updateHologram(String name, List<String> lines) {
        if (name == null) return;
        List<String> colored = colorize(lines);
        String joined = join(colored);
        String prev = lastContent.get(name);
        if (joined.equals(prev)) return; // no visual change — skip expensive DHAPI call

        Hologram h;
        try {
            h = DHAPI.getHologram(name);
        } catch (Exception e) {
            return;
        }
        if (h == null) return;
        try {
            DHAPI.setHologramLines(h, colored);
            lastContent.put(name, joined);
        } catch (Exception ignored) {}
    }

    public void removeHologram(String name) {
        if (name == null) return;
        lastContent.remove(name);
        try {
            DHAPI.removeHologram(name);
        } catch (Exception ignored) {}
    }

    private List<String> colorize(List<String> lines) {
        List<String> colored = new ArrayList<>(lines.size());
        for (String s : lines) colored.add(ColorUtil.colorize(s));
        return colored;
    }

    private static String join(List<String> lines) {
        StringBuilder sb = new StringBuilder(lines.size() * 24);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }
}
