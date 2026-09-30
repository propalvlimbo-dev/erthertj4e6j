package ru.rooyzee.elytrixparadise.sphere;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public class SphereBlockInfo {
    private final int relX;
    private final int relY;
    private final int relZ;
    private final Material material;
    private final BlockData blockData;

    public SphereBlockInfo(int relX, int relY, int relZ, Material material, BlockData blockData) {
        this.relX = relX;
        this.relY = relY;
        this.relZ = relZ;
        this.material = material;
        this.blockData = blockData;
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
