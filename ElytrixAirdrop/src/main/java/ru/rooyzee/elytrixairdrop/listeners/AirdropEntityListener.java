package ru.rooyzee.elytrixairdrop.listeners;

import org.bukkit.Location;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.airdrops.ActiveAirdrop;
import ru.rooyzee.elytrixairdrop.airdrops.types.ActiveFireAirdrop;

public class AirdropEntityListener implements Listener {

    private final Main plugin;

    public AirdropEntityListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockChange(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof FallingBlock)) return;

        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (a == null) return;

        Location loc = e.getBlock().getLocation();
        if (loc.getWorld() == null || a.getCenter() == null || !loc.getWorld().equals(a.getCenter().getWorld())) {
            return;
        }

        double distSq = loc.distanceSquared(a.getCenter());
        // В зоне аирдропа (до 100 блоков) падающие блоки огненного аирдропа не должны превращаться в блоки
        if (distSq <= 100 * 100) {
            e.setCancelled(true);
            e.getEntity().remove();
            if (a instanceof ActiveFireAirdrop) {
                ((ActiveFireAirdrop) a).removeTrackedDebris(e.getEntity().getUniqueId());
            }
        }
    }
}
