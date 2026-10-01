package ru.rooyzee.elytrixtrader.expansion;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.TraderInstance;

import java.util.List;

public class TraderExpansion extends PlaceholderExpansion {

    private final Main plugin;

    public TraderExpansion(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() { return "elytrixtrader"; }

    @Override
    public String getAuthor() { return "rooyzee"; }

    @Override
    public String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) return null;

        // %elytrixtrader_tracker%
        // Показывает: время следующего появления или координаты активного
        if (params.equalsIgnoreCase("tracker")) {
            List<TraderInstance> instances = plugin.traders().instances();
            TraderInstance active = null;
            for (TraderInstance inst : instances) {
                if (inst.alive()) {
                    active = inst;
                    break;
                }
            }
            if (active != null) {
                // Торговец активен — показываем координаты
                int x = active.location().getBlockX();
                int y = active.location().getBlockY();
                int z = active.location().getBlockZ();
                String world = active.location().getWorld() == null ? "?" : active.location().getWorld().getName();
                return "&aАктивен: &f" + world + " &7" + x + " " + y + " " + z;
            }

            // Нет активного — показываем время следующего + статус расписания
            if (!plugin.config().scheduleEnabled() || plugin.config().scheduleTimes().isEmpty()) {
                return "&cВыключен";
            }
            return "&eСледующий: &f" + plugin.traders().nextEventTime();
        }

        return null;
    }
}