package ru.rooyzee.elytrixparadise.managers;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import ru.rooyzee.elytrixparadise.Main;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
            plugin.getLogger().warning("Поместите вашу схематику в: " + new File(schematicsFolder, name).getAbsolutePath());
            return null;
        }

        List<ClipboardFormat> formatsToTry = new ArrayList<>();
        ClipboardFormat primaryFormat = ClipboardFormats.findByFile(file);
        if (primaryFormat != null) {
            formatsToTry.add(primaryFormat);
        }

        // Add all built-in formats (MCEdit, Sponge, etc.) to ensure legacy & modern formats load without error
        for (BuiltInClipboardFormat builtIn : BuiltInClipboardFormat.values()) {
            if (!formatsToTry.contains(builtIn)) {
                formatsToTry.add(builtIn);
            }
        }

        Exception lastException = null;
        for (ClipboardFormat format : formatsToTry) {
            try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
                Clipboard clipboard = reader.read();
                if (clipboard != null) {
                    plugin.getLogger().info("Схематика '" + file.getName() + "' успешно прочитана (формат: " + format.getName() + ")");
                    clipboardCache.put(name, clipboard);
                    return clipboard;
                }
            } catch (Exception e) {
                lastException = e;
            }
        }

        plugin.getLogger().severe("Ошибка при чтении схематики " + file.getName() + ": "
                + (lastException != null ? lastException.getMessage() : "Не удалось распознать формат"));
        return null;
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
