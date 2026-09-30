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

        // Если ничего не выпало, гарантируем минимум 1 обычный предмет
        if (result.isEmpty()) {
            List<ItemStack> commonItems = getItems(LootType.SPHERE, 1);
            if (!commonItems.isEmpty()) {
                ItemStack template = commonItems.get(ThreadLocalRandom.current().nextInt(commonItems.size()));
                if (template != null && template.getType() != Material.AIR) {
                    result.add(template.clone());
                }
            }
        }

        return result;
    }

    private void generateDefaultLoot(LootType type, Map<Integer, List<ItemStack>> pages) {
        // Page 1: Обычный лут
        List<ItemStack> p1 = new ArrayList<>();
        p1.add(createNamedItem(Material.GOLD_INGOT, 4, "&#F8BEFBЗолотой слиток", "&7● Обычный материал"));
        p1.add(createNamedItem(Material.IRON_INGOT, 5, "&#F8BEFBЖелезный слиток", "&7● Обычный материал"));
        p1.add(createNamedItem(Material.EXPERIENCE_BOTTLE, 3, "&#F8BEFBБутылёк опыта", "&7● Дарует священный опыт"));
        pages.put(1, p1);

        // Page 2: Редкий лут
        List<ItemStack> p2 = new ArrayList<>();
        p2.add(createNamedItem(Material.EMERALD, 2, "&#F8BEFBНебесный Изумруд", "&7● Редкий кристалл Рая"));
        p2.add(createNamedItem(Material.DIAMOND, 2, "&#F8BEFBРайский Алмаз", "&7● Сияющий драгоценный камень"));
        pages.put(2, p2);

        // Page 3: Эпический лут
        List<ItemStack> p3 = new ArrayList<>();
        p3.add(createNamedItem(Material.GOLDEN_APPLE, 1, "&#F8BEFBРайское Яблоко", "&7● Дарует небесную силу"));
        p3.add(createNamedItem(Material.NETHERITE_SCRAP, 1, "&#F8BEFBДревний Осколок", "&7● Редчайший сплав"));
        pages.put(3, p3);

        // Page 4: Легендарный лут
        List<ItemStack> p4 = new ArrayList<>();
        p4.add(createNamedItem(Material.ENCHANTED_GOLDEN_APPLE, 1, "&#F8BEFBСердце Бессмертия", "&7● Легендарный артефакт Рая"));
        p4.add(createNamedItem(Material.NETHERITE_INGOT, 1, "&#F8BEFBНезеритовый слиток Рая", "&7● Легендарный чистый незерит"));
        pages.put(4, p4);
    }

    private ItemStack createNamedItem(Material mat, int amount, String name, String... loreLines) {
        ItemStack is = new ItemStack(mat, amount);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            List<String> lore = new ArrayList<>();
            for (String l : loreLines) {
                lore.add(ColorUtil.colorize(l));
            }
            meta.setLore(lore);
            is.setItemMeta(meta);
        }
        return is;
    }
}
