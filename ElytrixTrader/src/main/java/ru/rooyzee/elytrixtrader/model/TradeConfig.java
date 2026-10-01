package ru.rooyzee.elytrixtrader.model;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixtrader.util.Cfg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TradeConfig {

    private final String id;
    private final String displayName;
    private final List<String> lore;
    private final IconConfig icon;
    private final List<CostConfig> costs;
    private final List<ItemConfig> receive;
    private final List<String> commands;
    private final List<String> messages;
    private final String sound;
    private final int maxPerTrade;
    private final int stock;
    private final int stockMin;
    private final int stockMax;
    private final String requiredPermission;
    private final int requiredLevel;
    private final List<String> worlds;
    private final boolean enabled;
    /**
     * Готовые ItemStack с полным NBT (зелья, зачарования и т.д.).
     * Если не пусто — PurchaseService выдаёт их клоны напрямую, игнорируя receive.
     * Заполняется из EditableTrade.toTradeConfig().
     */
    private final List<ItemStack> rawRewards;

    /** Конструктор для трейдов из YAML (без rawRewards). */
    public TradeConfig(String id, String displayName, List<String> lore, IconConfig icon, List<CostConfig> costs,
                       List<ItemConfig> receive, List<String> commands, List<String> messages, String sound,
                       int maxPerTrade, int stock, String requiredPermission, int requiredLevel,
                       List<String> worlds, boolean enabled) {
        this(id, displayName, lore, icon, costs, receive, commands, messages, sound,
                maxPerTrade, stock, stock, stock, requiredPermission, requiredLevel, worlds, enabled,
                Collections.emptyList());
    }

    /** Конструктор для трейдов из редактора (с stockMin/stockMax, без rawRewards). */
    public TradeConfig(String id, String displayName, List<String> lore, IconConfig icon, List<CostConfig> costs,
                       List<ItemConfig> receive, List<String> commands, List<String> messages, String sound,
                       int maxPerTrade, int stock, int stockMin, int stockMax, String requiredPermission,
                       int requiredLevel, List<String> worlds, boolean enabled) {
        this(id, displayName, lore, icon, costs, receive, commands, messages, sound,
                maxPerTrade, stock, stockMin, stockMax, requiredPermission, requiredLevel, worlds, enabled,
                Collections.emptyList());
    }

    /** Полный конструктор. */
    public TradeConfig(String id, String displayName, List<String> lore, IconConfig icon, List<CostConfig> costs,
                       List<ItemConfig> receive, List<String> commands, List<String> messages, String sound,
                       int maxPerTrade, int stock, int stockMin, int stockMax, String requiredPermission,
                       int requiredLevel, List<String> worlds, boolean enabled,
                       List<ItemStack> rawRewards) {
        this.id = id;
        this.displayName = displayName;
        this.lore = lore;
        this.icon = icon;
        this.costs = costs;
        this.receive = receive;
        this.commands = commands;
        this.messages = messages;
        this.sound = sound;
        this.maxPerTrade = maxPerTrade;
        this.stock = stock;
        this.stockMin = stockMin;
        this.stockMax = stockMax;
        this.requiredPermission = requiredPermission;
        this.requiredLevel = requiredLevel;
        this.worlds = worlds;
        this.enabled = enabled;
        this.rawRewards = rawRewards == null ? new ArrayList<>() : new ArrayList<>(rawRewards);
    }

    public static TradeConfig from(String id, ConfigurationSection section) {
        List<CostConfig> costs = new ArrayList<>();
        List<ItemConfig> receive = new ArrayList<>();
        for (ConfigurationSection entry : Cfg.entries(section, "cost")) {
            costs.add(CostConfig.from(entry));
        }
        for (ConfigurationSection entry : Cfg.entries(section, "receive")) {
            receive.add(ItemConfig.from(entry, Material.DIAMOND, 1));
        }
        String perm = Cfg.str(section, "require-permission", Cfg.str(section, "permission", null));
        if (perm != null && perm.trim().isEmpty()) {
            perm = null;
        }
        int reqLevel = Cfg.num(section, "require-level", Cfg.num(section, "required-level", 0));
        return new TradeConfig(
                id,
                Cfg.str(section, "display-name", Cfg.str(section, "name", id)),
                Cfg.list(section, "lore"),
                IconConfig.from(section == null ? null : section.getConfigurationSection("icon"), Material.DIAMOND),
                costs,
                receive,
                Cfg.list(section, "commands"),
                Cfg.list(section, "messages"),
                Cfg.str(section, "sound", "ENTITY_EXPERIENCE_ORB_PICKUP"),
                Cfg.num(section, "max-per-trade", 1),
                Cfg.num(section, "stock", -1),
                perm,
                reqLevel,
                Cfg.list(section, "worlds"),
                Cfg.bool(section, "enabled", true)
        );
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public List<String> lore() { return lore; }
    public IconConfig icon() { return icon; }
    public List<CostConfig> costs() { return costs; }
    public List<ItemConfig> receive() { return receive; }
    public List<ItemStack> rawRewards() { return rawRewards; }
    public List<String> commands() { return commands; }
    public List<String> messages() { return messages; }
    public String sound() { return sound; }
    public int maxPerTrade() { return Math.max(1, maxPerTrade); }
    public int stock() { return stock; }
    public int stockMin() { return stockMin; }
    public int stockMax() { return stockMax; }

    /** Первый rawItem из receive (иконка для GUI), или null. */
    public ItemStack firstRawItem() {
        // Сначала из rawRewards
        for (ItemStack is : rawRewards) {
            if (is != null && is.getType() != Material.AIR) return is;
        }
        // Потом из ItemConfig.rawItem()
        for (ItemConfig ic : receive) {
            ItemStack raw = ic.rawItem();
            if (raw != null && raw.getType() != Material.AIR) return raw;
        }
        return null;
    }

    /** Рандомный сток из диапазона stockMin..stockMax. */
    public int randomStock(java.util.Random rng) {
        if (stockMin < 0) return -1;
        if (stockMax <= stockMin) return stockMin;
        return stockMin + rng.nextInt(stockMax - stockMin + 1);
    }

    public String requiredPermission() { return requiredPermission; }
    public int requiredLevel() { return requiredLevel; }
    public List<String> worlds() { return worlds; }
    public boolean enabled() { return enabled; }

    public boolean hasCost(CostConfig.Type type) {
        for (CostConfig cost : costs) {
            if (cost.type() == type) return true;
        }
        return false;
    }
}
