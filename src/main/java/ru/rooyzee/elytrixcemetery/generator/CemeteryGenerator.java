package ru.rooyzee.elytrixcemetery.generator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.graves.Grave;
import ru.rooyzee.elytrixcemetery.util.MaterialUtil;

import java.util.*;

public class CemeteryGenerator {

    private final Main plugin;
    private final Random random = new Random();
    private final Set<Location> modifiedBlocks = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Location, Boolean>());

    public CemeteryGenerator(Main plugin) {
        this.plugin = plugin;
    }

    public Set<Location> getModifiedBlocks() {
        return modifiedBlocks;
    }

    public List<Grave> generateCemetery(Location center) {
        List<Grave> graves = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) return graves;

        modifiedBlocks.clear();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int radius = 45;

        Material wallMat = MaterialUtil.match("POLISHED_BLACKSTONE_WALL", "COBBLESTONE_WALL");
        Material brickMat = MaterialUtil.getDeepslateBrickMaterial();
        Material pathMat = MaterialUtil.getPathMaterial();

        // 1. Внешняя ограда по периметру (следует рельефу местности)
        for (int i = -radius; i <= radius; i++) {
            // Север / Юг
            buildPerimeterSegment(world, cx + i, cz - radius, wallMat, brickMat, (i % 6 == 0));
            buildPerimeterSegment(world, cx + i, cz + radius, wallMat, brickMat, (i % 6 == 0));
            // Запад / Восток
            buildPerimeterSegment(world, cx - radius, cz + i, wallMat, brickMat, (i % 6 == 0));
            buildPerimeterSegment(world, cx + radius, cz + i, wallMat, brickMat, (i % 6 == 0));
        }

        // 2. Входные ворота (Юг: X = cx, Z = cz - radius)
        int gateZ = cz - radius;
        for (int dx = -2; dx <= 2; dx++) {
            int y = findSurfaceY(world, cx + dx, gateZ);
            setBlock(world, cx + dx, y + 1, gateZ, Material.AIR);
            setBlock(world, cx + dx, y + 2, gateZ, Material.AIR);
            setBlock(world, cx + dx, y, gateZ, pathMat);
        }

        // 3. 20 Пространственно разнесенных могил (дистанция 15-20 блоков между каждой)
        int[][] graveOffsets = {
                {-32, -32}, {-16, -34}, {0, -32}, {18, -34}, {34, -30},
                {-36, -16}, {-18, -16}, {-4, -14}, {16, -16}, {34, -14},
                {-34, 12},  {-16, 14},  {2, 12},   {18, 14},  {35, 12},
                {-32, 32},  {-14, 34},  {0, 32},   {18, 34},  {32, 30}
        };

        int graveId = 1;
        for (int[] offset : graveOffsets) {
            int gx = cx + offset[0];
            int gz = cz + offset[1];
            int gy = findSurfaceY(world, gx, gz);

            Grave grave = buildGrave(world, gx, gy, gz, graveId++);
            graves.add(grave);

            // Прокладываем атмосферную тропинку к могиле
            buildPathTo(world, cx, cz, gx, gz, pathMat);
        }

        // 4. Жуткие деревья и фонари душ
        int[][] treeOffsets = {
                {-26, -24}, {26, -24}, {-26, 24}, {26, 24},
                {-10, -2}, {10, -2}, {0, 20}
        };
        for (int[] to : treeOffsets) {
            int tx = cx + to[0];
            int tz = cz + to[1];
            int ty = findSurfaceY(world, tx, tz);
            buildSpookyTree(world, tx, ty, tz);
        }

        // 5. Растительность кладбища (розы иссушителя, сухие кусты)
        for (int dx = -radius + 3; dx <= radius - 3; dx += 3) {
            for (int dz = -radius + 3; dz <= radius - 3; dz += 3) {
                int wx = cx + dx + random.nextInt(3) - 1;
                int wz = cz + dz + random.nextInt(3) - 1;
                int wy = findSurfaceY(world, wx, wz);
                Block floor = world.getBlockAt(wx, wy, wz);
                Block above = world.getBlockAt(wx, wy + 1, wz);
                if (floor.getType().isSolid() && above.getType() == Material.AIR) {
                    double r = random.nextDouble();
                    if (r < 0.08) setBlock(world, wx, wy + 1, wz, Material.WITHER_ROSE);
                    else if (r < 0.18) setBlock(world, wx, wy + 1, wz, Material.DEAD_BUSH);
                }
            }
        }

        return graves;
    }

    private void buildPerimeterSegment(World world, int x, int z, Material wallMat, Material brickMat, boolean isPillar) {
        int y = findSurfaceY(world, x, z);
        if (isPillar) {
            setBlock(world, x, y + 1, z, brickMat);
            setBlock(world, x, y + 2, z, brickMat);
            setBlock(world, x, y + 3, z, wallMat);
            setBlock(world, x, y + 4, z, Material.SOUL_LANTERN);
        } else {
            setBlock(world, x, y + 1, z, brickMat);
            setBlock(world, x, y + 2, z, Material.IRON_BARS);
        }
    }

    private Grave buildGrave(World world, int x, int y, int z, int id) {
        Material wall = MaterialUtil.match("POLISHED_BLACKSTONE_WALL", "COBBLESTONE_WALL");
        Material rootedDirt = MaterialUtil.getRootedDirtMaterial();

        // 1. Полноценный готический крест (высота 3 блока + горизонтальная перекладина)
        // Ножка креста
        setBlock(world, x, y + 1, z - 1, wall);
        // Центр перекрестия
        setBlock(world, x, y + 2, z - 1, wall);
        // Левое и правое плечо креста
        setBlock(world, x - 1, y + 2, z - 1, wall);
        setBlock(world, x + 1, y + 2, z - 1, wall);
        // Верхушка креста
        setBlock(world, x, y + 3, z - 1, wall);

        // 2. Насыпь могилы (земля/подзол)
        Block surfaceBlock = world.getBlockAt(x, y, z);
        Block surfaceBlock2 = world.getBlockAt(x, y, z + 1);
        setBlock(world, x, y, z, Material.COARSE_DIRT);
        setBlock(world, x, y, z + 1, rootedDirt);

        // 3. Подземный сундук под могилой (y - 1)
        Block chestBlock = world.getBlockAt(x, y - 1, z);
        setBlock(world, x, y - 1, z, Material.CHEST);

        // Дно могилы
        setBlock(world, x, y - 1, z + 1, Material.DIRT);

        Location digLoc = surfaceBlock.getLocation();
        Location chestLoc = chestBlock.getLocation();
        // Голограмма над могилой (ровно над сундуком на высоте +2.6 блока)
        Location holoLoc = new Location(world, x + 0.5, y + 2.6, z + 0.5);

        return new Grave(id, digLoc, chestLoc, holoLoc);
    }

    private void buildPathTo(World world, int startX, int startZ, int endX, int endZ, Material pathMat) {
        int steps = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i += 2) {
            double progress = (double) i / steps;
            int x = (int) Math.round(startX + (endX - startX) * progress);
            int z = (int) Math.round(startZ + (endZ - startZ) * progress);
            int y = findSurfaceY(world, x, z);
            Block b = world.getBlockAt(x, y, z);
            if (b.getType() != Material.CHEST && b.getType() != MaterialUtil.match("POLISHED_BLACKSTONE_WALL", "COBBLESTONE_WALL")) {
                setBlock(world, x, y, z, random.nextDouble() < 0.6 ? pathMat : Material.COARSE_DIRT);
            }
        }
    }

    private void buildSpookyTree(World world, int x, int y, int z) {
        for (int dy = 1; dy <= 4; dy++) {
            setBlock(world, x, y + dy, z, Material.DARK_OAK_LOG);
        }
        setBlock(world, x + 1, y + 4, z, Material.DARK_OAK_LOG);
        setBlock(world, x - 1, y + 5, z, Material.DARK_OAK_LOG);

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 4; dy <= 6; dy++) {
                    if (random.nextDouble() < 0.6) {
                        Block b = world.getBlockAt(x + dx, y + dy, z + dz);
                        if (b.getType() == Material.AIR) {
                            setBlock(world, x + dx, y + dy, z + dz, Material.OAK_LEAVES);
                        }
                    }
                }
            }
        }
    }

    public int findSurfaceY(World world, int x, int z) {
        int maxY = Math.min(255, world.getMaxHeight() - 1);
        for (int y = maxY; y >= 5; y--) {
            Block b = world.getBlockAt(x, y, z);
            Material type = b.getType();
            if (isSolidGround(type)) {
                return y;
            }
        }
        return world.getHighestBlockYAt(x, z);
    }

    private boolean isSolidGround(Material type) {
        if (type == Material.AIR || type.isAir()) return false;
        String name = type.name();
        if (name.contains("LEAVES") || name.contains("LOG") || name.contains("WOOD") ||
                name.contains("FENCE") || name.contains("WALL") || name.contains("FLOWER") ||
                (name.contains("GRASS") && !name.contains("BLOCK")) || name.contains("PLANT") ||
                name.contains("SAPLING") || name.contains("MUSHROOM") || name.contains("VINE") ||
                name.contains("LANTERN") || name.contains("TORCH") || name.contains("BARS")) {
            return false;
        }
        return type.isSolid();
    }

    private void setBlock(World world, int x, int y, int z, Material mat) {
        Block b = world.getBlockAt(x, y, z);
        modifiedBlocks.add(b.getLocation());
        b.setType(mat, false);
    }

    public void clearCemeteryBlocks() {
        for (Location loc : modifiedBlocks) {
            if (loc.getWorld() != null) {
                Block b = loc.getBlock();
                if (b.getType() != Material.AIR && (b.getType().name().contains("WALL") || b.getType().name().contains("LANTERN") || b.getType() == Material.CHEST || b.getType() == Material.IRON_BARS || b.getType().name().contains("LOG") || b.getType().name().contains("LEAVES"))) {
                    b.setType(Material.AIR, false);
                }
            }
        }
        modifiedBlocks.clear();
    }
}
