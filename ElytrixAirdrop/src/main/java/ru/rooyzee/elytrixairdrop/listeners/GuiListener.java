package ru.rooyzee.elytrixairdrop.listeners;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.gui.AirdropSettingsMenu;
import ru.rooyzee.elytrixairdrop.gui.LootEditorMenu;
import ru.rooyzee.elytrixairdrop.gui.MainMenu;
import ru.rooyzee.elytrixairdrop.gui.holders.LootEditorHolder;
import ru.rooyzee.elytrixairdrop.gui.holders.MainMenuHolder;
import ru.rooyzee.elytrixairdrop.gui.holders.SettingsMenuHolder;
import ru.rooyzee.elytrixairdrop.utils.ColorUtil;
import ru.rooyzee.elytrixairdrop.gui.SkySettingsMenu;

public class GuiListener implements Listener {

    private final Main plugin;

    public GuiListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        InventoryHolder holder = e.getInventory().getHolder();

        if (holder instanceof MainMenuHolder) {
            e.setCancelled(true);
            ItemStack cur = e.getCurrentItem();
            if (cur == null || cur.getType() == Material.AIR) return;
            String key = getKey(cur);
            if ("peaceful".equals(key)) new AirdropSettingsMenu(plugin, "peaceful").open(p);
            else if ("fire".equals(key)) new AirdropSettingsMenu(plugin, "fire").open(p);
            else if ("sky".equals(key)) new SkySettingsMenu(plugin).open(p);
            return;
        }

        if (holder instanceof SettingsMenuHolder) {
            e.setCancelled(true);
            ItemStack cur = e.getCurrentItem();
            if (cur == null || cur.getType() == Material.AIR) return;

            SettingsMenuHolder sh = (SettingsMenuHolder) holder;
            String id = sh.getId();
            String key = getKey(cur);
            if (key == null) return;

            if ("back".equals(key)) { new MainMenu(plugin).open(p); return; }
            if ("loot_editor".equals(key)) { new LootEditorMenu(plugin, id).open(p, 1); return; }
            if (key.startsWith("loot_")) {
                String lootId = key.substring(5);
                new LootEditorMenu(plugin, lootId).open(p, 1);
                return;
            }

            String path = "airdrops." + id + ".";

            if ("toggle".equals(key)) {
                boolean cur2 = plugin.getConfigManager().getConfig().getBoolean(path + "enabled");
                plugin.getConfigManager().getConfig().set(path + "enabled", !cur2);
                plugin.getConfigManager().saveConfig();
                if ("sky".equals(id)) new SkySettingsMenu(plugin).open(p);
                else new AirdropSettingsMenu(plugin, id).open(p);
                return;
            }

            int delta = 0;
            if (e.getClick() == ClickType.LEFT) delta = 1;
            else if (e.getClick() == ClickType.RIGHT) delta = -1;
            else if (e.getClick() == ClickType.SHIFT_LEFT) delta = 10;
            else if (e.getClick() == ClickType.SHIFT_RIGHT) delta = -10;

            String cfgKey = null;
            switch (key) {
                case "cooldown": cfgKey = "cooldown-minutes"; break;
                case "duration": cfgKey = "duration-minutes"; break;
                case "radius": cfgKey = "region-radius"; break;
                case "min": cfgKey = "loot-min-items"; break;
                case "max": cfgKey = "loot-max-items"; break;
            }
            if (cfgKey == null) return;
            int val = plugin.getConfigManager().getConfig().getInt(path + cfgKey) + delta;
            if (val < 0) val = 0;
            plugin.getConfigManager().getConfig().set(path + cfgKey, val);
            plugin.getConfigManager().saveConfig();
            if ("sky".equals(id)) new SkySettingsMenu(plugin).open(p);
            else new AirdropSettingsMenu(plugin, id).open(p);
            return;
        }

        if (holder instanceof LootEditorHolder) {
            LootEditorHolder loh = (LootEditorHolder) holder;
            int raw = e.getRawSlot();

            if (raw >= 45 && raw < 54) {
                e.setCancelled(true);
                int currentPage = loh.getPage();
                LootEditorMenu menu = new LootEditorMenu(plugin, loh.getId(), loh.getTitle());

                if (raw == 45) { // Предыдущая страница
                    if (currentPage > 1) {
                        menu.saveFromInventory(e.getInventory(), currentPage);
                        menu.open(p, currentPage - 1);
                        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    }
                } else if (raw == 53) { // Следующая страница
                    if (currentPage < 4) {
                        menu.saveFromInventory(e.getInventory(), currentPage);
                        menu.open(p, currentPage + 1);
                        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    }
                } else if (raw == 49) { // Сохранить
                    menu.saveFromInventory(e.getInventory(), currentPage);
                    p.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &aЛут для &f" + loh.getTitle() + " &8(Стр. " + currentPage + "/4) &aсохранён!"));
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof LootEditorHolder) {
            LootEditorHolder loh = (LootEditorHolder) holder;
            LootEditorMenu menu = new LootEditorMenu(plugin, loh.getId(), loh.getTitle());
            menu.saveFromInventory(e.getInventory(), loh.getPage());
            if (e.getPlayer() instanceof Player) {
                ((Player) e.getPlayer()).sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &aЛут для &f" + loh.getTitle() + " &8(Стр. " + loh.getPage() + "/4) &aавтоматически сохранён."));
            }
        }
    }

    private String getKey(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        ItemMeta m = it.getItemMeta();
        if (m.getLore() == null) return null;
        for (String s : m.getLore()) {
            String stripped = org.bukkit.ChatColor.stripColor(s);
            if (stripped != null && stripped.startsWith("key:")) return stripped.substring(4);
        }
        return null;
    }
}
