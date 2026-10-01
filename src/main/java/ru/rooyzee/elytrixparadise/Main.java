package ru.rooyzee.elytrixparadise;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.rooyzee.elytrixparadise.commands.ParadiseCommand;
import ru.rooyzee.elytrixparadise.listeners.ParadiseProtectionListener;
import ru.rooyzee.elytrixparadise.listeners.ShardMiningListener;
import ru.rooyzee.elytrixparadise.listeners.ShardProtectionListener;
import ru.rooyzee.elytrixparadise.loot.LootEditorGUI;
import ru.rooyzee.elytrixparadise.loot.LootEditorListener;
import ru.rooyzee.elytrixparadise.loot.LootStorageManager;
import ru.rooyzee.elytrixparadise.managers.ConfigManager;
import ru.rooyzee.elytrixparadise.managers.RegionManager;
import ru.rooyzee.elytrixparadise.managers.SchematicManager;
import ru.rooyzee.elytrixparadise.shards.ShardManager;
import ru.rooyzee.elytrixparadise.sphere.SphereManager;
import ru.rooyzee.elytrixparadise.tasks.ActionBarTask;
import ru.rooyzee.elytrixparadise.tasks.ShardTickTask;

import java.io.File;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Main extends JavaPlugin {

    private static Main instance;

    private ConfigManager configManager;
    private RegionManager regionManager;
    private ShardManager shardManager;
    private SchematicManager schematicManager;
    private SphereManager sphereManager;
    private LootStorageManager lootStorageManager;
    private LootEditorGUI lootEditorGUI;

    private BukkitTask shardTickTask;
    private BukkitTask actionBarTask;

    private final Set<UUID> disabledActionBarPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        instance = this;

        // 1. Создание папок
        File schemFolder = new File(getDataFolder(), "schematics");
        if (!schemFolder.exists()) {
            schemFolder.mkdirs();
        }
        File lootFolder = new File(getDataFolder(), "loot");
        if (!lootFolder.exists()) {
            lootFolder.mkdirs();
        }

        // 2. Загрузка конфигурации и хранилища лута
        configManager = new ConfigManager(this);
        configManager.load();
        lootStorageManager = new LootStorageManager(this);
        lootEditorGUI = new LootEditorGUI(this);

        // 3. Инициализация менеджеров
        regionManager = new RegionManager(this);
        shardManager = new ShardManager(this);
        schematicManager = new SchematicManager(this);
        sphereManager = new SphereManager(this);

        // 4. Регистрация слушателей событий (добыча, защита осколков, запрет флая/года, редактор лута)
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new ShardMiningListener(this), this);
        pm.registerEvents(new ShardProtectionListener(this), this);
        pm.registerEvents(new ParadiseProtectionListener(this), this);
        pm.registerEvents(new LootEditorListener(this, lootEditorGUI), this);

        // 5. Запуск периодических задач
        shardTickTask = new ShardTickTask(this).runTaskTimer(this, 20L, 20L);
        actionBarTask = new ActionBarTask(this).runTaskTimer(this, 20L, 20L);

        // 6. Вставка схематики и создание региона WorldGuard
        if (configManager.isPasteOnStartup()) {
            Location center = configManager.getCenterLocation();
            String schemName = configManager.getSchematicFile();
            getLogger().info("Вставка схематики '" + schemName + "' на координаты: X="
                    + center.getBlockX() + ", Y=" + center.getBlockY() + ", Z=" + center.getBlockZ()
                    + " в мире '" + configManager.getWorldName() + "'...");
            schematicManager.pasteSchematic(schemName, center);
            regionManager.createParadiseRegion(
                    center,
                    configManager.getScanRadiusXZ(),
                    configManager.getScanMinY(),
                    configManager.getScanMaxY()
            );
        } else {
            schematicManager.scanAndRegisterShards();
            regionManager.createParadiseRegion(
                    configManager.getCenterLocation(),
                    configManager.getScanRadiusXZ(),
                    configManager.getScanMinY(),
                    configManager.getScanMaxY()
            );
        }

        // 7. Инициализация сферы и повторное сканирование осколков через 3 секунды
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                if (schematicManager != null) {
                    schematicManager.scanAndRegisterShards();
                }
                if (sphereManager != null) {
                    sphereManager.init();
                }
            }
        }, 60L);

        // 8. Регистрация команд
        ParadiseCommand cmd = new ParadiseCommand(this);
        PluginCommand pluginCmd = getCommand("elytrixparadise");
        if (pluginCmd != null) {
            pluginCmd.setExecutor(cmd);
            pluginCmd.setTabCompleter(cmd);
        }

        // 9. Регистрация PlaceholderAPI расширения
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new ru.rooyzee.elytrixparadise.placeholders.ParadisePlaceholder(this, "elytrixparadise").register();
            new ru.rooyzee.elytrixparadise.placeholders.ParadisePlaceholder(this, "paradise").register();
            getLogger().info("PlaceholderAPI успешно подключен к ElytrixParadise (%elytrixparadise_...% и %paradise_...%)!");
        }

        getLogger().info("=========================================");
        getLogger().info("  ElytrixParadise v" + getDescription().getVersion() + " [Райское место] включён!");
        getLogger().info("  Координаты ивента: X=0, Y=" + configManager.getCenterY() + ", Z=0");
        getLogger().info("  Радиус региона и поиска: " + configManager.getScanRadiusXZ() + " блоков");
        getLogger().info("  Папка для схематик: plugins/ElytrixParadise/schematics/");
        getLogger().info("  Папка для лута: plugins/ElytrixParadise/loot/");
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

        if (sphereManager != null) {
            sphereManager.clearAll();
        }

        if (schematicManager != null) {
            if (configManager != null && configManager.isClearOnDisable()) {
                schematicManager.clearSchematic();
            }
        }

        if (regionManager != null && configManager != null) {
            regionManager.removeParadiseRegion(configManager.getCenterLocation().getWorld());
        }

        getLogger().info("ElytrixParadise успешно отключён.");
    }

    public static Main getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public RegionManager getRegionManager() {
        return regionManager;
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    public SchematicManager getSchematicManager() {
        return schematicManager;
    }

    public SphereManager getSphereManager() {
        return sphereManager;
    }

    public LootStorageManager getLootStorageManager() {
        return lootStorageManager;
    }

    public LootEditorGUI getLootEditorGUI() {
        return lootEditorGUI;
    }

    public boolean isActionBarDisabled(UUID uuid) {
        return disabledActionBarPlayers.contains(uuid);
    }

    public void setActionBarDisabled(UUID uuid, boolean disabled) {
        if (disabled) {
            disabledActionBarPlayers.add(uuid);
        } else {
            disabledActionBarPlayers.remove(uuid);
        }
    }
}
