package ru.rooyzee.elytrixcemetery.managers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;
import org.bukkit.World;
import ru.rooyzee.elytrixcemetery.Main;

public class RegionManager {

    private static final String REGION_NAME = "elytrix_cemetery";

    private final Main plugin;

    public RegionManager(Main plugin) {
        this.plugin = plugin;
    }

    public void createOrUpdateRegion() {
        Location center = plugin.getConfigManager().getCenterLocation();
        if (center == null || center.getWorld() == null) return;

        World world = center.getWorld();
        int r = plugin.getConfigManager().getBoundaryRadius();

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
            if (rm == null) return;

            BlockVector3 min = BlockVector3.at(center.getBlockX() - r, 0, center.getBlockZ() - r);
            BlockVector3 max = BlockVector3.at(center.getBlockX() + r, world.getMaxHeight() - 1, center.getBlockZ() + r);

            ProtectedCuboidRegion region = new ProtectedCuboidRegion(REGION_NAME, min, max);
            region.setPriority(100);
            region.setFlag(Flags.PVP, StateFlag.State.ALLOW);
            region.setFlag(Flags.MOB_SPAWNING, StateFlag.State.ALLOW);
            region.setFlag(Flags.CHEST_ACCESS, StateFlag.State.ALLOW);
            region.setFlag(Flags.USE, StateFlag.State.ALLOW);
            region.setFlag(Flags.INTERACT, StateFlag.State.ALLOW);
            region.setFlag(Flags.CREEPER_EXPLOSION, StateFlag.State.DENY);
            region.setFlag(Flags.TNT, StateFlag.State.DENY);
            region.setFlag(Flags.OTHER_EXPLOSION, StateFlag.State.DENY);

            rm.addRegion(region);
            plugin.getLogger().info("Регион Кладбища " + REGION_NAME + " успешно зарегистрирован в WorldGuard с доступом к сундукам.");
        } catch (Throwable t) {
            plugin.getLogger().warning("WorldGuard hook not loaded or unavailable: " + t.getMessage());
        }
    }

    public void removeRegion() {
        Location center = plugin.getConfigManager().getCenterLocation();
        if (center == null || center.getWorld() == null) return;

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(center.getWorld()));
            if (rm != null && rm.hasRegion(REGION_NAME)) {
                rm.removeRegion(REGION_NAME);
            }
        } catch (Throwable ignored) {}
    }
}
