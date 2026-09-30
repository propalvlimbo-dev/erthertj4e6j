package ru.rooyzee.elytrixparadise.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import ru.rooyzee.elytrixparadise.Main;

import java.util.Iterator;

public class ShardProtectionListener implements Listener {

    private final Main plugin;

    public ShardProtectionListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (b.getType() == Material.RED_GLAZED_TERRACOTTA || b.getType() == Material.GRAY_GLAZED_TERRACOTTA) {
                if (plugin.getShardManager().getShard(b.getLocation()) != null) {
                    it.remove();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (b.getType() == Material.RED_GLAZED_TERRACOTTA || b.getType() == Material.GRAY_GLAZED_TERRACOTTA) {
                if (plugin.getShardManager().getShard(b.getLocation()) != null) {
                    it.remove();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (b.getType() == Material.RED_GLAZED_TERRACOTTA || b.getType() == Material.GRAY_GLAZED_TERRACOTTA) {
                if (plugin.getShardManager().getShard(b.getLocation()) != null) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (b.getType() == Material.RED_GLAZED_TERRACOTTA || b.getType() == Material.GRAY_GLAZED_TERRACOTTA) {
                if (plugin.getShardManager().getShard(b.getLocation()) != null) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
}
