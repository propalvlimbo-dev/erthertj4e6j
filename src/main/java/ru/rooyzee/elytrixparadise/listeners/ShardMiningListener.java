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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.RED_GLAZED_TERRACOTTA || block.getType() == Material.GRAY_GLAZED_TERRACOTTA) {
            ParadiseShard shard = plugin.getShardManager().getShard(block.getLocation());
            if (shard != null) {
                event.setCancelled(true);
                plugin.getShardManager().handleShardHit(event.getPlayer(), block.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.RED_GLAZED_TERRACOTTA || block.getType() == Material.GRAY_GLAZED_TERRACOTTA) {
            ParadiseShard shard = plugin.getShardManager().getShard(block.getLocation());
            if (shard != null) {
                event.setCancelled(true);
                plugin.getShardManager().handleShardHit(event.getPlayer(), block.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && (block.getType() == Material.RED_GLAZED_TERRACOTTA || block.getType() == Material.GRAY_GLAZED_TERRACOTTA)) {
                ParadiseShard shard = plugin.getShardManager().getShard(block.getLocation());
                if (shard != null) {
                    event.setCancelled(true);
                    plugin.getShardManager().handleShardHit(event.getPlayer(), block.getLocation());
                }
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && (block.getType() == Material.RED_GLAZED_TERRACOTTA || block.getType() == Material.GRAY_GLAZED_TERRACOTTA)) {
                ParadiseShard shard = plugin.getShardManager().getShard(block.getLocation());
                if (shard != null) {
                    event.setCancelled(true);
                    Player player = event.getPlayer();
                    plugin.getShardManager().handleShardHit(player, block.getLocation());
                }
            }
        }
    }
}
