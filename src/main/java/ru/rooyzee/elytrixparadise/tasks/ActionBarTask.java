package ru.rooyzee.elytrixparadise.tasks;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.shards.ParadiseShard;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

public class ActionBarTask extends BukkitRunnable {

    private final Main plugin;
    private int tickCount = 0;

    public ActionBarTask(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        World world = Bukkit.getWorld(plugin.getConfigManager().getWorldName());
        if (world == null) return;

        Location center = plugin.getConfigManager().getCenterLocation();
        double radiusXZ = plugin.getConfigManager().getActionbarRadiusXZ();
        double radiusXZSq = radiusXZ * radiusXZ;
        double minY = plugin.getConfigManager().getActionbarMinY();
        double maxY = plugin.getConfigManager().getActionbarMaxY();

        int activeShards = plugin.getShardManager().getActiveShardsCount();
        int totalShards = plugin.getShardManager().getAllShards().size();

        tickCount++;
        // Полный цикл 35 секунд: 30 секунд статус осколков, 5 секунд подсказка отключения /paradise off
        int cycleSecond = tickCount % 35;
        boolean showOffHint = (cycleSecond >= 30);

        for (Player player : world.getPlayers()) {
            Location pLoc = player.getLocation();
            double dy = pLoc.getY();

            // 3D Proximity Check
            if (dy < minY || dy > maxY) {
                removeFatigueIfPresent(player);
                continue;
            }

            double dx = pLoc.getX() - center.getX();
            double dz = pLoc.getZ() - center.getZ();
            if (dx * dx + dz * dz > radiusXZSq) {
                removeFatigueIfPresent(player);
                continue;
            }

            // Поиск ближайшего активного/неактивного осколка (радиус 7 блоков от центра блока)
            ParadiseShard nearbyActiveShard = null;
            ParadiseShard nearbyCooldownShard = null;

            for (ParadiseShard s : plugin.getShardManager().getAllShards()) {
                if (s.getLocation().getWorld().equals(pLoc.getWorld())) {
                    Location sCenter = s.getLocation().clone().add(0.5, 0.5, 0.5);
                    if (sCenter.distanceSquared(pLoc) <= 49.0) { // 7.0 блоков
                        if (s.getState() == ParadiseShard.ShardState.ACTIVE) {
                            nearbyActiveShard = s;
                            break;
                        } else {
                            nearbyCooldownShard = s;
                        }
                    }
                }
            }

            String msg;
            if (nearbyActiveShard != null) {
                double risk = Math.round(nearbyActiveShard.getCurrentExplosionChance() * 10.0) / 10.0;
                msg = "&f✦ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBОсколок Рая &8| &aДобывай киркой &8| &fРиск: &#F8BEFB" + risk + "%";

                // Плавное наложение Утомления ТОЛЬКО возле активного осколка без мигания
                if (plugin.getConfigManager().isMiningFatigueEnabled()
                        && player.getGameMode() != GameMode.CREATIVE
                        && player.getGameMode() != GameMode.SPECTATOR
                        && !player.hasPermission("elytrixparadise.fatigue.bypass")) {

                    int level = plugin.getConfigManager().getMiningFatigueLevel();
                    int amplifier = Math.max(0, level - 1);
                    PotionEffect cur = player.getPotionEffect(PotionEffectType.SLOW_DIGGING);

                    // Обновляем только когда эффекта нет или осталось меньше 40 тиков (2 сек)
                    if (cur == null || cur.getDuration() < 40) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 80, amplifier, true, false, true), true);
                    }
                }
            } else if (nearbyCooldownShard != null) {
                String timeStr = ColorUtil.formatTimeShort(nearbyCooldownShard.getCooldownRemaining());
                msg = "&f✦ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBОсколок Рая &8| &cПерезарядка &8| &#F8BEFB" + timeStr;
                removeFatigueIfPresent(player);
            } else {
                removeFatigueIfPresent(player);
                if (showOffHint) {
                    msg = "&f✦ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBРайское место &8| &7Чтобы отключить: &#F8BEFB/paradise off";
                } else {
                    msg = "&f✦ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBРайское место &8| &fОсколков активно: &#F8BEFB" + activeShards + "&7/&#F8BEFB" + totalShards;
                }
            }

            if (!plugin.isActionBarDisabled(player.getUniqueId())) {
                ColorUtil.sendActionBar(player, msg);
            }
        }
    }

    private void removeFatigueIfPresent(Player player) {
        if (player.hasPotionEffect(PotionEffectType.SLOW_DIGGING) && !player.hasPermission("elytrixparadise.fatigue.keep")) {
            player.removePotionEffect(PotionEffectType.SLOW_DIGGING);
        }
    }
}
