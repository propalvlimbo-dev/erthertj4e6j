package ru.rooyzee.elytrixtrader;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.rooyzee.elytrixtrader.command.ElytrixTraderCommand;
import ru.rooyzee.elytrixtrader.config.AppConfig;
import ru.rooyzee.elytrixtrader.config.ConfigLoader;
import ru.rooyzee.elytrixtrader.config.GuiConfig;
import ru.rooyzee.elytrixtrader.config.Messages;
import ru.rooyzee.elytrixtrader.currency.CurrencyService;
import ru.rooyzee.elytrixtrader.currency.PurchaseService;
import ru.rooyzee.elytrixtrader.editor.TradeEditorManager;
import ru.rooyzee.elytrixtrader.expansion.TraderExpansion;
import ru.rooyzee.elytrixtrader.gui.BmenuSupport;
import ru.rooyzee.elytrixtrader.gui.TradeMenuGui;
import ru.rooyzee.elytrixtrader.interaction.InteractionManager;
import ru.rooyzee.elytrixtrader.listener.RegionProtectionListener;
import ru.rooyzee.elytrixtrader.listener.TradeListener;
import ru.rooyzee.elytrixtrader.model.TradeConfig;
import ru.rooyzee.elytrixtrader.model.TradeRegistry;
import ru.rooyzee.elytrixtrader.model.TraderConfig;
import ru.rooyzee.elytrixtrader.persistence.CooldownStore;
import ru.rooyzee.elytrixtrader.schematic.SchematicService;
import ru.rooyzee.elytrixtrader.skin.SkinService;
import ru.rooyzee.elytrixtrader.trader.TraderAnnouncer;
import ru.rooyzee.elytrixtrader.trader.TraderManager;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Main extends JavaPlugin {

    public static Main instance;

    private ConfigLoader loader;
    private AppConfig config;
    private Messages messages;
    private GuiConfig gui;
    private Map<String, TraderConfig> traderConfigs = new LinkedHashMap<>();
    private TradeRegistry trades = new TradeRegistry();
    private SkinService skins;
    private SchematicService schematics;
    private CurrencyService currency;
    private PurchaseService purchases;
    private CooldownStore cooldowns;
    private TraderManager traders;
    private TraderAnnouncer announcer;
    private InteractionManager interactions;
    private TradeMenuGui menus;
    private BmenuSupport bmenu;
    private TradeEditorManager tradeEditor;
    private TraderExpansion expansion;

    @Override
    public void onEnable() {
        instance = this;
        loader = new ConfigLoader(this);
        config = loader.loadMain();
        messages = loader.loadMessages();
        gui = loader.loadGui();
        trades = loader.loadTrades();
        traderConfigs = loader.loadTraders(config);
        skins = new SkinService(this);
        schematics = new SchematicService(this);
        currency = new CurrencyService(this);
        currency.setup();
        purchases = new PurchaseService(this);
        cooldowns = new CooldownStore(this, config.persistCooldowns());
        cooldowns.load();
        traders = new TraderManager(this);
        announcer = new TraderAnnouncer(this);
        interactions = new InteractionManager(this);
        bmenu = new BmenuSupport(this);
        menus = new TradeMenuGui(this);
        tradeEditor = new TradeEditorManager(this);
        new File(getDataFolder(), config.schematicFolder()).mkdirs();
        Bukkit.getPluginManager().registerEvents(new TradeListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RegionProtectionListener(this), this);
        ElytrixTraderCommand command = new ElytrixTraderCommand(this);
        PluginCommand pluginCommand = getCommand("elytrixtrader");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            expansion = new TraderExpansion(this);
            try {
                expansion.register();
            } catch (Throwable throwable) {
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            interactions.attach(player);
        }
        // Tab fix: hide traders from tab on join
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                Bukkit.getScheduler().runTaskLater(Main.this, () -> {
                    try {
                        for (ru.rooyzee.elytrixtrader.model.TraderInstance inst : traders.instances()) {
                            inst.hideFromTabFor(e.getPlayer());
                        }
                    } catch (Throwable ignored) {}
                }, 20L);

            }
        }, this);
        traders.start();
    }

    @Override
    public void onDisable() {
        if (traders != null) {
            try {
                traders.stopImmediate();
            } catch (Throwable ignored) {}
        }
        if (interactions != null) {
            interactions.detachAll();
        }
        if (cooldowns != null) {
            cooldowns.save();
        }
        if (expansion != null) {
            try {
                expansion.unregister();
            } catch (Throwable ignored) {
            }
        }
        instance = null;
    }

    public void reloadAll() {
        if (traders != null) {
            try {
                traders.stopImmediate();
            } catch (Throwable ignored) {}
        }
        config = loader.loadMain();
        messages = loader.loadMessages();
        gui = loader.loadGui();
        trades = loader.loadTrades();
        traderConfigs = loader.loadTraders(config);
        if (tradeEditor != null) {
            tradeEditor.loadAll();
        }
        currency.setup();
        if (skins != null) {
            skins.clear();
        }
        traders.start();
    }

    public AppConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public GuiConfig gui() {
        return gui;
    }

    public Map<String, TraderConfig> traderConfigs() {
        return traderConfigs;
    }

    public TradeRegistry trades() {
        return trades;
    }

    /**
     * Полный список сделок торговца: из реестра (trades:) + из редактора (trader_trades/*.yml).
     * Дубликаты по id не добавляются.
     */
    public List<TradeConfig> resolveTrades(TraderConfig config) {
        List<TradeConfig> result = new ArrayList<>();
        if (config == null) {
            return result;
        }
        for (TradeConfig trade : trades.resolve(config.tradeIds())) {
            result.add(trade);
        }
        if (tradeEditor != null) {
            Map<String, ru.rooyzee.elytrixtrader.editor.EditableTrade> custom = tradeEditor.getTrades(config.id());
            for (ru.rooyzee.elytrixtrader.editor.EditableTrade editable : custom.values()) {
                try {
                    TradeConfig converted = editable.toTradeConfig();
                    if (converted == null) {
                        continue;
                    }
                    boolean exists = false;
                    for (TradeConfig existing : result) {
                        if (existing.id().equalsIgnoreCase(converted.id())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        result.add(converted);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return result;
    }

    public SkinService skins() {
        return skins;
    }

    public SchematicService schematics() {
        return schematics;
    }

    public CurrencyService currency() {
        return currency;
    }

    public PurchaseService purchases() {
        return purchases;
    }

    public CooldownStore cooldowns() {
        return cooldowns;
    }

    public TraderManager traders() {
        return traders;
    }

    public TraderAnnouncer announcer() {
        return announcer;
    }

    public InteractionManager interactions() {
        return interactions;
    }

    public TradeMenuGui menus() {
        return menus;
    }

    public BmenuSupport bmenu() {
        return bmenu;
    }

    public TradeEditorManager tradeEditor() {
        return tradeEditor;
    }

    public boolean placeholderApiEnabled() {
        return config != null && config.placeholderApi();
    }

    public void log(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
    }
}
