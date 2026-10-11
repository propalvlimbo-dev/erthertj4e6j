package ru.rooyzee.elytrixairdrop.services;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.airdrops.ActiveAirdrop;

public class LaunchService {

    private static final double RADIUS = 20.0;
    private static final int SCAN_HEIGHT = 30;

    private final Main plugin;
    private final ActiveAirdrop active;
    private final Location center;

    public LaunchService(Main plugin, ActiveAirdrop active, Location center) {
        this.plugin = plugin;
        this.active = active;
        this.center = center;
    }

    public void launch() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(center.getWorld())) continue;
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;

            double dx = p.getLocation().getX() - center.getX();
            double dz = p.getLocation().getZ() - center.getZ();
            if (Math.sqrt(dx * dx + dz * dz) > RADIUS) continue;

            Location safe = findOpenAir(p);
            if (safe != null && safe.getBlockY() > p.getLocation().getBlockY()) p.teleport(safe);

            p.setFallDistance(0);
            p.setAllowFlight(false);
            p.setFlying(false);
            p.setVelocity(new Vector(0, 2.5, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, false, false));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.3f);
        }
    }

    private Location findOpenAir(Player p) {
        Location loc = p.getLocation();
        int x = loc.getBlockX(), z = loc.getBlockZ();
        int maxY = Math.min(p.getWorld().getMaxHeight() - 2, loc.getBlockY() + SCAN_HEIGHT);
        for (int y = loc.getBlockY(); y <= maxY; y++) {
            Block b1 = p.getWorld().getBlockAt(x, y, z);
            Block b2 = p.getWorld().getBlockAt(x, y + 1, z);
            if (b1.getType() == Material.AIR && b2.getType() == Material.AIR) {
                return new Location(p.getWorld(), loc.getX(), y, loc.getZ(), loc.getYaw(), loc.getPitch());
            }
        }
        return null;
    }
}