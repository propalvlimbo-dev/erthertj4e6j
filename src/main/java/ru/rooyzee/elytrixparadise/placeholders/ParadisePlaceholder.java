package ru.rooyzee.elytrixparadise.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.event.ParadiseEvent;

public class ParadisePlaceholder extends PlaceholderExpansion {

    private final Main plugin;

    public ParadisePlaceholder(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "elytrixparadise";
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
    public String onPlaceholderRequest(Player player, String identifier) {
        if (identifier == null) return null;

        ParadiseEvent event = plugin.getEventManager().getEvent();
        Location center = plugin.getConfigManager().getCenterLocation();

        switch (identifier.toLowerCase()) {
            case "status":
                if (event == null || !event.isRunning()) return "Не активен";
                return event.getPhaseDisplayName() + " &7(&f" + event.getRemainingFormatted() + "&7)";

            case "phase":
                return event != null ? event.getPhaseDisplayName() : "Не активен";

            case "phase_raw":
                return event != null ? event.getCurrentPhase().getId() : "none";

            case "time":
                return event != null ? event.getRemainingFormatted() : "00:00";

            case "time_russian":
                return event != null ? event.getRemainingRussian() : "0 сек";

            case "time_seconds":
                return event != null ? String.valueOf(event.getRemainingSeconds()) : "0";

            case "players":
                return event != null ? String.valueOf(event.getPlayerCount()) : "0";

            case "world":
                return center.getWorld() != null ? center.getWorld().getName() : "world";

            case "x":
                return String.valueOf(center.getBlockX());

            case "y":
                return String.valueOf(center.getBlockY());

            case "z":
                return String.valueOf(center.getBlockZ());

            case "is_active":
                return event != null && event.isRunning() ? "true" : "false";

            case "cycles":
                return event != null ? String.valueOf(event.getTotalCycles()) : "0";

            default:
                return null;
        }
    }
}
