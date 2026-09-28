package ru.rooyzee.elytrixairdrop.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.airdrops.ActiveAirdrop;
import ru.rooyzee.elytrixairdrop.airdrops.Airdrop;
import ru.rooyzee.elytrixairdrop.airdrops.types.ActiveFireAirdrop;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ProtectionListener implements Listener {

    private final Main plugin;
    private final Set<UUID> warnedPlayers = new HashSet<>();

    public ProtectionListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        warnedPlayers.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (a == null) return;
        Location loc = e.getBlock().getLocation();
        if (!near(loc, a)) return;

        if (a instanceof ActiveFireAirdrop) {
            ActiveFireAirdrop fire = (ActiveFireAirdrop) a;
            if (fire.isBreakable(loc)) {
                e.setCancelled(false);
                fire.onBreakableBroken(plugin, loc);
                return;
            }
            if (fire.isMeteorArea(loc)) { e.setCancelled(true); return; }
        }

        if (a.isProtectedBlock(loc)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player) || !(e.getDamager() instanceof Player)) return;

        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (a == null || a.getRegionId() == null) return;
        if (a.getType().isPvpAllowed()) return;

        if (plugin.getRegionManager().isInRegion(e.getEntity().getLocation(), a.getRegionId())
                || plugin.getRegionManager().isInRegion(e.getDamager().getLocation(), a.getRegionId())) {
            e.setCancelled(true);
            ((Player) e.getDamager()).sendMessage(plugin.getConfigManager().getMessage("airdrop.pvp-blocked"));
        }
    }

    @EventHandler
    public void onFlight(PlayerToggleFlightEvent e) {
        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (a == null || a.getRegionId() == null) return;

        Player p = e.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE
                || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        if (p.hasPermission("elytrixairdrop.admin")) return;
        if (!plugin.getRegionManager().isInRegion(p.getLocation(), a.getRegionId())) return;

        if (e.isFlying()) {
            e.setCancelled(true);
            p.setAllowFlight(false);
            p.setFlying(false);
            p.sendMessage(plugin.getConfigManager().getMessage("airdrop.no-fly"));
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (a == null || a.getRegionId() == null) return;

        Player p = e.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE
                || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        boolean inZone = plugin.getRegionManager().isInRegion(p.getLocation(), a.getRegionId());

        if (inZone) {
            if (warnedPlayers.add(p.getUniqueId())) {
                if (a.getType() == Airdrop.FIRE) {
                    p.sendMessage(plugin.getConfigManager().getMessage("airdrop.no-fly"));
                    p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.7f);
                    p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.5f);
                } else {
                    p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f);
                }
            }
            if (p.hasPermission("elytrixairdrop.admin")) return;
            if (p.isFlying() || p.getAllowFlight()) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }
        } else {
            warnedPlayers.remove(p.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (a == null || a.getRegionId() == null) return;
        if (e.getPlayer().hasPermission("elytrixairdrop.admin")) return;

        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if (c == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || c == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            Location to = e.getTo();
            Location from = e.getFrom();
            if ((to != null && plugin.getRegionManager().isInRegion(to, a.getRegionId()))
                    || plugin.getRegionManager().isInRegion(from, a.getRegionId())) {
                e.setCancelled(true);
                e.getPlayer().sendMessage(plugin.getConfigManager().getMessage("airdrop.no-teleport"));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearlThrow(PlayerInteractEvent e) {
        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (a == null || a.getRegionId() == null) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getItem() == null) return;
        Material t = e.getItem().getType();
        if (t != Material.ENDER_PEARL && t != Material.CHORUS_FRUIT) return;
        if (e.getPlayer().hasPermission("elytrixairdrop.admin")) return;
        if (!plugin.getRegionManager().isInRegion(e.getPlayer().getLocation(), a.getRegionId())) return;

        e.setCancelled(true);
        e.getPlayer().updateInventory();
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (a == null) return;
        if (!near(e.getLocation(), a)) return;
        e.blockList().removeIf(b -> a.isProtectedBlock(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (a == null) return;
        if (!near(e.getBlock().getLocation(), a)) return;
        e.blockList().removeIf(b -> a.isProtectedBlock(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (a == null) return;
        if (!near(e.getToBlock().getLocation(), a)) return;
        if (a.isProtectedBlock(e.getToBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (a == null || e.getSource().getLocation() == null) return;
        Location loc = e.getSource().getLocation().getBlock().getLocation();
        if (!near(loc, a)) return;
        if (a.isProtectedBlock(loc)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) { piston(e.getBlocks(), e); }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) { piston(e.getBlocks(), e); }

    private void piston(List<Block> blocks, org.bukkit.event.Cancellable e) {
        ActiveAirdrop a = plugin.getAirdropManager().getActive();
        if (a == null) return;
        for (Block b : blocks) {
            if (!near(b.getLocation(), a)) continue;
            if (a.isProtectedBlock(b.getLocation())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private boolean near(Location loc, ActiveAirdrop a) {
        if (loc == null || loc.getWorld() == null || a.getCenter().getWorld() == null) return false;
        if (!loc.getWorld().equals(a.getCenter().getWorld())) return false;
        return Math.abs(loc.getBlockX() - a.getCenter().getBlockX()) <= 100
                && Math.abs(loc.getBlockZ() - a.getCenter().getBlockZ()) <= 100;
    }
}