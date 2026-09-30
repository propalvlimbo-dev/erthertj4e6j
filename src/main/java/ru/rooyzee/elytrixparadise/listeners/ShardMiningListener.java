package ru.rooyzee.elytrixparadise.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.shards.ParadiseShard;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

public class ShardMiningListener implements Listener {

    private final Main plugin;

    public ShardMiningListener(Main plugin) {
        this.plugin = plugin;
    }

    private boolean isShardMaterial(Material type) {
        return type == Material.RED_GLAZED_TERRACOTTA || type == Material.GRAY_GLAZED_TERRACOTTA;
    }

    /**
     * Лут и опыт выпадают ТОЛЬКО при полном ломании (выкапывании) осколка.
     * Блок мгновенно восстанавливается на сервере и клиенте.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        final Block block = event.getBlock();
        if (isShardMaterial(block.getType())) {
            event.setCancelled(true);
            event.setDropItems(false);

            ParadiseShard shard = plugin.getShardManager().getShard(block.getLocation());
            if (shard == null) {
                shard = plugin.getShardManager().registerShard(block.getLocation());
            }

            if (shard != null) {
                plugin.getShardManager().handleShardMined(event.getPlayer(), block.getLocation());
            }

            final ParadiseShard finalShard = shard;
            // Принудительное мгновенное подтверждение блока для клиента и сервера
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    Material targetMat = (finalShard != null && finalShard.getState() == ParadiseShard.ShardState.COOLDOWN)
                            ? Material.GRAY_GLAZED_TERRACOTTA
                            : Material.RED_GLAZED_TERRACOTTA;
                    block.setType(targetMat, false);
                    block.getState().update(true, true);
                    for (Player p : block.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(block.getLocation()) <= 64 * 64) {
                            p.sendBlockChange(block.getLocation(), targetMat.createBlockData());
                        }
                    }
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        if (isShardMaterial(block.getType())) {
            ParadiseShard shard = plugin.getShardManager().getShard(block.getLocation());
            if (shard == null) {
                shard = plugin.getShardManager().registerShard(block.getLocation());
            }
            if (shard != null && shard.getState() == ParadiseShard.ShardState.COOLDOWN) {
                Player player = event.getPlayer();
                String prefix = plugin.getConfigManager().getAdminPrefix();
                player.sendMessage(ColorUtil.colorize(prefix + "&cОсколок находится на перезарядке! &fОсталось: &#F8BEFB"
                        + ColorUtil.formatTimeShort(shard.getCooldownRemaining())));
            }
        }
    }
}
