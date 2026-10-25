package ru.rooyzee.elytrixcemetery.util;

import org.bukkit.Material;

public final class MaterialUtil {
    private MaterialUtil() {}

    public static Material match(String... names) {
        for (String name : names) {
            if (name == null) continue;
            Material m = Material.matchMaterial(name);
            if (m != null) return m;
        }
        return Material.STONE;
    }

    public static Material getPathMaterial() {
        return match("DIRT_PATH", "GRASS_PATH", "COARSE_DIRT");
    }

    public static Material getGhostShardMaterial() {
        return match("AMETHYST_SHARD", "PRISMARINE_SHARD", "EMERALD");
    }

    public static Material getRootedDirtMaterial() {
        return match("ROOTED_DIRT", "COARSE_DIRT", "PODZOL", "DIRT");
    }

    public static Material getDeepslateBrickMaterial() {
        return match("DEEPSLATE_BRICKS", "POLISHED_BLACKSTONE_BRICKS", "CRACKED_STONE_BRICKS", "STONE_BRICKS");
    }

    public static Material getCrackedDeepslateBrickMaterial() {
        return match("CRACKED_DEEPSLATE_BRICKS", "CRACKED_POLISHED_BLACKSTONE_BRICKS", "CRACKED_STONE_BRICKS", "STONE_BRICKS");
    }

    public static Material getDeepslateSlabMaterial() {
        return match("POLISHED_DEEPSLATE_SLAB", "POLISHED_BLACKSTONE_SLAB", "STONE_BRICK_SLAB", "COBBLESTONE_SLAB");
    }
}
