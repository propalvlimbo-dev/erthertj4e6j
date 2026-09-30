package ru.rooyzee.elytrixparadise.tasks;

import org.bukkit.scheduler.BukkitRunnable;
import ru.rooyzee.elytrixparadise.Main;

public class ShardTickTask extends BukkitRunnable {

    private final Main plugin;

    public ShardTickTask(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (plugin.getShardManager() != null) {
            plugin.getShardManager().tick();
        }
    }
}
