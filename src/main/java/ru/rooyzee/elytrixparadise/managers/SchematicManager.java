package ru.rooyzee.elytrixparadise.managers;

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
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.regions.CuboidRegion;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    public Clipboard loadSchematic(String name) {
        if (!name.endsWith(".schem") && !name.endsWith(".schematic")) {
            name = name + ".schem";
        }

        File file = new File(schematicsFolder, name);
        if (!file.exists()) {
            File altFile = new File(plugin.getDataFolder(), name);
            if (altFile.exists()) {
                file = altFile;
            } else {
                plugin.getLogger().warning("Файл схематики '" + name + "' не найден в папке plugins/ElytrixParadise/schematics/!");
                return null;
            }
        }

        if (clipboardCache.containsKey(file.getAbsolutePath())) {
            return clipboardCache.get(file.getAbsolutePath());
        }

        plugin.getLogger().info("Загрузка схематики '" + name + "' (размер: " + file.length() + " байт)...");

        // Strategy 1: WorldEdit standard ClipboardFormats.findByFile
        try {
            ClipboardFormat format = ClipboardFormats.findByFile(file);
            if (format != null) {
                Clipboard clipboard = tryReadWithFormat(format, file);
                if (clipboard != null) {
                    clipboardCache.put(file.getAbsolutePath(), clipboard);
                    plugin.getLogger().info("✓ Схематика '" + name + "' успешно загружена (формат: " + format.getName() + ")");
                    return clipboard;
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Метод ClipboardFormats.findByFile не сработал: " + t.getMessage());
        }

        // Strategy 2: Iterate over BuiltInClipboardFormat enum values
        try {
            Class<?> builtInClass = Class.forName("com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat");
            if (builtInClass.isEnum()) {
                Object[] constants = builtInClass.getEnumConstants();
                if (constants != null) {
                    for (Object constant : constants) {
                        if (constant instanceof ClipboardFormat) {
                            ClipboardFormat fmt = (ClipboardFormat) constant;
                            Clipboard clipboard = tryReadWithFormat(fmt, file);
                            if (clipboard != null) {
                                clipboardCache.put(file.getAbsolutePath(), clipboard);
                                plugin.getLogger().info("✓ Схематика '" + name + "' успешно загружена через BuiltIn (" + fmt.getName() + ")");
                                return clipboard;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Strategy 3: Check FastAsyncWorldEdit FAWE formats
        try {
            Class<?> fastFormatsClass = Class.forName("com.fastasyncworldedit.core.extent.clipboard.io.FastClipboardFormats");
            for (Field field : fastFormatsClass.getDeclaredFields()) {
                if (ClipboardFormat.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    ClipboardFormat fmt = (ClipboardFormat) field.get(null);
                    if (fmt != null) {
                        Clipboard clipboard = tryReadWithFormat(fmt, file);
                        if (clipboard != null) {
                            clipboardCache.put(file.getAbsolutePath(), clipboard);
                            plugin.getLogger().info("✓ Схематика '" + name + "' успешно загружена через FAWE FastClipboardFormats (" + fmt.getName() + ")");
                            return clipboard;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Strategy 4: Direct instantiation of known format classes
        String[] readerClassNames = new String[] {
                "com.sk89q.worldedit.extent.clipboard.io.SpongeSchematicReader",
                "com.sk89q.worldedit.extent.clipboard.io.FastSchematicReader",
                "com.sk89q.worldedit.extent.clipboard.io.SchematicReader",
                "com.fastasyncworldedit.core.extent.clipboard.io.FastSchematicReader"
        };

        for (String className : readerClassNames) {
            try {
                Class<?> clazz = Class.forName(className);
                try (InputStream fis = new FileInputStream(file);
                     BufferedInputStream bis = new BufferedInputStream(fis)) {
                    ClipboardReader reader = null;
                    try {
                        reader = (ClipboardReader) clazz.getConstructor(InputStream.class).newInstance(bis);
                    } catch (Throwable t2) {
                        try {
                            reader = (ClipboardReader) clazz.getConstructor(BufferedInputStream.class).newInstance(bis);
                        } catch (Throwable ignored) {}
                    }

                    if (reader != null) {
                        try {
                            Clipboard cb = reader.read();
                            if (cb != null) {
                                reader.close();
                                clipboardCache.put(file.getAbsolutePath(), cb);
                                plugin.getLogger().info("✓ Схематика '" + name + "' загружена через прямой ридер " + className);
                                return cb;
                            }
                        } finally {
                            try {
                                reader.close();
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

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

        int radiusXZ = plugin.getConfigManager().getScanRadiusXZ();
        int configMinY = plugin.getConfigManager().getScanMinY();
        int configMaxY = plugin.getConfigManager().getScanMaxY();

        int worldMinY = 0;
        try {
            worldMinY = world.getMinHeight();
        } catch (Throwable ignored) {
            worldMinY = 0;
        }

        int worldMaxY = 255;
        try {
            worldMaxY = world.getMaxHeight() - 1;
        } catch (Throwable ignored) {
            worldMaxY = 255;
        }

        int minY = Math.max(worldMinY, Math.min(worldMaxY, configMinY));
        int maxY = Math.max(worldMinY, Math.min(worldMaxY, configMaxY));
        if (minY > maxY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }

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
                            try {
                                Block b = chunk.getBlock(x, y, z);
                                Material type = b.getType();
                                if (type == Material.RED_GLAZED_TERRACOTTA || type == Material.GRAY_GLAZED_TERRACOTTA) {
                                    plugin.getShardManager().registerShard(new Location(world, worldX, y, worldZ));
                                    found++;
                                }
                            } catch (Throwable ignored) {}
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
                int worldMinY = 0;
                int worldMaxY = 255;
                try {
                    worldMinY = lastPastedWorld.getMinHeight();
                } catch (Throwable ignored) {}
                try {
                    worldMaxY = lastPastedWorld.getMaxHeight() - 1;
                } catch (Throwable ignored) {}

                for (int x = lastPastedMin.getX(); x <= lastPastedMax.getX(); x++) {
                    for (int y = Math.max(worldMinY, lastPastedMin.getY()); y <= Math.min(worldMaxY, lastPastedMax.getY()); y++) {
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
