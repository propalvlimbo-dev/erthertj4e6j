package ru.rooyzee.elytrixcemetery.loot;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.util.MaterialUtil;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class LootStorageManager {

    private final Main plugin;
    private final File lootFolder;

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
            plugin.getLogger().severe("Failed to save loot " + file.getName() + ": " + e.getMessage());
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
     * Создание уникального Призрачного осколка
     */
    public ItemStack createGhostShard(int amount) {
        Material shardMat = MaterialUtil.getGhostShardMaterial();
        ItemStack is = new ItemStack(shardMat, Math.max(1, amount));
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize("&#F8BEFB&lПризрачный осколок"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.colorize("&7Древняя призрачная сущность,"));
            lore.add(ColorUtil.colorize("&7добытая с призраков на кладбище."));
            lore.add("");
            lore.add(ColorUtil.colorize("&#F8BEFB• &fИспользуется для торговли и крафтов"));
            meta.setLore(lore);
            is.setItemMeta(meta);
        }
        return is;
    }

    /**
     * Выбирает лут для сундука могилы в зависимости от онлайна сервера.
     */
    public List<ItemStack> rollGraveDrops(int playerCount) {
        List<ItemStack> result = new ArrayList<>();

        double[] chances;
        int maxPicks;

        if (playerCount <= 3) {
            chances = new double[]{75.0, 25.0, 5.0, 1.0};
            maxPicks = 3;
        } else if (playerCount <= 6) {
            chances = new double[]{90.0, 50.0, 20.0, 5.0};
            maxPicks = 5;
        } else {
            chances = new double[]{100.0, 75.0, 45.0, 15.0};
            maxPicks = 7;
        }

        for (int page = 1; page <= 4; page++) {
            if (result.size() >= maxPicks) break;
            double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
            if (roll <= chances[page - 1]) {
                List<ItemStack> items = getItems(LootType.GRAVES, page);
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
     * Выбирает дроп при убийстве призрака
     */
    public List<ItemStack> rollGhostDrops() {
        List<ItemStack> result = new ArrayList<>();
        // Всегда даем 1-2 призрачных осколка
        result.add(createGhostShard(1 + ThreadLocalRandom.current().nextInt(2)));

        // 35% шанс на дополнительный предмет из таблицы GHOSTS
        double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
        if (roll <= 35.0) {
            for (int page = 1; page <= 4; page++) {
                List<ItemStack> items = getItems(LootType.GHOSTS, page);
                if (!items.isEmpty()) {
                    ItemStack template = items.get(ThreadLocalRandom.current().nextInt(items.size()));
                    if (template != null && template.getType() != Material.AIR) {
                        result.add(template.clone());
                        break;
                    }
                }
            }
        }

        return result;
    }

    private void generateDefaultLoot(LootType type, Map<Integer, List<ItemStack>> pages) {
        for (int p = 1; p <= 4; p++) {
            pages.put(p, new ArrayList<ItemStack>());
        }
    }
}
