package ru.rooyzee.elytrixparadise.listeners;

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
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (isShardMaterial(block.getType())) {
            event.setCancelled(true);
            ParadiseShard shard = plugin.getShardManager().getShard(block.getLocation());
            if (shard == null) {
                shard = plugin.getShardManager().registerShard(block.getLocation());
            }
            if (shard != null) {
                plugin.getShardManager().handleShardMined(event.getPlayer(), block.getLocation());
            }
        }
    }

    /**
     * При начале копания — проверка статуса и инструмента (без мгновенной выдачи лута).
     */
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
