package ru.rooyzee.elytrixtrader.model;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import ru.rooyzee.elytrixtrader.util.Cfg;
import ru.rooyzee.elytrixtrader.util.ColorUtil;

import java.util.List;

public class IconConfig {

    private final Material material;
    private final int amount;
    private final boolean glow;
    private final int customModelData;
    private final String displayName;
    private final List<String> lore;
    private final String skin;
    private final String sound;
    private final int slot;

    public IconConfig(Material material, int amount, boolean glow, int customModelData, String displayName,
                      List<String> lore, String skin, String sound, int slot) {
        this.material = material;
        this.amount = Math.max(1, Math.min(64, amount));
        this.glow = glow;
        this.customModelData = customModelData;
        this.displayName = displayName;
        this.lore = lore;
        this.skin = skin;
        this.sound = sound;
        this.slot = slot;
    }

    public static IconConfig from(ConfigurationSection section, Material fallback) {
        return from(section, fallback, 1, null, null);
    }

    public static IconConfig from(ConfigurationSection section, Material fallback, int fallbackAmount,
                                  String fallbackName, List<String> fallbackLore) {
        if (section == null) {
            return new IconConfig(fallback, fallbackAmount, false, 0, fallbackName, fallbackLore, null, null, -1);
        }
        Material material = ColorUtil.material(Cfg.str(section, "material", fallback.name()), fallback);
        return new IconConfig(
                material,
                Cfg.num(section, "amount", fallbackAmount),
                Cfg.bool(section, "glow", false),
                Cfg.num(section, "custom-model-data", 0),
                Cfg.str(section, "display-name", fallbackName),
                Cfg.orEmpty(section, "lore", fallbackLore),
                Cfg.str(section, "skin", null),
                Cfg.str(section, "sound", null),
                Cfg.num(section, "slot", -1)
        );
    }

    public Material material() {
        return material;
    }

    public int amount() {
        return amount;
    }

    public boolean glow() {
        return glow;
    }

    public int customModelData() {
        return customModelData;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> lore() {
        return lore;
    }

    public String skin() {
        return skin;
    }

    public String sound() {
        return sound;
    }

    public int slot() {
        return slot;
    }
}
