package ru.rooyzee.elytrixcemetery;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.rooyzee.elytrixcemetery.commands.CemeteryCommand;
import ru.rooyzee.elytrixcemetery.ghosts.GhostController;
import ru.rooyzee.elytrixcemetery.graves.GraveManager;
import ru.rooyzee.elytrixcemetery.holograms.CemeteryHologramManager;
import ru.rooyzee.elytrixcemetery.listeners.CemeteryDigListener;
import ru.rooyzee.elytrixcemetery.listeners.CemeteryProtectionListener;
import ru.rooyzee.elytrixcemetery.loot.LootEditorGUI;
import ru.rooyzee.elytrixcemetery.loot.LootEditorListener;
import ru.rooyzee.elytrixcemetery.loot.LootStorageManager;
import ru.rooyzee.elytrixcemetery.managers.ConfigManager;
import ru.rooyzee.elytrixcemetery.managers.RegionManager;
import ru.rooyzee.elytrixcemetery.managers.SchematicManager;
import ru.rooyzee.elytrixcemetery.placeholders.CemeteryPlaceholder;
import ru.rooyzee.elytrixcemetery.tasks.ActionBarTask;
import ru.rooyzee.elytrixcemetery.tasks.CemeteryTickTask;

public class Main extends JavaPlugin {

    private static Main instance;

    private ConfigManager configManager;
    private LootStorageManager lootStorageManager;
    private GraveManager graveManager;
    private GhostController ghostController;
    private SchematicManager schematicManager;
    private RegionManager regionManager;
    private CemeteryHologramManager hologramManager;

    private ActionBarTask actionBarTask;
    private CemeteryTickTask tickTask;

    @Override
    public void onEnable() {
        instance = this;

        this.configManager = new ConfigManager(this);
        this.lootStorageManager = new LootStorageManager(this);
        this.graveManager = new GraveManager(this);
        this.ghostController = new GhostController(this);
        this.schematicManager = new SchematicManager(this);
        this.regionManager = new RegionManager(this);
        this.hologramManager = new CemeteryHologramManager(this);

        // Регистрация команд
        CemeteryCommand cmd = new CemeteryCommand(this);
        if (getCommand("elytrixcemetery") != null) {
            getCommand("elytrixcemetery").setExecutor(cmd);
            getCommand("elytrixcemetery").setTabCompleter(cmd);
        }
        if (getCommand("cemetery") != null) {
            getCommand("cemetery").setExecutor(cmd);
            getCommand("cemetery").setTabCompleter(cmd);
        }
        if (getCommand("elytrixparadise") != null) {
            getCommand("elytrixparadise").setExecutor(cmd);
            getCommand("elytrixparadise").setTabCompleter(cmd);
        }
        if (getCommand("paradise") != null) {
            getCommand("paradise").setExecutor(cmd);
            getCommand("paradise").setTabCompleter(cmd);
        }

        // Регистрация слушателей событий
        LootEditorGUI lootGui = new LootEditorGUI(this);
        Bukkit.getPluginManager().registerEvents(new CemeteryDigListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CemeteryProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new LootEditorListener(this, lootGui), this);

        // Регистрация PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CemeteryPlaceholder(this).register();
        }

        // Создание/вставка постройки кладбища и спавн
        Bukkit.getScheduler().runTaskLater(this, () -> {
            schematicManager.buildOrPasteCemetery();
            regionManager.createOrUpdateRegion();
            hologramManager.createOrUpdateMainHologram();
        }, 20L);

        // Запуск фоновых задач
        this.tickTask = new CemeteryTickTask(this);
        this.tickTask.runTaskTimer(this, 20L, 20L);

        this.actionBarTask = new ActionBarTask(this);
        this.actionBarTask.runTaskTimer(this, 20L, 20L);

        getLogger().info("=========================================");
        getLogger().info(" ElytrixCemetery v1.0.0 успешно запущен!");
        getLogger().info(" Локация: 0, 70, 0 (Кладбище)");
        getLogger().info(" Автор: rooyzee");
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) tickTask.cancel();
        if (actionBarTask != null) actionBarTask.cancel();

        if (hologramManager != null) hologramManager.removeMainHologram();
        if (graveManager != null) graveManager.clearAll();
        if (ghostController != null) ghostController.clearAll();
        if (schematicManager != null && schematicManager.getGenerator() != null) {
            schematicManager.getGenerator().clearCemeteryBlocks();
        }
        if (regionManager != null) regionManager.removeRegion();

        getLogger().info("ElytrixCemetery выключен.");
    }

    public static Main getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public LootStorageManager getLootStorageManager() { return lootStorageManager; }
    public GraveManager getGraveManager() { return graveManager; }
    public GhostController getGhostController() { return ghostController; }
    public SchematicManager getSchematicManager() { return schematicManager; }
    public RegionManager getRegionManager() { return regionManager; }
    public CemeteryHologramManager getHologramManager() { return hologramManager; }
    public ActionBarTask getActionBarTask() { return actionBarTask; }
}
