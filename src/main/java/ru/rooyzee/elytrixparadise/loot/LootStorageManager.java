package ru.rooyzee.elytrixparadise.loot;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class LootStorageManager {

    private final Main plugin;
    private final File lootFolder;

    // LootType -> (Page 1..4 -> List<ItemStack>)
    private final Map<LootType, Map<Integer, List<ItemStack>>> lootTables = new ConcurrentHashMap<>();

    public LootStorageManager(Main plugin) {
        this.plugin = plugin;
        this.lootFolder = new File(plugin.getDataFolder(), "loot");
        if (!lootFolder.exists()) {
            lootFolder.mkdirs();
        }
        loadAll();
    }

    public void loadAll() {
        lootTables.clear();
        for (LootType type : LootType.values()) {
            loadType(type);
        }
    }

    public void loadType(LootType type) {
        File file = new File(lootFolder, type.getFileName());
        Map<Integer, List<ItemStack>> pages = new HashMap<>();
        for (int p = 1; p <= 4; p++) {
            pages.put(p, new ArrayList<ItemStack>());
        }

        if (!file.exists()) {
            generateDefaultLoot(type, pages);
            saveType(type, pages);
        } else {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (int p = 1; p <= 4; p++) {
                List<?> list = config.getList("page_" + p);
                if (list != null) {
                    List<ItemStack> items = new ArrayList<>();
                    for (Object obj : list) {
                        if (obj instanceof ItemStack) {
                            items.add((ItemStack) obj);
                        }
                    }
                    pages.put(p, items);
                }
            }
        }

        lootTables.put(type, pages);
    }

    public void saveType(LootType type, Map<Integer, List<ItemStack>> pages) {
        lootTables.put(type, pages);
        File file = new File(lootFolder, type.getFileName());
        FileConfiguration config = new YamlConfiguration();

        for (int p = 1; p <= 4; p++) {
            List<ItemStack> list = pages.get(p);
            config.set("page_" + p, list != null ? list : Collections.emptyList());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить файл лута " + file.getName() + ": " + e.getMessage());
        }
    }

    public List<ItemStack> getItems(LootType type, int page) {
        Map<Integer, List<ItemStack>> pages = lootTables.get(type);
        if (pages == null) return Collections.emptyList();
        List<ItemStack> list = pages.get(page);
        return list != null ? list : Collections.emptyList();
    }

    public void setItems(LootType type, int page, List<ItemStack> items) {
        Map<Integer, List<ItemStack>> pages = lootTables.computeIfAbsent(type, k -> new HashMap<>());
        pages.put(page, new ArrayList<>(items));
        saveType(type, pages);
    }

    /**
     * Выбирает случайные предметы из таблицы лута Осколков Рая
     */
    public List<ItemStack> rollShardDrops() {
        List<ItemStack> result = new ArrayList<>();

        // Шансы для 4 страниц:
        // 1: Обычный (70%)
        // 2: Редкий (35%)
        // 3: Эпический (10%)
        // 4: Легендарный (3%)
        double[] chances = {70.0, 35.0, 10.0, 3.0};

        for (int page = 1; page <= 4; page++) {
            double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
            if (roll <= chances[page - 1]) {
                List<ItemStack> items = getItems(LootType.SHARDS, page);
                if (!items.isEmpty()) {
                    ItemStack template = items.get(ThreadLocalRandom.current().nextInt(items.size()));
                    if (template != null && template.getType() != Material.AIR) {
                        result.add(template.clone());
                    }
                }
            }
        }

        return result;
    }

    /**
     * Выбирает случайные предметы из таблицы лута Сердца Рая, динамически масштабируя
     * количество и качество предметов в зависимости от числа игроков (1-3, 4-6, 7-10+).
     */
    public List<ItemStack> rollSphereDrops(int playerCount) {
        List<ItemStack> result = new ArrayList<>();

        double[] chances;
        int maxPicks;

        if (playerCount <= 3) {
            // 1-3 игрока: базовый лут, редко что-то крутое, не так много предметов
            chances = new double[]{75.0, 25.0, 5.0, 1.0};
            maxPicks = 2;
        } else if (playerCount <= 6) {
            // 4-6 игроков: улучшенный лут
            chances = new double[]{90.0, 50.0, 20.0, 5.0};
            maxPicks = 4;
        } else {
            // 7-10+ игроков: максимальный сочный лут
            chances = new double[]{100.0, 75.0, 45.0, 15.0};
            maxPicks = 6;
        }

        for (int page = 1; page <= 4; page++) {
            if (result.size() >= maxPicks) break;
            double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
            if (roll <= chances[page - 1]) {
                List<ItemStack> items = getItems(LootType.SPHERE, page);
                if (!items.isEmpty()) {
                    ItemStack template = items.get(ThreadLocalRandom.current().nextInt(items.size()));
                    if (template != null && template.getType() != Material.AIR) {
                        result.add(template.clone());
                    }
                }
            }
        }

        // Если страницы пусты или ничего не выпало — возвращаем пустой список (администратор сам заполнит лут через /ep loot)
        return result;
    }

    private void generateDefaultLoot(LootType type, Map<Integer, List<ItemStack>> pages) {
        // Оставляем пустыми все 4 страницы — администратор сам заполнит лут через GUI меню /ep loot
        for (int p = 1; p <= 4; p++) {
            pages.put(p, new ArrayList<ItemStack>());
        }
    }
}
