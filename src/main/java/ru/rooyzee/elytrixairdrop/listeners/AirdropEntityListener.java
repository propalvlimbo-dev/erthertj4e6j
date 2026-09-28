package ru.rooyzee.elytrixairdrop.listeners;

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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockChange(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof FallingBlock)) return;
        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (!(a instanceof ActiveFireAirdrop)) return;
        ActiveFireAirdrop fire = (ActiveFireAirdrop) a;
        if (fire.isTrackedDebris(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
            fire.removeTrackedDebris(e.getEntity().getUniqueId());
            e.getEntity().remove();
        }
    }
}