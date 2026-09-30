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
        boolean alt = (tickCount % 2 == 0);

        for (Player player : world.getPlayers()) {
            Location pLoc = player.getLocation();
            double dy = pLoc.getY();

            // Strict 3D Proximity Check: only show actionbar if in event Y range (not running on ground)
            if (dy < minY || dy > maxY) {
                continue;
            }

            double dx = pLoc.getX() - center.getX();
            double dz = pLoc.getZ() - center.getZ();
            if (dx * dx + dz * dz > radiusXZSq) {
                continue;
            }

            // Check if player is right next to a shard (< 4 blocks)
            ParadiseShard nearbyShard = null;
            for (ParadiseShard s : plugin.getShardManager().getAllShards()) {
                if (s.getLocation().distanceSquared(pLoc) <= 16.0) {
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
                if (alt) {
                    msg = "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBРайское место &8| &fОсколков активно: &#F8BEFB" + activeShards + "&7/&#F8BEFB" + totalShards;
                } else {
                    msg = "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBРайское место &8| &fДобывай осколки ради наград";
                }
            }

            ColorUtil.sendActionBar(player, msg);
        }
    }
}
