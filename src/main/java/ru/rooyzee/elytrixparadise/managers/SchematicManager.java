package ru.rooyzee.elytrixparadise.managers;

import com.sk89q.jnbt.NBTInputStream;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.MCEditSchematicReader;
import com.sk89q.worldedit.extent.clipboard.io.SpongeSchematicReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import ru.rooyzee.elytrixparadise.Main;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

public class SchematicManager {

    private final Main plugin;
    private final File schematicsFolder;
    private final Map<String, Clipboard> clipboardCache = new ConcurrentHashMap<>();

    private BlockVector3 lastPastedMin = null;
    private BlockVector3 lastPastedMax = null;
    private World lastPastedWorld = null;

    public SchematicManager(Main plugin) {
        this.plugin = plugin;
        this.schematicsFolder = new File(plugin.getDataFolder(), "schematics");
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
        }
    }

    public File getSchematicsFolder() {
        return schematicsFolder;
    }

    public File findSchematicFile(String name) {
        File file = new File(schematicsFolder, name);
        if (file.exists() && file.isFile()) {
            return file;
        }

        // Check alternate extensions (.schematic <-> .schem)
        if (name.toLowerCase().endsWith(".schem")) {
            File alt = new File(schematicsFolder, name.substring(0, name.length() - 6) + ".schematic");
            if (alt.exists() && alt.isFile()) return alt;
        } else if (name.toLowerCase().endsWith(".schematic")) {
            File alt = new File(schematicsFolder, name.substring(0, name.length() - 10) + ".schem");
            if (alt.exists() && alt.isFile()) return alt;
        } else {
            File alt1 = new File(schematicsFolder, name + ".schem");
            if (alt1.exists() && alt1.isFile()) return alt1;
            File alt2 = new File(schematicsFolder, name + ".schematic");
            if (alt2.exists() && alt2.isFile()) return alt2;
        }

        return null;
    }

    public Clipboard loadSchematic(String name) {
        Clipboard cached = clipboardCache.get(name);
        if (cached != null) return cached;

        File file = findSchematicFile(name);
        if (file == null) {
            plugin.getLogger().warning("Схематика не найдена в папке schematics: " + name);
            plugin.getLogger().warning("Поместите файл вашей схематики в: " + new File(schematicsFolder, name).getAbsolutePath());
            return null;
        }

        plugin.getLogger().info("Загрузка схематики '" + file.getName() + "' (размер: " + file.length() + " байт)...");

        // 1. Стандартный автодетект по файлу
        try {
            ClipboardFormat detected = ClipboardFormats.findByFile(file);
            if (detected != null) {
                Clipboard clip = tryReadWithFormat(detected, file);
                if (clip != null) {
                    plugin.getLogger().info("✓ Схематика '" + file.getName() + "' успешно загружена (формат: " + detected.getName() + ")");
                    clipboardCache.put(name, clip);
                    return clip;
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Автодетект формата выдал ошибку: " + t.getMessage());
        }

        // 2. Перебор зарегистрированных форматов
        List<ClipboardFormat> candidateFormats = new ArrayList<>();
        try {
            for (ClipboardFormat f : ClipboardFormats.getAll()) {
                if (f != null && !candidateFormats.contains(f)) {
                    candidateFormats.add(f);
                }
            }
        } catch (Throwable ignored) {}

        for (String alias : new String[]{"sponge", "schem", "mcedit", "schematic", "fast", "fawe"}) {
            try {
                ClipboardFormat f = ClipboardFormats.findByAlias(alias);
                if (f != null && !candidateFormats.contains(f)) {
                    candidateFormats.add(f);
                }
            } catch (Throwable ignored) {}
        }

        try {
            for (BuiltInClipboardFormat b : BuiltInClipboardFormat.values()) {
                if (b != null && !candidateFormats.contains(b)) {
                    candidateFormats.add(b);
                }
            }
        } catch (Throwable ignored) {}

        for (ClipboardFormat format : candidateFormats) {
            if (format == null) continue;
            Clipboard clip = tryReadWithFormat(format, file);
            if (clip != null) {
                plugin.getLogger().info("✓ Схематика '" + file.getName() + "' успешно загружена (парсер: " + format.getName() + ")");
                clipboardCache.put(name, clip);
                return clip;
            }
        }

        // 3. Прямой SpongeSchematicReader (GZIP NBT)
        try (InputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GZIPInputStream gzip = new GZIPInputStream(bis);
             NBTInputStream nbt = new NBTInputStream(gzip)) {
            SpongeSchematicReader spongeReader = new SpongeSchematicReader(nbt);
            Clipboard clipboard = spongeReader.read();
            if (clipboard != null) {
                plugin.getLogger().info("✓ Схематика '" + file.getName() + "' успешно загружена через SpongeSchematicReader");
                clipboardCache.put(name, clipboard);
                return clipboard;
            }
        } catch (Throwable ignored) {}

        // 4. Прямой MCEditSchematicReader (GZIP NBT)
        try (InputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GZIPInputStream gzip = new GZIPInputStream(bis);
             NBTInputStream nbt = new NBTInputStream(gzip)) {
            MCEditSchematicReader mceditReader = new MCEditSchematicReader(nbt);
            Clipboard clipboard = mceditReader.read();
            if (clipboard != null) {
                plugin.getLogger().info("✓ Схематика '" + file.getName() + "' успешно загружена через MCEditSchematicReader");
                clipboardCache.put(name, clipboard);
                return clipboard;
            }
        } catch (Throwable ignored) {}

        // 5. Прямой Raw NBT (если без сжатия)
        try (InputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis);
             NBTInputStream nbt = new NBTInputStream(bis)) {
            SpongeSchematicReader spongeReader = new SpongeSchematicReader(nbt);
            Clipboard clipboard = spongeReader.read();
            if (clipboard != null) {
                plugin.getLogger().info("✓ Схематика '" + file.getName() + "' успешно загружена (Raw NBT Sponge)");
                clipboardCache.put(name, clipboard);
                return clipboard;
            }
        } catch (Throwable ignored) {}

        try (InputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis);
             NBTInputStream nbt = new NBTInputStream(bis)) {
            MCEditSchematicReader mceditReader = new MCEditSchematicReader(nbt);
            Clipboard clipboard = mceditReader.read();
            if (clipboard != null) {
                plugin.getLogger().info("✓ Схематика '" + file.getName() + "' успешно загружена (Raw NBT MCEdit)");
                clipboardCache.put(name, clipboard);
                return clipboard;
            }
        } catch (Throwable ignored) {}

        plugin.getLogger().severe("✗ Не удалось прочитать файл схематики " + file.getName() + " ни одним из форматов.");
        return null;
    }

    private Clipboard tryReadWithFormat(ClipboardFormat format, File file) {
        if (format == null || file == null || !file.exists()) return null;
        try (InputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            ClipboardReader reader = format.getReader(bis);
            if (reader == null) {
                return null;
            }
            try {
                return reader.read();
            } finally {
                try {
                    reader.close();
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            return null;
        }
    }

    public void invalidateCache() {
        clipboardCache.clear();
    }

    public boolean pasteSchematic(String name, Location target) {
        if (target == null || target.getWorld() == null) return false;

        World world = target.getWorld();
        int chunkX = target.getBlockX() >> 4;
        int chunkZ = target.getBlockZ() >> 4;

        // Ensure chunks in the wide radius are loaded (radius 8 chunks = 128 blocks)
        for (int cx = chunkX - 8; cx <= chunkX + 8; cx++) {
            for (int cz = chunkZ - 8; cz <= chunkZ + 8; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    world.getChunkAt(cx, cz);
                }
            }
        }

        Clipboard clipboard = loadSchematic(name);
        if (clipboard == null) {
            plugin.getLogger().warning("Схематика не загружена. Вставка пропущена (дополнительные блоки не создаются).");
            return false;
        }

        try (EditSession editSession = WorldEdit.getInstance()
                .getEditSessionFactory()
                .getEditSession(BukkitAdapter.adapt(world), -1)) {

            int offsetY = plugin.getConfigManager().getSchematicOffsetY();
            BlockVector3 to = BlockVector3.at(
                    target.getBlockX(),
                    target.getBlockY() + offsetY,
                    target.getBlockZ()
            );

            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(to)
                    .ignoreAirBlocks(plugin.getConfigManager().isIgnoreAir())
                    .build();

            Operations.complete(operation);

            // Compute bounding box
            BlockVector3 origin = clipboard.getOrigin();
            this.lastPastedMin = clipboard.getMinimumPoint().subtract(origin).add(to);
            this.lastPastedMax = clipboard.getMaximumPoint().subtract(origin).add(to);
            this.lastPastedWorld = world;

            plugin.getLogger().info("✓ Схематика " + name + " успешно вставлена на координаты X="
                    + target.getBlockX() + ", Y=" + (target.getBlockY() + offsetY) + ", Z=" + target.getBlockZ());

            // Scan for shards
            scanAndRegisterShards();

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Не удалось вставить схематику " + name + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public int scanAndRegisterShards() {
        Location center = plugin.getConfigManager().getCenterLocation();
        World world = center.getWorld();
        if (world == null) return 0;

        int radiusXZ = plugin.getConfigManager().getScanRadiusXZ(); // 120 blocks
        int minY = plugin.getConfigManager().getScanMinY(); // 140
        int maxY = plugin.getConfigManager().getScanMaxY(); // 215

        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        int minChunkX = (cx - radiusXZ) >> 4;
        int maxChunkX = (cx + radiusXZ) >> 4;
        int minChunkZ = (cz - radiusXZ) >> 4;
        int maxChunkZ = (cz + radiusXZ) >> 4;

        int found = 0;
        for (int chX = minChunkX; chX <= maxChunkX; chX++) {
            for (int chZ = minChunkZ; chZ <= maxChunkZ; chZ++) {
                if (!world.isChunkLoaded(chX, chZ)) {
                    world.getChunkAt(chX, chZ);
                }
                Chunk chunk = world.getChunkAt(chX, chZ);
                for (int x = 0; x < 16; x++) {
                    int worldX = (chX << 4) + x;
                    if (Math.abs(worldX - cx) > radiusXZ) continue;
                    for (int z = 0; z < 16; z++) {
                        int worldZ = (chZ << 4) + z;
                        if (Math.abs(worldZ - cz) > radiusXZ) continue;
                        for (int y = minY; y <= maxY; y++) {
                            Block b = chunk.getBlock(x, y, z);
                            Material type = b.getType();
                            if (type == Material.RED_GLAZED_TERRACOTTA || type == Material.GRAY_GLAZED_TERRACOTTA) {
                                plugin.getShardManager().registerShard(new Location(world, worldX, y, worldZ));
                                found++;
                            }
                        }
                    }
                }
            }
        }

        plugin.getLogger().info("Найдено и зарегистрировано Осколков Рая: " + found + " (радиус: " + radiusXZ + " блоков)");
        return found;
    }

    public void clearSchematic() {
        if (plugin.getShardManager() != null) {
            plugin.getShardManager().clearAllShards();
        }

        if (lastPastedMin == null || lastPastedMax == null || lastPastedWorld == null) {
            return;
        }

        try (EditSession editSession = WorldEdit.getInstance()
                .getEditSessionFactory()
                .getEditSession(BukkitAdapter.adapt(lastPastedWorld), -1)) {

            CuboidRegion region = new CuboidRegion(lastPastedMin, lastPastedMax);
            editSession.setBlocks(region, BlockTypes.AIR.getDefaultState());
            plugin.getLogger().info("Область схематики Райского места очищена.");
        } catch (Throwable t) {
            try {
                for (int x = lastPastedMin.getX(); x <= lastPastedMax.getX(); x++) {
                    for (int y = lastPastedMin.getY(); y <= lastPastedMax.getY(); y++) {
                        for (int z = lastPastedMin.getZ(); z <= lastPastedMax.getZ(); z++) {
                            Block b = lastPastedWorld.getBlockAt(x, y, z);
                            if (b.getType() != Material.AIR) {
                                b.setType(Material.AIR, false);
                            }
                        }
                    }
                }
                plugin.getLogger().info("Область схематики очищена вручную.");
            } catch (Throwable ignored) {}
        }

        lastPastedMin = null;
        lastPastedMax = null;
        lastPastedWorld = null;
    }
}
