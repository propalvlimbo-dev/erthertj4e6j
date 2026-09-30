package ru.rooyzee.elytrixparadise.sphere;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

public class ChainNode {

    private final int id;
    private final String name;
    private final Location anchorTop;
    private final Location anchorBottom;
    private final List<Location> chainBlocks = new ArrayList<>();
    private final int maxHp;
    private int currentHp;
    private boolean broken = false;

    public ChainNode(int id, String name, Location anchorTop, Location anchorBottom, int maxHp) {
        this.id = id;
        this.name = name;
        this.anchorTop = anchorTop;
        this.anchorBottom = anchorBottom;
        this.maxHp = maxHp;
        this.currentHp = maxHp;
        calculateChainBlocks();
    }

    private void calculateChainBlocks() {
        chainBlocks.clear();
        if (anchorTop == null || anchorBottom == null) return;
        World world = anchorTop.getWorld();
        if (world == null) return;

        int minX = Math.min(anchorTop.getBlockX(), anchorBottom.getBlockX());
        int maxX = Math.max(anchorTop.getBlockX(), anchorBottom.getBlockX());
        int minY = Math.min(anchorTop.getBlockY(), anchorBottom.getBlockY());
        int maxY = Math.max(anchorTop.getBlockY(), anchorBottom.getBlockY());
        int minZ = Math.min(anchorTop.getBlockZ(), anchorBottom.getBlockZ());
        int maxZ = Math.max(anchorTop.getBlockZ(), anchorBottom.getBlockZ());

        int steps = Math.max(Math.abs(maxY - minY), Math.max(Math.abs(maxX - minX), Math.abs(maxZ - minZ)));
        if (steps <= 0) steps = 1;

        for (int i = 0; i <= steps; i++) {
            double fraction = (double) i / steps;
            double x = anchorBottom.getX() + (anchorTop.getX() - anchorBottom.getX()) * fraction;
            double y = anchorBottom.getY() + (anchorTop.getY() - anchorBottom.getY()) * fraction;
            double z = anchorBottom.getZ() + (anchorTop.getZ() - anchorBottom.getZ()) * fraction;
            Location loc = new Location(world, Math.floor(x), Math.floor(y), Math.floor(z));
            if (!chainBlocks.contains(loc)) {
                chainBlocks.add(loc);
            }
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Location getAnchorTop() {
        return anchorTop;
    }

    public Location getAnchorBottom() {
        return anchorBottom;
    }

    public List<Location> getChainBlocks() {
        return chainBlocks;
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
        if (chainBlocks.isEmpty()) return anchorBottom.clone().add(0.5, 1.5, 0.5);
        int mid = chainBlocks.size() / 2;
        return chainBlocks.get(mid).clone().add(0.5, 1.2, 0.5);
    }
}
