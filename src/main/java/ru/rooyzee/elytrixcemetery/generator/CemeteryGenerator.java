package ru.rooyzee.elytrixcemetery.generator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.graves.Grave;
import ru.rooyzee.elytrixcemetery.util.MaterialUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CemeteryGenerator {

    private final Main plugin;
    private final Random random = new Random();

    public CemeteryGenerator(Main plugin) {
        this.plugin = plugin;
    }

    public List<Grave> generateCemetery(Location center) {
        List<Grave> graves = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) return graves;

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int radius = 20;

        Material pathMat = MaterialUtil.getPathMaterial();

        // 1. Выравнивание площадки и установка грунта (41x41)
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int wx = cx + dx;
                int wz = cz + dz;

                // Базовый грунт
                Block base = world.getBlockAt(wx, cy, wz);
                base.setType(random.nextDouble() < 0.6 ? Material.GRASS_BLOCK : (random.nextBoolean() ? Material.PODZOL : Material.COARSE_DIRT), false);

                // Заполняем под землей камнем/землей
                for (int dy = -1; dy >= -3; dy--) {
                    world.getBlockAt(wx, cy + dy, wz).setType(Material.DIRT, false);
                }

                // Очищаем воздух над землей
                for (int dy = 1; dy <= 8; dy++) {
                    world.getBlockAt(wx, cy + dy, wz).setType(Material.AIR, false);
                }
            }
        }

        // 2. Внешняя каменная стена и решетки по периметру
        for (int i = -radius; i <= radius; i++) {
            // Северная и Южная стены
            buildWallPillar(world, cx + i, cy, cz - radius, (i % 4 == 0));
            buildWallPillar(world, cx + i, cy, cz + radius, (i % 4 == 0));
            // Западная и Восточная стены
            buildWallPillar(world, cx - radius, cy, cz + i, (i % 4 == 0));
            buildWallPillar(world, cx + radius, cy, cz + i, (i % 4 == 0));
        }

        // 3. Главные входные ворота с аркой (Юг / Z = -radius)
        for (int dx = -2; dx <= 2; dx++) {
            world.getBlockAt(cx + dx, cy + 1, cz - radius).setType(Material.AIR, false);
            world.getBlockAt(cx + dx, cy + 2, cz - radius).setType(Material.AIR, false);
            world.getBlockAt(cx + dx, cy, cz - radius).setType(pathMat, false);
        }
        // Арка над входом
        Material brickMat = MaterialUtil.getDeepslateBrickMaterial();
        Material slabMat = MaterialUtil.getDeepslateSlabMaterial();
        world.getBlockAt(cx - 2, cy + 3, cz - radius).setType(brickMat, false);
        world.getBlockAt(cx + 2, cy + 3, cz - radius).setType(brickMat, false);
        world.getBlockAt(cx - 1, cy + 3, cz - radius).setType(slabMat, false);
        world.getBlockAt(cx + 1, cy + 3, cz - radius).setType(slabMat, false);
        world.getBlockAt(cx, cy + 4, cz - radius).setType(brickMat, false);
        world.getBlockAt(cx - 2, cy + 4, cz - radius).setType(Material.SOUL_LANTERN, false);
        world.getBlockAt(cx + 2, cy + 4, cz - radius).setType(Material.SOUL_LANTERN, false);

        // 4. Главная дорожка от входа до склепа
        for (int z = -radius; z <= 10; z++) {
            for (int dx = -1; dx <= 1; dx++) {
                Block b = world.getBlockAt(cx + dx, cy, cz + z);
                b.setType(random.nextDouble() < 0.7 ? pathMat : Material.COARSE_DIRT, false);
            }
        }
        // Боковые дорожки к могилам
        int[] sideZ = {-12, -6, 0, 6};
        for (int sz : sideZ) {
            for (int dx = -16; dx <= 16; dx++) {
                Block b = world.getBlockAt(cx + dx, cy, cz + sz);
                if (b.getType() != pathMat) {
                    b.setType(Material.COARSE_DIRT, false);
                }
            }
        }

        // 5. Задний Склеп / Мавзолей (Z = 12..18, X = -4..4)
        buildCrypt(world, cx, cy, cz + 14);

        // 6. Генерация ровно 20 атмосферных могил
        int graveId = 1;
        int[] xOffsets = {-14, -9, -5, 5, 9, 14};
        int[] zOffsets = {-15, -9, -3, 3};

        for (int xOff : xOffsets) {
            for (int zOff : zOffsets) {
                if (graves.size() >= 20) break;
                // Пропускаем центр дорожки
                if (Math.abs(xOff) < 3) continue;

                int gx = cx + xOff;
                int gz = cz + zOff;

                Grave grave = buildGrave(world, gx, cy, gz, graveId++);
                graves.add(grave);
            }
            if (graves.size() >= 20) break;
        }

        // 7. Жуткие деревья и растительность
        buildSpookyTree(world, cx - 12, cy, cz - 8);
        buildSpookyTree(world, cx + 12, cy, cz - 8);
        buildSpookyTree(world, cx - 12, cy, cz + 8);
        buildSpookyTree(world, cx + 12, cy, cz + 8);

        // Растительность (Цветы, сухие кусты, роза иссушителя)
        for (int dx = -radius + 1; dx <= radius - 1; dx++) {
            for (int dz = -radius + 1; dz <= radius - 1; dz++) {
                Block floor = world.getBlockAt(cx + dx, cy, cz + dz);
                Block above = world.getBlockAt(cx + dx, cy + 1, cz + dz);
                if (floor.getType() == Material.GRASS_BLOCK && above.getType() == Material.AIR) {
                    double r = random.nextDouble();
                    if (r < 0.05) above.setType(Material.WITHER_ROSE, false);
                    else if (r < 0.12) above.setType(Material.DEAD_BUSH, false);
                    else if (r < 0.20) above.setType(Material.POPPY, false);
                    else if (r < 0.28) above.setType(Material.FERN, false);
                }
            }
        }

        return graves;
    }

    private void buildWallPillar(World world, int x, int y, int z, boolean isPillar) {
        Material brickMat = MaterialUtil.getDeepslateBrickMaterial();
        Material wallMat = MaterialUtil.match("POLISHED_BLACKSTONE_WALL", "COBBLESTONE_WALL");
        if (isPillar) {
            world.getBlockAt(x, y + 1, z).setType(brickMat, false);
            world.getBlockAt(x, y + 2, z).setType(brickMat, false);
            world.getBlockAt(x, y + 3, z).setType(wallMat, false);
            world.getBlockAt(x, y + 4, z).setType(Material.SOUL_LANTERN, false);
        } else {
            world.getBlockAt(x, y + 1, z).setType(brickMat, false);
            world.getBlockAt(x, y + 2, z).setType(Material.IRON_BARS, false);
        }
    }

    private void buildCrypt(World world, int cx, int cy, int cz) {
        Material brick = MaterialUtil.getDeepslateBrickMaterial();
        Material cracked = MaterialUtil.getCrackedDeepslateBrickMaterial();
        Material slab = MaterialUtil.getDeepslateSlabMaterial();
        Material wall = MaterialUtil.match("POLISHED_BLACKSTONE_WALL", "COBBLESTONE_WALL");
        Material chiseled = MaterialUtil.match("CHISELED_POLISHED_BLACKSTONE", "CHISELED_STONE_BRICKS");

        // Стены склепа (7x6)
        for (int x = -3; x <= 3; x++) {
            for (int z = -2; z <= 3; z++) {
                for (int y = 1; y <= 4; y++) {
                    Material m = (x == -3 || x == 3 || z == -2 || z == 3) ?
                            (random.nextDouble() < 0.7 ? brick : cracked) : Material.AIR;
                    world.getBlockAt(cx + x, cy + y, cz + z).setType(m, false);
                }
                // Крыша
                world.getBlockAt(cx + x, cy + 5, cz + z).setType(slab, false);
            }
        }
        // Вход в склеп
        world.getBlockAt(cx, cy + 1, cz - 2).setType(Material.AIR, false);
        world.getBlockAt(cx, cy + 2, cz - 2).setType(Material.AIR, false);
        world.getBlockAt(cx - 1, cy + 1, cz - 2).setType(wall, false);
        world.getBlockAt(cx + 1, cy + 1, cz - 2).setType(wall, false);

        // Алтарь вечного огня в центре склепа
        world.getBlockAt(cx, cy + 1, cz + 1).setType(chiseled, false);
        world.getBlockAt(cx, cy + 2, cz + 1).setType(Material.SOUL_CAMPFIRE, false);
    }

    private Grave buildGrave(World world, int x, int y, int z, int id) {
        Material wall = MaterialUtil.match("POLISHED_BLACKSTONE_WALL", "COBBLESTONE_WALL");
        Material rootedDirt = MaterialUtil.getRootedDirtMaterial();

        // 1. Надгробный крест (Stone Wall)
        world.getBlockAt(x, y + 1, z - 1).setType(wall, false);
        world.getBlockAt(x, y + 2, z - 1).setType(wall, false);
        world.getBlockAt(x - 1, y + 2, z - 1).setType(wall, false);
        world.getBlockAt(x + 1, y + 2, z - 1).setType(wall, false);

        // 2. Насыпь могилы (земля/подзол)
        Block surfaceBlock = world.getBlockAt(x, y, z);
        Block surfaceBlock2 = world.getBlockAt(x, y, z + 1);
        surfaceBlock.setType(Material.COARSE_DIRT, false);
        surfaceBlock2.setType(rootedDirt, false);

        // 3. Подземный сундук под могилой (y - 1)
        Block chestBlock = world.getBlockAt(x, y - 1, z);
        chestBlock.setType(Material.CHEST, false);

        // Блок под второй частью могилы
        world.getBlockAt(x, y - 1, z + 1).setType(Material.DIRT, false);

        Location digLoc = surfaceBlock.getLocation();
        Location chestLoc = chestBlock.getLocation();
        Location holoLoc = new Location(world, x + 0.5, y + 2.4, z + 0.5);

        return new Grave(id, digLoc, chestLoc, holoLoc);
    }

    private void buildSpookyTree(World world, int x, int y, int z) {
        // Ствол
        for (int dy = 1; dy <= 4; dy++) {
            world.getBlockAt(x, y + dy, z).setType(Material.DARK_OAK_LOG, false);
        }
        world.getBlockAt(x + 1, y + 4, z).setType(Material.DARK_OAK_LOG, false);
        world.getBlockAt(x - 1, y + 5, z).setType(Material.DARK_OAK_LOG, false);

        // Листва
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 4; dy <= 6; dy++) {
                    if (random.nextDouble() < 0.6) {
                        Block b = world.getBlockAt(x + dx, y + dy, z + dz);
                        if (b.getType() == Material.AIR) {
                            b.setType(Material.OAK_LEAVES, false);
                        }
                    }
                }
            }
        }
    }
}
