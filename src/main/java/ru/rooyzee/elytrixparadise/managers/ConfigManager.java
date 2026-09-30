package ru.rooyzee.elytrixparadise.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.shards.LootItem;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private double actionbarRadiusXZ = 45.0;
    private double actionbarMinY = 150.0;
    private double actionbarMaxY = 195.0;

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
    private String playerPrefix = "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &#FFFFA0Райское место &7» ";

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

        actionbarRadiusXZ = config.getDouble("actionbar.radius-xz", 45.0);
        actionbarMinY = config.getDouble("actionbar.min-y", 150.0);
        actionbarMaxY = config.getDouble("actionbar.max-y", 195.0);

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
        ConfigurationSection section = config.getConfigurationSection("shards.loot");
        if (section == null) {
            addDefaultLoot();
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSec = section.getConfigurationSection(key);
            if (itemSec == null) continue;

            String matName = itemSec.getString("material", "DIAMOND");
            Material mat = Material.matchMaterial(matName);
            if (mat == null) mat = Material.DIAMOND;

            int min = itemSec.getInt("min", 1);
            int max = itemSec.getInt("max", 1);
            double chance = itemSec.getDouble("chance", 20.0);
            String name = itemSec.getString("name", "");
            List<String> lore = itemSec.getStringList("lore");

            Map<Enchantment, Integer> enchants = new HashMap<>();
            ConfigurationSection enchSec = itemSec.getConfigurationSection("enchantments");
            if (enchSec != null) {
                for (String enchKey : enchSec.getKeys(false)) {
                    Enchantment ench = Enchantment.getByName(enchKey.toUpperCase());
                    if (ench != null) {
                        enchants.put(ench, enchSec.getInt(enchKey, 1));
                    }
                }
            }

            lootItems.add(new LootItem(mat, min, max, chance, name, lore, enchants));
        }

        if (lootItems.isEmpty()) {
            addDefaultLoot();
        }
    }

    private void addDefaultLoot() {
        lootItems.add(new LootItem(Material.DIAMOND, 1, 2, 25.0, "&#FFFFA0✦ Райский Алмаз", null, null));
        lootItems.add(new LootItem(Material.EMERALD, 1, 4, 40.0, "&#A0FFA0✦ Небесный Изумруд", null, null));
        lootItems.add(new LootItem(Material.GOLD_INGOT, 2, 6, 60.0, null, null, null));
        lootItems.add(new LootItem(Material.IRON_INGOT, 3, 8, 70.0, null, null, null));
        lootItems.add(new LootItem(Material.GOLDEN_APPLE, 1, 1, 15.0, "&#F8BEFB✦ Райское Яблоко", null, null));
        lootItems.add(new LootItem(Material.EXPERIENCE_BOTTLE, 2, 5, 50.0, null, null, null));
    }

    private void loadMessages() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        try (InputStreamReader defStream = new InputStreamReader(plugin.getResource("messages.yml"), StandardCharsets.UTF_8)) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defStream);
            messagesConfig.setDefaults(defConfig);
        } catch (Exception ignored) {}

        adminPrefix = messagesConfig.getString("prefix.admin", "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» ");
        playerPrefix = messagesConfig.getString("prefix.player", "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &#FFFFA0Райское место &7» ");
    }

    public String getMessage(String path) {
        if (messagesConfig == null) return path;
        String msg = messagesConfig.getString(path, path);
        return ColorUtil.colorize(msg
                .replace("%admin_prefix%", adminPrefix)
                .replace("%player_prefix%", playerPrefix)
                .replace("%prefix%", adminPrefix));
    }

    public List<String> getMessageList(String path) {
        if (messagesConfig == null) return new ArrayList<>();
        List<String> list = messagesConfig.getStringList(path);
        List<String> colored = new ArrayList<>(list.size());
        for (String line : list) {
            colored.add(ColorUtil.colorize(line
                    .replace("%admin_prefix%", adminPrefix)
                    .replace("%player_prefix%", playerPrefix)
                    .replace("%prefix%", adminPrefix)));
        }
        return colored;
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
        this.worldName = loc.getWorld().getName();
        this.centerX = loc.getBlockX();
        this.centerY = loc.getBlockY();
        this.centerZ = loc.getBlockZ();
        plugin.getConfig().set("location.world", worldName);
        plugin.getConfig().set("location.x", centerX);
        plugin.getConfig().set("location.y", centerY);
        plugin.getConfig().set("location.z", centerZ);
        plugin.saveConfig();
    }

    public void setSpawnLocation(Location loc) {
        this.spawnX = loc.getX();
        this.spawnY = loc.getY();
        this.spawnZ = loc.getZ();
        this.spawnYaw = loc.getYaw();
        this.spawnPitch = loc.getPitch();
        plugin.getConfig().set("location.spawn.x", spawnX);
        plugin.getConfig().set("location.spawn.y", spawnY);
        plugin.getConfig().set("location.spawn.z", spawnZ);
        plugin.getConfig().set("location.spawn.yaw", spawnYaw);
        plugin.getConfig().set("location.spawn.pitch", spawnPitch);
        plugin.saveConfig();
    }

    public String getWorldName() { return worldName; }
    public int getCenterX() { return centerX; }
    public int getCenterY() { return centerY; }
    public int getCenterZ() { return centerZ; }
    public String getSchematicFile() { return schematicFile; }
    public boolean isPasteOnStartup() { return pasteOnStartup; }
    public boolean isClearOnDisable() { return clearOnDisable; }
    public boolean isIgnoreAir() { return ignoreAir; }
    public int getSchematicOffsetY() { return schematicOffsetY; }
    public double getActionbarRadiusXZ() { return actionbarRadiusXZ; }
    public double getActionbarMinY() { return actionbarMinY; }
    public double getActionbarMaxY() { return actionbarMaxY; }
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
