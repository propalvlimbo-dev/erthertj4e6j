package ru.rooyzee.elytrixairdrop;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.rooyzee.elytrixairdrop.airdrops.ActiveAirdrop;
import ru.rooyzee.elytrixairdrop.commands.AirdropCommand;
import ru.rooyzee.elytrixairdrop.listeners.AirdropEntityListener;
import ru.rooyzee.elytrixairdrop.listeners.AirdropInteractListener;
import ru.rooyzee.elytrixairdrop.listeners.GuiListener;
import ru.rooyzee.elytrixairdrop.listeners.ProtectionListener;
import ru.rooyzee.elytrixairdrop.managers.*;
import ru.rooyzee.elytrixairdrop.placeholders.AirdropPlaceholder;

import java.io.File;

public class Main extends JavaPlugin {

    private static Main instance;
    private ConfigManager configManager;
    private AirdropManager airdropManager;
    private SchematicManager schematicManager;
    private RegionManager regionManager;
    private HologramManager hologramManager;

    @Override
    public void onEnable() {
        instance = this;
        new File(getDataFolder(), "schematics").mkdirs();
        new File(getDataFolder(), "loot").mkdirs();
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        configManager.load();

        schematicManager = new SchematicManager(this);
        regionManager = new RegionManager(this);
        hologramManager = new HologramManager(this);
        airdropManager = new AirdropManager(this);

        AirdropCommand cmd = new AirdropCommand(this);
        getCommand("elytrixairdrop").setExecutor(cmd);
        getCommand("elytrixairdrop").setTabCompleter(cmd);

        Bukkit.getPluginManager().registerEvents(new AirdropEntityListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AirdropInteractListener(this), this);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AirdropPlaceholder(this).register();
        }

        airdropManager.startScheduler();
        getLogger().info("ElytrixAirdrop enabled");
    }

    @Override
    public void onDisable() {
        if (airdropManager != null) {
            ActiveAirdrop a = airdropManager.getActive();
            if (a != null) {
                try {
                    if (a.getHologramName() != null) hologramManager.removeHologram(a.getHologramName());
                    if (a.getRegionId() != null && a.getCenter().getWorld() != null)
                        regionManager.removeRegion(a.getCenter().getWorld(), a.getRegionId());
                    if (a.getBackup() != null) schematicManager.restoreImmediate(a.getBackup());
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
    }

    public static Main getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public AirdropManager getAirdropManager() { return airdropManager; }
    public SchematicManager getSchematicManager() { return schematicManager; }
    public RegionManager getRegionManager() { return regionManager; }
    public HologramManager getHologramManager() { return hologramManager; }
}