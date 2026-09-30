package ru.rooyzee.elytrixparadise.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.shards.ParadiseShard;

public class ShardMiningListener implements Listener {

    private final Main plugin;

    public ShardMiningListener(Main plugin) {
        this.plugin = plugin;
    }

    private boolean isShardMaterial(Material type) {
        return type == Material.RED_GLAZED_TERRACOTTA || type == Material.GRAY_GLAZED_TERRACOTTA;
    }

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
                plugin.getShardManager().handleShardHit(event.getPlayer(), block.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        if (isShardMaterial(block.getType())) {
            event.setCancelled(true);
            ParadiseShard shard = plugin.getShardManager().getShard(block.getLocation());
            if (shard == null) {
                shard = plugin.getShardManager().registerShard(block.getLocation());
            }
            if (shard != null) {
                plugin.getShardManager().handleShardHit(event.getPlayer(), block.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block != null && isShardMaterial(block.getType())) {
            event.setCancelled(true);
            ParadiseShard shard = plugin.getShardManager().getShard(block.getLocation());
            if (shard == null) {
                shard = plugin.getShardManager().registerShard(block.getLocation());
            }
            if (shard != null && event.getAction() == Action.LEFT_CLICK_BLOCK) {
                plugin.getShardManager().handleShardHit(event.getPlayer(), block.getLocation());
            }
        }
    }
}
