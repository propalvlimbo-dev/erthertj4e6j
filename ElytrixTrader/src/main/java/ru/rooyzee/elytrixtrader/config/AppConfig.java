package ru.rooyzee.elytrixtrader.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.rooyzee.elytrixtrader.util.Cfg;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {

    // === CORE (из config.yml) ===
    private final int lifetimeSeconds;
    private final boolean scheduleEnabled;
    private final List<String> scheduleTimes;
    private final int minRadius;
    private final int maxRadius;
    private final double interactionRadius;
    private final int viewDistance;
    private final boolean debug;
    private final boolean auraEnabled;
    private final boolean placeholderApi;

    // === DEFAULT SOUNDS (можно переопределить в config.yml) ===
    private final String spawnSound;
    private final String despawnSound;
    private final boolean broadcastDespawn;
    private final String eventSound;
    private final boolean broadcastSpawn;
    private final boolean persistCooldowns = true;
    private final String schematicFolder = "schematics";
    private final boolean hologramEnabled = true;
    private final int hologramUpdateInterval = 10;
    private final boolean asyncSkins = true;
    private final int skinCacheSeconds = 86400;
    private final String fallbackSkin = "Steve";
    private final boolean openOnAttack = false;
    private final String purchaseSound = "ENTITY_EXPERIENCE_ORB_PICKUP";
    private final String denySound = "BLOCK_NOTE_BLOCK_BASS";
    private final String openSound = "ENTITY_EXPERIENCE_ORB_PICKUP";
    private final boolean broadcastPurchase = false;
    private final int purchaseBroadcastThreshold = 1;
    private final String coinsPlaceholder = "%playerpoints_points%";
    private final String coinsSpendCommand = "eco take {player} {amount}";

    // Fixed passable blocks list
    private final List<String> passableBlocks = defaultPassable();

    public AppConfig(FileConfiguration config) {
        ConfigurationSection schedule = config.getConfigurationSection("schedule");

        scheduleEnabled = Cfg.bool(schedule, "enabled", true);
        scheduleTimes = Cfg.orEmpty(schedule, "times", defaultSchedule());
        lifetimeSeconds = Math.max(60, Cfg.num(config, "lifetime-seconds", 7200));
        minRadius = Math.max(32, Cfg.num(config, "min-radius", 450));
        maxRadius = Math.max(minRadius + 16, Cfg.num(config, "max-radius", 1400));
        interactionRadius = Math.max(2.0D, Cfg.dbl(config, "interaction-radius", 5.0D));
        viewDistance = Math.max(4, Math.min(16, Cfg.num(config, "view-distance", 10)));
        debug = Cfg.bool(config, "debug", false);
        auraEnabled = Cfg.bool(config.getConfigurationSection("aura"), "enabled", true);
        spawnSound = Cfg.str(config, "spawn-sound", "ENTITY_PLAYER_LEVELUP");
        despawnSound = Cfg.str(config, "despawn-sound", "BLOCK_BEACON_DEACTIVATE");
        broadcastDespawn = Cfg.bool(config, "broadcast-despawn", true);
        eventSound = Cfg.str(config, "event-sound", "UI_TOAST_CHALLENGE_COMPLETE");
        broadcastSpawn = Cfg.bool(config, "broadcast-spawn", true);
        placeholderApi = Cfg.bool(config, "placeholderapi", true)
                && org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    private static List<String> defaultSchedule() {
        List<String> result = new ArrayList<>();
        result.add("00:00"); result.add("04:00"); result.add("08:00");
        result.add("12:00"); result.add("16:00"); result.add("20:00");
        return result;
    }

    private static List<String> defaultPassable() {
        List<String> r = new ArrayList<>();
        r.add("AIR"); r.add("CAVE_AIR"); r.add("VOID_AIR");
        r.add("WATER"); r.add("LAVA");
        r.add("GRASS"); r.add("FERN"); r.add("TALL_GRASS"); r.add("LARGE_FERN");
        r.add("SNOW"); r.add("DEAD_BUSH"); r.add("ROSE_BUSH"); r.add("PEONY");
        r.add("DANDELION"); r.add("POPPY"); r.add("SEAGRASS"); r.add("KELP");
        return r;
    }

    // === Getters ===
    public int lifetimeSeconds() { return lifetimeSeconds; }
    public boolean scheduleEnabled() { return scheduleEnabled; }
    public List<String> scheduleTimes() { return scheduleTimes; }
    public int minRadius() { return minRadius; }
    public int maxRadius() { return maxRadius; }
    public double interactionRadius() { return interactionRadius; }
    public int viewDistance() { return viewDistance; }
    public boolean debug() { return debug; }
    public boolean auraEnabled() { return auraEnabled; }
    public boolean placeholderApi() { return placeholderApi; }

    public boolean persistCooldowns() { return persistCooldowns; }
    public boolean broadcastSpawn() { return broadcastSpawn; }
    public boolean broadcastDespawn() { return broadcastDespawn; }
    public String spawnSound() { return spawnSound; }
    public String despawnSound() { return despawnSound; }
    public String eventSound() { return eventSound; }
    public boolean hologramEnabled() { return hologramEnabled; }
    public int hologramUpdateInterval() { return hologramUpdateInterval; }
    public boolean openOnAttack() { return openOnAttack; }
    public boolean asyncSkins() { return asyncSkins; }
    public int skinCacheSeconds() { return skinCacheSeconds; }
    public String fallbackSkin() { return fallbackSkin; }
    public String schematicFolder() { return schematicFolder; }
    public String purchaseSound() { return purchaseSound; }
    public String denySound() { return denySound; }
    public String openSound() { return openSound; }
    public boolean broadcastPurchase() { return broadcastPurchase; }
    public int purchaseBroadcastThreshold() { return purchaseBroadcastThreshold; }
    public String coinsPlaceholder() { return coinsPlaceholder; }
    public String coinsSpendCommand() { return coinsSpendCommand; }
    public List<String> passableBlocks() { return passableBlocks; }
}