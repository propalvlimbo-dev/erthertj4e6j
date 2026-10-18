package ru.rooyzee.elytrixschalkerpvp.manager;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixschalkerpvp.Main;
import ru.rooyzee.elytrixschalkerpvp.model.Rarity;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class LootManager {

    private final Main plugin;
    private final File lootFile;
    private FileConfiguration lootConfig;
    private final Map<Rarity, Map<Integer, List<ItemStack>>> lootTables = new EnumMap<>(Rarity.class);

    public LootManager(Main plugin) {
        this.plugin = plugin;
        this.lootFile = new File(plugin.getDataFolder(), "loot.yml");
        loadLoot();
    }

    public void loadLoot() {
        if (!lootFile.exists()) {
            try {
                lootFile.getParentFile().mkdirs();
                lootFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        lootConfig = YamlConfiguration.loadConfiguration(lootFile);
        lootTables.clear();

        for (Rarity rarity : Rarity.values()) {
            Map<Integer, List<ItemStack>> pages = new HashMap<>();
            for (int p = 1; p <= 4; p++) {
                pages.put(p, new ArrayList<ItemStack>());
            }

            boolean hasPages = false;
            for (int p = 1; p <= 4; p++) {
                String path = "loot." + rarity.name() + ".page_" + p;
                if (lootConfig.contains(path)) {
                    hasPages = true;
                    List<?> list = lootConfig.getList(path);
                    if (list != null) {
                        List<ItemStack> items = new ArrayList<>();
                        for (Object obj : list) {
                            if (obj instanceof ItemStack) items.add((ItemStack) obj);
                        }
                        pages.put(p, items);
                    }
                }
            }

            // Миграция старых списков
            if (!hasPages && lootConfig.contains("loot." + rarity.name())) {
                List<?> list = lootConfig.getList("loot." + rarity.name());
                if (list != null) {
                    List<ItemStack> items = new ArrayList<>();
                    for (Object obj : list) {
                        if (obj instanceof ItemStack) items.add((ItemStack) obj);
                    }
                    pages.put(1, items);
                }
            }

            lootTables.put(rarity, pages);
        }
    }

    public void saveLoot(Rarity rarity, int page, List<ItemStack> items) {
        Map<Integer, List<ItemStack>> pages = lootTables.computeIfAbsent(rarity, k -> new HashMap<>());
        List<ItemStack> filtered = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) filtered.add(item);
        }
        pages.put(page, filtered);

        lootConfig.set("loot." + rarity.name() + ".page_" + page, filtered);
        try {
            lootConfig.save(lootFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<ItemStack> getItems(Rarity rarity, int page) {
        Map<Integer, List<ItemStack>> pages = lootTables.get(rarity);
        if (pages == null) return Collections.emptyList();
        List<ItemStack> list = pages.get(page);
        return list != null ? list : Collections.emptyList();
    }

    public List<ItemStack> getLootTable(Rarity rarity) {
        List<ItemStack> all = new ArrayList<>();
        for (int p = 1; p <= 4; p++) {
            all.addAll(getItems(rarity, p));
        }
        return all;
    }

    /**
     * Динамическая генерация лута с масштабированием от онлайна (1-3, 4-6, 7+).
     */
    public List<ItemStack> generateRandomLoot(Rarity rarity, int inventorySize, int playerCount) {
        List<ItemStack> result = new ArrayList<>(Collections.nCopies(inventorySize, null));

        double[] chances;
        int maxItems;

        if (playerCount <= 3) {
            chances = new double[]{75.0, 25.0, 5.0, 1.0};
            maxItems = Math.max(2, inventorySize / 4);
        } else if (playerCount <= 6) {
            chances = new double[]{90.0, 50.0, 20.0, 5.0};
            maxItems = Math.max(3, inventorySize / 3);
        } else {
            chances = new double[]{100.0, 75.0, 45.0, 15.0};
            maxItems = Math.max(4, inventorySize / 2);
        }

        List<ItemStack> picked = new ArrayList<>();
        for (int page = 1; page <= 4; page++) {
            if (picked.size() >= maxItems) break;
            double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
            if (roll <= chances[page - 1]) {
                List<ItemStack> items = getItems(rarity, page);
                if (!items.isEmpty()) {
                    ItemStack template = items.get(ThreadLocalRandom.current().nextInt(items.size()));
                    if (template != null && template.getType() != Material.AIR) {
                        picked.add(template.clone());
                    }
                }
            }
        }

        if (picked.isEmpty()) {
            List<ItemStack> fallback = getLootTable(rarity);
            if (!fallback.isEmpty()) {
                Collections.shuffle(fallback);
                for (int i = 0; i < Math.min(maxItems, fallback.size()); i++) {
                    picked.add(fallback.get(i).clone());
                }
            }
        }

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventorySize; i++) slots.add(i);
        Collections.shuffle(slots);

        for (int i = 0; i < picked.size() && i < slots.size(); i++) {
            result.set(slots.get(i), picked.get(i));
        }

        return result;
    }

    public List<ItemStack> generateRandomLoot(Rarity rarity, int inventorySize) {
        int online = org.bukkit.Bukkit.getOnlinePlayers().size();
        return generateRandomLoot(rarity, inventorySize, online);
    }
}
