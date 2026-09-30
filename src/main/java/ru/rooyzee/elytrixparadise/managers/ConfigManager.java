package ru.rooyzee.elytrixparadise.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.shards.LootItem;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ConfigManager {

    private final Main plugin;

    private String worldName = "world";
    private int centerX = 0;
    private int centerY = 170;
    private int centerZ = 0;

    private double spawnX = 0.5;
    private double spawnY = 172.0;
    private double spawnZ = 0.5;
    private float spawnYaw = 0.0f;
    private float spawnPitch = 0.0f;

    private String schematicFile = "paradise.schem";
    private boolean pasteOnStartup = true;
    private boolean clearOnDisable = true;
    private boolean ignoreAir = true;
    private int schematicOffsetY = 0;

    private List<String> blockedCommands = new ArrayList<>(Arrays.asList("setwarp", "warp", "sethome", "home", "tp", "tpa", "tpaccept", "spawn"));
    private boolean miningFatigueEnabled = true;
    private int miningFatigueLevel = 2;

    private double actionbarRadiusXZ = 150.0;
    private double actionbarMinY = 0.0;
    private double actionbarMaxY = 256.0;

    private int scanRadiusXZ = 150;
    private int scanMinY = 0;
    private int scanMaxY = 255;

    private int shardCooldownSeconds = 600; // 10 minutes
    private double shardBaseExplosionChance = 2.0;
    private double shardChanceIncreasePerHit = 1.5;
    private double shardMaxExplosionChance = 50.0;
    private double shardExplosionDamage = 24.0;
    private double shardExplosionRadius = 5.5;
    private long shardHitDelayMs = 800;
    private int shardExpMin = 2;
    private int shardExpMax = 6;

    private final List<LootItem> lootItems = new ArrayList<>();

    private String adminPrefix = "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» ";
    private String playerPrefix = "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBРайское место &7» ";

    private FileConfiguration messagesConfig;
    private File messagesFile;

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        worldName = config.getString("location.world", "world");
        centerX = config.getInt("location.x", 0);
        centerY = config.getInt("location.y", 170);
        centerZ = config.getInt("location.z", 0);

        spawnX = config.getDouble("location.spawn.x", 0.5);
        spawnY = config.getDouble("location.spawn.y", 172.0);
        spawnZ = config.getDouble("location.spawn.z", 0.5);
        spawnYaw = (float) config.getDouble("location.spawn.yaw", 0.0);
        spawnPitch = (float) config.getDouble("location.spawn.pitch", 0.0);

        schematicFile = config.getString("schematic.file", "paradise.schem");
        pasteOnStartup = config.getBoolean("schematic.paste-on-startup", true);
        clearOnDisable = config.getBoolean("schematic.clear-on-disable", true);
        ignoreAir = config.getBoolean("schematic.ignore-air", true);
        schematicOffsetY = config.getInt("schematic.offset-y", 0);

        if (config.contains("blocked-commands")) {
            blockedCommands = config.getStringList("blocked-commands");
        }
        miningFatigueEnabled = config.getBoolean("mining-fatigue.enabled", true);
        miningFatigueLevel = config.getInt("mining-fatigue.level", 2);

        actionbarRadiusXZ = config.getDouble("actionbar.radius-xz", 150.0);
        actionbarMinY = config.getDouble("actionbar.min-y", 0.0);
        actionbarMaxY = config.getDouble("actionbar.max-y", 256.0);

        scanRadiusXZ = config.getInt("shards.scan-radius-xz", 150);
        scanMinY = config.getInt("shards.scan-min-y", 0);
        scanMaxY = config.getInt("shards.scan-max-y", 255);

        shardCooldownSeconds = config.getInt("shards.cooldown-seconds", 600);
        shardBaseExplosionChance = config.getDouble("shards.base-explosion-chance", 2.0);
        shardChanceIncreasePerHit = config.getDouble("shards.chance-increase-per-hit", 1.5);
        shardMaxExplosionChance = config.getDouble("shards.max-explosion-chance", 50.0);
        shardExplosionDamage = config.getDouble("shards.explosion-damage", 24.0);
        shardExplosionRadius = config.getDouble("shards.explosion-radius", 5.5);
        shardHitDelayMs = config.getLong("shards.hit-delay-ms", 800);
        shardExpMin = config.getInt("shards.exp-min", 2);
        shardExpMax = config.getInt("shards.exp-max", 6);

        loadLootItems(config);
        loadMessages();
    }

    private void loadLootItems(FileConfiguration config) {
        lootItems.clear();
        ConfigurationSection lootSec = config.getConfigurationSection("shards.loot");
        if (lootSec == null) {
            lootItems.add(new LootItem(Material.DIAMOND, 1, 2, 25.0, "&#F8BEFBРайский Алмаз",
                    Arrays.asList("&#F8BEFB&l┃ &fРедкость: &#F8BEFBРедкий", "&#F8BEFB&l┃ &fИсточник: &#F8BEFBОсколок Рая", "&#F8BEFB&l┃ ", "&7● &fДобыт в Райском месте")));
            lootItems.add(new LootItem(Material.EMERALD, 1, 4, 40.0, "&#F8BEFBНебесный Изумруд",
                    Arrays.asList("&#F8BEFB&l┃ &fРедкость: &#F8BEFBОбычный", "&#F8BEFB&l┃ &fИсточник: &#F8BEFBОсколок Рая", "&#F8BEFB&l┃ ", "&7● &fДобыт в Райском месте")));
            lootItems.add(new LootItem(Material.GOLDEN_APPLE, 1, 1, 15.0, "&#F8BEFBРайское Яблоко",
                    Arrays.asList("&#F8BEFB&l┃ &fРедкость: &#F8BEFBЭпический", "&#F8BEFB&l┃ &fЭффект: &#F8BEFBВосстановление", "&#F8BEFB&l┃ ", "&7● &fДарует небесную защиту")));
            lootItems.add(new LootItem(Material.GOLD_INGOT, 2, 6, 60.0, null, Collections.<String>emptyList()));
            lootItems.add(new LootItem(Material.IRON_INGOT, 3, 8, 70.0, null, Collections.<String>emptyList()));
            lootItems.add(new LootItem(Material.EXPERIENCE_BOTTLE, 2, 5, 50.0, null, Collections.<String>emptyList()));
            return;
        }

        for (String key : lootSec.getKeys(false)) {
            ConfigurationSection itemSec = lootSec.getConfigurationSection(key);
            if (itemSec == null) continue;

            String matName = itemSec.getString("material", "DIAMOND");
            Material mat = Material.matchMaterial(matName);
            if (mat == null) {
                plugin.getLogger().warning("Неизвестный материал в shards.loot: " + matName);
                continue;
            }

            int min = itemSec.getInt("min", 1);
            int max = itemSec.getInt("max", 1);
            double chance = itemSec.getDouble("chance", 10.0);
            String name = itemSec.getString("name", null);
            List<String> lore = itemSec.getStringList("lore");

            lootItems.add(new LootItem(mat, min, max, chance, name, lore));
        }
    }

    public void loadMessages() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream, StandardCharsets.UTF_8)
            );
            messagesConfig.setDefaults(defConfig);
        }

        adminPrefix = messagesConfig.getString("prefix.admin", "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» ");
        playerPrefix = messagesConfig.getString("prefix.player", "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBРайское место &7» ");
    }

    public String getMessage(String path) {
        if (messagesConfig == null) return "";
        return messagesConfig.getString(path, "");
    }

    public List<String> getMessageList(String path) {
        if (messagesConfig == null) return Collections.emptyList();
        return messagesConfig.getStringList(path);
    }

    public Location getCenterLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        return new Location(world, centerX, centerY, centerZ);
    }

    public Location getSpawnLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        return new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
    }

    public void setCenterLocation(Location loc) {
        if (loc == null) return;
        this.worldName = loc.getWorld().getName();
        this.centerX = loc.getBlockX();
        this.centerY = loc.getBlockY();
        this.centerZ = loc.getBlockZ();

        FileConfiguration config = plugin.getConfig();
        config.set("location.world", worldName);
        config.set("location.x", centerX);
        config.set("location.y", centerY);
        config.set("location.z", centerZ);
        plugin.saveConfig();
    }

    public void setSpawnLocation(Location loc) {
        if (loc == null) return;
        this.spawnX = loc.getX();
        this.spawnY = loc.getY();
        this.spawnZ = loc.getZ();
        this.spawnYaw = loc.getYaw();
        this.spawnPitch = loc.getPitch();

        FileConfiguration config = plugin.getConfig();
        config.set("location.spawn.x", spawnX);
        config.set("location.spawn.y", spawnY);
        config.set("location.spawn.z", spawnZ);
        config.set("location.spawn.yaw", (double) spawnYaw);
        config.set("location.spawn.pitch", (double) spawnPitch);
        plugin.saveConfig();
    }

    // Getters
    public String getWorldName() { return worldName; }
    public int getCenterX() { return centerX; }
    public int getCenterY() { return centerY; }
    public int getCenterZ() { return centerZ; }

    public String getSchematicFile() { return schematicFile; }
    public boolean isPasteOnStartup() { return pasteOnStartup; }
    public boolean isClearOnDisable() { return clearOnDisable; }
    public boolean isIgnoreAir() { return ignoreAir; }
    public int getSchematicOffsetY() { return schematicOffsetY; }

    public List<String> getBlockedCommands() { return blockedCommands; }
    public boolean isMiningFatigueEnabled() { return miningFatigueEnabled; }
    public int getMiningFatigueLevel() { return miningFatigueLevel; }

    public double getActionbarRadiusXZ() { return actionbarRadiusXZ; }
    public double getActionbarMinY() { return actionbarMinY; }
    public double getActionbarMaxY() { return actionbarMaxY; }

    public int getScanRadiusXZ() { return scanRadiusXZ; }
    public int getScanMinY() { return scanMinY; }
    public int getScanMaxY() { return scanMaxY; }

    public int getShardCooldownSeconds() { return shardCooldownSeconds; }
    public double getShardBaseExplosionChance() { return shardBaseExplosionChance; }
    public double getShardChanceIncreasePerHit() { return shardChanceIncreasePerHit; }
    public double getShardMaxExplosionChance() { return shardMaxExplosionChance; }
    public double getShardExplosionDamage() { return shardExplosionDamage; }
    public double getShardExplosionRadius() { return shardExplosionRadius; }
    public long getShardHitDelayMs() { return shardHitDelayMs; }
    public int getShardExpMin() { return shardExpMin; }
    public int getShardExpMax() { return shardExpMax; }

    public List<LootItem> getLootItems() { return lootItems; }
    public String getAdminPrefix() { return adminPrefix; }
    public String getPlayerPrefix() { return playerPrefix; }
}
