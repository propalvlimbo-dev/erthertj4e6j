package ru.rooyzee.elytrixparadise.sphere;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public class SphereBlockInfo {
    private final Location originalLocation;
    private final int relX;
    private final int relY;
    private final int relZ;
    private final Material material;
    private final BlockData blockData;

    public SphereBlockInfo(Location originalLocation, int relX, int relY, int relZ, Material material, BlockData blockData) {
        this.originalLocation = originalLocation;
        this.relX = relX;
        this.relY = relY;
        this.relZ = relZ;
        this.material = material;
        this.blockData = blockData;
    }

    public Location getOriginalLocation() {
        return originalLocation;
    }

    public int getRelX() {
        return relX;
    }

    public int getRelY() {
        return relY;
    }

    public int getRelZ() {
        return relZ;
    }

    public Material getMaterial() {
        return material;
    }

    public BlockData getBlockData() {
        return blockData;
    }
}
