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
import com.sk89q.worldedit.session.ClipboardHolder;
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

        // Check alternate extension (.schematic <-> .schem)
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

        // 1. Попытка стандартного автоопределения формата по файлу
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

        // 2. Сбор кандидатов форматов
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

        // 3. Прямая попытка через SpongeSchematicReader (GZIP NBT)
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

        // 4. Прямая попытка через MCEditSchematicReader (GZIP NBT)
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

        // 5. Прямая попытка без GZIP (если файл уже распакованный NBT)
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
        plugin.getLogger().severe("Убедитесь, что файл не повреждён и создан в WorldEdit / FAWE.");
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

        // Ensure chunks in the radius are loaded
        for (int cx = chunkX - 2; cx <= chunkX + 2; cx++) {
            for (int cz = chunkZ - 2; cz <= chunkZ + 2; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    world.getChunkAt(cx, cz);
                }
            }
        }

        Clipboard clipboard = loadSchematic(name);
        if (clipboard == null) {
            plugin.getLogger().info("Схематика не найдена или не загружена. Создаётся стартовая платформа на X=0, Y="
                    + target.getBlockY() + ", Z=0...");
            generateFallbackPlatform(target);
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
            plugin.getLogger().info("Схематика " + name + " успешно вставлена на координаты "
                    + target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ());
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Не удалось вставить схематику " + name + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void generateFallbackPlatform(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        int radius = 10;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    Block b = world.getBlockAt(cx + x, cy, cz + z);
                    b.setType(Material.SMOOTH_QUARTZ, false);
                }
            }
        }
        world.getBlockAt(cx, cy, cz).setType(Material.GOLD_BLOCK, false);
        world.getBlockAt(cx, cy + 1, cz).setType(Material.BEACON, false);
    }
}
