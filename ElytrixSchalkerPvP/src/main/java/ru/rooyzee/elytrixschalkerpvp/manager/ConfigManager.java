package ru.rooyzee.elytrixschalkerpvp.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixschalkerpvp.Main;
import ru.rooyzee.elytrixschalkerpvp.model.Rarity;
import ru.rooyzee.elytrixschalkerpvp.util.ColorUtil;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigManager {

    private final Main plugin;
    private FileConfiguration config;

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public int getMinSleepMinutes() {
        return config.getInt("settings.min-sleep-minutes", 15);
    }

    public int getMaxSleepMinutes() {
        return config.getInt("settings.max-sleep-minutes", 30);
    }

    public int getLootTimeSeconds() {
        return config.getInt("settings.loot-time-seconds", 60);
    }

    public double getExplosionRadius() {
        return config.getDouble("settings.explosion-radius", 6.0);
    }

    public double getExplosionDamage() {
        return config.getDouble("settings.explosion-damage", 16.0);
    }

    public int getRarityChance(Rarity rarity) {
        return config.getInt("rarity-chances." + rarity.name(), 50);
    }

    public String getRarityName(Rarity rarity) {
        return config.getString("rarity-display." + rarity.name() + ".name", rarity.name());
    }

    public String getRarityColor(Rarity rarity) {
        return config.getString("rarity-display." + rarity.name() + ".color", "&7");
    }

    public String getRarityHexColor(Rarity rarity) {
        return config.getString("rarity-display." + rarity.name() + ".hex-color", "&#AAAAAA");
    }

    public int getInventorySize(Rarity rarity) {
        return config.getInt("inventory-sizes." + rarity.name(), 27);
    }

    public int getLootFillMin(Rarity rarity) {
        return config.getInt("loot-fill-percent." + rarity.name() + ".min", 20);
    }

    public int getLootFillMax(Rarity rarity) {
        return config.getInt("loot-fill-percent." + rarity.name() + ".max", 50);
    }

    public String getMessage(String key) {
        String prefix = ColorUtil.colorize(config.getString("messages.prefix", ""));
        String message = ColorUtil.colorize(config.getString("messages." + key, "&cMessage not found: " + key));
        return prefix + " " + message;
    }

    public String getRawMessage(String key) {
        return ColorUtil.colorize(config.getString("messages." + key, "&cMessage not found: " + key));
    }

    public List<String> getHologramLines(String state) {
        List<String> lines = config.getStringList("hologram." + state);
        return lines.stream().map(ColorUtil::colorize).collect(Collectors.toList());
    }

    public FileConfiguration getConfig() {
        return config;
    }
}