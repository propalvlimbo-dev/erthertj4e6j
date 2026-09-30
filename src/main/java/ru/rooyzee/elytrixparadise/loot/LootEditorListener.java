package ru.rooyzee.elytrixparadise.loot;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

public class LootEditorListener implements Listener {

    private final Main plugin;
    private final LootEditorGUI gui;

    public LootEditorListener(Main plugin, LootEditorGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LootEditorHolder)) return;

        LootEditorHolder holder = (LootEditorHolder) event.getInventory().getHolder();
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();

        // Если клик по нижней панели управления (слоты 45..53)
        if (slot >= 45 && slot < 54) {
            event.setCancelled(true);
            Inventory inv = event.getInventory();
            LootType type = holder.getLootType();
            int currentPage = holder.getPage();

            if (slot == 45) { // Предыдущая страница
                if (currentPage > 1) {
                    gui.saveFromInventory(inv, type, currentPage);
                    gui.open(player, type, currentPage - 1);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                }
            } else if (slot == 53) { // Следующая страница
                if (currentPage < 4) {
                    gui.saveFromInventory(inv, type, currentPage);
                    gui.open(player, type, currentPage + 1);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                }
            } else if (slot == 49) { // Сохранить
                gui.saveFromInventory(inv, type, currentPage);
                String prefix = plugin.getConfigManager().getAdminPrefix();
                player.sendMessage(ColorUtil.colorize(prefix + "&aЛут для &f" + type.getTitle() + " &8(Стр. " + currentPage + "/4) &aуспешно сохранён!"));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof LootEditorHolder) {
            LootEditorHolder holder = (LootEditorHolder) event.getInventory().getHolder();
            gui.saveFromInventory(event.getInventory(), holder.getLootType(), holder.getPage());
            Player player = (Player) event.getPlayer();
            String prefix = plugin.getConfigManager().getAdminPrefix();
            player.sendMessage(ColorUtil.colorize(prefix + "&aПредметы для &f" + holder.getLootType().getTitle() + " &8(Стр. " + holder.getPage() + "/4) &aавтоматически сохранены."));
        }
    }
}
