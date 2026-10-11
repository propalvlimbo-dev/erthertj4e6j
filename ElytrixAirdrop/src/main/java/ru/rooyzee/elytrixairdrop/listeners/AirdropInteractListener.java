package ru.rooyzee.elytrixairdrop.listeners;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.airdrops.ActiveAirdrop;

public class AirdropInteractListener implements Listener {

    private final Main plugin;

    public AirdropInteractListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block b = e.getClickedBlock();
        if (b == null) return;

        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (a == null) return;

        if (a.handleChestClick(plugin, e.getPlayer(), b.getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLootClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;

        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (a == null) return;

        a.handleLootClick(plugin, (Player) e.getWhoClicked(), e);
    }
}