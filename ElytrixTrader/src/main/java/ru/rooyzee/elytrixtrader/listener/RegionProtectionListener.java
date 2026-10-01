package ru.rooyzee.elytrixtrader.listener;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.region.TraderRegion;

public class RegionProtectionListener implements Listener {

    private final Main plugin;

    public RegionProtectionListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
            Player p = event.getPlayer();
            // Optionally send message
            // plugin.messages().send(p, "general.region-protected");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlock();
        if (isProtected(block.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block block = event.getBlock();
        if (isProtected(block.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Location loc = event.getLocation();
        if (isProtected(loc)) {
            event.setCancelled(true);
        } else {
            // Remove blocks that are inside protected region from explosion list
            event.blockList().removeIf(b -> isProtected(b.getLocation()));
        }
    }

    private boolean isProtected(Location loc) {
        try {
            if (plugin.traders() == null || plugin.traders().regions() == null) return false;
            TraderRegion region = plugin.traders().regions().getRegionAt(loc);
            return region != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
