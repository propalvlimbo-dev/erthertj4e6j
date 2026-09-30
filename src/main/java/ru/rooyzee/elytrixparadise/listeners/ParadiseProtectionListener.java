package ru.rooyzee.elytrixparadise.listeners;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ParadiseProtectionListener implements Listener {

    private final Main plugin;
    private final Set<UUID> warnedFlyPlayers = new HashSet<>();

    public ParadiseProtectionListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        warnedFlyPlayers.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlightToggle(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.hasPermission("elytrixparadise.bypass")) return;

        if (plugin.getRegionManager().isInParadiseRegion(player.getLocation())) {
            event.setCancelled(true);
            player.setAllowFlight(false);
            player.setFlying(false);
            String prefix = plugin.getConfigManager().getAdminPrefix();
            player.sendMessage(ColorUtil.colorize(prefix + "&cПолёт в зоне Райского места запрещён!"));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.7f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        boolean inRegion = plugin.getRegionManager().isInParadiseRegion(event.getTo());
        if (inRegion) {
            // Disable Godmode / Invulnerability
            if (player.isInvulnerable()) {
                player.setInvulnerable(false);
            }

            // Disable Flight
            if (player.isFlying() || player.getAllowFlight()) {
                if (!player.hasPermission("elytrixparadise.bypass")) {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                    if (warnedFlyPlayers.add(player.getUniqueId())) {
                        String prefix = plugin.getConfigManager().getAdminPrefix();
                        player.sendMessage(ColorUtil.colorize(prefix + "&cРежим полёта отключён в зоне Райского места!"));
                        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.7f);
                    }
                }
            }
        } else {
            warnedFlyPlayers.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getPlayer().hasPermission("elytrixparadise.bypass")) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            Location to = event.getTo();
            Location from = event.getFrom();
            if ((to != null && plugin.getRegionManager().isInParadiseRegion(to))
                    || plugin.getRegionManager().isInParadiseRegion(from)) {
                event.setCancelled(true);
                String prefix = plugin.getConfigManager().getAdminPrefix();
                event.getPlayer().sendMessage(ColorUtil.colorize(prefix + "&cИспользование жемчуга Края и хоруса запрещено в Райском месте!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearlThrow(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null) return;

        Material itemType = event.getItem().getType();
        if (itemType != Material.ENDER_PEARL && itemType != Material.CHORUS_FRUIT) return;
        if (event.getPlayer().hasPermission("elytrixparadise.bypass")) return;

        if (plugin.getRegionManager().isInParadiseRegion(event.getPlayer().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().updateInventory();
            String prefix = plugin.getConfigManager().getAdminPrefix();
            event.getPlayer().sendMessage(ColorUtil.colorize(prefix + "&cИспользование жемчуга Края и хоруса запрещено в Райском месте!"));
        }
    }
}
