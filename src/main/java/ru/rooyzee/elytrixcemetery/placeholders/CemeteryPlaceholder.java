package ru.rooyzee.elytrixcemetery.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixcemetery.Main;

public class CemeteryPlaceholder extends PlaceholderExpansion {

    private final Main plugin;

    public CemeteryPlaceholder(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "elytrixcemetery";
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
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (params == null) return "";
        String p = params.toLowerCase();

        switch (p) {
            case "time":
            case "timer":
            case "refill_time":
                return plugin.getGraveManager().formatTime(plugin.getGraveManager().getRemainingRefillSeconds());

            case "graves":
                return plugin.getGraveManager().getDugCount() + "/" + plugin.getGraveManager().getTotalCount();

            case "dug":
                return String.valueOf(plugin.getGraveManager().getDugCount());

            case "total":
                return String.valueOf(plugin.getGraveManager().getTotalCount());

            case "ghosts":
            case "alive_ghosts":
                return String.valueOf(plugin.getGhostController().getAliveCount());

            case "status":
                return "Активно всегда";

            case "location":
                return "Кладбище";

            default:
                return null;
        }
    }
}
