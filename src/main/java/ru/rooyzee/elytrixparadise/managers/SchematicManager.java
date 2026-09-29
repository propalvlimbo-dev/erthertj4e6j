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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import ru.rooyzee.elytrixparadise.Main;

import java.io.File;
import java.io.FileInputStream;
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

    public boolean isSchematicPresent(String name) {
        File file = new File(schematicsFolder, name);
        return file.exists() && file.isFile() && file.length() > 0;
    }

    public Clipboard loadSchematic(String name) {
        Clipboard cached = clipboardCache.get(name);
        if (cached != null) return cached;

        File file = new File(schematicsFolder, name);
        if (!file.exists()) {
            plugin.getLogger().warning("Схематика не найдена в папке schematics: " + name);
            plugin.getLogger().warning("Поместите вашу схематику в: " + file.getAbsolutePath());
            return null;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            plugin.getLogger().warning("Неизвестный формат схематики: " + name);
            return null;
        }

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clip = reader.read();
            clipboardCache.put(name, clip);
            return clip;
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка при чтении схематики " + name + ": " + e.getMessage());
            return null;
        }
    }

    public void invalidateCache() {
        clipboardCache.clear();
    }

    /**
     * Pastes the configured schematic at the specified target location.
     * If schematic file doesn't exist, generates a fallback cloud paradise platform.
     */
    public boolean pasteSchematic(String name, Location target) {
        if (target == null || target.getWorld() == null) return false;

        World world = target.getWorld();
        int chunkX = target.getBlockX() >> 4;
        int chunkZ = target.getBlockZ() >> 4;

        // Ensure chunks in the paste radius are loaded
        for (int cx = chunkX - 2; cx <= chunkX + 2; cx++) {
            for (int cz = chunkZ - 2; cz <= chunkZ + 2; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    world.getChunkAt(cx, cz);
                }
            }
        }

        Clipboard clipboard = loadSchematic(name);
        if (clipboard == null) {
            plugin.getLogger().info("Схематика '" + name + "' не найдена. Генерируется резервная райская облачная платформа на координатах 0, 0...");
            generateFallbackCloudIsland(target);
            return true;
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
            plugin.getLogger().info("Схематика " + name + " успешно вставлена на координатах "
                    + target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ());
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Не удалось вставить схематику " + name + ": " + e.getMessage());
            e.printStackTrace();
            generateFallbackCloudIsland(target);
            return false;
        }
    }

    /**
     * Generates a starter cloud island platform when no schematic is provided yet.
     */
    public void generateFallbackCloudIsland(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        int radius = 18;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= radius) {
                    // Cloud bottom fluff (white wool / stained glass)
                    int fluffHeight = (int) (Math.sin(x * 0.4) * Math.cos(z * 0.4) * 2);

                    for (int y = -2 + fluffHeight; y <= 0; y++) {
                        Block b = world.getBlockAt(cx + x, cy + y, cz + z);
                        if (y == 0) {
                            if (dist < 8) {
                                b.setType(Material.SMOOTH_QUARTZ, false);
                            } else if (dist < 14) {
                                b.setType(Material.WHITE_CONCRETE, false);
                            } else {
                                b.setType(Material.WHITE_WOOL, false);
                            }
                        } else if (y == -1) {
                            b.setType(Material.WHITE_STAINED_GLASS, false);
                        } else {
                            b.setType(Material.WHITE_STAINED_GLASS, false);
                        }
                    }

                    // Gold & Sea lantern pillars at cardinal points
                    if ((Math.abs(x) == 7 && z == 0) || (Math.abs(z) == 7 && x == 0)) {
                        world.getBlockAt(cx + x, cy + 1, cz + z).setType(Material.QUARTZ_PILLAR, false);
                        world.getBlockAt(cx + x, cy + 2, cz + z).setType(Material.QUARTZ_PILLAR, false);
                        world.getBlockAt(cx + x, cy + 3, cz + z).setType(Material.SEA_LANTERN, false);
                    }
                }
            }
        }

        // Central Altar
        world.getBlockAt(cx, cy, cz).setType(Material.GOLD_BLOCK, false);
        world.getBlockAt(cx, cy + 1, cz).setType(Material.BEACON, false);
        world.getBlockAt(cx + 1, cy, cz).setType(Material.GOLD_BLOCK, false);
        world.getBlockAt(cx - 1, cy, cz).setType(Material.GOLD_BLOCK, false);
        world.getBlockAt(cx, cy, cz + 1).setType(Material.GOLD_BLOCK, false);
        world.getBlockAt(cx, cy, cz - 1).setType(Material.GOLD_BLOCK, false);

        plugin.getLogger().info("Резервная облачная платформа Рая создана на " + cx + ", " + cy + ", " + cz);
    }
}
