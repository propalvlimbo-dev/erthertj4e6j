package ru.rooyzee.elytrixparadise.listeners;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import ru.rooyzee.elytrixparadise.Main;

import java.util.Iterator;

public class ParadiseProtectionListener implements Listener {

    private final Main plugin;

    public ParadiseProtectionListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("elytrixparadise.admin") || p.hasPermission("elytrixparadise.bypass")) return;

        if (plugin.getConfigManager().isPreventBlockBreak() && plugin.getRegionManager().isInRegion(e.getBlock().getLocation())) {
            e.setCancelled(true);
            p.sendMessage(plugin.getConfigManager().getMessage("protection.block-break",
                    "&f☁ &#F8BEFBРай &7» &cСтроительство и разрушение блоков в Раю запрещено!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("elytrixparadise.admin") || p.hasPermission("elytrixparadise.bypass")) return;

        if (plugin.getConfigManager().isPreventBlockPlace() && plugin.getRegionManager().isInRegion(e.getBlock().getLocation())) {
            e.setCancelled(true);
            p.sendMessage(plugin.getConfigManager().getMessage("protection.block-place",
                    "&f☁ &#F8BEFBРай &7» &cСтроительство блоков в Раю запрещено!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (plugin.getRegionManager().isInRegion(b.getLocation())) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (plugin.getRegionManager().isInRegion(b.getLocation())) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent e) {
        if (e.getBlock() != null && plugin.getRegionManager().isInRegion(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent e) {
        if (e.getBlock() != null && plugin.getRegionManager().isInRegion(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }
}
