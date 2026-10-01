package ru.rooyzee.elytrixschalkerpvp.manager;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.rooyzee.elytrixschalkerpvp.Main;
import ru.rooyzee.elytrixschalkerpvp.model.Rarity;
import ru.rooyzee.elytrixschalkerpvp.model.SchalkerData;
import ru.rooyzee.elytrixschalkerpvp.model.SchalkerState;
import ru.rooyzee.elytrixschalkerpvp.util.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SchalkerManager {

    private final Main plugin;
    private final Map<String, SchalkerData> schalkers = new ConcurrentHashMap<>();
    private final Map<String, Inventory> activeInventories = new ConcurrentHashMap<>();
    private final File dataFile;
    private FileConfiguration dataConfig;

    public SchalkerManager(Main plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "schalkers.yml");
    }

    public void loadSchalkers() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        schalkers.clear();

        ConfigurationSection section = dataConfig.getConfigurationSection("schalkers");
        if (section == null) {
            plugin.getLogger().info("No schalkers section found, 0 loaded.");
            return;
        }

        for (String id : section.getKeys(false)) {
            String path = "schalkers." + id;
            String worldName = dataConfig.getString(path + ".world");
            int x = dataConfig.getInt(path + ".x");
            int y = dataConfig.getInt(path + ".y");
            int z = dataConfig.getInt(path + ".z");

            if (worldName == null || worldName.isEmpty()) {
                String[] parts = id.split("_");
                if (parts.length >= 4) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 3; i < parts.length; i++) {
                        if (i > 3) sb.append("_");
                        sb.append(parts[i]);
                    }
                    worldName = sb.toString();
                }
            }

            World world = worldName != null ? Bukkit.getWorld(worldName) : null;
            Location loc;
            if (world != null) {
                loc = new Location(world, x, y, z);
            } else {
                loc = new Location(null, x, y, z);
            }

            SchalkerData data = new SchalkerData(id, loc, worldName);

            String rarityStr = dataConfig.getString(path + ".rarity", null);
            if (rarityStr != null) {
                try {
                    data.setCurrentRarity(Rarity.fromString(rarityStr));
                } catch (Exception ignored) {}
            }

            String stateStr = dataConfig.getString(path + ".state", "SLEEPING");
            SchalkerState loadedState;
            try {
                loadedState = SchalkerState.valueOf(stateStr);
            } catch (IllegalArgumentException e) {
                loadedState = SchalkerState.SLEEPING;
            }

            long stateChangeTime = dataConfig.getLong(path + ".stateChangeTime", System.currentTimeMillis());
            long sleepEndTime = dataConfig.getLong(path + ".sleepEndTime", 0L);

            // Устанавливаем состояние напрямую без вызова setState чтобы не сбросить время, потом сетим время
            // Используем сеттеры которые мы контролируем
            // Сначала ставим состояние через поле? Мы вызовем setState а потом восстановим время
            data.setState(loadedState);
            data.setStateChangeTime(stateChangeTime);
            data.setSleepEndTime(sleepEndTime);

            schalkers.put(id, data);
            scheduleInitWithRetry(data, 20L);
        }

        plugin.getLogger().info("Loaded " + schalkers.size() + " schalkers from file.");
    }

    private void scheduleInitWithRetry(SchalkerData data, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!data.tryResolveWorld()) {
                String worldName = data.getWorldName();
                World world = worldName != null ? Bukkit.getWorld(worldName) : null;
                if (world == null) {
                    plugin.getLogger().warning("World " + worldName + " not loaded yet for schalker " + data.getId() + ", retrying in 5 sec...");
                    Bukkit.getScheduler().runTaskLater(plugin, () -> scheduleInitWithRetry(data, 100L), 100L);
                    return;
                }
            }
            ensureShulkerBlock(data);
            startSchalkerCycle(data);
        }, delay);
    }

    public void saveSchalkers() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, SchalkerData> entry : schalkers.entrySet()) {
            SchalkerData data = entry.getValue();
            String path = "schalkers." + data.getId();

            String worldName = data.getWorldName();
            Location loc = data.getLocation();
            if (loc != null && loc.getWorld() != null) {
                worldName = loc.getWorld().getName();
                data.setWorldName(worldName);
            }
            if (worldName == null) {
                String id = data.getId();
                String[] parts = id.split("_");
                if (parts.length >= 4) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 3; i < parts.length; i++) {
                        if (i > 3) sb.append("_");
                        sb.append(parts[i]);
                    }
                    worldName = sb.toString();
                } else {
                    continue;
                }
            }

            cfg.set(path + ".world", worldName);
            if (loc != null) {
                cfg.set(path + ".x", loc.getBlockX());
                cfg.set(path + ".y", loc.getBlockY());
                cfg.set(path + ".z", loc.getBlockZ());
            } else {
                continue;
            }

            if (data.getCurrentRarity() != null) {
                cfg.set(path + ".rarity", data.getCurrentRarity().name());
            }
            cfg.set(path + ".state", data.getState().name());
            cfg.set(path + ".stateChangeTime", data.getStateChangeTime());
            if (data.getSleepEndTime() > 0) {
                cfg.set(path + ".sleepEndTime", data.getSleepEndTime());
            }
        }
        try {
            cfg.save(dataFile);
            this.dataConfig = cfg;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public SchalkerData createSchalker(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        String id = loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ() + "_" + loc.getWorld().getName();
        if (schalkers.containsKey(id)) return null;

        Location blockLoc = loc.getBlock().getLocation();
        SchalkerData data = new SchalkerData(id, blockLoc, loc.getWorld().getName());
        ensureShulkerBlock(data);
        schalkers.put(id, data);
        saveSchalkers();
        startSchalkerCycle(data);
        return data;
    }

    public boolean removeSchalker(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        String id = loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ() + "_" + loc.getWorld().getName();
        SchalkerData data = schalkers.remove(id);
        if (data == null) return false;

        cancelTasks(data);
        plugin.getHologramManager().removeHologram(data);
        activeInventories.remove(data.getId());

        Block block = loc.getBlock();
        if (isShulkerBox(block.getType())) {
            block.setType(Material.AIR);
        }

        saveSchalkers();
        return true;
    }

    public SchalkerData getSchalkerAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        String id = loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ() + "_" + loc.getWorld().getName();
        return schalkers.get(id);
    }

    public SchalkerData findSchalkerBelow(Player player) {
        Location playerLoc = player.getLocation();
        Location below = playerLoc.clone().subtract(0, 1, 0);
        Block blockBelow = below.getBlock();

        if (isShulkerBox(blockBelow.getType())) {
            SchalkerData data = getSchalkerAt(blockBelow.getLocation());
            if (data != null) return data;
        }

        Location at = playerLoc.getBlock().getLocation();
        SchalkerData data = getSchalkerAt(at);
        if (data != null) return data;

        for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Location check = playerLoc.clone().add(dx, dy, dz);
                    if (isShulkerBox(check.getBlock().getType())) {
                        SchalkerData found = getSchalkerAt(check.getBlock().getLocation());
                        if (found != null) return found;
                    }
                }
            }
        }
        return null;
    }

    public void startSchalkerCycle(SchalkerData data) {
        cancelTasks(data);

        if (!data.tryResolveWorld()) {
            scheduleInitWithRetry(data, 100L);
            return;
        }

        ensureShulkerBlock(data);

        switch (data.getState()) {
            case SLEEPING:
                handleSleepingState(data);
                break;
            case READY:
                onReadyPhase(data);
                break;
            case ACTIVE:
                plugin.getLogger().info("Schalker " + data.getId() + " was ACTIVE on load, resetting to SLEEPING");
                data.getHiddenLoot().clear();
                activeInventories.remove(data.getId());
                handleSleepingState(data);
                break;
            case EXPLODING:
                performExplosionAndSpawn(data);
                break;
        }
    }

    private void handleSleepingState(SchalkerData data) {
        long savedChangeTime = data.getStateChangeTime();
        long savedSleepEnd = data.getSleepEndTime();
        long now = System.currentTimeMillis();

        if (savedSleepEnd > 0) {
            long remainingMs = savedSleepEnd - now;
            if (remainingMs <= 0) {
                plugin.getLogger().info("Schalker " + data.getId() + " sleep expired during offline, triggering spawn");
                performExplosionAndSpawn(data);
                return;
            }

            // Восстанавливаем состояние корректно
            // setState сбрасывает время, поэтому сохраняем и восстанавливаем
            data.setState(SchalkerState.SLEEPING);
            data.setStateChangeTime(savedChangeTime > 0 ? savedChangeTime : now);
            data.setSleepEndTime(savedSleepEnd);

            updateShulkerColor(data, DyeColor.GRAY);
            startHologramUpdater(data, savedSleepEnd);

            long remainingTicks = Math.max(20L, (remainingMs / 1000L) * 20L);
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    performExplosionAndSpawn(data);
                }
            }.runTaskLater(plugin, remainingTicks);
            data.setTaskId(task.getTaskId());
            return;
        }

        // Если sleepEnd нет (старый файл) — проверяем elapsed
        ConfigManager cm = plugin.getConfigManager();
        int maxMin = cm.getMaxSleepMinutes();
        long elapsed = now - savedChangeTime;
        long maxElapsedMs = (long) maxMin * 60L * 1000L;

        if (elapsed >= maxElapsedMs && savedChangeTime > 0) {
            performExplosionAndSpawn(data);
            return;
        }

        // Новый цикл сна
        startNewSleepPhase(data);
    }

    private void startNewSleepPhase(SchalkerData data) {
        data.setState(SchalkerState.SLEEPING);
        updateShulkerColor(data, DyeColor.GRAY);

        ConfigManager cm = plugin.getConfigManager();
        int minMin = cm.getMinSleepMinutes();
        int maxMin = cm.getMaxSleepMinutes();
        Random rand = new Random();
        int sleepMinutes = minMin + rand.nextInt(Math.max(1, maxMin - minMin + 1));
        long sleepTicks = sleepMinutes * 60L * 20L;

        long now = System.currentTimeMillis();
        long wakeUpTime = now + (sleepMinutes * 60L * 1000L);
        data.setStateChangeTime(now);
        data.setSleepEndTime(wakeUpTime);

        startHologramUpdater(data, wakeUpTime);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                performExplosionAndSpawn(data);
            }
        }.runTaskLater(plugin, sleepTicks);
        data.setTaskId(task.getTaskId());
        saveSchalkers();
    }

    // Старое имя для совместимости вызовов из других мест (если остались)
    private void startSleepPhase(SchalkerData data) {
        startNewSleepPhase(data);
    }

    private void performExplosionAndSpawn(SchalkerData data) {
        if (!data.tryResolveWorld()) {
            scheduleInitWithRetry(data, 100L);
            return;
        }

        data.setState(SchalkerState.EXPLODING);
        data.setSleepEndTime(0);
        plugin.getHologramManager().updateHologram(data, null, 0);

        Location baseLoc = data.getLocation();
        if (baseLoc == null) return;
        Location loc = baseLoc.clone().add(0.5, 0.5, 0.5);
        World world = loc.getWorld();
        if (world == null) {
            world = data.getResolvedWorld();
            if (world == null) return;
            loc.setWorld(world);
        }

        ConfigManager cm = plugin.getConfigManager();
        double radius = cm.getExplosionRadius();
        double damage = cm.getExplosionDamage();

        try {
            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);
            world.spawnParticle(Particle.EXPLOSION_HUGE, loc, 3);
            world.spawnParticle(Particle.FLAME, loc, 50, 2, 2, 2, 0.1);
            world.spawnParticle(Particle.SMOKE_LARGE, loc, 30, 2, 2, 2, 0.05);
        } catch (Exception ignored) {}

        for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (entity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) entity;
                double dist = living.getLocation().distance(loc);
                double dmg = damage * (1.0 - (dist / radius));
                if (dmg > 0) {
                    living.damage(dmg);
                }
                if (entity instanceof Player) {
                    Player p = (Player) entity;
                    p.sendMessage(ColorUtil.colorize(cm.getMessage("schalker-exploded")));
                }
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!data.tryResolveWorld()) {
                scheduleInitWithRetry(data, 20L);
                return;
            }
            Rarity rarity = rollRarity();
            data.setCurrentRarity(rarity);
            data.setState(SchalkerState.READY);
            data.setSleepEndTime(0);
            updateShulkerColor(data, getColorForRarity(rarity));
            onReadyPhase(data);
            saveSchalkers();
        }, 20L);
    }

    public void spawnWithRarity(SchalkerData data, Rarity rarity) {
        if (!data.tryResolveWorld()) return;
        cancelTasks(data);
        data.setCurrentRarity(rarity);
        data.setState(SchalkerState.EXPLODING);
        data.setSleepEndTime(0);

        Location baseLoc = data.getLocation();
        if (baseLoc != null) {
            Location loc = baseLoc.clone().add(0.5, 0.5, 0.5);
            World world = loc.getWorld();
            if (world == null) world = data.getResolvedWorld();
            if (world != null) {
                try {
                    world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);
                    world.spawnParticle(Particle.EXPLOSION_HUGE, loc, 1);
                } catch (Exception ignored) {}
            }
        }

        data.setState(SchalkerState.READY);
        data.setSleepEndTime(0);
        updateShulkerColor(data, getColorForRarity(rarity));
        onReadyPhase(data);
        saveSchalkers();
    }

    private void onReadyPhase(SchalkerData data) {
        cancelHologramTask(data);
        if (!data.tryResolveWorld()) {
            scheduleInitWithRetry(data, 100L);
            return;
        }
        ensureShulkerBlock(data);
        if (data.getCurrentRarity() != null) {
            updateShulkerColor(data, getColorForRarity(data.getCurrentRarity()));
        } else {
            Rarity rarity = rollRarity();
            data.setCurrentRarity(rarity);
            updateShulkerColor(data, getColorForRarity(rarity));
        }
        plugin.getHologramManager().updateHologram(data, null, 0);
        saveSchalkers();
    }

    public Inventory openSchalkerInventory(SchalkerData data, Player player) {
        if (data.getState() != SchalkerState.READY) return null;
        if (!data.tryResolveWorld()) return null;

        data.setState(SchalkerState.ACTIVE);
        data.setSleepEndTime(0);
        ConfigManager cm = plugin.getConfigManager();
        int size = cm.getInventorySize(data.getCurrentRarity());
        String rarityName = cm.getRarityName(data.getCurrentRarity());
        String hexColor = cm.getRarityHexColor(data.getCurrentRarity());

        String title = ColorUtil.colorize(hexColor + "Шалкер " + rarityName);
        Inventory inv = Bukkit.createInventory(null, size, title);

        data.getHiddenLoot().clear();

        ItemStack maskItem = new ItemStack(Material.BLACK_DYE);
        org.bukkit.inventory.meta.ItemMeta meta = maskItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize("&8???"));
            maskItem.setItemMeta(meta);
        }

        List<ItemStack> loot = plugin.getLootManager().generateRandomLoot(data.getCurrentRarity(), size);
        for (int i = 0; i < loot.size() && i < size; i++) {
            if (loot.get(i) != null) {
                data.getHiddenLoot().put(i, loot.get(i));
                inv.setItem(i, maskItem.clone());
            }
        }

        activeInventories.put(data.getId(), inv);
        player.openInventory(inv);

        int lootTime = cm.getLootTimeSeconds();
        long endTime = System.currentTimeMillis() + (lootTime * 1000L);

        startActiveHologramUpdater(data, endTime);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                closeActiveSchalker(data);
            }
        }.runTaskLater(plugin, lootTime * 20L);
        data.setTaskId(task.getTaskId());

        String msg = cm.getMessage("schalker-opened")
                .replace("{time}", String.valueOf(lootTime));
        player.sendMessage(ColorUtil.colorize(msg));

        saveSchalkers();
        return inv;
    }

    public void closeActiveSchalker(SchalkerData data) {
        if (data.getState() != SchalkerState.ACTIVE) return;

        Inventory inv = activeInventories.remove(data.getId());
        if (inv != null) {
            for (org.bukkit.entity.HumanEntity viewer : new ArrayList<>(inv.getViewers())) {
                viewer.closeInventory();
                if (viewer instanceof Player) {
                    Player p = (Player) viewer;
                    p.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getMessage("schalker-closed-time")));
                }
            }
        }

        data.getHiddenLoot().clear();
        cancelTasks(data);
        handleSleepingState(data);
        saveSchalkers();
    }

    public boolean isSchalkerInventory(Inventory inv) {
        return activeInventories.containsValue(inv);
    }

    public SchalkerData getSchalkerByInventory(Inventory inv) {
        for (Map.Entry<String, Inventory> entry : activeInventories.entrySet()) {
            if (entry.getValue().equals(inv)) {
                return schalkers.get(entry.getKey());
            }
        }
        return null;
    }

    public Inventory getActiveInventory(String id) {
        return activeInventories.get(id);
    }

    private Rarity rollRarity() {
        ConfigManager cm = plugin.getConfigManager();
        Random rand = new Random();
        int roll = rand.nextInt(100) + 1;

        int legendaryChance = cm.getRarityChance(Rarity.LEGENDARY);
        int mythicalChance = cm.getRarityChance(Rarity.MYTHICAL);
        int rareChance = cm.getRarityChance(Rarity.RARE);

        if (roll <= legendaryChance) return Rarity.LEGENDARY;
        if (roll <= mythicalChance) return Rarity.MYTHICAL;
        if (roll <= rareChance) return Rarity.RARE;
        return Rarity.COMMON;
    }

    private DyeColor getColorForRarity(Rarity rarity) {
        switch (rarity) {
            case RARE: return DyeColor.BLUE;
            case MYTHICAL: return DyeColor.RED;
            case LEGENDARY: return DyeColor.YELLOW;
            default: return DyeColor.LIGHT_GRAY;
        }
    }

    private Material getShulkerMaterial(DyeColor color) {
        switch (color) {
            case BLUE: return Material.BLUE_SHULKER_BOX;
            case RED: return Material.RED_SHULKER_BOX;
            case YELLOW: return Material.YELLOW_SHULKER_BOX;
            case GRAY: return Material.GRAY_SHULKER_BOX;
            case LIGHT_GRAY: return Material.LIGHT_GRAY_SHULKER_BOX;
            default: return Material.SHULKER_BOX;
        }
    }

    private void updateShulkerColor(SchalkerData data, DyeColor color) {
        if (!data.tryResolveWorld()) return;
        Location loc = data.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        try {
            World world = loc.getWorld();
            int chunkX = loc.getBlockX() >> 4;
            int chunkZ = loc.getBlockZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.loadChunk(chunkX, chunkZ, true);
            }
        } catch (Exception ignored) {}

        Block block = loc.getBlock();
        Material newMat = getShulkerMaterial(color);
        if (block.getType() != newMat) {
            block.setType(newMat);
        }
    }

    private void ensureShulkerBlock(SchalkerData data) {
        if (!data.tryResolveWorld()) return;
        Location loc = data.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        try {
            World world = loc.getWorld();
            int chunkX = loc.getBlockX() >> 4;
            int chunkZ = loc.getBlockZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.loadChunk(chunkX, chunkZ, true);
            }
        } catch (Exception ignored) {}

        Block block = loc.getBlock();
        if (!isShulkerBox(block.getType())) {
            block.setType(Material.GRAY_SHULKER_BOX);
        }
    }

    private void ensureShulkerBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        try {
            World world = loc.getWorld();
            int chunkX = loc.getBlockX() >> 4;
            int chunkZ = loc.getBlockZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.loadChunk(chunkX, chunkZ, true);
            }
        } catch (Exception ignored) {}

        Block block = loc.getBlock();
        if (!isShulkerBox(block.getType())) {
            block.setType(Material.GRAY_SHULKER_BOX);
        }
    }

    public static boolean isShulkerBox(Material mat) {
        return mat != null && mat.name().contains("SHULKER_BOX");
    }

    private void cancelTasks(SchalkerData data) {
        if (data.getTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(data.getTaskId());
            data.setTaskId(-1);
        }
        cancelHologramTask(data);
    }

    private void cancelHologramTask(SchalkerData data) {
        if (data.getHologramTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(data.getHologramTaskId());
            data.setHologramTaskId(-1);
        }
    }

    private void startHologramUpdater(SchalkerData data, long wakeUpTime) {
        cancelHologramTask(data);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (data.getState() != SchalkerState.SLEEPING) {
                    cancel();
                    return;
                }
                long remaining = (wakeUpTime - System.currentTimeMillis()) / 1000;
                if (remaining < 0) remaining = 0;
                try {
                    plugin.getHologramManager().updateHologram(data, null, remaining);
                } catch (Exception ignored) {}
            }
        }.runTaskTimer(plugin, 0L, 20L);
        data.setHologramTaskId(task.getTaskId());
    }

    private void startActiveHologramUpdater(SchalkerData data, long endTime) {
        cancelHologramTask(data);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (data.getState() != SchalkerState.ACTIVE) {
                    cancel();
                    return;
                }
                long remaining = (endTime - System.currentTimeMillis()) / 1000;
                if (remaining < 0) remaining = 0;
                try {
                    plugin.getHologramManager().updateHologram(data, null, remaining);
                } catch (Exception ignored) {}
            }
        }.runTaskTimer(plugin, 0L, 20L);
        data.setHologramTaskId(task.getTaskId());
    }

    public void shutdown() {
        try {
            saveSchalkers();
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (SchalkerData data : schalkers.values()) {
            cancelTasks(data);
            Inventory inv = activeInventories.get(data.getId());
            if (inv != null) {
                for (org.bukkit.entity.HumanEntity viewer : new ArrayList<>(inv.getViewers())) {
                    try {
                        viewer.closeInventory();
                    } catch (Exception ignored) {}
                }
            }
        }
        activeInventories.clear();
    }

    public Collection<SchalkerData> getAllSchalkers() {
        return schalkers.values();
    }
}
