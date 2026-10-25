package ru.rooyzee.elytrixcemetery.managers;

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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.generator.CemeteryGenerator;
import ru.rooyzee.elytrixcemetery.graves.Grave;
import ru.rooyzee.elytrixcemetery.util.MaterialUtil;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class SchematicManager {

    private final Main plugin;
    private final File schematicsFolder;
    private final CemeteryGenerator generator;

    public SchematicManager(Main plugin) {
        this.plugin = plugin;
        this.schematicsFolder = new File(plugin.getDataFolder(), "schematics");
        if (!schematicsFolder.exists()) schematicsFolder.mkdirs();
        this.generator = new CemeteryGenerator(plugin);
    }

    public boolean buildOrPasteCemetery() {
        Location center = plugin.getConfigManager().getCenterLocation();
        if (center == null || center.getWorld() == null) return false;

        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        // Загружаем нужные 9 чанков
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int chX = (cx >> 4) + dx;
                int chZ = (cz >> 4) + dz;
                if (!world.isChunkLoaded(chX, chZ)) {
                    world.getChunkAt(chX, chZ);
                }
            }
        }

        File schemFile = new File(schematicsFolder, "cemetery.schem");
        if (schemFile.exists()) {
            Clipboard clipboard = loadSchematic(schemFile);
            if (clipboard != null) {
                pasteSchematic(clipboard, center);
                scanAndRegisterGraves(center);
                return true;
            }
        }

        // Если файла схематики нет — генерируем красивое кладбище процедурно!
        plugin.getLogger().info("Схематика не найдена. Запуск процедурной генерации кладбища...");
        List<Grave> generatedGraves = generator.generateCemetery(center);
        plugin.getGraveManager().registerGraves(generatedGraves);
        plugin.getLogger().info("✓ Кладбище успешно сгенерировано! Создано " + generatedGraves.size() + " могил.");
        return true;
    }

    private Clipboard loadSchematic(File file) {
        try {
            ClipboardFormat format = ClipboardFormats.findByFile(file);
            if (format != null) {
                try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
                    return reader.read();
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось загрузить схематику: " + t.getMessage());
        }
        return null;
    }

    private void pasteSchematic(Clipboard clipboard, Location target) {
        World world = target.getWorld();
        if (world == null) return;

        try (EditSession session = createEditSession(world)) {
            Operation op = new ClipboardHolder(clipboard)
                    .createPaste(session)
                    .to(BlockVector3.at(target.getBlockX(), target.getBlockY(), target.getBlockZ()))
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(op);
            plugin.getLogger().info("✓ Схематика кладбища успешно вставлена.");
        } catch (Throwable t) {
            plugin.getLogger().severe("Ошибка вставки схематики: " + t.getMessage());
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

    public void scanAndRegisterGraves(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        List<Grave> found = new ArrayList<>();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int radius = plugin.getConfigManager().getBoundaryRadius();
        Material rootedDirt = MaterialUtil.getRootedDirtMaterial();

        int id = 1;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (int y = cy - 2; y <= cy + 3; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    Block below = world.getBlockAt(x, y - 1, z);
                    if (below.getType() == Material.CHEST && (b.getType() == Material.COARSE_DIRT || b.getType() == rootedDirt || b.getType() == Material.PODZOL || b.getType() == Material.DIRT)) {
                        Location digLoc = b.getLocation();
                        Location chestLoc = below.getLocation();
                        Location holoLoc = new Location(world, x + 0.5, y + 2.4, z + 0.5);
                        found.add(new Grave(id++, digLoc, chestLoc, holoLoc));
                    }
                }
            }
        }

        plugin.getGraveManager().registerGraves(found);
        plugin.getLogger().info("Найдено и зарегистрировано могил в схематике: " + found.size());
    }
}
