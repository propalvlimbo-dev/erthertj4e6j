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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixschalkerpvp.Main;
import ru.rooyzee.elytrixschalkerpvp.manager.SchalkerManager;
import ru.rooyzee.elytrixschalkerpvp.model.Rarity;
import ru.rooyzee.elytrixschalkerpvp.model.SchalkerData;
import ru.rooyzee.elytrixschalkerpvp.model.SchalkerState;
import ru.rooyzee.elytrixschalkerpvp.util.ColorUtil;
import ru.rooyzee.elytrixschalkerpvp.util.TimeUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SchalkerListener implements Listener {

    private final Main plugin;
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();

    public SchalkerListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();

        // Обработка кликов внутри GUI редактора лута
        if (holder instanceof LootEditHolder) {
            LootEditHolder leh = (LootEditHolder) holder;
            int raw = event.getRawSlot();

            if (raw >= 45 && raw < 54) {
                event.setCancelled(true);
                int currentPage = leh.getPage();
                Rarity rarity = leh.getRarity();

                if (raw == 45) { // Предыдущая страница
                    if (currentPage > 1) {
                        LootEditGUI.saveFromInventory(plugin, inv, rarity, currentPage);
                        LootEditGUI.openEditor(plugin, player, rarity, currentPage - 1);
                        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    }
                } else if (raw == 53) { // Следующая страница
                    if (currentPage < 4) {
                        LootEditGUI.saveFromInventory(plugin, inv, rarity, currentPage);
                        LootEditGUI.openEditor(plugin, player, rarity, currentPage + 1);
                        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    }
                } else if (raw == 49) { // Сохранить
                    LootEditGUI.saveFromInventory(plugin, inv, rarity, currentPage);
                    String rarityName = plugin.getConfigManager().getRarityName(rarity);
                    player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &aЛут для &f" + rarityName + " &8(Стр. " + currentPage + "/4) &aсохранён!"));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                }
            }
            return;
        }

        // Обработка игрового лута шалкера
        if (plugin.getSchalkerManager().isSchalkerInventory(inv)) {
            event.setCancelled(true);

            if (event.getClickedInventory() == null) return;
            if (!event.getClickedInventory().equals(inv)) return;

            SchalkerData data = plugin.getSchalkerManager().getSchalkerByInventory(inv);
            if (data == null || data.getState() != SchalkerState.ACTIVE) return;

            int slot = event.getSlot();
            if (data.getHiddenLoot().containsKey(slot)) {
                long now = System.currentTimeMillis();
                long lastClick = clickCooldowns.getOrDefault(player.getUniqueId(), 0L);
                long cooldownMs = plugin.getConfigManager().getConfig().getLong("settings.loot-cooldown-ms", 800);

                if (now - lastClick < cooldownMs) return;
                clickCooldowns.put(player.getUniqueId(), now);

                ItemStack realItem = data.getHiddenLoot().remove(slot);
                inv.setItem(slot, null);

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

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (plugin.getSchalkerManager().isSchalkerInventory(event.getInventory())) {
            for (int slot : event.getRawSlots()) {
                if (slot < event.getInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!SchalkerManager.isShulkerBox(block.getType())) return;

        SchalkerData data = plugin.getSchalkerManager().getSchalkerAt(block.getLocation());
        if (data == null) return;

        event.setCancelled(true);
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!SchalkerManager.isShulkerBox(block.getType())) return;

        SchalkerData data = plugin.getSchalkerManager().getSchalkerAt(block.getLocation());
        if (data != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!SchalkerManager.isShulkerBox(block.getType())) return;

        SchalkerData data = plugin.getSchalkerManager().getSchalkerAt(block.getLocation());
        if (data != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();

        // Авто-сохранение лута при закрытии
        if (holder instanceof LootEditHolder) {
            LootEditHolder leh = (LootEditHolder) holder;
            LootEditGUI.saveFromInventory(plugin, inv, leh.getRarity(), leh.getPage());
            String rarityName = plugin.getConfigManager().getRarityName(leh.getRarity());
            player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &aЛут для &f" + rarityName + " &8(Стр. " + leh.getPage() + "/4) &aавтоматически сохранён."));
        }
    }
}
