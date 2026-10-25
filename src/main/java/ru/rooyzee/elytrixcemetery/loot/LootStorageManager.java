package ru.rooyzee.elytrixcemetery.loot;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
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
            // Если файл был пустой, заполняем дефолтным
            boolean empty = true;
            for (int p = 1; p <= 4; p++) {
                if (!pages.get(p).isEmpty()) {
                    empty = false;
                    break;
                }
            }
            if (empty) {
                generateDefaultLoot(type, pages);
                saveType(type, pages);
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
        int targetStacks;

        if (playerCount <= 3) {
            chances = new double[]{85.0, 45.0, 15.0, 5.0};
            targetStacks = 4;
        } else if (playerCount <= 6) {
            chances = new double[]{95.0, 60.0, 30.0, 12.0};
            targetStacks = 6;
        } else {
            chances = new double[]{100.0, 80.0, 50.0, 25.0};
            targetStacks = 8;
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int page = 1; page <= 4; page++) {
            List<ItemStack> pool = getItems(LootType.GRAVES, page);
            if (pool.isEmpty()) continue;

            double roll = rng.nextDouble(0.0, 100.0);
            if (roll <= chances[page - 1]) {
                int picks = Math.max(1, targetStacks / 2);
                for (int i = 0; i < picks; i++) {
                    if (result.size() >= targetStacks) break;
                    ItemStack sample = pool.get(rng.nextInt(pool.size()));
                    if (sample != null && sample.getType() != Material.AIR) {
                        result.add(sample.clone());
                    }
                }
            }
        }

        // Гарантия, что сундук никогда не останется пустым
        if (result.isEmpty()) {
            List<ItemStack> p1 = getItems(LootType.GRAVES, 1);
            if (!p1.isEmpty()) {
                result.add(p1.get(rng.nextInt(p1.size())).clone());
            } else {
                result.add(new ItemStack(Material.GOLDEN_APPLE, 2));
                result.add(new ItemStack(Material.DIAMOND, 4));
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

    public List<ItemStack> rollGuardianDrops() {
        List<ItemStack> result = new ArrayList<>();
        result.add(createGhostShard(2 + ThreadLocalRandom.current().nextInt(3)));

        // 2 редких предмета
        for (int p = 3; p <= 4; p++) {
            List<ItemStack> items = getItems(LootType.GRAVES, p);
            if (!items.isEmpty()) {
                ItemStack template = items.get(ThreadLocalRandom.current().nextInt(items.size()));
                if (template != null && template.getType() != Material.AIR) {
                    result.add(template.clone());
                }
            }
        }
        if (result.size() < 2) {
            result.add(new ItemStack(Material.NETHERITE_INGOT, 1));
            result.add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
        }
        return result;
    }

    private void generateDefaultLoot(LootType type, Map<Integer, List<ItemStack>> pages) {
        for (int p = 1; p <= 4; p++) {
            pages.put(p, new ArrayList<ItemStack>());
        }

        if (type == LootType.GRAVES) {
            // Страница 1 (Обычный лут)
            List<ItemStack> p1 = pages.get(1);
            p1.add(new ItemStack(Material.GOLDEN_APPLE, 3));
            p1.add(new ItemStack(Material.DIAMOND, 6));
            p1.add(new ItemStack(Material.EMERALD, 12));
            p1.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 16));
            p1.add(new ItemStack(Material.ENDER_PEARL, 6));
            p1.add(new ItemStack(Material.GOLDEN_CARROT, 12));
            p1.add(new ItemStack(Material.IRON_INGOT, 16));
            p1.add(new ItemStack(Material.GOLD_INGOT, 12));
            p1.add(new ItemStack(Material.OBSIDIAN, 8));

            // Страница 2 (Необычный лут)
            List<ItemStack> p2 = pages.get(2);
            p2.add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
            p2.add(matchItem("SHULKER_SHELL", 3));
            p2.add(matchItem("NETHERITE_SCRAP", 2));
            p2.add(createEnchantedBook(Enchantment.MENDING, 1));
            p2.add(createEnchantedBook(Enchantment.PROTECTION_ENVIRONMENTAL, 4));
            p2.add(createEnchantedBook(Enchantment.DAMAGE_ALL, 5));
            p2.add(createEnchantedBook(Enchantment.DURABILITY, 3));

            // Страница 3 (Редкий лут)
            List<ItemStack> p3 = pages.get(3);
            p3.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
            p3.add(matchItem("NETHERITE_INGOT", 2));
            p3.add(new ItemStack(Material.DIAMOND_BLOCK, 2));
            p3.add(new ItemStack(Material.EMERALD_BLOCK, 4));
            p3.add(createGear(Material.ELYTRA, "&#FFBD59Элитры Скитальца", Enchantment.DURABILITY, 3, Enchantment.MENDING, 1));
            p3.add(createGear(Material.TRIDENT, "&#C58BFFТрезубец Хранителя", Enchantment.IMPALING, 5, Enchantment.DURABILITY, 3));

            // Страница 4 (Легендарный лут)
            List<ItemStack> p4 = pages.get(4);
            p4.add(matchItem("NETHERITE_BLOCK", 1));
            p4.add(createGear(matchMaterial("NETHERITE_CHESTPLATE", "DIAMOND_CHESTPLATE"), "&#FF5E8AДоспех Правды", Enchantment.PROTECTION_ENVIRONMENTAL, 4, Enchantment.DURABILITY, 3, Enchantment.MENDING, 1));
            p4.add(createGear(matchMaterial("NETHERITE_HELMET", "DIAMOND_HELMET"), "&#F8BEFBШлем Стража", Enchantment.PROTECTION_ENVIRONMENTAL, 4, Enchantment.DURABILITY, 3, Enchantment.MENDING, 1));
            p4.add(matchItem("NETHERITE_UPGRADE_SMITHING_TEMPLATE", 1));
            p4.add(createGhostShard(4));
        } else if (type == LootType.GHOSTS) {
            List<ItemStack> p1 = pages.get(1);
            p1.add(new ItemStack(Material.ENDER_PEARL, 4));
            p1.add(new ItemStack(Material.GOLDEN_APPLE, 2));
            p1.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 12));

            List<ItemStack> p2 = pages.get(2);
            p2.add(new ItemStack(Material.DIAMOND, 4));
            p2.add(matchItem("SHULKER_SHELL", 2));
            p2.add(createEnchantedBook(Enchantment.MENDING, 1));

            List<ItemStack> p3 = pages.get(3);
            p3.add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
            p3.add(matchItem("NETHERITE_SCRAP", 2));

            List<ItemStack> p4 = pages.get(4);
            p4.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
            p4.add(matchItem("NETHERITE_INGOT", 1));
        }
    }

    private ItemStack matchItem(String name, int amount) {
        Material m = Material.matchMaterial(name);
        if (m == null) m = Material.DIAMOND;
        return new ItemStack(m, Math.max(1, amount));
    }

    private Material matchMaterial(String primary, String fallback) {
        Material m = Material.matchMaterial(primary);
        if (m != null) return m;
        m = Material.matchMaterial(fallback);
        return m != null ? m : Material.DIAMOND;
    }

    private ItemStack createEnchantedBook(Enchantment ench, int level) {
        ItemStack is = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = is.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta) {
            ((EnchantmentStorageMeta) meta).addStoredEnchant(ench, level, true);
            is.setItemMeta(meta);
        }
        return is;
    }

    private ItemStack createGear(Material mat, String name, Object... enchants) {
        ItemStack is = new ItemStack(mat != null ? mat : Material.DIAMOND_SWORD);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            for (int i = 0; i < enchants.length; i += 2) {
                if (i + 1 < enchants.length && enchants[i] instanceof Enchantment && enchants[i + 1] instanceof Integer) {
                    meta.addEnchant((Enchantment) enchants[i], (Integer) enchants[i + 1], true);
                }
            }
            is.setItemMeta(meta);
        }
        return is;
    }
}
