package ru.rooyzee.elytrixairdrop.services;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import ru.rooyzee.elytrixairdrop.Main;

import java.util.*;

public class LocationFinder {

    private static final int MIN_DISTANCE = 200;
    private static final int Y_OFFSET = 2;
    private static final int REGION_PADDING = 20;
    private static final int MAX_RECENT = 20;
    private static final int TERRAIN_CHECK_RADIUS = 10;
    private static final int TERRAIN_STEP = 4;

    private final Main plugin;
    private final Random random = new Random();
    private final Deque<long[]> recent = new ArrayDeque<>();
    private final Map<Long, Integer> heightCache = new HashMap<>();

    // Reused per-search WG snapshot to avoid iterating all regions every attempt
    private List<int[]> regionBoxes; // {minX, maxX, minZ, maxZ}

    public LocationFinder(Main plugin) {
        this.plugin = plugin;
    }

    public Location find(World world, int airdropRadius) {
        return findWithLevel(world, airdropRadius, 0);
    }

    public Location findWithLevel(World world, int airdropRadius, int level) {
        heightCache.clear();
        regionBoxes = buildRegionBoxes(world);
        switch (level) {
            case 0: return tryFind(world, airdropRadius, 5, 3, false, false, 80);
            case 1: return tryFind(world, airdropRadius, 8, 5, true, false, 120);
            case 2: return tryFind(world, airdropRadius, 12, 8, true, true, 160);
            case 3: return tryEmergency(world, airdropRadius);
            default: return null;
        }
    }

    public Location findSky(World world, int airdropRadius, int minAbove, int maxAbove) {
        heightCache.clear();
        regionBoxes = buildRegionBoxes(world);
        ConfigurationSection s = plugin.getConfigManager().getConfig().getConfigurationSection("spawn");
        int minX = s.getInt("min-x"), maxX = s.getInt("max-x");
        int minZ = s.getInt("min-z"), maxZ = s.getInt("max-z");

        for (int i = 0; i < 200; i++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);
            if (isRecent(x, z)) continue;

            if (!world.isChunkLoaded(x >> 4, z >> 4)) world.getChunkAt(x >> 4, z >> 4);

            int groundY = getHeight(world, x, z);
            if (groundY < 1) continue;

            int extra = minAbove + random.nextInt(maxAbove - minAbove + 1);
            int y = groundY + extra;
            if (y > world.getMaxHeight() - 20) y = world.getMaxHeight() - 20;
            if (y - groundY < minAbove) continue;

            Location loc = new Location(world, x, y, z);
            if (!isFarFromRegionsFast(loc, airdropRadius)) continue;
            if (!isAirAround(world, x, y, z, 8)) continue;

            remember(loc);
            return loc;
        }
        return null;
    }

    private boolean isAirAround(World world, int cx, int cy, int cz, int radius) {
        int checks = 0, air = 0;
        for (int dx = -radius; dx <= radius; dx += 4) {
            for (int dz = -radius; dz <= radius; dz += 4) {
                for (int dy = -3; dy <= 3; dy += 3) {
                    Material m = world.getBlockAt(cx + dx, cy + dy, cz + dz).getType();
                    checks++;
                    if (m == Material.AIR || m == Material.CAVE_AIR) air++;
                }
            }
        }
        return checks > 0 && (double) air / checks >= 0.85;
    }

    private Location tryFind(World world, int airdropRadius, int maxDiff, int strictDiff,
                             boolean loadChunks, boolean ignoreRecent, int attempts) {
        ConfigurationSection s = plugin.getConfigManager().getConfig().getConfigurationSection("spawn");
        int minX = s.getInt("min-x"), maxX = s.getInt("max-x");
        int minZ = s.getInt("min-z"), maxZ = s.getInt("max-z");

        Location best = null;

        for (int i = 0; i < attempts; i++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);
            if (!ignoreRecent && isRecent(x, z)) continue;

            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                if (loadChunks) world.getChunkAt(x >> 4, z >> 4);
                else continue;
            }

            int y = getHeight(world, x, z);
            if (y < 1 || y > world.getMaxHeight() - 20) continue;

            Material under = world.getBlockAt(x, y, z).getType();
            if (under == Material.WATER || under == Material.LAVA) continue;

            // Cheap region check before expensive terrain scan
            Location locProbe = new Location(world, x, y + 1 + Y_OFFSET, z);
            if (!isFarFromRegionsFast(locProbe, airdropRadius)) continue;

            int heightDiff = getMaxHeightDiff(world, x, z, TERRAIN_CHECK_RADIUS, maxDiff);
            if (heightDiff > maxDiff) continue;

            if (hasLiquidAround(world, x, z, 4)) continue;

            Location loc = locProbe;
            if (heightDiff <= strictDiff && isFlat(loc, 3)) {
                remember(loc);
                return loc;
            }
            if (best == null) best = loc;
        }

        if (best != null) { remember(best); return best; }
        return null;
    }

    private Location tryEmergency(World world, int airdropRadius) {
        ConfigurationSection s = plugin.getConfigManager().getConfig().getConfigurationSection("spawn");
        int minX = s.getInt("min-x"), maxX = s.getInt("max-x");
        int minZ = s.getInt("min-z"), maxZ = s.getInt("max-z");

        for (int i = 0; i < 300; i++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);

            if (!world.isChunkLoaded(x >> 4, z >> 4)) world.getChunkAt(x >> 4, z >> 4);

            int y = getHeight(world, x, z);
            if (y < 1 || y > world.getMaxHeight() - 20) continue;

            Material under = world.getBlockAt(x, y, z).getType();
            if (under == Material.LAVA) continue;

            Location loc = new Location(world, x, y + 1 + Y_OFFSET, z);
            if (!isFarFromRegionsFast(loc, airdropRadius)) continue;

            remember(loc);
            return loc;
        }
        return null;
    }

    private int getHeight(World world, int x, int z) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        Integer cached = heightCache.get(key);
        if (cached != null) return cached;
        int y = world.getHighestBlockYAt(x, z);
        heightCache.put(key, y);
        return y;
    }

    private int getMaxHeightDiff(World world, int cx, int cz, int radius, int limit) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx += TERRAIN_STEP) {
            for (int dz = -radius; dz <= radius; dz += TERRAIN_STEP) {
                int y = getHeight(world, cx + dx, cz + dz);
                if (y < min) min = y;
                if (y > max) max = y;
                if (max - min > limit) return max - min;
            }
        }
        return max - min;
    }

    private boolean hasLiquidAround(World world, int cx, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx += 3) {
            for (int dz = -radius; dz <= radius; dz += 3) {
                int y = getHeight(world, cx + dx, cz + dz);
                Material m = world.getBlockAt(cx + dx, y, cz + dz).getType();
                if (m == Material.WATER || m == Material.LAVA) return true;
            }
        }
        return false;
    }

    /**
     * Snapshot WG region bounding boxes once per search (ignores elytrix_ / global).
     */
    private List<int[]> buildRegionBoxes(World world) {
        List<int[]> boxes = new ArrayList<>();
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
            if (rm == null) return boxes;
            for (ProtectedRegion r : rm.getRegions().values()) {
                String rid = r.getId();
                if (rid.startsWith("elytrix_")) continue;
                if (rid.equals("__global__") || rid.equalsIgnoreCase("global")) continue;
                BlockVector3 mn = r.getMinimumPoint();
                BlockVector3 mx = r.getMaximumPoint();
                boxes.add(new int[]{
                        mn.getBlockX(), mx.getBlockX(),
                        mn.getBlockZ(), mx.getBlockZ()
                });
            }
        } catch (Exception ignored) {}
        return boxes;
    }

    private boolean isFarFromRegionsFast(Location loc, int airdropRadius) {
        if (regionBoxes == null || regionBoxes.isEmpty()) return true;
        int total = airdropRadius + REGION_PADDING;
        int minX = loc.getBlockX() - total;
        int maxX = loc.getBlockX() + total;
        int minZ = loc.getBlockZ() - total;
        int maxZ = loc.getBlockZ() + total;

        for (int[] b : regionBoxes) {
            // AABB overlap on XZ
            if (minX <= b[1] && maxX >= b[0] && minZ <= b[3] && maxZ >= b[2]) {
                return false;
            }
        }
        return true;
    }

    private void remember(Location loc) {
        recent.addLast(new long[]{loc.getBlockX(), loc.getBlockZ()});
        while (recent.size() > MAX_RECENT) recent.pollFirst();
    }

    private boolean isRecent(int x, int z) {
        for (long[] c : recent) {
            if (Math.abs(c[0] - x) < MIN_DISTANCE && Math.abs(c[1] - z) < MIN_DISTANCE) return true;
        }
        return false;
    }

    private boolean isFlat(Location center, int radius) {
        World w = center.getWorld();
        int baseY = center.getBlockY() - Y_OFFSET - 1;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                int y = getHeight(w, center.getBlockX() + dx, center.getBlockZ() + dz);
                if (Math.abs(y - baseY) > 2) return false;
                Material m = w.getBlockAt(center.getBlockX() + dx, y, center.getBlockZ() + dz).getType();
                if (m == Material.WATER || m == Material.LAVA) return false;
            }
        }
        return true;
    }
}
