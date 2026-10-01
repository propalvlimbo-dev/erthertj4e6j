package ru.rooyzee.elytrixschalkerpvp.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class SchalkerData {

    private final String id;
    private Location location;
    private String worldName;
    private Rarity currentRarity;
    private SchalkerState state;
    private long stateChangeTime;
    private long sleepEndTime = 0;
    private int taskId = -1;
    private int hologramTaskId = -1;

    // Хранилище реального лута (Слот -> Предмет)
    private final Map<Integer, ItemStack> hiddenLoot = new HashMap<>();

    public SchalkerData(String id, Location location) {
        this.id = id;
        this.location = location;
        this.worldName = location != null && location.getWorld() != null ? location.getWorld().getName() : null;
        // Попытка вытащить имя мира из id если location без мира: формат x_y_z_world
        if (this.worldName == null && id != null) {
            String[] parts = id.split("_");
            if (parts.length >= 4) {
                // мир может содержать подчеркивания, поэтому собираем остаток
                StringBuilder sb = new StringBuilder();
                for (int i = 3; i < parts.length; i++) {
                    if (i > 3) sb.append("_");
                    sb.append(parts[i]);
                }
                this.worldName = sb.toString();
            }
        }
        this.currentRarity = null;
        this.state = SchalkerState.SLEEPING;
        this.stateChangeTime = System.currentTimeMillis();
    }

    public SchalkerData(String id, Location location, String worldName) {
        this.id = id;
        this.location = location;
        this.worldName = worldName;
        if (this.worldName == null && location != null && location.getWorld() != null) {
            this.worldName = location.getWorld().getName();
        }
        if (this.worldName == null && id != null) {
            String[] parts = id.split("_");
            if (parts.length >= 4) {
                StringBuilder sb = new StringBuilder();
                for (int i = 3; i < parts.length; i++) {
                    if (i > 3) sb.append("_");
                    sb.append(parts[i]);
                }
                this.worldName = sb.toString();
            }
        }
        this.currentRarity = null;
        this.state = SchalkerState.SLEEPING;
        this.stateChangeTime = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }
    public Rarity getCurrentRarity() { return currentRarity; }
    public void setCurrentRarity(Rarity currentRarity) { this.currentRarity = currentRarity; }
    public SchalkerState getState() { return state; }

    public void setState(SchalkerState state) {
        this.state = state;
        this.stateChangeTime = System.currentTimeMillis();
    }

    public long getStateChangeTime() { return stateChangeTime; }
    public void setStateChangeTime(long stateChangeTime) { this.stateChangeTime = stateChangeTime; }
    public long getSleepEndTime() { return sleepEndTime; }
    public void setSleepEndTime(long sleepEndTime) { this.sleepEndTime = sleepEndTime; }
    public int getTaskId() { return taskId; }
    public void setTaskId(int taskId) { this.taskId = taskId; }
    public int getHologramTaskId() { return hologramTaskId; }
    public void setHologramTaskId(int hologramTaskId) { this.hologramTaskId = hologramTaskId; }
    public Map<Integer, ItemStack> getHiddenLoot() { return hiddenLoot; }

    /**
     * Возвращает актуальный мир, пытаясь резолвить по worldName если location без мира
     */
    public World getResolvedWorld() {
        if (location != null && location.getWorld() != null) {
            return location.getWorld();
        }
        if (worldName != null) {
            return Bukkit.getWorld(worldName);
        }
        return null;
    }

    /**
     * Обновляет location если мир был загружен позже
     */
    public boolean tryResolveWorld() {
        if (location != null && location.getWorld() != null) return true;
        if (worldName == null) return false;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return false;
        if (location == null) return false;
        // location может быть с null миром, пересоздаем с миром
        this.location = new Location(world, location.getX(), location.getY(), location.getZ());
        return true;
    }
}