package ru.rooyzee.elytrixparadise.tasks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
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
            if (plugin.isActionBarDisabled(player.getUniqueId())) {
                continue;
            }

            Location pLoc = player.getLocation();
            double dy = pLoc.getY();

            // 3D Proximity Check: высота и горизонтальный радиус
            if (dy < minY || dy > maxY) {
                continue;
            }

            double dx = pLoc.getX() - center.getX();
            double dz = pLoc.getZ() - center.getZ();
            if (dx * dx + dz * dz > radiusXZSq) {
                continue;
            }

            // Проверка близости к конкретному осколку (< 6 блоков)
            ParadiseShard nearbyShard = null;
            for (ParadiseShard s : plugin.getShardManager().getAllShards()) {
                if (s.getLocation().getWorld().equals(pLoc.getWorld()) && s.getLocation().distanceSquared(pLoc) <= 36.0) {
                    nearbyShard = s;
                    break;
                }
            }

            String msg;
            if (nearbyShard != null) {
                if (nearbyShard.getState() == ParadiseShard.ShardState.ACTIVE) {
                    double risk = Math.round(nearbyShard.getCurrentExplosionChance() * 10.0) / 10.0;
                    msg = "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBОсколок Рая &8| &aДобывай киркой &8| &fРиск: &#F8BEFB" + risk + "%";
                } else {
                    String timeStr = ColorUtil.formatTimeShort(nearbyShard.getCooldownRemaining());
                    msg = "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBОсколок Рая &8| &cПерезарядка &8| &#F8BEFB" + timeStr;
                }
            } else {
                if (showOffHint) {
                    msg = "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBРайское место &8| &7Чтобы отключить: &#F8BEFB/paradise off";
                } else {
                    msg = "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBРайское место &8| &fОсколков активно: &#F8BEFB" + activeShards + "&7/&#F8BEFB" + totalShards;
                }
            }

            ColorUtil.sendActionBar(player, msg);
        }
    }
}
