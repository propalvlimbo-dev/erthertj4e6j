package ru.rooyzee.elytrixtrader.model;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import ru.rooyzee.elytrixtrader.util.Cfg;

import java.util.ArrayList;
import java.util.List;

public class TraderConfig {

    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final String plainName;
    private final String skin;
    private final List<String> worlds;
    private final int lifetimeSeconds;
    private final List<String> hologramLines;
    private final double hologramHeight;
    private final double hologramLineHeight;
    private final boolean hologramEnabled;
    private final IconConfig icon;
    private final IconConfig infoItem;
    private final SchematicConfig schematic;
    private final List<String> tradeIds;
    private final boolean holdItem;
    private final int maxSimultaneous = 1;
    private final int randomTradesCount;
    private final int tradeCooldownSeconds;
    private final String spawnSound;

    public TraderConfig(String id, boolean enabled, String displayName, String plainName, String skin,
                        List<String> worlds, int lifetimeSeconds,
                        List<String> hologramLines, double hologramHeight, double hologramLineHeight,
                        boolean hologramEnabled, IconConfig icon, IconConfig infoItem,
                        SchematicConfig schematic, List<String> tradeIds, boolean holdItem,
                        int randomTradesCount, int tradeCooldownSeconds, String spawnSound) {
        this.id = id;
        this.enabled = enabled;
        this.displayName = displayName;
        this.plainName = plainName;
        this.skin = skin;
        this.worlds = worlds;
        this.lifetimeSeconds = Math.max(60, lifetimeSeconds);
        this.hologramLines = hologramLines;
        this.hologramHeight = hologramHeight;
        this.hologramLineHeight = hologramLineHeight;
        this.hologramEnabled = hologramEnabled;
        this.icon = icon;
        this.infoItem = infoItem;
        this.schematic = schematic;
        this.tradeIds = tradeIds;
        this.holdItem = holdItem;
        this.randomTradesCount = Math.max(0, randomTradesCount);
        this.tradeCooldownSeconds = Math.max(0, tradeCooldownSeconds);
        this.spawnSound = spawnSound;
    }

    public static TraderConfig from(String id, ConfigurationSection section) {
        if (section == null) return null;
        String display = Cfg.str(section, "display-name", Cfg.str(section, "name", id));
        return new TraderConfig(
                id,
                Cfg.bool(section, "enabled", true),
                display,
                ru.rooyzee.elytrixtrader.util.ColorUtil.plain(display),
                Cfg.str(section, "skin", "Steve"),
                Cfg.list(section, "worlds"),
                Cfg.num(section, "lifetime-seconds", 7200),
                Cfg.orEmpty(section.getConfigurationSection("hologram"), "lines", new ArrayList<>()),
                Cfg.dbl(section.getConfigurationSection("hologram"), "height", 2.8D),
                Cfg.dbl(section.getConfigurationSection("hologram"), "line-height", 0.30D),
                Cfg.bool(section.getConfigurationSection("hologram"), "enabled", true),
                IconConfig.from(section.getConfigurationSection("icon"), Material.EMERALD),
                IconConfig.from(section.getConfigurationSection("info-item"), Material.PAPER),
                SchematicConfig.from(section.getConfigurationSection("schematic")),
                Cfg.list(section, "trades"),
                Cfg.bool(section, "hold-item", true),
                Cfg.num(section, "random-trades", 14),
                Cfg.num(section, "trade-cooldown-seconds", 60),
                Cfg.str(section, "spawn-sound", "")
        );
    }

    public String id() { return id; }
    public boolean enabled() { return enabled; }
    public String displayName() { return displayName; }
    public String plainName() { return plainName; }
    public String skin() { return skin; }
    public List<String> worlds() { return worlds; }
    public int lifetimeSeconds() { return lifetimeSeconds; }
    public List<String> hologramLines() { return hologramLines; }
    public double hologramHeight() { return hologramHeight; }
    public double hologramLineHeight() { return hologramLineHeight; }
    public boolean hologramEnabled() { return hologramEnabled; }
    public IconConfig icon() { return icon; }
    public IconConfig infoItem() { return infoItem; }
    public SchematicConfig schematic() { return schematic; }
    public List<String> tradeIds() { return tradeIds; }
    public boolean holdItem() { return holdItem; }
    public int maxSimultaneous() { return maxSimultaneous; }
    public int randomTradesCount() { return randomTradesCount; }
    public int tradeCooldownSeconds() { return tradeCooldownSeconds; }
    public String spawnSound() { return spawnSound; }
}