package ru.rooyzee.elytrixtrader.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import ru.rooyzee.elytrixtrader.util.Cfg;

public class SchematicConfig {

    private final String file;
    private final double rotation;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;
    private final boolean enabled;
    private final boolean skipAir;
    private final boolean pasteEntities;
    private final int clearRadius;
    private final String markerMaterial;

    public SchematicConfig(String file, double rotation, int offsetX, int offsetY, int offsetZ,
                           boolean enabled, boolean skipAir, boolean pasteEntities, int clearRadius,
                           String markerMaterial) {
        this.file = file;
        this.rotation = rotation;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.enabled = enabled;
        this.skipAir = skipAir;
        this.pasteEntities = pasteEntities;
        this.clearRadius = clearRadius;
        this.markerMaterial = markerMaterial;
    }

    public static SchematicConfig from(ConfigurationSection section) {
        return new SchematicConfig(
                Cfg.str(section, "file", null),
                Cfg.dbl(section, "rotation", 0.0D),
                Cfg.num(section, "offset-x", 0),
                Cfg.num(section, "offset-y", 0),
                Cfg.num(section, "offset-z", 0),
                Cfg.bool(section, "enabled", true) && Cfg.str(section, "file", null) != null,
                Cfg.bool(section, "skip-air", false),
                Cfg.bool(section, "paste-entities", false),
                Cfg.num(section, "clear-radius", 0),
                Cfg.str(section, "marker-material", "BEEHIVE")
        );
    }

    public String file() {
        return file;
    }

    public double rotation() {
        return rotation;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean skipAir() {
        return skipAir;
    }

    public boolean pasteEntities() {
        return pasteEntities;
    }

    public int clearRadius() {
        return Math.max(0, clearRadius);
    }

    /** Материал блока-маркера центра схематики (на нём стоит торговец). */
    public String markerMaterial() {
        return markerMaterial == null || markerMaterial.trim().isEmpty() ? "BEEHIVE" : markerMaterial.trim();
    }

    public Location apply(Location location) {
        if (location == null) {
            return null;
        }
        World world = location.getWorld();
        int minH = getMinHeight(world);
        return new Location(world,
                location.getBlockX() + offsetX,
                Math.max(minH, location.getBlockY() + offsetY),
                location.getBlockZ() + offsetZ,
                location.getYaw(),
                location.getPitch());
    }

    public Location anchor(Location location) {
        if (location == null) {
            return null;
        }
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                (float) rotation, 0.0F);
    }

    /** Кеш минимальной высоты мира (ключ — имя мира). */
    private static final java.util.Map<String, Integer> MIN_HEIGHTS = new java.util.concurrent.ConcurrentHashMap<>();

    public static int getMinHeight(World world) {
        if (world == null) {
            return 0;
        }
        Integer cached = MIN_HEIGHTS.get(world.getName());
        if (cached != null) {
            return cached;
        }
        int minHeight = 0;
        try {
            // getMethod/invoke в горячих циклах (сотни тысяч вызовов на спавн
            // схематики) — дорого, поэтому результат кешируем по имени мира.
            java.lang.reflect.Method method = world.getClass().getMethod("getMinHeight");
            Object result = method.invoke(world);
            if (result instanceof Number) {
                minHeight = ((Number) result).intValue();
            }
        } catch (Throwable ignored) {
        }
        MIN_HEIGHTS.put(world.getName(), minHeight);
        return minHeight;
    }
}
