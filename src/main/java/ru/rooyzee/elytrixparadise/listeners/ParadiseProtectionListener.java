package ru.rooyzee.elytrixparadise.listeners;

import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.util.HashSet;
import java.util.List;
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

    /**
     * Блокировка команд (/setwarp, /warp, /sethome, /home, /tp, /spawn и т.д.) внутри зоны Райского места
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("elytrixparadise.bypass.commands")) return;

        if (plugin.getRegionManager().isInParadiseRegion(player.getLocation())) {
            String fullMsg = event.getMessage().trim();
            if (fullMsg.startsWith("/")) {
                String cmdName = fullMsg.substring(1).split(" ")[0].toLowerCase();
                if (cmdName.contains(":")) {
                    cmdName = cmdName.substring(cmdName.indexOf(":") + 1);
                }

                List<String> blocked = plugin.getConfigManager().getBlockedCommands();
                for (String b : blocked) {
                    if (cmdName.equalsIgnoreCase(b)) {
                        event.setCancelled(true);
                        String prefix = plugin.getConfigManager().getAdminPrefix();
                        player.sendMessage(ColorUtil.colorize(prefix + "&cИспользование команды &f/" + cmdName + " &cзапрещено в зоне Райского места!"));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                        return;
                    }
                }
            }
        }
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
}
