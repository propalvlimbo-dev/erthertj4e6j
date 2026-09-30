package ru.rooyzee.elytrixparadise.tasks;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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

        // Применение эффекта Утомления (Mining Fatigue) в зоне Райского места
        if (plugin.getConfigManager().isMiningFatigueEnabled()) {
            int level = plugin.getConfigManager().getMiningFatigueLevel();
            int amplifier = Math.max(0, level - 1);
            PotionEffect fatigueEffect = new PotionEffect(PotionEffectType.SLOW_DIGGING, 50, amplifier, true, false, true);

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if (player.hasPermission("elytrixparadise.fatigue.bypass")) {
                    continue;
                }
                if (plugin.getRegionManager().isInParadiseRegion(player.getLocation())) {
                    player.addPotionEffect(fatigueEffect, true);
                }
            }
        }
    }
}
