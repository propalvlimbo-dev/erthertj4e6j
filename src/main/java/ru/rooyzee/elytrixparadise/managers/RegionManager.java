package ru.rooyzee.elytrixparadise.managers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.event.ParadisePhase;

public class RegionManager {

    private final Main plugin;

    public RegionManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isWorldGuardEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
    }

    public void createOrUpdateRegion() {
        if (!plugin.getConfigManager().isRegionEnabled()) return;
        if (!isWorldGuardEnabled()) return;

        Location center = plugin.getConfigManager().getCenterLocation();
        World world = center.getWorld();
        if (world == null) return;

        int radius = plugin.getConfigManager().getRegionRadius();
        String regionId = plugin.getConfigManager().getRegionName();

        BlockVector3 min = BlockVector3.at(
                center.getBlockX() - radius,
                Math.max(0, center.getBlockY() - 40),
                center.getBlockZ() - radius
        );
        BlockVector3 max = BlockVector3.at(
                center.getBlockX() + radius,
                Math.min(world.getMaxHeight() - 1, center.getBlockY() + 60),
                center.getBlockZ() + radius
        );

        ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);
        region.setPriority(200);

        // Configure WorldGuard flags
        if (plugin.getConfigManager().isPreventBlockBreak()) {
            region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
        }
        if (plugin.getConfigManager().isPreventBlockPlace()) {
            region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);
        }
        if (plugin.getConfigManager().isPreventMobSpawning()) {
            region.setFlag(Flags.MOB_SPAWNING, StateFlag.State.DENY);
        }
        if (plugin.getConfigManager().isPreventFallDamage()) {
            region.setFlag(Flags.FALL_DAMAGE, StateFlag.State.DENY);
        }

        region.setFlag(Flags.TNT, StateFlag.State.DENY);
        region.setFlag(Flags.OTHER_EXPLOSION, StateFlag.State.DENY);
        region.setFlag(Flags.LIGHTER, StateFlag.State.DENY);
        region.setFlag(Flags.FIRE_SPREAD, StateFlag.State.DENY);
        region.setFlag(Flags.CHEST_ACCESS, StateFlag.State.ALLOW);
        region.setFlag(Flags.USE, StateFlag.State.ALLOW);

        // PvP flag based on default or config
        boolean pvp = plugin.getConfigManager().getConfig().getBoolean("region.pvp", false);
        region.setFlag(Flags.PVP, pvp ? StateFlag.State.ALLOW : StateFlag.State.DENY);

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
        if (rm == null) return;

        rm.addRegion(region);
        try {
            rm.save();
            plugin.getLogger().info("Регион WorldGuard '" + regionId + "' успешно зарегистрирован (радиус: " + radius + ").");
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при сохранении региона WorldGuard: " + e.getMessage());
        }
    }

    public void updatePvpFlag(ParadisePhase phase) {
        if (!plugin.getConfigManager().isRegionEnabled() || !isWorldGuardEnabled()) return;

        World world = plugin.getConfigManager().getWorld();
        String regionId = plugin.getConfigManager().getRegionName();

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
        if (rm == null) return;

        ProtectedRegion region = rm.getRegion(regionId);
        if (region == null) return;

        boolean pvpEnabled = plugin.getConfigManager().getConfig()
                .getBoolean("event.phases." + phase.getId() + ".pvp", phase.isDefaultPvp());

        region.setFlag(Flags.PVP, pvpEnabled ? StateFlag.State.ALLOW : StateFlag.State.DENY);
    }

    public void removeRegion() {
        if (!isWorldGuardEnabled()) return;
        World world = plugin.getConfigManager().getWorld();
        String regionId = plugin.getConfigManager().getRegionName();

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
        if (rm == null) return;

        if (rm.hasRegion(regionId)) {
            rm.removeRegion(regionId);
            try {
                rm.save();
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при удалении региона WorldGuard: " + e.getMessage());
            }
        }
    }

    public boolean isInRegion(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        if (isWorldGuardEnabled()) {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(loc.getWorld()));
            if (rm != null) {
                ProtectedRegion region = rm.getRegion(plugin.getConfigManager().getRegionName());
                if (region != null) {
                    return region.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                }
            }
        }

        // Distance fallback
        Location center = plugin.getConfigManager().getCenterLocation();
        if (!loc.getWorld().equals(center.getWorld())) return false;

        int radius = plugin.getConfigManager().getRegionRadius();
        int dx = Math.abs(loc.getBlockX() - center.getBlockX());
        int dz = Math.abs(loc.getBlockZ() - center.getBlockZ());
        int dy = loc.getBlockY() - center.getBlockY();

        return dx <= radius && dz <= radius && dy >= -40 && dy <= 60;
    }

    public boolean isPlayerInParadise(Player player) {
        return player != null && isInRegion(player.getLocation());
    }
}
