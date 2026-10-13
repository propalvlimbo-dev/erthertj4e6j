package ru.rooyzee.elytrixschalkerpvp.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixschalkerpvp.Main;
import ru.rooyzee.elytrixschalkerpvp.model.Rarity;
import ru.rooyzee.elytrixschalkerpvp.util.ColorUtil;

import java.util.List;

public class LootEditGUI {

    public static final String EDIT_TITLE_PREFIX = "§8Редактирование: ";

    public static void openEditor(Main plugin, Player player, Rarity rarity) {
        String rarityName = plugin.getConfigManager().getRarityName(rarity);
        String hexColor = plugin.getConfigManager().getRarityHexColor(rarity);
        int size = plugin.getConfigManager().getInventorySize(rarity);

        String title = EDIT_TITLE_PREFIX + ColorUtil.colorize(hexColor + rarityName);
        Inventory inv = Bukkit.createInventory(null, size, title);

        List<ItemStack> existingLoot = plugin.getLootManager().getLootTable(rarity);
        for (int i = 0; i < existingLoot.size() && i < size; i++) {
            inv.setItem(i, existingLoot.get(i));
        }

        player.openInventory(inv);

        String msg = plugin.getConfigManager().getMessage("edit-opened")
                .replace("{rarity}", rarityName);
        player.sendMessage(ColorUtil.colorize(msg));
    }

    public static boolean isEditInventory(String title) {
        return title != null && title.startsWith(EDIT_TITLE_PREFIX);
    }

    public static Rarity getRarityFromTitle(Main plugin, String title) {
        for (Rarity rarity : Rarity.values()) {
            String rarityName = plugin.getConfigManager().getRarityName(rarity);
            String hexColor = plugin.getConfigManager().getRarityHexColor(rarity);
            String expected = EDIT_TITLE_PREFIX + ColorUtil.colorize(hexColor + rarityName);
            if (title.equals(expected)) {
                return rarity;
            }
        }
        return null;
    }
}