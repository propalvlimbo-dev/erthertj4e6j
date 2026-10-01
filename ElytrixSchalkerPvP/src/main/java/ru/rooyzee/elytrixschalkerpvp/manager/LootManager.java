package ru.rooyzee.elytrixschalkerpvp.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixschalkerpvp.Main;
import ru.rooyzee.elytrixschalkerpvp.model.Rarity;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LootManager {

    private final Main plugin;
    private final File lootFile;
    private FileConfiguration lootConfig;
    private final Map<Rarity, List<ItemStack>> lootTables = new EnumMap<>(Rarity.class);

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
            List<ItemStack> items = new ArrayList<>();
            String path = "loot." + rarity.name();
            if (lootConfig.contains(path)) {
                List<?> list = lootConfig.getList(path);
                if (list != null) {
                    for (Object obj : list) {
                        if (obj instanceof ItemStack) {
                            items.add((ItemStack) obj);
                        }
                    }
                }
            }
            lootTables.put(rarity, items);
        }
    }

    public void saveLoot(Rarity rarity, List<ItemStack> items) {
        List<ItemStack> filtered = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null) {
                filtered.add(item);
            }
        }
        lootTables.put(rarity, filtered);
        lootConfig.set("loot." + rarity.name(), filtered);
        try {
            lootConfig.save(lootFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<ItemStack> getLootTable(Rarity rarity) {
        return lootTables.getOrDefault(rarity, new ArrayList<>());
    }

    public List<ItemStack> generateRandomLoot(Rarity rarity, int inventorySize) {
        List<ItemStack> source = getLootTable(rarity);
        if (source.isEmpty()) {
            return new ArrayList<>();
        }

        ConfigManager cm = plugin.getConfigManager();
        int minPercent = cm.getLootFillMin(rarity);
        int maxPercent = cm.getLootFillMax(rarity);

        Random random = new Random();
        int fillPercent = minPercent + random.nextInt(Math.max(1, maxPercent - minPercent + 1));
        int slotsToFill = Math.max(1, (inventorySize * fillPercent) / 100);
        slotsToFill = Math.min(slotsToFill, source.size());

        List<ItemStack> shuffled = new ArrayList<>(source);
        Collections.shuffle(shuffled, random);

        List<ItemStack> selectedItems = new ArrayList<>(shuffled.subList(0, slotsToFill));

        List<ItemStack> result = new ArrayList<>(Collections.nCopies(inventorySize, null));
        List<Integer> availableSlots = new ArrayList<>();
        for (int i = 0; i < inventorySize; i++) {
            availableSlots.add(i);
        }
        Collections.shuffle(availableSlots, random);

        for (int i = 0; i < selectedItems.size(); i++) {
            result.set(availableSlots.get(i), selectedItems.get(i).clone());
        }

        return result;
    }
}