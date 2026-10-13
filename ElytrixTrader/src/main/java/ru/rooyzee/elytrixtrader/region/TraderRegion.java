package ru.rooyzee.elytrixtrader.region;

import org.bukkit.Location;
import org.bukkit.World;

public class TraderRegion {

    private final String id;
    private final Location center;
    private final int radius;
    private final String traderId;
    private final int traderUid;
    private final long createdAt;

    public TraderRegion(String id, Location center, int radius, String traderId, int traderUid) {
        this.id = id;
        this.center = center.clone();
        this.radius = radius;
        this.traderId = traderId;
        this.traderUid = traderUid;
        this.createdAt = System.currentTimeMillis();
    }

    public String id() {
        return id;
    }

    public Location center() {
        return center;
    }

    public int radius() {
        return radius;
    }

    public String traderId() {
        return traderId;
    }

    public int traderUid() {
        return traderUid;
    }

    public long createdAt() {
        return createdAt;
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (center.getWorld() == null) return false;
        if (!loc.getWorld().equals(center.getWorld())) return false;
        // 3D distance check, but we want horizontal radius 30 and vertical maybe 30 as well
        double dx = loc.getX() - center.getX();
        double dy = loc.getY() - center.getY();
        double dz = loc.getZ() - center.getZ();
        // Use horizontal distance for main check, but also limit vertical
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz > radius) return false;
        // Allow 40 blocks vertical to cover schematic height
        return Math.abs(dy) <= radius + 20;
    }

    public boolean containsBlock(int x, int y, int z, World world) {
        if (world == null || center.getWorld() == null) return false;
        if (!world.equals(center.getWorld())) return false;
        double dx = x + 0.5 - center.getX();
        double dz = z + 0.5 - center.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz > radius) return false;
        return Math.abs(y - center.getY()) <= radius + 20;
    }
}
