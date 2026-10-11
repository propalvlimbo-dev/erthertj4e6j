package ru.rooyzee.elytrixairdrop.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.airdrops.ActiveAirdrop;

public class AirdropEntityListener implements Listener {

    private final Main plugin;

    public AirdropEntityListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockChange(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof FallingBlock)) return;
        FallingBlock fb = (FallingBlock) e.getEntity();

        Material mat = fb.getBlockData() != null ? fb.getBlockData().getMaterial() : fb.getMaterial();
        if (fb.hasMetadata("elytrix_fire_debris") || mat == Material.MAGMA_BLOCK) {
            e.setCancelled(true);
            fb.remove();
            e.getBlock().setType(Material.AIR, false);
            return;
        }

        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (a != null && a.getCenter() != null) {
            Location loc = e.getBlock().getLocation();
            if (loc.getWorld() != null && loc.getWorld().equals(a.getCenter().getWorld())) {
                if (loc.distanceSquared(a.getCenter()) <= 120 * 120) {
                    e.setCancelled(true);
                    fb.remove();
                    e.getBlock().setType(Material.AIR, false);
                }
            }
        }
    }
}
