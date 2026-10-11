package ru.rooyzee.elytrixairdrop.managers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;
import org.bukkit.World;
import ru.rooyzee.elytrixairdrop.Main;

public class RegionManager {

    private final Main plugin;

    public RegionManager(Main plugin) { this.plugin = plugin; }

    public String createRegion(Location center, int radius, String airdropId) {
        String id = "elytrix_" + Long.toHexString(System.currentTimeMillis());
        World world = center.getWorld();

        BlockVector3 min = BlockVector3.at(center.getBlockX() - radius, 0, center.getBlockZ() - radius);
        BlockVector3 max = BlockVector3.at(center.getBlockX() + radius, 255, center.getBlockZ() + radius);

        ProtectedCuboidRegion region = new ProtectedCuboidRegion(id, min, max);
        region.setPriority(100);
        if ("peaceful".equalsIgnoreCase(airdropId)) {
            region.setFlag(Flags.PVP, StateFlag.State.DENY);
        } else {
            region.setFlag(Flags.PVP, StateFlag.State.ALLOW);
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
        if (rm == null) return null;
        rm.addRegion(region);
        return id;
    }

    public void removeRegion(World world, String id) {
        if (world == null || id == null) return;
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
        if (rm == null) return;
        rm.removeRegion(id);
        try {
            rm.save();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save WorldGuard regions after removing " + id + ": " + e.getMessage());
        }
    }

    public boolean isInRegion(Location loc, String id) {
        if (id == null) return false;
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(loc.getWorld()));
        if (rm == null) return false;
        ProtectedRegion region = rm.getRegion(id);
        if (region == null) return false;
        return region.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}