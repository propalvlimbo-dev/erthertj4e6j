package ru.rooyzee.elytrixparadise.sphere;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChainNode {

    private final int id;
    private final String name;
    private final List<Location> chainBlocks = new ArrayList<>();
    private final Map<Location, BlockData> originalData = new HashMap<>();
    private final int maxHp;
    private int currentHp;
    private boolean broken = false;

    public ChainNode(int id, String name, int maxHp) {
        this.id = id;
        this.name = name;
        this.maxHp = maxHp;
        this.currentHp = maxHp;
    }

    public void addBlock(Location loc, BlockData data) {
        if (!chainBlocks.contains(loc)) {
            chainBlocks.add(loc);
            originalData.put(loc, data.clone());
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Location> getChainBlocks() {
        return chainBlocks;
    }

    public Map<Location, BlockData> getOriginalData() {
        return originalData;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public boolean isBroken() {
        return broken;
    }

    public void setBroken(boolean broken) {
        this.broken = broken;
    }

    public void damage(int amount) {
        this.currentHp = Math.max(0, this.currentHp - amount);
        if (this.currentHp <= 0) {
            this.broken = true;
        }
    }

    public void reset() {
        this.currentHp = this.maxHp;
        this.broken = false;
    }

    public Location getHologramLocation() {
        if (chainBlocks.isEmpty()) return null;
        int mid = chainBlocks.size() / 2;
        return chainBlocks.get(mid).clone().add(0.5, 1.2, 0.5);
    }
}
