package ru.rooyzee.elytrixairdrop.managers;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.utils.ParticleUtil;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер схематик и единой безопасной волновой анимации растворения/восстановления блоков.
 */
public class SchematicManager {

    private static final int RESTORE_STEPS = 20;
    private static final int MAX_PARTICLE_PER_TICK = 35;

    private final Main plugin;
    private final Map<String, Clipboard> clipboardCache = new ConcurrentHashMap<>();

    public SchematicManager(Main plugin) {
        this.plugin = plugin;
    }

    public Clipboard loadSchematic(String name) {
        Clipboard cached = clipboardCache.get(name);
        if (cached != null) return cached;

        File file = new File(plugin.getDataFolder(), "schematics/" + name);
        if (!file.exists()) {
            plugin.getLogger().warning("Schematic not found: " + name);
            return null;
        }
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            plugin.getLogger().warning("Unknown schematic format: " + name);
            return null;
        }
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clip = reader.read();
            clipboardCache.put(name, clip);
            return clip;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load schematic " + name + ": " + e.getMessage());
            return null;
        }
    }

    public void invalidateCache() {
        clipboardCache.clear();
    }

    public PasteResult pasteAndBackup(Clipboard clipboard, Location target) {
        Map<Location, BlockData> backup = new ConcurrentHashMap<>();
        List<Location> beacons = new ArrayList<>();
        World world = target.getWorld();
        if (world == null) return null;

        BlockVector3 origin = clipboard.getOrigin();
        int dx = target.getBlockX() - origin.getBlockX();
        int dy = target.getBlockY() - origin.getBlockY();
        int dz = target.getBlockZ() - origin.getBlockZ();

        int clearAbove = 15;
        int maxWorldY = world.getMaxHeight() - 1;

        Map<Long, int[]> columnY = new HashMap<>();
        List<int[]> solidBlocks = new ArrayList<>();

        for (BlockVector3 pos : clipboard.getRegion()) {
            BaseBlock block = clipboard.getFullBlock(pos);
            if (block.getBlockType() == BlockTypes.AIR) continue;

            int wx = pos.getBlockX() + dx;
            int wy = pos.getBlockY() + dy;
            int wz = pos.getBlockZ() + dz;

            long colKey = (((long) wx) << 32) | (wz & 0xFFFFFFFFL);
            int[] yr = columnY.get(colKey);
            if (yr == null) {
                yr = new int[]{wy, wy};
                columnY.put(colKey, yr);
            } else {
                if (wy < yr[0]) yr[0] = wy;
                if (wy > yr[1]) yr[1] = wy;
            }

            boolean isBeacon = block.getBlockType().equals(com.sk89q.worldedit.world.block.BlockTypes.BEACON);
            solidBlocks.add(new int[]{wx, wy, wz, isBeacon ? 1 : 0});
        }

        // 1. Очищаем только колонны, где присутствует схематика
        for (Map.Entry<Long, int[]> e : columnY.entrySet()) {
            long key = e.getKey();
            int wx = (int) (key >> 32);
            int wz = (int) (key & 0xFFFFFFFFL);
            int minY = e.getValue()[0];
            int maxY = Math.min(maxWorldY, e.getValue()[1] + clearAbove);

            if (!world.isChunkLoaded(wx >> 4, wz >> 4)) {
                world.getChunkAt(wx >> 4, wz >> 4);
            }

            for (int y = minY; y <= maxY; y++) {
                Block b = world.getBlockAt(wx, y, wz);
                Material type = b.getType();
                if (type == Material.AIR || type == Material.CAVE_AIR) continue;
                Location loc = new Location(world, wx, y, wz);
                backup.putIfAbsent(loc, new BlockData(type, b.getBlockData().getAsString()));
                b.setType(Material.AIR, false);
            }
        }

        // 2. Сохраняем все позиции вставки блоков схематики
        for (int[] sb : solidBlocks) {
            int wx = sb[0], wy = sb[1], wz = sb[2];
            Location loc = new Location(world, wx, wy, wz);
            if (!backup.containsKey(loc)) {
                Block b = world.getBlockAt(wx, wy, wz);
                backup.put(loc, new BlockData(b.getType(), b.getBlockData().getAsString()));
            }
            if (sb[3] == 1) {
                beacons.add(loc.clone());
            }
        }

        // 3. Вставляем схематику через WorldEdit
        try (EditSession editSession = createEditSession(world)) {
            Operation op = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BlockVector3.at(target.getBlockX(), target.getBlockY(), target.getBlockZ()))
                    .ignoreAirBlocks(true)
                    .build();
            Operations.complete(op);
            return new PasteResult(backup, beacons);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to paste schematic: " + e.getMessage());
            restoreImmediate(backup);
            return null;
        }
    }

    private EditSession createEditSession(World world) {
        try {
            Method m = WorldEdit.class.getMethod("newEditSession", com.sk89q.worldedit.world.World.class);
            return (EditSession) m.invoke(WorldEdit.getInstance(), BukkitAdapter.adapt(world));
        } catch (Throwable t) {
            return WorldEdit.getInstance().getEditSessionFactory().getEditSession(BukkitAdapter.adapt(world), -1);
        }
    }

    /**
     * Единая безопасная волновая анимация растворения блоков и полного восстановления террейна.
     */
    public void restore(Map<Location, BlockData> backup) {
        if (backup == null || backup.isEmpty()) return;
        if (!plugin.isEnabled()) {
            restoreImmediate(backup);
            return;
        }

        double centerX = 0, centerY = 0, centerZ = 0;
        int count = 0;
        for (Location l : backup.keySet()) {
            centerX += l.getBlockX();
            centerY += l.getBlockY();
            centerZ += l.getBlockZ();
            count++;
        }
        if (count == 0) return;
        centerX /= count;
        centerY /= count;
        centerZ /= count;

        double maxDistSq = 0;
        for (Location l : backup.keySet()) {
            double ddx = l.getBlockX() - centerX;
            double ddy = l.getBlockY() - centerY;
            double ddz = l.getBlockZ() - centerZ;
            double d = ddx * ddx + ddy * ddy + ddz * ddz;
            if (d > maxDistSq) maxDistSq = d;
        }
        final double maxDist = Math.max(1.0, Math.sqrt(maxDistSq));
        final double cX = centerX, cY = centerY, cZ = centerZ;

        List<List<Map.Entry<Location, BlockData>>> waves = new ArrayList<>();
        for (int i = 0; i < RESTORE_STEPS; i++) waves.add(new ArrayList<>());

        for (Map.Entry<Location, BlockData> e : backup.entrySet()) {
            Location l = e.getKey();
            double ddx = l.getBlockX() - cX;
            double ddy = l.getBlockY() - cY;
            double ddz = l.getBlockZ() - cZ;
            double dist = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
            int step = (int) Math.floor((dist / (maxDist + 0.001)) * RESTORE_STEPS);
            if (step >= RESTORE_STEPS) step = RESTORE_STEPS - 1;
            waves.get(step).add(e);
        }

        final int[] currentStep = {0};
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (currentStep[0] >= RESTORE_STEPS || !plugin.isEnabled()) {
                    if (holder[0] != null) holder[0].cancel();
                    restoreImmediate(backup);
                    return;
                }
                List<Map.Entry<Location, BlockData>> wave = waves.get(currentStep[0]);
                int particleBudget = MAX_PARTICLE_PER_TICK;

                for (Map.Entry<Location, BlockData> e : wave) {
                    try {
                        Block b = e.getKey().getBlock();
                        Material targetMat = e.getValue().material;
                        Material currentMat = b.getType();

                        if (currentMat != targetMat) {
                            if (particleBudget > 0 && b.getWorld() != null) {
                                Location particleLoc = e.getKey().clone().add(0.5, 0.5, 0.5);
                                if (currentMat != Material.AIR) {
                                    try {
                                        b.getWorld().spawnParticle(ParticleUtil.BLOCK_CRACK, particleLoc, 3,
                                                0.18, 0.18, 0.18, 0.02, b.getBlockData());
                                    } catch (Throwable ignored) {}
                                }
                                b.getWorld().spawnParticle(ParticleUtil.CLOUD, particleLoc, 1, 0.15, 0.15, 0.15, 0.01);
                                particleBudget--;
                            }
                            b.setType(targetMat, false);
                        }
                        try {
                            org.bukkit.block.data.BlockData data = Bukkit.createBlockData(e.getValue().data);
                            b.setBlockData(data, false);
                        } catch (Exception ignored) {}
                    } catch (Exception ignored) {}
                }
                currentStep[0]++;
            } catch (Exception ex) {
                plugin.getLogger().severe("Restore wave error: " + ex.getMessage());
                if (holder[0] != null) holder[0].cancel();
                restoreImmediate(backup);
            }
        }, 1L, 2L);
    }

    public void restoreImmediate(Map<Location, BlockData> backup) {
        if (backup == null || backup.isEmpty()) return;
        for (Map.Entry<Location, BlockData> e : backup.entrySet()) {
            try {
                Location loc = e.getKey();
                if (loc == null || loc.getWorld() == null) continue;
                Block b = loc.getBlock();
                BlockData bd = e.getValue();
                if (bd == null) continue;

                if (b.getType() != bd.material) {
                    b.setType(bd.material, false);
                }
                try {
                    org.bukkit.block.data.BlockData data = Bukkit.createBlockData(bd.data);
                    b.setBlockData(data, false);
                } catch (Exception ignored) {}
            } catch (Exception ignored) {}
        }
    }

    public static class BlockData {
        public final Material material;
        public final String data;
        public BlockData(Material material, String data) {
            this.material = material;
            this.data = data;
        }
    }

    public static class PasteResult {
        public final Map<Location, BlockData> backup;
        public final List<Location> beacons;

        public PasteResult(Map<Location, BlockData> backup) {
            this(backup, Collections.emptyList());
        }

        public PasteResult(Map<Location, BlockData> backup, List<Location> beacons) {
            this.backup = backup;
            this.beacons = beacons != null ? beacons : Collections.emptyList();
        }
    }
}
