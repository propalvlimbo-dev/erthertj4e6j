package ru.rooyzee.elytrixparadise.listeners;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.event.ParadiseEvent;
import ru.rooyzee.elytrixparadise.event.ParadisePhase;

public class ParadisePlayerListener implements Listener {

    private final Main plugin;

    public ParadisePlayerListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player player = (Player) e.getEntity();

        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (plugin.getConfigManager().isPreventFallDamage()) {
                if (plugin.getRegionManager().isPlayerInParadise(player)) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvPDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();

        Player attacker = null;
        if (e.getDamager() instanceof Player) {
            attacker = (Player) e.getDamager();
        } else if (e.getDamager() instanceof Projectile) {
            Projectile proj = (Projectile) e.getDamager();
            if (proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }
        }

        if (attacker == null) return;

        boolean victimIn = plugin.getRegionManager().isPlayerInParadise(victim);
        boolean attackerIn = plugin.getRegionManager().isPlayerInParadise(attacker);

        if (victimIn || attackerIn) {
            ParadiseEvent event = plugin.getEventManager().getEvent();
            ParadisePhase phase = event != null ? event.getCurrentPhase() : ParadisePhase.WAITING;

            boolean pvpAllowed = plugin.getConfigManager().getConfig()
                    .getBoolean("event.phases." + phase.getId() + ".pvp", phase.isDefaultPvp());

            if (!pvpAllowed) {
                e.setCancelled(true);
                attacker.sendMessage(plugin.getConfigManager().getMessage("event.pvp-disabled",
                        "&f☁ &#F8BEFBРай &7» &cPvP отключено в текущей фазе Рая!"));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        ParadiseEvent event = plugin.getEventManager().getEvent();
        if (event != null) {
            event.removePlayer(e.getPlayer());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        // Handled dynamically in next tick
    }
}
