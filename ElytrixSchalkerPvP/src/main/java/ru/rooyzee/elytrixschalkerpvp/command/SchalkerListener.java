package ru.rooyzee.elytrixschalkerpvp.command;

import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixschalkerpvp.Main;
import ru.rooyzee.elytrixschalkerpvp.command.LootEditGUI;
import ru.rooyzee.elytrixschalkerpvp.manager.SchalkerManager;
import ru.rooyzee.elytrixschalkerpvp.model.Rarity;
import ru.rooyzee.elytrixschalkerpvp.model.SchalkerData;
import ru.rooyzee.elytrixschalkerpvp.model.SchalkerState;
import ru.rooyzee.elytrixschalkerpvp.util.ColorUtil;
import ru.rooyzee.elytrixschalkerpvp.util.TimeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SchalkerListener implements Listener {

    private final Main plugin;
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();

    public SchalkerListener(Main plugin) {
        this.plugin = plugin;
    }

    // Обработка клика внутри инвентаря шалкера (маскировка, защита от скролла и спама)
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getInventory();

        if (plugin.getSchalkerManager().isSchalkerInventory(inv)) {
            event.setCancelled(true); // Отменяем стандартное поведение (скролл, шифт-клик и т.д.)

            if (event.getClickedInventory() == null) return;
            if (!event.getClickedInventory().equals(inv)) return; // Обрабатываем клики только по самому шалкеру

            SchalkerData data = plugin.getSchalkerManager().getSchalkerByInventory(inv);
            if (data == null || data.getState() != SchalkerState.ACTIVE) return;

            int slot = event.getSlot();
            if (data.getHiddenLoot().containsKey(slot)) {

                long now = System.currentTimeMillis();
                long lastClick = clickCooldowns.getOrDefault(player.getUniqueId(), 0L);
                long cooldownMs = plugin.getConfigManager().getConfig().getLong("settings.loot-cooldown-ms", 800);

                // Если кулдаун не прошел - тихо игнорируем
                if (now - lastClick < cooldownMs) return;
                clickCooldowns.put(player.getUniqueId(), now);

                // Выдаем реальный предмет
                ItemStack realItem = data.getHiddenLoot().remove(slot);
                inv.setItem(slot, null); // Убираем маскировочный краситель

                HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(realItem);
                if (!leftOver.isEmpty()) {
                    for (ItemStack drop : leftOver.values()) {
                        player.getWorld().dropItem(player.getLocation(), drop);
                    }
                }
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
            }
        }
    }

    // Полная блокировка зажатия мыши (drag) по слотам шалкера
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (plugin.getSchalkerManager().isSchalkerInventory(event.getInventory())) {
            for (int slot : event.getRawSlots()) {
                if (slot < event.getInventory().getSize()) { // Если затронут слот шалкера (верхний инвентарь)
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // Взаимодействие с самим блоком шалкера
    /**
     * Обрабатываем открытие игрового шалкера даже если WorldGuard уже
     * отменил PlayerInteractEvent. Обычный игрок может не иметь права
     * взаимодействовать с блоками в регионе, но игровой шалкер открывается
     * через собственный инвентарь, а не через стандартное взаимодействие.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!SchalkerManager.isShulkerBox(block.getType())) return;

        SchalkerData data = plugin.getSchalkerManager().getSchalkerAt(block.getLocation());
        if (data == null) return;

        event.setCancelled(true); // Отменяем обычное открытие шалкера

        Player player = event.getPlayer();

        switch (data.getState()) {
            case SLEEPING:
                long sleepEnd = data.getSleepEndTime();
                if (sleepEnd <= 0) {
                    sleepEnd = data.getStateChangeTime() +
                            (plugin.getConfigManager().getMaxSleepMinutes() * 60L * 1000L);
                }
                long remaining = (sleepEnd - System.currentTimeMillis()) / 1000;
                if (remaining < 0) remaining = 0;

                String msg = plugin.getConfigManager().getMessage("schalker-sleeping")
                        .replace("{time}", TimeUtil.formatSeconds(remaining));
                player.sendMessage(ColorUtil.colorize(msg));
                break;

            case READY:
                plugin.getSchalkerManager().openSchalkerInventory(data, player);
                break;

            case ACTIVE:
                Inventory activeInv = plugin.getSchalkerManager().getActiveInventory(data.getId());
                if (activeInv != null) {
                    player.openInventory(activeInv);
                }
                break;

            case EXPLODING:
                break;
        }
    }

    // Защита от разрушения шалкера
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!SchalkerManager.isShulkerBox(block.getType())) return;

        SchalkerData data = plugin.getSchalkerManager().getSchalkerAt(block.getLocation());
        if (data != null) {
            event.setCancelled(true);
        }
    }

    // Защита от установки блока внутри шалкера
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!SchalkerManager.isShulkerBox(block.getType())) return;

        SchalkerData data = plugin.getSchalkerManager().getSchalkerAt(block.getLocation());
        if (data != null) {
            event.setCancelled(true);
        }
    }

    // Обработка закрытия инвентаря
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        Inventory inv = event.getInventory();
        String title = event.getView().getTitle();

        // Сохранение лута через команду edit
        if (LootEditGUI.isEditInventory(title)) {
            Rarity rarity = LootEditGUI.getRarityFromTitle(plugin, title);
            if (rarity != null) {
                List<ItemStack> items = new ArrayList<>();
                for (ItemStack item : inv.getContents()) {
                    items.add(item);
                }
                plugin.getLootManager().saveLoot(rarity, items);

                String msg = plugin.getConfigManager().getMessage("edit-saved")
                        .replace("{rarity}", plugin.getConfigManager().getRarityName(rarity));
                player.sendMessage(ColorUtil.colorize(msg));
            }
            return;
        }

        // Если это игровой шалкер, мы не закрываем его принудительно для всех, пока не истечет время таймера.
        if (plugin.getSchalkerManager().isSchalkerInventory(inv)) {
            SchalkerData data = plugin.getSchalkerManager().getSchalkerByInventory(inv);
            if (data != null && data.getState() == SchalkerState.ACTIVE) {
                // Таймер в SchalkerManager позаботится об очистке и закрытии, когда придет время.
            }
        }
    }
}