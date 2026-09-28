package ru.rooyzee.elytrixairdrop.managers;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.airdrops.ActiveAirdrop;
import ru.rooyzee.elytrixairdrop.airdrops.Airdrop;
import ru.rooyzee.elytrixairdrop.airdrops.types.ActiveFireAirdrop;
import ru.rooyzee.elytrixairdrop.airdrops.types.ActivePeacefulAirdrop;
import ru.rooyzee.elytrixairdrop.airdrops.types.ActiveSkyAirdrop;
import ru.rooyzee.elytrixairdrop.services.AirdropBroadcaster;
import ru.rooyzee.elytrixairdrop.services.LocationFinder;
import ru.rooyzee.elytrixairdrop.services.LootFileService;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class AirdropManager {

    private static final long MIN_MANUAL_START_DELAY = 5_000L;
    private static final long HARD_TIMEOUT_EXTRA_MS = 5 * 60_000L;
    private static final long SPAWN_INTERVAL_MS = 30 * 60_000L;
    private static final long RETRY_DELAY_MS = 60_000L;
    private static final int MIN_ONLINE_FOR_SPAWN = 5;

    private final Main plugin;
    private final LootFileService lootFileService;
    private final LocationFinder locationFinder;
    private final AirdropBroadcaster broadcaster;
    private final Random random = new Random();

    private volatile ActiveAirdrop active;
    private long nextScheduledSpawn;
    private long nextRetry = 0;
    private long lastManualStart = 0;
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    public AirdropManager(Main plugin) {
        this.plugin = plugin;
        this.lootFileService = new LootFileService(plugin);
        this.locationFinder = new LocationFinder(plugin);
        this.broadcaster = new AirdropBroadcaster(plugin);
        this.nextScheduledSpawn = System.currentTimeMillis() + SPAWN_INTERVAL_MS;
    }

    public ActiveAirdrop getActive() { return active; }
    public LootFileService getLootFileService() { return lootFileService; }
    public AirdropBroadcaster getBroadcaster() { return broadcaster; }
    public LocationFinder getLocationFinder() { return locationFinder; }

    public long getNextSpawnSeconds() {
        if (active != null) return 0;
        return Math.max(0, (nextScheduledSpawn - System.currentTimeMillis()) / 1000);
    }

    public String formatTime(long seconds) {
        if (seconds < 60) return seconds + "с";
        return (seconds / 60) + "м " + (seconds % 60) + "с";
    }

    public void startScheduler() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (active != null) {
                    ActiveAirdrop a = active;
                    ConfigurationSection sec = plugin.getConfigManager().getConfig()
                            .getConfigurationSection("airdrops." + a.getType().getId());
                    if (sec != null) {
                        try { a.onTick(plugin, sec); } catch (Exception e) { e.printStackTrace(); }
                    }
                    long hardTimeout = a.getEndTime() + HARD_TIMEOUT_EXTRA_MS;
                    if (System.currentTimeMillis() >= hardTimeout) {
                        plugin.getLogger().warning("Airdrop hard timeout, forcing stop");
                        stop(false);
                        return;
                    }
                    if (System.currentTimeMillis() >= a.getEndTime()) stop(false);
                    return;
                }

                if (System.currentTimeMillis() < nextScheduledSpawn) return;
                if (System.currentTimeMillis() < nextRetry) return;

                if (Bukkit.getOnlinePlayers().size() < MIN_ONLINE_FOR_SPAWN) {
                    nextRetry = System.currentTimeMillis() + RETRY_DELAY_MS;
                    plugin.getLogger().info("Airdrop delayed: online < " + MIN_ONLINE_FOR_SPAWN);
                    return;
                }

                if (startRandom()) {
                    nextScheduledSpawn = System.currentTimeMillis() + SPAWN_INTERVAL_MS;
                    nextRetry = 0;
                } else {
                    nextRetry = System.currentTimeMillis() + RETRY_DELAY_MS;
                    plugin.getLogger().warning("Airdrop scheduled spawn failed completely");
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Scheduler error: " + e.getMessage());
                e.printStackTrace();
            }
        }, 20L, 20L);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ActiveAirdrop a = active;
            if (a == null || a.getRegionId() == null) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == org.bukkit.GameMode.CREATIVE
                        || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                if (p.hasPermission("elytrixairdrop.admin")) continue;
                if (!plugin.getRegionManager().isInRegion(p.getLocation(), a.getRegionId())) continue;
                if (p.isFlying() || p.getAllowFlight()) {
                    p.setFlying(false);
                    p.setAllowFlight(false);
                }
            }
        }, 20L, 10L);
    }

    public boolean isNightTime() {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        return hour >= 2 && hour < 6;
    }

    public boolean startRandom() {
        List<Airdrop> enabled = new ArrayList<>();
        boolean night = isNightTime();
        for (Airdrop airdrop : Airdrop.values()) {
            if (!plugin.getConfigManager().getConfig().getBoolean("airdrops." + airdrop.getId() + ".enabled", true)) continue;
            if (night && airdrop != Airdrop.PEACEFUL) continue;
            enabled.add(airdrop);
        }
        if (enabled.isEmpty()) return false;
        Collections.shuffle(enabled, random);
        for (Airdrop airdrop : enabled) {
            if (start(airdrop)) return true;
        }
        return false;
    }

    public boolean canManualStart() {
        return System.currentTimeMillis() - lastManualStart >= MIN_MANUAL_START_DELAY;
    }

    public boolean start(Airdrop id) {
        if (active != null) return false;
        if (!starting.compareAndSet(false, true)) return false;
        try {
            lastManualStart = System.currentTimeMillis();
            String cfgId = id.getId();
            if (!plugin.getConfigManager().getConfig().getBoolean("airdrops." + cfgId + ".enabled", true)) {
                plugin.getLogger().warning("Airdrop " + cfgId + ": disabled in config");
                return false;
            }

            ConfigurationSection sec = plugin.getConfigManager().getConfig().getConfigurationSection("airdrops." + cfgId);
            if (sec == null) return false;

            World world = Bukkit.getWorld(plugin.getConfigManager().getConfig().getString("world", "world"));
            if (world == null) return false;

            // Loot checks use cache — cheap after first load
            if (id == Airdrop.SKY) {
                boolean anyLoot = false;
                for (String r : new String[]{"common", "rare", "mythic", "legendary"}) {
                    if (!lootFileService.load("sky_" + r).isEmpty()) { anyLoot = true; break; }
                }
                if (!anyLoot) {
                    plugin.getLogger().warning("Airdrop sky: all loot pools empty");
                    return false;
                }
            } else {
                int loot = lootFileService.load(cfgId).size();
                if (loot == 0) {
                    plugin.getLogger().warning("Airdrop " + cfgId + ": empty loot pool");
                    return false;
                }
            }

            int radius = sec.getInt("region-radius", 30);

            Location loc;
            if (id == Airdrop.SKY) {
                int minAbove = sec.getInt("min-above-ground", 35);
                int maxAbove = sec.getInt("max-above-ground", 50);
                loc = locationFinder.findSky(world, radius, minAbove, maxAbove);
                if (loc == null) {
                    plugin.getLogger().severe("Airdrop sky: no sky location found");
                    return false;
                }
            } else {
                loc = null;
                for (int level = 0; level <= 3; level++) {
                    loc = locationFinder.findWithLevel(world, radius, level);
                    if (loc != null) {
                        if (level > 0) plugin.getLogger().info("Airdrop " + cfgId
                                + ": location found at escalation lvl " + level);
                        break;
                    }
                    plugin.getLogger().info("Airdrop " + cfgId + ": lvl " + level + " failed, escalating");
                }
                if (loc == null) {
                    plugin.getLogger().severe("Airdrop " + cfgId + ": NO location after all escalations");
                    return false;
                }
            }

            String schemName = sec.getString("schematic", "mir.schem");
            // Schematic is cached after first load
            Clipboard clip = plugin.getSchematicManager().loadSchematic(schemName);
            if (clip == null) return false;

            SchematicManager.PasteResult result = plugin.getSchematicManager().pasteAndBackup(clip, loc);
            if (result == null || result.backup == null || result.backup.isEmpty()) {
                plugin.getLogger().warning("Airdrop " + cfgId + ": paste failed");
                return false;
            }

            String regionId = plugin.getRegionManager().createRegion(loc, radius, cfgId);
            if (regionId == null) {
                plugin.getSchematicManager().restore(result.backup);
                return false;
            }

            String holoName = plugin.getHologramManager().createHologram(
                    loc.clone().add(0.5, 4.5, 0.5),
                    Collections.singletonList("&#F8BEFBЗагрузка...")
            );

            long endTime = System.currentTimeMillis() + sec.getInt("duration-minutes", 10) * 60_000L;

            // Beacon positions collected during paste — no world scan needed
            List<Location> beacons = result.beacons != null ? result.beacons : Collections.emptyList();

            ActiveAirdrop newActive;
            if (id == Airdrop.PEACEFUL) {
                newActive = new ActivePeacefulAirdrop(loc, regionId, holoName, result.backup, endTime, beacons);
            } else if (id == Airdrop.FIRE) {
                newActive = new ActiveFireAirdrop(loc, regionId, holoName, result.backup, endTime);
            } else if (id == Airdrop.SKY) {
                newActive = new ActiveSkyAirdrop(loc, regionId, holoName, result.backup, endTime, beacons);
            } else {
                plugin.getHologramManager().removeHologram(holoName);
                plugin.getRegionManager().removeRegion(world, regionId);
                plugin.getSchematicManager().restore(result.backup);
                return false;
            }

            active = newActive;
            active.onStart(plugin, sec);
            broadcaster.broadcast("broadcast.spawn", broadcaster.spawnVars(
                    broadcaster.typeDisplay(cfgId), loc, radius,
                    formatTime(sec.getInt("duration-minutes", 10) * 60L)
            ));

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == org.bukkit.GameMode.CREATIVE
                        || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.5f);
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start airdrop: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            starting.set(false);
        }
    }

    public void stop(boolean looted) {
        if (active == null) return;
        if (!stopping.compareAndSet(false, true)) return;
        try {
            ActiveAirdrop a = active;
            active = null;

            ConfigurationSection sec = plugin.getConfigManager().getConfig()
                    .getConfigurationSection("airdrops." + a.getType().getId());
            try { a.onStop(plugin, sec); } catch (Exception e) { e.printStackTrace(); }

            plugin.getHologramManager().removeHologram(a.getHologramName());
            // Remove the protection before restoring the schematic so cleanup cannot leave
            // an orphaned WorldGuard region when a type-specific cleanup fails.
            plugin.getRegionManager().removeRegion(a.getCenter().getWorld(), a.getRegionId());
            plugin.getSchematicManager().restore(a.getBackup());

            Map<String, String> vars = new HashMap<>();
            vars.put("type", broadcaster.typeDisplay(a.getType().getId()));
            broadcaster.broadcast("broadcast.end", vars);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to stop airdrop: " + e.getMessage());
            active = null;
        } finally {
            stopping.set(false);
        }
    }

    public void stopAll() {
        if (active != null) stop(false);
    }
}
