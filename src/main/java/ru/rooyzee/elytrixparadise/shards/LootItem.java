package ru.rooyzee.elytrixparadise.shards;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class LootItem {

    private final Material material;
    private final int minAmount;
    private final int maxAmount;
    private final double chance; // percentage 0.0 - 100.0
    private final String customName;
    private final List<String> lore;
    private final Map<Enchantment, Integer> enchantments;

    public LootItem(Material material, int minAmount, int maxAmount, double chance,
                    String customName, List<String> lore, Map<Enchantment, Integer> enchantments) {
        this.material = material;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.chance = chance;
        this.customName = customName;
        this.lore = lore != null ? lore : new ArrayList<String>();
        this.enchantments = enchantments != null ? enchantments : new HashMap<Enchantment, Integer>();
    }

    public LootItem(Material material, int minAmount, int maxAmount, double chance,
                    String customName, List<String> lore) {
        this(material, minAmount, maxAmount, chance, customName, lore, null);
    }

    public Material getMaterial() {
        return material;
    }

    public double getChance() {
        return chance;
    }

    public ItemStack createItemStack() {
        int amount = minAmount;
        if (maxAmount > minAmount) {
            amount = ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);
        }
        if (amount <= 0) amount = 1;

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (customName != null && !customName.isEmpty()) {
                meta.setDisplayName(ColorUtil.colorize(customName));
            }
            if (!lore.isEmpty()) {
                meta.setLore(ColorUtil.colorize(lore));
            }
            item.setItemMeta(meta);
        }

        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            if (entry.getKey() != null) {
                item.addUnsafeEnchantment(entry.getKey(), entry.getValue());
            }
        }

        return item;
    }
}
