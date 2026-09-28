package ru.rooyzee.elytrixairdrop.services;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixairdrop.Main;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LootFileService {

    private final Main plugin;
    private final Map<String, List<ItemStack>> cache = new ConcurrentHashMap<>();

    public LootFileService(Main plugin) {
        this.plugin = plugin;
        File folder = new File(plugin.getDataFolder(), "loot");
        if (!folder.exists()) folder.mkdirs();
    }

    public List<ItemStack> load(String id) {
        List<ItemStack> cached = cache.get(id);
        if (cached != null) {
            return cloneList(cached);
        }

        File file = new File(plugin.getDataFolder(), "loot/" + id + ".yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.set("items", new ArrayList<ItemStack>());
            try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
            cache.put(id, new ArrayList<>());
            return new ArrayList<>();
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<?> raw = cfg.getList("items");
        List<ItemStack> out = new ArrayList<>();
        if (raw != null) for (Object o : raw) if (o instanceof ItemStack) out.add((ItemStack) o);
        cache.put(id, cloneList(out));
        return out;
    }

    public void save(String id, List<ItemStack> items) {
        List<ItemStack> copy = cloneList(items);
        cache.put(id, copy);

        File file = new File(plugin.getDataFolder(), "loot/" + id + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("items", copy);
        try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    public void invalidate(String id) {
        if (id == null) cache.clear();
        else cache.remove(id);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private static List<ItemStack> cloneList(List<ItemStack> src) {
        List<ItemStack> out = new ArrayList<>(src.size());
        for (ItemStack it : src) {
            out.add(it == null ? null : it.clone());
        }
        return out;
    }
}
