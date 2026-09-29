package ru.rooyzee.elytrixparadise;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.rooyzee.elytrixparadise.commands.ParadiseCommand;
import ru.rooyzee.elytrixparadise.event.ParadiseEventManager;
import ru.rooyzee.elytrixparadise.listeners.ParadisePlayerListener;
import ru.rooyzee.elytrixparadise.listeners.ParadiseProtectionListener;
import ru.rooyzee.elytrixparadise.managers.AmbientManager;
import ru.rooyzee.elytrixparadise.managers.ConfigManager;
import ru.rooyzee.elytrixparadise.managers.HologramManager;
import ru.rooyzee.elytrixparadise.managers.RegionManager;
import ru.rooyzee.elytrixparadise.managers.SchematicManager;
import ru.rooyzee.elytrixparadise.placeholders.ParadisePlaceholder;

import java.io.File;

public class Main extends JavaPlugin {

    private static Main instance;

    private ConfigManager configManager;
    private SchematicManager schematicManager;
    private RegionManager regionManager;
    private HologramManager hologramManager;
    private AmbientManager ambientManager;
    private ParadiseEventManager eventManager;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Ensure folders exist
        new File(getDataFolder(), "schematics").mkdirs();

        // 2. Load configurations
        configManager = new ConfigManager(this);
        configManager.load();

        // 3. Initialize managers
        schematicManager = new SchematicManager(this);
        regionManager = new RegionManager(this);
        hologramManager = new HologramManager(this);
        ambientManager = new AmbientManager(this);
        eventManager = new ParadiseEventManager(this);
        eventManager.initialize();

        // 4. Paste schematic if configured
        if (configManager.isPasteOnStartup()) {
            Location center = configManager.getCenterLocation();
            String schemName = configManager.getSchematicFile();
            schematicManager.pasteSchematic(schemName, center);
        }

        // 5. Setup WorldGuard protection region
        regionManager.createOrUpdateRegion();

        // 6. Register Commands
        ParadiseCommand cmd = new ParadiseCommand(this);
        PluginCommand pluginCmd = getCommand("elytrixparadise");
        if (pluginCmd != null) {
            pluginCmd.setExecutor(cmd);
            pluginCmd.setTabCompleter(cmd);
        }

        // 7. Register Listeners
        Bukkit.getPluginManager().registerEvents(new ParadisePlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ParadiseProtectionListener(this), this);

        // 8. Register PlaceholderAPI
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new ParadisePlaceholder(this).register();
            getLogger().info("PlaceholderAPI expansion registered successfully.");
        }

        // 9. Start continuous event loop
        eventManager.startScheduler();

        // 10. Create initial hologram
        hologramManager.createOrUpdateHologram(eventManager.getEvent());

        getLogger().info("=========================================");
        getLogger().info("  ElytrixParadise v" + getDescription().getVersion() + " успешно включён!");
        getLogger().info("  Центр Рая: X=0, Z=0 (Y=" + configManager.getCenterY() + ") в мире '" + configManager.getWorldName() + "'");
        getLogger().info("  Папка для вашей схематики: plugins/ElytrixParadise/schematics/");
        getLogger().info("  Имя файла схематики: " + configManager.getSchematicFile());
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            eventManager.stopScheduler();
        }
        if (ambientManager != null) {
            ambientManager.remove();
        }
        if (hologramManager != null) {
            hologramManager.removeHologram();
        }
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

    public RegionManager getRegionManager() {
        return regionManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public AmbientManager getAmbientManager() {
        return ambientManager;
    }

    public ParadiseEventManager getEventManager() {
        return eventManager;
    }
}
