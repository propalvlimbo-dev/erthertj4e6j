package ru.rooyzee.elytrixtrader.region;

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
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.TraderConfig;
import ru.rooyzee.elytrixtrader.model.TraderInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class RegionManager {

    private static final String PREFIX = "elytrix_";
    private static final int ID_LENGTH = 8;
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final Main plugin;
    private final Map<String, TraderRegion> regionsById = new ConcurrentHashMap<>();
    private final Map<Integer, TraderRegion> regionsByUid = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public RegionManager(Main plugin) {
        this.plugin = plugin;
    }

    public TraderRegion createRegion(Location center, TraderConfig config, TraderInstance instance) {
        int radius = 30;
        World world = center.getWorld();
        if (world == null) return null;

        // Защита от стыка регионов: не создаём регион поверх уже существующего
        if (wouldOverlap(center, radius, world)) {
            if (plugin.config().debug()) {
            }
            return null;
        }

        String id = generateId();

        // Create our own tracking region
        TraderRegion traderRegion = new TraderRegion(id, center, radius, config.id(), instance.uid());
        regionsById.put(id, traderRegion);
        regionsByUid.put(instance.uid(), traderRegion);

        // Try WorldGuard
        try {
            if (isWorldGuardAvailable()) {
                BlockVector3 min = BlockVector3.at(center.getBlockX() - radius, 0, center.getBlockZ() - radius);
                BlockVector3 max = BlockVector3.at(center.getBlockX() + radius, 255, center.getBlockZ() + radius);
                ProtectedCuboidRegion wgRegion = new ProtectedCuboidRegion(id, min, max);
                wgRegion.setPriority(100);
                // PVP allowed as per requirement
                wgRegion.setFlag(Flags.PVP, StateFlag.State.ALLOW);
                // Deny building
                try {
                    wgRegion.setFlag(Flags.BUILD, StateFlag.State.DENY);
                } catch (Throwable ignored) {}
                try {
                    wgRegion.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
                } catch (Throwable ignored) {}
                try {
                    wgRegion.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);
                } catch (Throwable ignored) {}

                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
                if (rm != null) {
                    rm.addRegion(wgRegion);
                    if (plugin.config().debug()) {
                    }
                }
            }
        } catch (Throwable t) {
            if (plugin.config().debug()) {
            }
        }

        if (plugin.config().debug()) {
        }

        return traderRegion;
    }

    private boolean wouldOverlap(Location center, int radius, World world) {
        // 1. Проверяем наши собственные регионы
        for (TraderRegion region : regionsById.values()) {
            if (region.center() == null || region.center().getWorld() == null) continue;
            if (!region.center().getWorld().equals(world)) continue;
            double dx = region.center().getX() - center.getX();
            double dz = region.center().getZ() - center.getZ();
            double minDist = region.radius() + radius;
            if (dx * dx + dz * dz < minDist * minDist) {
                return true;
            }
        }
        // 2. Проверяем WorldGuard регионы (кроме наших elytrix_* и __global__)
        try {
            if (isWorldGuardAvailable()) {
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
                if (rm != null) {
                    BlockVector3 cMin = BlockVector3.at(center.getBlockX() - radius, 0, center.getBlockZ() - radius);
                    BlockVector3 cMax = BlockVector3.at(center.getBlockX() + radius, 255, center.getBlockZ() + radius);
                    for (ProtectedRegion existing : rm.getRegions().values()) {
                        String existingId = existing.getId().toLowerCase();
                        if (existingId.startsWith(PREFIX) || existingId.equals("__global__")) continue;
                        try {
                            BlockVector3 eMin = existing.getMinimumPoint();
                            BlockVector3 eMax = existing.getMaximumPoint();
                            if (intersects(cMin, cMax, eMin, eMax)) {
                                return true;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean intersects(BlockVector3 aMin, BlockVector3 aMax, BlockVector3 bMin, BlockVector3 bMax) {
        return aMin.getX() <= bMax.getX() && aMax.getX() >= bMin.getX()
                && aMin.getY() <= bMax.getY() && aMax.getY() >= bMin.getY()
                && aMin.getZ() <= bMax.getZ() && aMax.getZ() >= bMin.getZ();
    }

    private boolean isWorldGuardAvailable() {
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            return plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private String generateId() {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        String id = sb.toString();
        if (regionsById.containsKey(id)) {
            return generateId();
        }
        // Also check WG
        try {
            if (isWorldGuardAvailable()) {
                for (World world : plugin.getServer().getWorlds()) {
                    RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                    com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
                    if (rm != null && rm.getRegion(id) != null) {
                        return generateId();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return id;
    }

    public void removeRegion(TraderRegion region) {
        if (region == null) return;
        regionsById.remove(region.id());
        regionsByUid.remove(region.traderUid());

        // Remove WorldGuard region
        try {
            if (isWorldGuardAvailable()) {
                World world = region.center().getWorld();
                if (world != null) {
                    RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                    com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
                    if (rm != null) {
                        rm.removeRegion(region.id());
                    }
                } else {
                    // Try all worlds
                    for (World w : plugin.getServer().getWorlds()) {
                        try {
                            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                            com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(w));
                            if (rm != null && rm.getRegion(region.id()) != null) {
                                rm.removeRegion(region.id());
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (plugin.config().debug()) {
        }
    }

    public void removeRegionByUid(int uid) {
        TraderRegion region = regionsByUid.remove(uid);
        if (region != null) {
            regionsById.remove(region.id());
            // Remove WG
            try {
                if (isWorldGuardAvailable()) {
                    World world = region.center().getWorld();
                    if (world != null) {
                        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                        com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
                        if (rm != null) rm.removeRegion(region.id());
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    public void removeRegion(World world, String id) {
        if (id == null) return;
        regionsById.remove(id);
        // Remove from byUid map
        regionsByUid.entrySet().removeIf(e -> e.getValue().id().equals(id));

        try {
            if (isWorldGuardAvailable() && world != null) {
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
                if (rm != null) rm.removeRegion(id);
            }
        } catch (Throwable ignored) {}
    }

    public TraderRegion getRegion(String id) {
        return regionsById.get(id);
    }

    public TraderRegion getRegionByUid(int uid) {
        return regionsByUid.get(uid);
    }

    public TraderRegion getRegionAt(Location loc) {
        if (loc == null) return null;
        // First check our own
        for (TraderRegion region : regionsById.values()) {
            if (region.contains(loc)) {
                return region;
            }
        }
        // Also check WorldGuard for elytrix_ regions
        try {
            if (isWorldGuardAvailable()) {
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(loc.getWorld()));
                if (rm != null) {
                    for (ProtectedRegion pr : rm.getApplicableRegions(BukkitAdapter.asBlockVector(loc)).getRegions()) {
                        if (pr.getId().startsWith(PREFIX)) {
                            // Return our tracking if exists, else create temporary
                            TraderRegion tr = regionsById.get(pr.getId());
                            if (tr != null) return tr;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public boolean isInRegion(Location loc, String id) {
        if (id == null || loc == null) return false;
        try {
            if (isWorldGuardAvailable()) {
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(loc.getWorld()));
                if (rm == null) return false;
                ProtectedRegion region = rm.getRegion(id);
                if (region == null) return false;
                return region.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            }
        } catch (Throwable ignored) {}
        TraderRegion tr = regionsById.get(id);
        return tr != null && tr.contains(loc);
    }

    public boolean isProtected(Location loc) {
        return getRegionAt(loc) != null;
    }

    public boolean isProtectedBlock(int x, int y, int z, World world) {
        for (TraderRegion region : regionsById.values()) {
            if (region.containsBlock(x, y, z, world)) {
                return true;
            }
        }
        return false;
    }

    public Collection<TraderRegion> allRegions() {
        return regionsById.values();
    }

    public void clearAll() {
        // Remove all WG regions
        try {
            if (isWorldGuardAvailable()) {
                for (World world : plugin.getServer().getWorlds()) {
                    try {
                        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                        com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
                        if (rm != null) {
                            for (String id : new ArrayList<>(regionsById.keySet())) {
                                if (rm.getRegion(id) != null) {
                                    rm.removeRegion(id);
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        regionsById.clear();
        regionsByUid.clear();
    }
}
