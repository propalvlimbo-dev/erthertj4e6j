package ru.rooyzee.elytrixcemetery.tasks;

import org.bukkit.scheduler.BukkitRunnable;
import ru.rooyzee.elytrixcemetery.Main;

public class CemeteryTickTask extends BukkitRunnable {

    private final Main plugin;

    public CemeteryTickTask(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.isEnabled()) return;
        try {
            plugin.getGraveManager().tick();
            plugin.getGhostController().tick();
            plugin.getHologramManager().createOrUpdateMainHologram();
        } catch (Throwable t) {
            plugin.getLogger().warning("Tick task error: " + t.getMessage());
        }
    }
}
