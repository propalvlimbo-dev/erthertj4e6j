package ru.rooyzee.elytrixschalkerpvp;

import org.bukkit.plugin.java.JavaPlugin;
import ru.rooyzee.elytrixschalkerpvp.command.SchalkerCommand;
import ru.rooyzee.elytrixschalkerpvp.command.SchalkerListener;
import ru.rooyzee.elytrixschalkerpvp.command.SchalkerTabCompleter;
import ru.rooyzee.elytrixschalkerpvp.manager.ConfigManager;
import ru.rooyzee.elytrixschalkerpvp.manager.HologramManager;
import ru.rooyzee.elytrixschalkerpvp.manager.LootManager;
import ru.rooyzee.elytrixschalkerpvp.manager.SchalkerManager;

public class Main extends JavaPlugin {

    private static Main instance;
    private ConfigManager configManager;
    private SchalkerManager schalkerManager;
    private LootManager lootManager;
    private HologramManager hologramManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.lootManager = new LootManager(this);
        this.hologramManager = new HologramManager(this);
        this.schalkerManager = new SchalkerManager(this);

        // Загружаем шалкеры с задержкой чтобы миры успели прогрузиться, а также сразу
        schalkerManager.loadSchalkers();
        // Повторная попытка через 3 секунды на случай если миры грузятся через Multiverse и т.п.
        getServer().getScheduler().runTaskLater(this, () -> {
            if (schalkerManager.getAllSchalkers().isEmpty()) {
                getLogger().info("Retrying schalker load after 3 seconds (worlds may not have been loaded)");
                schalkerManager.loadSchalkers();
            } else {
                // Проверяем есть ли шалкеры с незагруженными мирами и пытаемся их инициализировать
                schalkerManager.getAllSchalkers().forEach(data -> {
                    if (!data.tryResolveWorld()) {
                        getLogger().info("Retrying init for schalker " + data.getId());
                    }
                    schalkerManager.startSchalkerCycle(data);
                });
            }
        }, 60L);

        // Регистрация команд и TabCompleter
        getCommand("elytrixschalker").setExecutor(new SchalkerCommand(this));
        getCommand("elytrixschalker").setTabCompleter(new SchalkerTabCompleter());

        // Регистрация слушателей
        getServer().getPluginManager().registerEvents(new SchalkerListener(this), this);
    }

    @Override
    public void onDisable() {
        if (schalkerManager != null) {
            schalkerManager.shutdown();
        }
        if (hologramManager != null) {
            hologramManager.removeAll();
        }
    }

    public static Main getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SchalkerManager getSchalkerManager() {
        return schalkerManager;
    }

    public LootManager getLootManager() {
        return lootManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }
}