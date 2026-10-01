package ru.rooyzee.elytrixtrader.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ru.rooyzee.elytrixtrader.model.IconConfig;
import ru.rooyzee.elytrixtrader.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IconFactory {

    private IconFactory() {
    }

    public static ItemStack build(IconConfig icon, Player player, Map<String, String> placeholders) {
        return build(icon, player, placeholders, 1);
    }

    public static ItemStack build(IconConfig icon, Player player, Map<String, String> placeholders, int amount) {
        Material material = icon.material() == null ? Material.PAPER : icon.material();
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (icon.displayName() != null) {
            meta.setDisplayName(Text.apply(icon.displayName(), player, placeholders));
        }
        List<String> lore = new ArrayList<>();
        if (icon.lore() != null) {
            for (String line : icon.lore()) {
                String applied = Text.apply(line, player, placeholders);
                if (applied == null || applied.isEmpty()) {
                    continue;
                }
                lore.add(applied);
            }
        }
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        if (icon.glow()) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (icon.customModelData() > 0) {
            meta.setCustomModelData(icon.customModelData());
        }
        item.setItemMeta(meta);
        if (icon.skin() != null && !icon.skin().trim().isEmpty() && material == Material.PLAYER_HEAD && item.getItemMeta() instanceof SkullMeta) {
            SkullMeta skull = (SkullMeta) item.getItemMeta();
            @SuppressWarnings("deprecation")
            OfflinePlayer owner = Bukkit.getOfflinePlayer(icon.skin().trim());
            skull.setOwningPlayer(owner);
            item.setItemMeta(skull);
        }
        return item;
    }
}
