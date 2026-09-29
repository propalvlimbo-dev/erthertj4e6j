package ru.rooyzee.elytrixparadise;

import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.rooyzee.elytrixparadise.commands.ParadiseCommand;
import ru.rooyzee.elytrixparadise.managers.ConfigManager;
import ru.rooyzee.elytrixparadise.managers.SchematicManager;

import java.io.File;

public class Main extends JavaPlugin {

    private static Main instance;

    private ConfigManager configManager;
    private SchematicManager schematicManager;

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

        // 3. Инициализация менеджера схематик
        schematicManager = new SchematicManager(this);

        // 4. Вставка схематики на координаты X=0, Y=130, Z=0 при старте
        if (configManager.isPasteOnStartup()) {
            Location center = configManager.getCenterLocation();
            String schemName = configManager.getSchematicFile();
            getLogger().info("Вставка схематики '" + schemName + "' на координаты: X="
                    + center.getBlockX() + ", Y=" + center.getBlockY() + ", Z=" + center.getBlockZ()
                    + " в мире '" + configManager.getWorldName() + "'...");
            schematicManager.pasteSchematic(schemName, center);
        }

        // 5. Регистрация команд
        ParadiseCommand cmd = new ParadiseCommand(this);
        PluginCommand pluginCmd = getCommand("elytrixparadise");
        if (pluginCmd != null) {
            pluginCmd.setExecutor(cmd);
            pluginCmd.setTabCompleter(cmd);
        }

        getLogger().info("=========================================");
        getLogger().info("  ElytrixParadise v" + getDescription().getVersion() + " [Patch-NPE-Fix] включён!");
        getLogger().info("  Координаты спавна: X=0, Y=" + configManager.getCenterY() + ", Z=0");
        getLogger().info("  Папка для схематик: plugins/ElytrixParadise/schematics/");
        getLogger().info("  Файл схематики: " + configManager.getSchematicFile());
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        if (schematicManager != null) {
            schematicManager.invalidateCache();
        }
        getLogger().info("ElytrixParadise отключён.");
    }

    public static Main getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SchematicManager getSchematicManager() {
        return schematicManager;
    }
}
