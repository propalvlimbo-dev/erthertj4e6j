package ru.rooyzee.elytrixparadise.shards;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ParadiseShard {

    public enum ShardState {
        ACTIVE,
        COOLDOWN
    }

    private final Location location;
    private final String id;
    private ShardState state = ShardState.ACTIVE;
    private int cooldownRemaining = 0; // seconds
    private int hitCount = 0;
    private double currentExplosionChance;
    private long shieldUntil = 0L;
    private final Map<UUID, Long> playerLastHitTime = new ConcurrentHashMap<>();

    public ParadiseShard(Location location, double baseExplosionChance) {
        this.location = location;
        this.id = "ep_shard_" + location.getWorld().getName() + "_"
                + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();
        this.currentExplosionChance = baseExplosionChance;
    }

    public Location getLocation() {
        return location;
    }

    public String getId() {
        return id;
    }

    public ShardState getState() {
        return state;
    }

    public void setState(ShardState state) {
        this.state = state;
    }

    public int getCooldownRemaining() {
        return cooldownRemaining;
    }

    public void setCooldownRemaining(int cooldownRemaining) {
        this.cooldownRemaining = cooldownRemaining;
    }

    public void decrementCooldown() {
        if (this.cooldownRemaining > 0) {
            this.cooldownRemaining--;
        }
    }

    public int getHitCount() {
        return hitCount;
    }

    public void incrementHitCount() {
        this.hitCount++;
    }

    public double getCurrentExplosionChance() {
        return currentExplosionChance;
    }

    public void increaseExplosionChance(double step, double max) {
        this.currentExplosionChance = Math.min(max, this.currentExplosionChance + step);
    }

    public void resetExplosionChance(double base) {
        this.currentExplosionChance = base;
        this.hitCount = 0;
        this.shieldUntil = 0L;
    }

    public long getShieldUntil() {
        return shieldUntil;
    }

    public void setShieldUntil(long shieldUntil) {
        this.shieldUntil = shieldUntil;
    }

    public boolean isShieldActive() {
        return System.currentTimeMillis() < shieldUntil;
    }

    public boolean canPlayerHit(UUID uuid, long delayMs) {
        long now = System.currentTimeMillis();
        Long last = playerLastHitTime.get(uuid);
        if (last == null || (now - last) >= delayMs) {
            playerLastHitTime.put(uuid, now);
            return true;
        }
        return false;
    }
}
