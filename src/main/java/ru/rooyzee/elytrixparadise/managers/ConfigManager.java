package ru.rooyzee.elytrixparadise.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private final Main plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private File messagesFile;

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);

        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            this.messages.setDefaults(defConfig);
        }
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        if (messagesFile != null && messagesFile.exists()) {
            this.messages = YamlConfiguration.loadConfiguration(messagesFile);
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public String getWorldName() {
        return config.getString("location.world", "world");
    }

    public World getWorld() {
        String name = getWorldName();
        World w = Bukkit.getWorld(name);
        return w != null ? w : Bukkit.getWorlds().get(0);
    }

    public int getCenterX() {
        return config.getInt("location.x", 0);
    }

    public int getCenterY() {
        return config.getInt("location.y", 130);
    }

    public int getCenterZ() {
        return config.getInt("location.z", 0);
    }

    public Location getCenterLocation() {
        return new Location(getWorld(), getCenterX(), getCenterY(), getCenterZ());
    }

    public Location getSafeSpawnLocation() {
        double x = config.getDouble("location.spawn.x", getCenterX() + 0.5);
        double y = config.getDouble("location.spawn.y", getCenterY() + 2.0);
        double z = config.getDouble("location.spawn.z", getCenterZ() + 0.5);
        float yaw = (float) config.getDouble("location.spawn.yaw", 0.0);
        float pitch = (float) config.getDouble("location.spawn.pitch", 0.0);
        return new Location(getWorld(), x, y, z, yaw, pitch);
    }

    public void setCenterLocation(Location loc) {
        config.set("location.world", loc.getWorld().getName());
        config.set("location.x", loc.getBlockX());
        config.set("location.y", loc.getBlockY());
        config.set("location.z", loc.getBlockZ());
        plugin.saveConfig();
    }

    public void setSpawnLocation(Location loc) {
        config.set("location.spawn.x", loc.getX());
        config.set("location.spawn.y", loc.getY());
        config.set("location.spawn.z", loc.getZ());
        config.set("location.spawn.yaw", loc.getYaw());
        config.set("location.spawn.pitch", loc.getPitch());
        plugin.saveConfig();
    }

    public String getSchematicFile() {
        return config.getString("schematic.file", "paradise.schem");
    }

    public boolean isPasteOnStartup() {
        return config.getBoolean("schematic.paste-on-startup", true);
    }

    public boolean isIgnoreAir() {
        return config.getBoolean("schematic.ignore-air", true);
    }

    public int getSchematicOffsetY() {
        return config.getInt("schematic.offset-y", 0);
    }

    public String getPrefix() {
        return ColorUtil.colorize(messages.getString("prefix", "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &#FFFFA0Рай &7» "));
    }

    public String getMessage(String path) {
        return getMessage(path, "");
    }

    public String getMessage(String path, String def) {
        String msg = messages.getString(path, def);
        if (msg == null || msg.isEmpty()) return "";
        return ColorUtil.colorize(msg.replace("%prefix%", getPrefix()));
    }

    public List<String> getMessageList(String path) {
        List<String> raw = messages.getStringList(path);
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        List<String> result = new ArrayList<>(raw.size());
        String prefix = getPrefix();
        for (String line : raw) {
            result.add(ColorUtil.colorize(line.replace("%prefix%", prefix)));
        }
        return result;
    }
}
