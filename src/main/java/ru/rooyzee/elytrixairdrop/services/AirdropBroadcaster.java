package ru.rooyzee.elytrixairdrop.services;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.utils.ColorUtil;

import java.util.HashMap;
import java.util.Map;

public class AirdropBroadcaster {

    private final Main plugin;

    public AirdropBroadcaster(Main plugin) { this.plugin = plugin; }

    public void broadcast(String path, Map<String, String> vars) {
        for (String s : plugin.getConfigManager().getMessageList(path)) {
            String msg = s;
            for (Map.Entry<String, String> e : vars.entrySet()) {
                msg = msg.replace("{" + e.getKey() + "}", e.getValue());
            }
            Bukkit.broadcastMessage(msg);
        }
    }

    public Map<String, String> spawnVars(String typeDisplay, Location loc, int radius, String duration) {
        Map<String, String> v = new HashMap<>();
        v.put("type", typeDisplay);
        v.put("x", String.valueOf(loc.getBlockX()));
        v.put("y", String.valueOf(loc.getBlockY()));
        v.put("z", String.valueOf(loc.getBlockZ()));
        v.put("radius", String.valueOf(radius));
        v.put("duration", duration);
        return v;
    }

    public String typeDisplay(String id) {
        return ColorUtil.colorize(plugin.getConfigManager().getConfig()
                .getString("airdrops." + id + ".display-name", id));
    }
}