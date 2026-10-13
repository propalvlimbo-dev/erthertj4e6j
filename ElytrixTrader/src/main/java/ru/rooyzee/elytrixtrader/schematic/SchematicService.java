package ru.rooyzee.elytrixtrader.schematic;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.SchematicConfig;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class SchematicService {

    private final Main plugin;

    public SchematicService(Main plugin) {
        this.plugin = plugin;
    }

    public boolean available() {
        return worldEdit() != null;
    }

    private WorldEditPlugin worldEdit() {
        if (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") instanceof WorldEditPlugin) {
            return (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
        }
        return (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");
    }

    public File resolve(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }
        String base = fileName.trim().replace('\\', '/');
        if (base.contains("/") && !base.startsWith("..")) {
            File direct = new File(plugin.getDataFolder().getParentFile(), base);
            if (direct.isFile()) {
                return direct;
            }
        }
        String name = base;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        for (File directory : directories()) {
            if (directory == null || !directory.isDirectory()) {
                continue;
            }
            for (String extension : new String[]{"schem", "sponge", "schematic"}) {
                File candidate = new File(directory, name + "." + extension);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
            File literal = new File(directory, base);
            if (literal.isFile()) {
                return literal;
            }
        }
        return null;
    }

    private File[] directories() {
        File local = new File(plugin.getDataFolder(), plugin.config().schematicFolder());
        File fawe = new File(Bukkit.getWorldContainer(), "plugins/FastAsyncWorldEdit/schematics");
        File we = new File(Bukkit.getWorldContainer(), "plugins/WorldEdit/schematics");
        WorldEditPlugin pluginInstance = worldEdit();
        File configured = null;
        if (pluginInstance != null) {
            try {
                File dataFolder = pluginInstance.getDataFolder();
                File configFile = new File(dataFolder, "config.yml");
                if (configFile.isFile()) {
                    org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
                    String saveDir = yaml.getString("saving.dir");
                    if (saveDir == null) saveDir = yaml.getString("worldedit.schematic.directory", "schematics");
                    if (saveDir == null) saveDir = yaml.getString("saving.schematics-dir", "schematics");
                    if (saveDir != null) {
                        File dir = new File(saveDir);
                        if (dir.isAbsolute()) configured = dir;
                        else configured = new File(dataFolder, saveDir);
                    }
                }
                if (configured == null) {
                    try {
                        Object cfgObj = pluginInstance.getClass().getMethod("getConfiguration").invoke(pluginInstance);
                        if (cfgObj instanceof FileConfiguration) {
                            FileConfiguration cfg = (FileConfiguration) cfgObj;
                            String saveDir = cfg.getString("worldedit.schematic.directory", "schematics");
                            if (saveDir == null) saveDir = cfg.getString("saving.dir", "schematics");
                            configured = new File(pluginInstance.getDataFolder(), saveDir);
                        }
                    } catch (NoSuchMethodException ignored2) {
                    }
                }
                if (configured == null) {
                    try {
                        Object weInstance = WorldEdit.getInstance();
                        Object localConfig = weInstance.getClass().getMethod("getConfiguration").invoke(weInstance);
                        if (localConfig != null) {
                            try {
                                Object savingDir = localConfig.getClass().getField("savingDir").get(localConfig);
                                if (savingDir instanceof String) {
                                    configured = new File(pluginInstance.getDataFolder(), (String) savingDir);
                                }
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {
            }
        }
        return new File[]{local, configured, fawe, we};
    }

    public boolean paste(Location location, SchematicConfig config) {
        SchematicSnapshot snap = pasteWithBackup(location, config);
        return snap != null;
    }

    public SchematicSnapshot pasteWithBackup(Location location, SchematicConfig config) {
        WorldEditPlugin worldEdit = worldEdit();
        if (worldEdit == null) {
            return null;
        }
        if (location == null || location.getWorld() == null || config == null || !config.enabled()) {
            return null;
        }
        File file = resolve(config.file());
        if (file == null) {
            return null;
        }
        long started = System.currentTimeMillis();
        try {
            ClipboardFormat format = ClipboardFormats.findByFile(file);
            if (format == null) {
                format = ClipboardFormats.findByAlias("sponge");
            }
            if (format == null) {
                return null;
            }
            Clipboard clipboard;
            try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
                clipboard = reader.read();
            }
            if (clipboard == null) {
                return null;
            }

            World world = location.getWorld();

            // Backup before clear and paste.
            // Сканируем ТОЛЬКО область, которую реально затронет вставка
            // (границы схематики с учётом поворота + clear-radius), а не
            // фиксированный бокс ~±40 блоков — это убирало сотни тысяч
            // синхронных обращений к блокам на главном потоке (микролаг
            // при спавне). Логика отката не меняется.
            int[] bounds = affectedBounds(world, location, clipboard, config);
            java.util.Map<Long, BlockSnapshot> originals = backupArea(world, bounds);

            clearArea(world, location, config);

            EditSession editSession;
            try {
                editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world));
            } catch (Throwable t) {
                editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(BukkitAdapter.adapt(world), -1);
            }
            try {
                ClipboardHolder holder = new ClipboardHolder(clipboard);
                double rotation = config.rotation();
                if (Math.abs(rotation) > 0.001D) {
                    AffineTransform transform = new AffineTransform().rotateY(Math.toRadians(-rotation));
                    holder.setTransform(holder.getTransform().combine(transform));
                }
                Operation operation = holder.createPaste(editSession)
                        .to(BlockVector3.at(location.getX(), location.getY(), location.getZ()))
                        .ignoreAirBlocks(config.skipAir())
                        .copyEntities(config.pasteEntities())
                        .build();
                try {
                    Operations.complete(operation);
                } catch (Throwable t) {
                    Operations.completeLegacy(operation);
                }
                editSession.flushSession();
            } finally {
                try {
                    editSession.close();
                } catch (Throwable ignored) {
                }
            }

            // Collect placed blocks for animation (only schematic's own blocks)
            List<Location> placed = collectPlacedBlocks(world, bounds, originals);

            // Маркер центра (по умолчанию УЛЕЙ): на этом блоке стоит торговец
            Location centerMarker = findCenterMarker(world, location, clipboard, config, placed);

            if (plugin.config().debug()) {
            }

            return new SchematicSnapshot(location, new ArrayList<>(originals.values()), placed, centerMarker);

        } catch (Throwable throwable) {
            if (plugin.config().debug()) {
            }
            return null;
        }
    }

    /**
     * Границы области, которую реально изменит вставка: объём схематики
     * (с учётом поворота) + зона clear-radius + небольшой запас.
     */
    private int[] affectedBounds(World world, Location origin, Clipboard clipboard, SchematicConfig config) {
        int centerX = origin.getBlockX();
        int centerY = origin.getBlockY();
        int centerZ = origin.getBlockZ();
        int clearRadius = config.clearRadius();

        BlockVector3 dim = clipboard.getDimensions();
        double rotation = ((config.rotation() % 360.0D) + 360.0D) % 360.0D;
        boolean swapped = Math.abs(rotation - 90.0D) < 45.0D || Math.abs(rotation - 270.0D) < 45.0D;
        int sizeX = Math.max(1, swapped ? dim.getZ() : dim.getX());
        int sizeZ = Math.max(1, swapped ? dim.getX() : dim.getZ());
        int sizeY = Math.max(1, dim.getY());

        // Границы повёрнутой схематики: прямоугольник dx×dz вращается вокруг
        // своего нижнего угла (якоря), поэтому при произвольном угле (например
        // 45°) он может вылезать и «в минус». Считаем обе стороны вращения.
        double radians = Math.toRadians(rotation);
        int rotMinX = 0, rotMaxX = 0, rotMinZ = 0, rotMaxZ = 0;
        for (int sign = -1; sign <= 1; sign += 2) {
            double a = radians * sign;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            for (double cx : new double[]{0.0D, sizeX}) {
                for (double cz : new double[]{0.0D, sizeZ}) {
                    double rx = cx * cos - cz * sin;
                    double rz = cx * sin + cz * cos;
                    int ix = (int) Math.floor(rx);
                    int iz = (int) Math.floor(rz);
                    if (ix < rotMinX) rotMinX = ix;
                    if (ix > rotMaxX) rotMaxX = ix;
                    if (iz < rotMinZ) rotMinZ = iz;
                    if (iz > rotMaxZ) rotMaxZ = iz;
                }
            }
        }

        // Небольшой запас: на случай вставки «в минус» от якоря и погрешностей.
        int margin = 8;
        int minX = Math.min(centerX + rotMinX, centerX - clearRadius) - margin;
        int maxX = Math.max(centerX + rotMaxX, centerX + clearRadius) + margin;
        int minZ = Math.min(centerZ + rotMinZ, centerZ - clearRadius) - margin;
        int maxZ = Math.max(centerZ + rotMaxZ, centerZ + clearRadius) + margin;

        // clearArea зачищает блоки от центра вверх (+0..+6), схематика — до sizeY.
        int minY = Math.max(SchematicConfig.getMinHeight(world), centerY - 10 - clearRadius);
        int maxY = Math.max(centerY + 6, centerY + sizeY - 1) + margin;
        if (maxY < minY) {
            maxY = minY + 1;
        }
        maxY = Math.min(world.getMaxHeight() - 1, maxY);

        // Жёсткие ограничения объёма (как в старой версии), чтобы даже очень
        // большая схема не выжгла память и не повесила главный поток.
        if (maxX - minX > 80) {
            minX = centerX - 40;
            maxX = centerX + 40;
        }
        if (maxZ - minZ > 80) {
            minZ = centerZ - 40;
            maxZ = centerZ + 40;
        }
        if (maxY - minY > 60) {
            maxY = minY + 60;
        }
        return new int[]{minX, maxX, minY, maxY, minZ, maxZ};
    }

    /**
     * Снимок оригинальных блоков области вставки. Воздух не храним: позиция
     * «до вставки был воздух» определяется отсутствием записи в карте — это
     * убирает сотни тысяч лишних объектов и вызовов getBlockData на спавне.
     */
    private java.util.Map<Long, BlockSnapshot> backupArea(World world, int[] b) {
        java.util.Map<Long, BlockSnapshot> result = new java.util.HashMap<>();
        try {
            int minX = b[0], maxX = b[1], minY = b[2], maxY = b[3], minZ = b[4], maxZ = b[5];
            int minH = SchematicConfig.getMinHeight(world);
            int maxH = world.getMaxHeight();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        if (y < minH || y >= maxH) continue;
                        Block block = world.getBlockAt(x, y, z);
                        Material type = block.getType();
                        if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
                            continue;
                        }
                        BlockSnapshot snap = BlockSnapshot.fromBlock(block);
                        if (snap != null) {
                            result.put(BlockSnapshot.key(x, y, z), snap);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            if (plugin.config().debug()) {
            }
        }
        return result;
    }

    /**
     * Блоки, поставленные схематикой (для анимации и поиска маркера):
     * текущий блок не-воздух и отличается от исходного (или стоял воздух).
     */
    private List<Location> collectPlacedBlocks(World world, int[] b,
                                               java.util.Map<Long, BlockSnapshot> originals) {
        List<Location> result = new ArrayList<>();
        try {
            int minX = b[0], maxX = b[1], minY = b[2], maxY = b[3], minZ = b[4], maxZ = b[5];
            int minH = SchematicConfig.getMinHeight(world);
            int maxH = world.getMaxHeight();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        if (y < minH || y >= maxH) continue;
                        Block current = world.getBlockAt(x, y, z);
                        Material type = current.getType();
                        if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
                            continue;
                        }
                        BlockSnapshot original = originals.get(BlockSnapshot.key(x, y, z));
                        // Блок отличается от исходного — значит его поставила схематика
                        if (original != null && type == original.material()) {
                            continue;
                        }
                        result.add(new Location(world, x + 0.5, y, z + 0.5));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    // ─── Маркер центра схематики ─────────────────────────────────────
    // Автор схемы размечает середину постройки специальным блоком
    // (по умолчанию УЛЕЙ / BEEHIVE — в природе не генерируется, в отличие
    // от BEE_NEST). Торговец ставится ровно НА этот блок.
    //
    // Порядок поиска:
    //  1) среди блоков, поставленных схематикой (обычный случай);
    //  2) если не нашли — по всему объёму, который заняла схема
    //     (на случай, когда маркер совпал с уже стоявшим блоком).
    // Если маркеров несколько — берём ближайший к центру схемы, самый нижний.
    // ─────────────────────────────────────────────────────────────────

    private Location findCenterMarker(World world, Location origin, Clipboard clipboard,
                                      SchematicConfig config, List<Location> placed) {
        Material marker = resolveMarkerMaterial(config);
        if (marker == null || marker.isAir()) {
            return null;
        }
        try {
            // 1) среди поставленных схематикой блоков
            Location best = null;
            double bestScore = Double.MAX_VALUE;
            if (placed != null) {
                for (Location loc : placed) {
                    Block block = world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    if (block.getType() != marker) continue;
                    double score = markerScore(loc, origin);
                    if (score < bestScore) {
                        bestScore = score;
                        best = new Location(world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    }
                }
            }
            if (best != null) {
                return best;
            }

            // 2) по объёму вставки (с учётом поворота 0/90/180/270)
            BlockVector3 dim = clipboard.getDimensions();
            double rotation = ((config.rotation() % 360.0D) + 360.0D) % 360.0D;
            boolean swapped = Math.abs(rotation - 90.0D) < 45.0D || Math.abs(rotation - 270.0D) < 45.0D;
            int sizeX = Math.max(1, (swapped ? dim.getZ() : dim.getX()));
            int sizeZ = Math.max(1, (swapped ? dim.getX() : dim.getZ()));
            int sizeY = Math.max(1, dim.getY());
            int baseX = origin.getBlockX();
            int baseY = origin.getBlockY();
            int baseZ = origin.getBlockZ();
            int minH = SchematicConfig.getMinHeight(world);
            int maxH = world.getMaxHeight() - 1;

            for (int x = baseX; x < baseX + sizeX; x++) {
                for (int z = baseZ; z < baseZ + sizeZ; z++) {
                    for (int y = Math.max(minH, baseY); y <= Math.min(maxH, baseY + sizeY); y++) {
                        if (world.getBlockAt(x, y, z).getType() != marker) continue;
                        Location loc = new Location(world, x, y, z);
                        double score = markerScore(loc, origin);
                        if (score < bestScore) {
                            bestScore = score;
                            best = loc;
                        }
                    }
                }
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    private double markerScore(Location marker, Location origin) {
        // Ближе к центру схемы — лучше; при равенстве — ниже (основание, не крыша)
        double dx = marker.getX() - origin.getX();
        double dz = marker.getZ() - origin.getZ();
        return dx * dx + dz * dz - marker.getY() * 0.01D;
    }

    private Material resolveMarkerMaterial(SchematicConfig config) {
        try {
            Material material = Material.matchMaterial(config.markerMaterial());
            return material == null ? Material.BEEHIVE : material;
        } catch (Throwable t) {
            return Material.BEEHIVE;
        }
    }

    public void restore(SchematicSnapshot snapshot) {
        if (snapshot == null) return;
        try {
            snapshot.restore();
        } catch (Throwable t) {
        }
    }

    private void clearArea(World world, Location location, SchematicConfig config) {
        int radius = config.clearRadius();
        if (radius <= 0) {
            return;
        }
        int minH = SchematicConfig.getMinHeight(world);
        int centerY = location.getBlockY();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 0; y <= 6; y++) {
                    Block block = world.getBlockAt(location.getBlockX() + x, centerY + y, location.getBlockZ() + z);
                    if (block.getY() > world.getMaxHeight() || block.getY() < (minH + 1)) {
                        continue;
                    }
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }
}
