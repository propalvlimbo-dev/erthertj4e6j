package ru.rooyzee.elytrixairdrop.airdrops;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.managers.SchematicManager;

import java.util.Map;

public abstract class ActiveAirdrop {
    private final Airdrop type;
    private final Location center;
    private final String regionId;
    private final String hologramName;
    private final Map<Location, SchematicManager.BlockData> backup;
    private final long endTime;

    public ActiveAirdrop(Airdrop type, Location center, String regionId, String hologramName,
                         Map<Location, SchematicManager.BlockData> backup, long endTime) {
        this.type = type;
        this.center = center;
        this.regionId = regionId;
        this.hologramName = hologramName;
        this.backup = backup;
        this.endTime = endTime;
    }

    public Airdrop getType() { return type; }
    public Location getCenter() { return center; }
    public String getRegionId() { return regionId; }
    public String getHologramName() { return hologramName; }
    public Map<Location, SchematicManager.BlockData> getBackup() { return backup; }
    public long getEndTime() { return endTime; }

    public long getRemainingSeconds() {
        return Math.max(0, (endTime - System.currentTimeMillis()) / 1000);
    }

    public boolean isProtectedBlock(Location loc) {
        if (loc == null) return false;
        return backup.containsKey(blockKey(loc));
    }

    protected Location blockKey(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    protected boolean sameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || b.getWorld() == null) return false;
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    public abstract void onStart(Main plugin, ConfigurationSection cfg);
    public abstract void onTick(Main plugin, ConfigurationSection cfg);
    public abstract void onStop(Main plugin, ConfigurationSection cfg);
    public abstract boolean handleChestClick(Main plugin, Player player, Location blockLoc);
    public abstract boolean handleLootClick(Main plugin, Player player, InventoryClickEvent event);
}