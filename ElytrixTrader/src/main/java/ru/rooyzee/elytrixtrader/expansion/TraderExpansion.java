package ru.rooyzee.elytrixtrader.expansion;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.TraderInstance;
import ru.rooyzee.elytrixtrader.util.ColorUtil;

import java.util.List;

public class TraderExpansion extends PlaceholderExpansion {

    private final Main plugin;
    private final String identifier;

    public TraderExpansion(Main plugin) {
        this(plugin, "elytrixtrader");
    }

    public TraderExpansion(Main plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }

    @Override
    public boolean canRegister() { return true; }

    @Override
    public boolean persist() { return true; }

    @Override
    public String getIdentifier() { return identifier; }

    @Override
    public String getAuthor() { return "rooyzee"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        return handle(params);
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return handle(params);
    }

    private String handle(String params) {
        List<TraderInstance> instances = plugin.traders().instances();
        TraderInstance active = null;
        for (TraderInstance inst : instances) {
            if (inst.alive()) {
                active = inst;
                break;
            }
        }

        if (params == null || params.isEmpty() || params.equalsIgnoreCase("formatted") || params.equalsIgnoreCase("tracker")) {
            if (active != null) {
                Location loc = active.location();
                int x = loc.getBlockX();
                int y = loc.getBlockY();
                int z = loc.getBlockZ();
                return ColorUtil.translate("&aАктивен &8(&fX: " + x + ", Y: " + y + ", Z: " + z + "&8)");
            }

            if (!plugin.config().scheduleEnabled() || plugin.config().scheduleTimes().isEmpty()) {
                return ColorUtil.translate("&cВыключен");
            }
            return ColorUtil.translate("&#F8BEFB" + plugin.traders().nextEventTime());
        }

        String lower = params.toLowerCase().replace("-", "_");
        switch (lower) {
            case "status":
                return active != null ? ColorUtil.translate("&aАктивен") : ColorUtil.translate("&cНе активен");

            case "time":
            case "timer":
            case "next":
            case "next_time":
                if (active != null) {
                    return ColorUtil.translate("&aИдет сейчас");
                }
                return ColorUtil.translate("&#F8BEFB" + plugin.traders().nextEventTime());

            case "location":
            case "coords":
            case "coordinates":
                if (active != null && active.location() != null) {
                    Location loc = active.location();
                    return "X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ();
                }
                return ColorUtil.translate("&#F8BEFBСлучайные координаты");

            case "location_full":
                if (active != null && active.location() != null) {
                    Location loc = active.location();
                    String world = loc.getWorld() == null ? "world" : loc.getWorld().getName();
                    return world + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
                }
                return ColorUtil.translate("&7Не заспавнен");

            case "x":
                return active != null && active.location() != null ? String.valueOf(active.location().getBlockX()) : "0";

            case "y":
                return active != null && active.location() != null ? String.valueOf(active.location().getBlockY()) : "0";

            case "z":
                return active != null && active.location() != null ? String.valueOf(active.location().getBlockZ()) : "0";

            case "world":
                return active != null && active.location() != null && active.location().getWorld() != null
                        ? active.location().getWorld().getName() : "world";

            case "count":
            case "total":
                return String.valueOf(plugin.traders().total());

            case "name":
            case "trader_name":
                if (active != null && active.config() != null) {
                    return ColorUtil.translate(active.config().displayName());
                }
                return ColorUtil.translate("&aТорговец опытом");
        }

        return null;
    }
}
