package ru.rooyzee.elytrixairdrop.services;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixairdrop.Main;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class LootFileService {

    private final Main plugin;
    private final File lootFolder;
    private final Map<String, Map<Integer, List<ItemStack>>> lootTables = new ConcurrentHashMap<>();

    public LootFileService(Main plugin) {
        this.plugin = plugin;
        this.lootFolder = new File(plugin.getDataFolder(), "loot");
        if (!lootFolder.exists()) lootFolder.mkdirs();
        loadAll();
    }

    public void loadAll() {
        lootTables.clear();
        File[] files = lootFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                String id = f.getName().replace(".yml", "");
                loadType(id);
            }
        }
    }

    public void loadType(String id) {
        File file = new File(lootFolder, id + ".yml");
        Map<Integer, List<ItemStack>> pages = new HashMap<>();
        for (int p = 1; p <= 4; p++) {
            pages.put(p, new ArrayList<ItemStack>());
        }

        if (file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            boolean hasPages = false;
            for (int p = 1; p <= 4; p++) {
                if (config.contains("page_" + p)) {
                    hasPages = true;
                    List<?> list = config.getList("page_" + p);
                    if (list != null) {
                        List<ItemStack> items = new ArrayList<>();
                        for (Object obj : list) {
                            if (obj instanceof ItemStack) items.add((ItemStack) obj);
                        }
                        pages.put(p, items);
                    }
                }
            }

            // Миграция старых файлов с ключом "items"
            if (!hasPages && config.contains("items")) {
                List<?> list = config.getList("items");
                if (list != null) {
                    List<ItemStack> items = new ArrayList<>();
                    for (Object obj : list) {
                        if (obj instanceof ItemStack) items.add((ItemStack) obj);
                    }
                    pages.put(1, items);
                    saveType(id, pages);
                }
            }
        } else {
            saveType(id, pages);
        }

        lootTables.put(id, pages);
    }

    public void saveType(String id, Map<Integer, List<ItemStack>> pages) {
        lootTables.put(id, pages);
        File file = new File(lootFolder, id + ".yml");
        FileConfiguration config = new YamlConfiguration();

        for (int p = 1; p <= 4; p++) {
            List<ItemStack> list = pages.get(p);
            config.set("page_" + p, list != null ? list : Collections.emptyList());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save loot file " + file.getName() + ": " + e.getMessage());
        }
    }

    public List<ItemStack> getItems(String id, int page) {
        Map<Integer, List<ItemStack>> pages = lootTables.get(id);
        if (pages == null) {
            loadType(id);
            pages = lootTables.get(id);
        }
        if (pages == null) return Collections.emptyList();
        List<ItemStack> list = pages.get(page);
        return list != null ? list : Collections.emptyList();
    }

    public void setItems(String id, int page, List<ItemStack> items) {
        Map<Integer, List<ItemStack>> pages = lootTables.computeIfAbsent(id, k -> new HashMap<>());
        pages.put(page, new ArrayList<>(items));
        saveType(id, pages);
    }

    // Совместимость с методом load(id)
    public List<ItemStack> load(String id) {
        List<ItemStack> all = new ArrayList<>();
        for (int p = 1; p <= 4; p++) {
            all.addAll(getItems(id, p));
        }
        return all;
    }

    public void save(String id, List<ItemStack> items) {
        setItems(id, 1, items);
    }

    public void invalidate(String id) {
        if (id == null) lootTables.clear();
        else lootTables.remove(id);
    }

    public void invalidateAll() {
        lootTables.clear();
    }

    /**
     * Динамический расчет лута в зависимости от онлайна сервера (1-3, 4-6, 7+ игроков).
     */
    public List<ItemStack> rollDrops(String id, int playerCount) {
        List<ItemStack> result = new ArrayList<>();

        double[] chances;
        int maxPicks;

        if (playerCount <= 3) {
            // 1-3 игрока: базовый лут (2-3 предмета)
            chances = new double[]{75.0, 25.0, 5.0, 1.0};
            maxPicks = 3;
        } else if (playerCount <= 6) {
            // 4-6 игроков: улучшенный лут (4-5 предметов)
            chances = new double[]{90.0, 50.0, 20.0, 5.0};
            maxPicks = 5;
        } else {
            // 7+ игроков: максимальный сочный лут (6-8 предметов)
            chances = new double[]{100.0, 75.0, 45.0, 15.0};
            maxPicks = 8;
        }

        for (int page = 1; page <= 4; page++) {
            if (result.size() >= maxPicks) break;
            double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
            if (roll <= chances[page - 1]) {
                List<ItemStack> items = getItems(id, page);
                if (!items.isEmpty()) {
                    ItemStack template = items.get(ThreadLocalRandom.current().nextInt(items.size()));
                    if (template != null && template.getType() != Material.AIR) {
                        result.add(template.clone());
                    }
                }
            }
        }

        // Если лут в тирах не выпал или мало, добираем из доступных
        if (result.isEmpty()) {
            List<ItemStack> fallback = load(id);
            if (!fallback.isEmpty()) {
                int picks = Math.min(maxPicks, fallback.size());
                Collections.shuffle(fallback);
                for (int i = 0; i < picks; i++) {
                    result.add(fallback.get(i).clone());
                }
            }
        }

        return result;
    }
}
