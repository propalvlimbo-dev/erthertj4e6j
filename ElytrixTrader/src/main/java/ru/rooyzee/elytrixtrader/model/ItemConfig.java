package ru.rooyzee.elytrixtrader.model;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;
import ru.rooyzee.elytrixtrader.util.Cfg;
import ru.rooyzee.elytrixtrader.util.ColorUtil;
import ru.rooyzee.elytrixtrader.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ItemConfig {

    private final Material material;
    private final int amount;
    private final String displayName;
    private final List<String> lore;
    private final boolean glow;
    private final int customModelData;
    private final int durability;
    private final String skullOwner;
    private final PotionType potionType;
    private final boolean extendedPotion;
    private final boolean upgradedPotion;
    private final List<String[]> enchantments;
    /** Готовый ItemStack — если задан, build() возвращает его клон (NBT сохранён полностью). */
    private final ItemStack rawItem;

    public ItemConfig(Material material, int amount, String displayName, List<String> lore, boolean glow,
                      int customModelData, int durability, String skullOwner, PotionType potionType,
                      boolean extendedPotion, boolean upgradedPotion, List<String[]> enchantments) {
        this(material, amount, displayName, lore, glow, customModelData, durability, skullOwner,
                potionType, extendedPotion, upgradedPotion, enchantments, null);
    }

    /** Конструктор с rawItem — для трейдов созданных через редактор (ItemStack as-is). */
    public ItemConfig(ItemStack rawItem) {
        this(rawItem == null ? Material.AIR : rawItem.getType(),
                rawItem == null ? 1 : rawItem.getAmount(),
                null, new ArrayList<>(), false, 0, 0, null, null, false, false, new ArrayList<>(),
                rawItem);
    }

    public ItemConfig(Material material, int amount, String displayName, List<String> lore, boolean glow,
                      int customModelData, int durability, String skullOwner, PotionType potionType,
                      boolean extendedPotion, boolean upgradedPotion, List<String[]> enchantments,
                      ItemStack rawItem) {
        this.material = material;
        this.amount = Math.max(1, amount);
        this.displayName = displayName;
        this.lore = lore;
        this.glow = glow;
        this.customModelData = customModelData;
        this.durability = durability;
        this.skullOwner = skullOwner;
        this.potionType = potionType;
        this.extendedPotion = extendedPotion;
        this.upgradedPotion = upgradedPotion;
        this.enchantments = enchantments;
        this.rawItem = rawItem;
    }

    public static ItemConfig from(ConfigurationSection section, Material fallback, int fallbackAmount) {
        Material material = ColorUtil.material(Cfg.str(section, "material", fallback.name()), fallback);
        int amount = Cfg.num(section, "amount", fallbackAmount);
        ConfigurationSection meta = section == null ? null : section.getConfigurationSection("meta");
        String name = Cfg.str(meta, "display-name", null);
        List<String> lore = Cfg.list(meta, "lore");
        boolean glow = Cfg.bool(meta, "glow", false);
        int model = Cfg.num(meta, "custom-model-data", 0);
        int damage = Cfg.num(meta, "durability", 0);
        String owner = Cfg.str(meta, "skull-owner", null);
        String rawPotion = Cfg.str(meta, "potion-type", null);
        PotionType potion = rawPotion == null ? null : potionType(rawPotion);
        boolean extended = Cfg.bool(meta, "potion-extended", rawPotion != null && (rawPotion.toUpperCase(Locale.ROOT).contains("LONG") || rawPotion.toUpperCase(Locale.ROOT).contains("EXTENDED")));
        boolean upgraded = Cfg.bool(meta, "potion-upgraded", rawPotion != null && (rawPotion.toUpperCase(Locale.ROOT).contains("STRONG") || rawPotion.toUpperCase(Locale.ROOT).contains("UPGRADED") || rawPotion.toUpperCase(Locale.ROOT).contains("II")));
        return new ItemConfig(material, amount, name, lore, glow, model, damage, owner, potion, extended, upgraded, readEnchantments(meta), null);
    }

    private static PotionType potionType(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String formatted = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (formatted.startsWith("LONG_")) {
            formatted = formatted.substring(5);
        } else if (formatted.startsWith("STRONG_")) {
            formatted = formatted.substring(7);
        } else if (formatted.endsWith("_LONG")) {
            formatted = formatted.substring(0, formatted.length() - 5);
        } else if (formatted.endsWith("_STRONG")) {
            formatted = formatted.substring(0, formatted.length() - 7);
        }
        try {
            return PotionType.valueOf(formatted);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static List<String[]> readEnchantments(ConfigurationSection meta) {
        List<String[]> result = new ArrayList<>();
        if (meta == null) {
            return result;
        }
        for (String entry : Cfg.list(meta, "enchantments")) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            String[] parts = entry.split("[:=]");
            String key = parts[0].trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
            int level = 1;
            if (parts.length > 1) {
                try {
                    level = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignored) {
                    level = 1;
                }
            }
            result.add(new String[]{key, String.valueOf(Math.max(1, level))});
        }
        return result;
    }

    public Material material() {
        return material;
    }

    public int amount() {
        return amount;
    }

    public ItemStack rawItem() {
        return rawItem;
    }

    public ItemStack single() {
        List<ItemStack> stacks = build(1);
        return stacks.isEmpty() ? null : stacks.get(0);
    }

    public List<ItemStack> build(int multiply) {
        List<ItemStack> stacks = new ArrayList<>();

        // Если есть готовый ItemStack — клонируем с учётом количества
        if (rawItem != null && rawItem.getType() != Material.AIR) {
            int maxStack = Math.max(1, rawItem.getType().getMaxStackSize());
            int total = Math.max(1, rawItem.getAmount() * Math.max(1, multiply));
            int remaining = total;
            while (remaining > 0) {
                ItemStack clone = rawItem.clone();
                clone.setAmount(Math.min(maxStack, remaining));
                remaining -= clone.getAmount();
                stacks.add(clone);
            }
            return stacks;
        }

        if (material == null || material == Material.AIR) {
            return stacks;
        }
        int maxStack = Math.max(1, material.getMaxStackSize());
        int total = Math.max(1, amount * Math.max(1, multiply));
        int remaining = total;
        while (remaining > 0) {
            ItemStack stack = new ItemStack(material, Math.min(maxStack, remaining));
            remaining -= stack.getAmount();
            applyMeta(stack);
            stacks.add(stack);
        }
        return stacks;
    }

    private void applyMeta(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (displayName != null) {
            meta.setDisplayName(Text.apply(displayName, null));
        }
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(ColorUtil.translate(lore));
        }
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        for (String[] entry : enchantments) {
            Enchantment enchantment = findEnchantment(entry[0]);
            if (enchantment == null) {
                continue;
            }
            int level = 1;
            try {
                level = Integer.parseInt(entry[1]);
            } catch (NumberFormatException ignored) {
                level = 1;
            }
            if (meta instanceof EnchantmentStorageMeta) {
                ((EnchantmentStorageMeta) meta).addStoredEnchant(enchantment, Math.max(1, level), true);
            } else {
                meta.addEnchant(enchantment, Math.max(1, level), true);
            }
        }
        if (glow && enchantments.isEmpty()) {
            Enchantment unbreaking = findEnchantment("DURABILITY");
            if (unbreaking != null) {
                if (meta instanceof EnchantmentStorageMeta) {
                    ((EnchantmentStorageMeta) meta).addStoredEnchant(unbreaking, 1, true);
                } else {
                    meta.addEnchant(unbreaking, 1, true);
                }
            }
        }
        if (glow || !enchantments.isEmpty()) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (durability > 0 && meta instanceof Damageable) {
            ((Damageable) meta).setDamage(durability);
        }
        if (potionType != null && meta instanceof PotionMeta) {
            ((PotionMeta) meta).setBasePotionData(new PotionData(potionType, extendedPotion, upgradedPotion));
        }
        item.setItemMeta(meta);
        if (skullOwner == null || skullOwner.trim().isEmpty() || !(item.getItemMeta() instanceof SkullMeta)) {
            return;
        }
        SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
        @SuppressWarnings("deprecation")
        OfflinePlayer player = Bukkit.getOfflinePlayer(skullOwner.trim());
        skullMeta.setOwningPlayer(player);
        item.setItemMeta(skullMeta);
    }

    private Enchantment findEnchantment(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        String clean = key.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            Enchantment byKey = Enchantment.getByKey(NamespacedKey.minecraft(clean));
            if (byKey != null) {
                return byKey;
            }
        } catch (Throwable ignored) {
        }
        @SuppressWarnings("deprecation")
        Enchantment byName = Enchantment.getByName(key.trim().toUpperCase(Locale.ROOT));
        if (byName != null) {
            return byName;
        }
        for (Enchantment enchantment : Enchantment.values()) {
            if (enchantment != null && (enchantment.getName().equalsIgnoreCase(key) || enchantment.getKey().getKey().equalsIgnoreCase(clean))) {
                return enchantment;
            }
        }
        return null;
    }
}
