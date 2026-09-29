package ru.rooyzee.elytrixparadise.event;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.rooyzee.elytrixparadise.Main;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ParadiseEventManager {

    private final Main plugin;
    private ParadiseEvent event;
    private BukkitTask task;

    public ParadiseEventManager(Main plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        Location center = plugin.getConfigManager().getCenterLocation();
        Location safeSpawn = plugin.getConfigManager().getSafeSpawnLocation();
        this.event = new ParadiseEvent(plugin, center, safeSpawn);
    }

    public void startScheduler() {
        if (event == null) {
            initialize();
        }

        if (task != null) {
            task.cancel();
        }

        event.start();

        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (event == null || !event.isRunning()) return;

                // 1. Tick event timer & phase transitions
                event.tick();

                // 2. Scan and track players inside the region
                updatePlayerTracking();

                // 3. Update hologram
                plugin.getHologramManager().createOrUpdateHologram(event);

                // 4. Update ambient effects & bossbar
                plugin.getAmbientManager().tick(event);

            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка в тике ParadiseEventManager: " + e.getMessage());
            }
        }, 20L, 20L);
    }

    private void updatePlayerTracking() {
        if (event == null) return;

        Set<UUID> currentFound = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getRegionManager().isPlayerInParadise(p)) {
                currentFound.add(p.getUniqueId());
                if (!event.isPlayerInside(p)) {
                    event.addPlayer(p);
                }
            }
        }

        // Check players who left
        for (Player p : event.getOnlinePlayersInside()) {
            if (!currentFound.contains(p.getUniqueId())) {
                event.removePlayer(p);
            }
        }
    }

    public void stopScheduler() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (event != null) {
            event.stop();
        }
    }

    public ParadiseEvent getEvent() {
        return event;
    }

    public void restartEvent() {
        if (event != null) {
            event.setPhase(ParadisePhase.WAITING);
            event.start();
        }
    }

    public void forcePhase(ParadisePhase phase) {
        if (event != null) {
            event.setPhase(phase);
        }
    }
}
