package ru.rooyzee.elytrixschalkerpvp.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixschalkerpvp.Main;
import ru.rooyzee.elytrixschalkerpvp.model.SchalkerData;
import ru.rooyzee.elytrixschalkerpvp.model.SchalkerState;
import ru.rooyzee.elytrixschalkerpvp.util.ColorUtil;
import ru.rooyzee.elytrixschalkerpvp.util.TimeUtil;

import java.util.Collection;

public class SchalkerPlaceholder extends PlaceholderExpansion {

    private final Main plugin;
    private final String identifier;

    public SchalkerPlaceholder(Main plugin) {
        this(plugin, "elytrixschalkerpvp");
    }

    public SchalkerPlaceholder(Main plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String getAuthor() {
        return "rooyzee";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        return handle(params);
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return handle(params);
    }

    private String handle(String params) {
        Collection<SchalkerData> all = plugin.getSchalkerManager().getAllSchalkers();
        int online = Bukkit.getOnlinePlayers().size();
        int minOnline = 5;
        boolean hasOnline = online >= minOnline;

        SchalkerData activeOrReady = null;
        SchalkerData nearestSleeping = null;
        long minSleepRemaining = Long.MAX_VALUE;

        for (SchalkerData data : all) {
            if (data.getState() == SchalkerState.READY || data.getState() == SchalkerState.ACTIVE || data.getState() == SchalkerState.EXPLODING) {
                activeOrReady = data;
                break;
            } else if (data.getState() == SchalkerState.SLEEPING) {
                long rem = Math.max(0, (data.getSleepEndTime() - System.currentTimeMillis()) / 1000);
                if (rem < minSleepRemaining) {
                    minSleepRemaining = rem;
                    nearestSleeping = data;
                }
            }
        }

        if (params == null || params.isEmpty() || params.equalsIgnoreCase("formatted")) {
            if (activeOrReady != null) {
                return ColorUtil.colorize("&aИдет сейчас");
            }
            if (!hasOnline) {
                return ColorUtil.colorize("&cНужно 5 игроков");
            }
            if (nearestSleeping != null && minSleepRemaining != Long.MAX_VALUE) {
                return ColorUtil.colorize(minSleepRemaining <= 0 ? "&aскоро" : TimeUtil.formatSeconds(minSleepRemaining));
            }
            return ColorUtil.colorize("&7—");
        }

        String lower = params.toLowerCase().replace("-", "_");
        switch (lower) {
            case "status":
                if (activeOrReady != null) {
                    if (activeOrReady.getState() == SchalkerState.READY) return ColorUtil.colorize("&aГотов");
                    if (activeOrReady.getState() == SchalkerState.ACTIVE) return ColorUtil.colorize("&eОткрыт");
                    return ColorUtil.colorize("&6Спавн");
                }
                if (!hasOnline) {
                    return ColorUtil.colorize("&cНужно 5 игроков");
                }
                return ColorUtil.colorize("&7Спит");

            case "time":
            case "timer":
            case "time_short": {
                if (activeOrReady != null) {
                    return ColorUtil.colorize("&aИдет сейчас");
                }
                if (!hasOnline) {
                    return ColorUtil.colorize("&cНужно 5 игроков");
                }
                if (nearestSleeping != null && minSleepRemaining != Long.MAX_VALUE) {
                    return ColorUtil.colorize(minSleepRemaining <= 0 ? "&aскоро" : TimeUtil.formatSeconds(minSleepRemaining));
                }
                return ColorUtil.colorize("&7—");
            }

            case "rarity": {
                if (activeOrReady != null && activeOrReady.getCurrentRarity() != null) {
                    String name = plugin.getConfigManager().getRarityName(activeOrReady.getCurrentRarity());
                    String color = plugin.getConfigManager().getRarityHexColor(activeOrReady.getCurrentRarity());
                    return ColorUtil.colorize(color + name);
                }
                return ColorUtil.colorize("&7—");
            }

            case "location": {
                SchalkerData target = activeOrReady != null ? activeOrReady : nearestSleeping;
                if (target != null && target.getLocation() != null) {
                    Location loc = target.getLocation();
                    return "X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ();
                }
                return ColorUtil.colorize("&#F8BEFBPvP Арена");
            }

            case "x": {
                SchalkerData target = activeOrReady != null ? activeOrReady : nearestSleeping;
                return target != null && target.getLocation() != null ? String.valueOf(target.getLocation().getBlockX()) : "0";
            }
            case "y": {
                SchalkerData target = activeOrReady != null ? activeOrReady : nearestSleeping;
                return target != null && target.getLocation() != null ? String.valueOf(target.getLocation().getBlockY()) : "0";
            }
            case "z": {
                SchalkerData target = activeOrReady != null ? activeOrReady : nearestSleeping;
                return target != null && target.getLocation() != null ? String.valueOf(target.getLocation().getBlockZ()) : "0";
            }
            case "world": {
                SchalkerData target = activeOrReady != null ? activeOrReady : nearestSleeping;
                return target != null && target.getWorldName() != null ? target.getWorldName() : "world";
            }

            case "online":
                return online + "/" + minOnline;
            case "online_current":
                return String.valueOf(online);
            case "online_min":
            case "min_online":
                return String.valueOf(minOnline);

            case "total":
            case "count":
                return String.valueOf(all.size());
        }

        return null;
    }
}
