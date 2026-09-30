package ru.rooyzee.elytrixparadise;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.rooyzee.elytrixparadise.commands.ParadiseCommand;
import ru.rooyzee.elytrixparadise.listeners.ShardMiningListener;
import ru.rooyzee.elytrixparadise.listeners.ShardProtectionListener;
import ru.rooyzee.elytrixparadise.managers.ConfigManager;
import ru.rooyzee.elytrixparadise.managers.SchematicManager;
import ru.rooyzee.elytrixparadise.shards.ShardManager;
import ru.rooyzee.elytrixparadise.tasks.ActionBarTask;
import ru.rooyzee.elytrixparadise.tasks.ShardTickTask;

import java.io.File;

public class Main extends JavaPlugin {

    private static Main instance;

    private ConfigManager configManager;
    private ShardManager shardManager;
    private SchematicManager schematicManager;

    private BukkitTask shardTickTask;
    private BukkitTask actionBarTask;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Создание папки для схематик
        File schemFolder = new File(getDataFolder(), "schematics");
        if (!schemFolder.exists()) {
            schemFolder.mkdirs();
        }

        // 2. Загрузка конфигурации
        configManager = new ConfigManager(this);
        configManager.load();

        // 3. Инициализация менеджеров
        shardManager = new ShardManager(this);
        schematicManager = new SchematicManager(this);

        // 4. Регистрация слушателей событий (добыча и защита осколков)
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new ShardMiningListener(this), this);
        pm.registerEvents(new ShardProtectionListener(this), this);

        // 5. Запуск периодических задач
        shardTickTask = new ShardTickTask(this).runTaskTimer(this, 20L, 20L);
        actionBarTask = new ActionBarTask(this).runTaskTimer(this, 20L, 20L);

        // 6. Вставка схематики на координаты X=0, Y=170, Z=0 при старте
        if (configManager.isPasteOnStartup()) {
            Location center = configManager.getCenterLocation();
            String schemName = configManager.getSchematicFile();
            getLogger().info("Вставка схематики '" + schemName + "' на координаты: X="
                    + center.getBlockX() + ", Y=" + center.getBlockY() + ", Z=" + center.getBlockZ()
                    + " в мире '" + configManager.getWorldName() + "'...");
            schematicManager.pasteSchematic(schemName, center);
        }

        // 7. Регистрация команд
        ParadiseCommand cmd = new ParadiseCommand(this);
        PluginCommand pluginCmd = getCommand("elytrixparadise");
        if (pluginCmd != null) {
            pluginCmd.setExecutor(cmd);
            pluginCmd.setTabCompleter(cmd);
        }

        getLogger().info("=========================================");
        getLogger().info("  ElytrixParadise v" + getDescription().getVersion() + " [Райское место] включён!");
        getLogger().info("  Координаты ивента: X=0, Y=" + configManager.getCenterY() + ", Z=0");
        getLogger().info("  Папка для схематик: plugins/ElytrixParadise/schematics/");
        getLogger().info("  Файл схематики: " + configManager.getSchematicFile());
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        if (shardTickTask != null) {
            shardTickTask.cancel();
        }
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }

        // Удаление схематики и очистка осколков/голограмм при выключении плагина
        if (schematicManager != null) {
            if (configManager != null && configManager.isClearOnDisable()) {
                schematicManager.clearSchematic();
            }
            schematicManager.invalidateCache();
        }

        if (shardManager != null) {
            shardManager.clearAllShards();
        }

        getLogger().info("ElytrixParadise отключён.");
    }

    public static Main getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    public SchematicManager getSchematicManager() {
        return schematicManager;
    }
}
