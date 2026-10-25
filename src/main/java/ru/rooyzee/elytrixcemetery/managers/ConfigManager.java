package ru.rooyzee.elytrixcemetery.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private final Main plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private File messagesFile;

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
        loadAll();
    }

    public void loadAll() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void reload() {
        loadAll();
    }

    public Location getCenterLocation() {
        String worldName = config.getString("center.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        int x = config.getInt("center.x", 0);
        int y = config.getInt("center.y", 70);
        int z = config.getInt("center.z", 0);
        return world != null ? new Location(world, x, y, z) : null;
    }

    public int getBoundaryRadius() {
        return config.getInt("boundary-radius", 50);
    }

    public int getRefillIntervalSeconds() {
        return config.getInt("refill-interval-seconds", 1800); // 30 минут
    }

    public int getGhostCount() {
        return config.getInt("ghosts.count", 8);
    }

    public double getGhostHealth() {
        return config.getDouble("ghosts.health", 16.0);
    }

    public int getGhostRespawnCooldownSeconds() {
        return config.getInt("ghosts.respawn-cooldown-seconds", 60);
    }

    public boolean isGraveHologramsEnabled() {
        return config.getBoolean("grave-holograms-enabled", true);
    }

    public String getAdminPrefix() {
        return messages.getString("prefix.admin", "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» ");
    }

    public String getPlayerPrefix() {
        return messages.getString("prefix.player", "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» ");
    }

    public List<String> getMessageList(String path) {
        List<String> list = messages.getStringList(path);
        List<String> colored = new ArrayList<>();
        for (String s : list) {
            colored.add(ColorUtil.colorize(s));
        }
        return colored;
    }
}
