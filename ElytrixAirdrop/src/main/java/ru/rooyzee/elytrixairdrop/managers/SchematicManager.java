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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;
import ru.rooyzee.elytrixairdrop.Main;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SchematicManager {

    private static final int RESTORE_STEPS = 15;
    private static final int MAX_PARTICLE_PER_TICK = 30;

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

    public PasteResult pasteAndBackup(Clipboard clipboard, Location target, int zoneRadius) {
        World world = target.getWorld();
        if (world == null) return null;

        Map<Location, BlockData> backup = new HashMap<>();
        List<Location> beacons = new ArrayList<>();

        int cx = target.getBlockX();
        int cy = target.getBlockY();
        int cz = target.getBlockZ();
        int r = Math.max(zoneRadius + 5, 35);
        int rSq = r * r;

        int minChunkX = (cx - r) >> 4;
        int maxChunkX = (cx + r) >> 4;
        int minChunkZ = (cz - r) >> 4;
        int maxChunkZ = (cz + r) >> 4;

        for (int chX = minChunkX; chX <= maxChunkX; chX++) {
            for (int chZ = minChunkZ; chZ <= maxChunkZ; chZ++) {
                if (!world.isChunkLoaded(chX, chZ)) {
                    world.getChunkAt(chX, chZ);
                }
            }
        }

        int maxWorldY = world.getMaxHeight() - 1;

        // Делаем полный снимок цилиндрической зоны вокруг аирдропа
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > rSq) continue;
                int x = cx + dx;
                int z = cz + dz;
                int topY = world.getHighestBlockYAt(x, z);

                int startY = Math.max(0, Math.min(topY - 12, cy - 12));
                int endY = Math.min(maxWorldY, Math.max(topY + 18, cy + 28));

                for (int y = startY; y <= endY; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    Location loc = new Location(world, x, y, z);
                    backup.put(loc, new BlockData(b.getType(), b.getBlockData().getAsString()));
                }
            }
        }

        // Вставляем схематику
        try (EditSession editSession = WorldEdit.getInstance()
                .getEditSessionFactory()
                .getEditSession(BukkitAdapter.adapt(world), -1)) {
            Operation op = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BlockVector3.at(target.getBlockX(), target.getBlockY(), target.getBlockZ()))
                    .ignoreAirBlocks(true)
                    .build();
            Operations.complete(op);

            // Собираем маяки
            for (BlockVector3 pos : clipboard.getRegion()) {
                if (clipboard.getFullBlock(pos).getBlockType().equals(com.sk89q.worldedit.world.block.BlockTypes.BEACON)) {
                    int wx = pos.getBlockX() + (target.getBlockX() - clipboard.getOrigin().getBlockX());
                    int wy = pos.getBlockY() + (target.getBlockY() - clipboard.getOrigin().getBlockY());
                    int wz = pos.getBlockZ() + (target.getBlockZ() - clipboard.getOrigin().getBlockZ());
                    beacons.add(new Location(world, wx, wy, wz));
                }
            }

            return new PasteResult(backup, beacons);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to paste schematic: " + e.getMessage());
            restoreImmediate(backup);
            return null;
        }
    }

    public void restore(Map<Location, BlockData> backup) {
        if (backup == null || backup.isEmpty()) return;
        if (!plugin.isEnabled()) {
            restoreImmediate(backup);
            return;
        }

        // Выполняем немедленное восстановление всех измененных блоков
        restoreImmediate(backup);
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
                    if (!b.getBlockData().matches(data)) {
                        b.setBlockData(data, false);
                    }
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
