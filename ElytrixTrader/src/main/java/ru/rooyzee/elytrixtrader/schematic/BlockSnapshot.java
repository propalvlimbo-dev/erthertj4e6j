package ru.rooyzee.elytrixtrader.schematic;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

public class BlockSnapshot {

    private final int x;
    private final int y;
    private final int z;
    private final Material material;
    private final String blockDataString;

    public BlockSnapshot(int x, int y, int z, Material material, String blockDataString) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.material = material;
        this.blockDataString = blockDataString;
    }

    /**
     * Компактный ключ позиции (x,y,z) → long — для карт «позиция → снимок»
     * без аллокации Location/Boxed-объектов.
     */
    public static long key(int x, int y, int z) {
        return (((long) x & 0x3FFFFFL) << 37) | (((long) y & 0x7FFFL) << 22) | ((long) z & 0x3FFFFFL);
    }

    public static BlockSnapshot fromBlock(Block block) {
        if (block == null) return null;
        try {
            Material mat = block.getType();
            String data = "";
            // Воздух сериализуем в "" — его blockData всегда minecraft:air,
            // а getAsString() в горячем цикле снимка стоил заметно дорого.
            if (mat != null && mat != Material.AIR && mat != Material.CAVE_AIR && mat != Material.VOID_AIR) {
                try {
                    BlockData bd = block.getBlockData();
                    if (bd != null) data = bd.getAsString();
                } catch (Throwable ignored) {}
            }
            return new BlockSnapshot(block.getX(), block.getY(), block.getZ(), mat, data);
        } catch (Throwable t) {
            return null;
        }
    }

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public Material material() { return material; }
    public String blockDataString() { return blockDataString; }

    public void restore(World world) {
        if (world == null) return;
        try {
            Block block = world.getBlockAt(x, y, z);
            if (material == null) return;
            // Skip air-empty restoration — no-op fast path
            if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR || block.getType() == Material.VOID_AIR) {
                    return;
                }
                block.setType(material, false);
                return;
            }
            // Block has TileEntity (hive, bell, sign, ...): restoring on Paper 1.16.5 tries to
            // re-register POI which wasn't tracked → "POI data mismatch: never registered".
            // Solution: place block + blockData WITHOUT trying to preserve TE. The block's
            // functionality returns; TileEntity runtime state is reset (no bees etc.) which is
            // acceptable for a 2h spawn. Mismatch log spam is suppressed here because the
            // actually logging paper does is on chunk ticker; we just suppress our block ops.
            Material currentType = block.getType();
            if (currentType == material) return; // nothing to do
            try {
                block.setType(material, false);
            } catch (Throwable ignored) {}
            if (blockDataString != null && !blockDataString.isEmpty()) {
                try {
                    BlockData data = org.bukkit.Bukkit.createBlockData(blockDataString);
                    block.setBlockData(data, false);
                } catch (Throwable ignored) {
                    try {
                        BlockData data = org.bukkit.Bukkit.getServer().createBlockData(blockDataString);
                        block.setBlockData(data, false);
                    } catch (Throwable ignored2) {}
                }
            }
        } catch (Throwable ignored) {}
    }
}
