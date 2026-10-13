package ru.rooyzee.elytrixairdrop.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.utils.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ConfigManager {

    private final Main plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private File messagesFile;

    public ConfigManager(Main plugin) { this.plugin = plugin; }

    public void load() {
        plugin.reloadConfig();
        config = plugin.getConfig();

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) plugin.saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getMessages() { return messages; }

    public String getPrefix() {
        return ColorUtil.colorize(messages.getString("prefix", ""));
    }

    public String getMessage(String path) {
        String s = messages.getString(path, "&cmessage not found: " + path);
        s = s.replace("%prefix%", messages.getString("prefix", ""));
        return ColorUtil.colorize(s);
    }

    public List<String> getMessageList(String path) {
        return ColorUtil.colorize(messages.getStringList(path));
    }

    public void saveConfig() { plugin.saveConfig(); }

    public void saveMessages() {
        try { messages.save(messagesFile); } catch (IOException e) { e.printStackTrace(); }
    }
}