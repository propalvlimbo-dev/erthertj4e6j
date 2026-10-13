package ru.rooyzee.elytrixtrader.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.TradeConfig;
import ru.rooyzee.elytrixtrader.model.TradeRegistry;
import ru.rooyzee.elytrixtrader.model.TraderConfig;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigLoader {

    // Сюда добавляй новых торговцев когда будешь готов: "burning", "mysterious", ...
    private static final String[] BUNDLED_TRADERS = {"experienced"};

    private final Main plugin;

    public ConfigLoader(Main plugin) {
        this.plugin = plugin;
    }

    public AppConfig loadMain() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        return new AppConfig(plugin.getConfig());
    }

    public Messages loadMessages() {
        return new Messages(read("messages.yml"));
    }

    public GuiConfig loadGui() {
        return new GuiConfig(read("gui.yml"));
    }

    public Map<String, TraderConfig> loadTraders(AppConfig defaults) {
        Map<String, TraderConfig> result = new LinkedHashMap<>();
        File folder = new File(plugin.getDataFolder(), "traders");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        // Распаковываем встроенные файлы торговцев (по одному на вид)
        for (String name : BUNDLED_TRADERS) {
            File file = new File(folder, name + ".yml");
            if (!file.exists()) {
                plugin.saveResource("traders/" + name + ".yml", false);
            }
        }
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String id = file.getName().replaceAll("(?i)\\.yml$", "").toLowerCase();
                try {
                    YamlConfiguration config = ru.rooyzee.elytrixtrader.util.ItemCodec.loadYaml(file);
                    result.put(id, TraderConfig.from(id, config));
                } catch (Throwable t) {
                }
            }
        }
        return result;
    }

    public TradeRegistry loadTrades() {
        TradeRegistry registry = new TradeRegistry();

        // Legacy trades.yml - keep for backward compat but not required
        File legacyFile = new File(plugin.getDataFolder(), "trades.yml");
        if (legacyFile.exists()) {
            YamlConfiguration config = ru.rooyzee.elytrixtrader.util.ItemCodec.loadYaml(legacyFile);
            for (String key : config.getKeys(false)) {
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section == null) continue;
                try {
                    TradeConfig trade = TradeConfig.from(key, section);
                    if (trade.costs().isEmpty() && trade.receive().isEmpty() && trade.commands().isEmpty()) {
                        continue;
                    }
                    registry.register(trade);
                } catch (Throwable t) {
                }
            }
        }

        // New system: trader_trades/*.yml
        File folder = new File(plugin.getDataFolder(), "trader_trades");
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    YamlConfiguration cfg = ru.rooyzee.elytrixtrader.util.ItemCodec.loadYaml(file);
                    ConfigurationSection tradesSec = cfg.getConfigurationSection("trades");
                    if (tradesSec == null) continue;
                    for (String tradeId : tradesSec.getKeys(false)) {
                        ConfigurationSection sec = tradesSec.getConfigurationSection(tradeId);
                        if (sec == null) continue;
                        try {
                            // Try new format (EditableTrade)
                            if (sec.contains("rewards") || sec.contains("coin-cost")) {
                                // Convert via EditableTrade
                                String traderId = file.getName().replaceAll("(?i)\\.yml$", "");
                                ru.rooyzee.elytrixtrader.editor.EditableTrade et = ru.rooyzee.elytrixtrader.editor.EditableTrade.from(tradeId, traderId, sec);
                                registry.register(et.toTradeConfig());
                            } else {
                                TradeConfig trade = TradeConfig.from(tradeId, sec);
                                registry.register(trade);
                            }
                        } catch (Throwable t) {
                        }
                    }
                }
            }
        }

        return registry;
    }

    private YamlConfiguration read(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }
}
