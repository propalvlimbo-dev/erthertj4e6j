package ru.rooyzee.elytrixairdrop.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
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

import java.util.ArrayList;
import java.util.List;

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
            if ("loot_editor".equals(key)) { new LootEditorMenu(plugin, id, 0).open(p); return; }
            if (key.startsWith("loot_")) {
                String lootId = key.substring(5);
                new LootEditorMenu(plugin, lootId, 0).open(p);
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

            if (raw >= 45 && raw <= 53) {
                e.setCancelled(true);
                ItemStack cur = e.getCurrentItem();
                if (cur == null) return;
                String key = getKey(cur);
                if (key == null) return;

                switch (key) {
                    case "prev":
                        if (loh.getPage() > 0) {
                            saveLoot(e.getInventory(), loh.getId(), loh.getPage());
                            new LootEditorMenu(plugin, loh.getId(), loh.getPage() - 1).open(p);
                        }
                        break;
                    case "next":
                        // Save first so a full page (slots 0..44) extends the loot list,
                        // then open the next page only if loot now justifies it.
                        saveLoot(e.getInventory(), loh.getId(), loh.getPage());
                        int targetPage = loh.getPage() + 1;
                        LootEditorMenu nextMenu = new LootEditorMenu(plugin, loh.getId(), targetPage);
                        if (targetPage > nextMenu.getMaxPages()) {
                            p.sendMessage(ColorUtil.colorize(
                                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cЗаполните все 45 слотов страницы, чтобы открыть следующую"));
                            new LootEditorMenu(plugin, loh.getId(), loh.getPage()).open(p);
                        } else {
                            nextMenu.open(p);
                        }
                        break;
                    case "save":
                        saveLoot(e.getInventory(), loh.getId(), loh.getPage());
                        p.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &aЛут сохранён"));
                        break;
                    case "back":
                        saveLoot(e.getInventory(), loh.getId(), loh.getPage());
                        if (loh.getId().startsWith("sky_")) new SkySettingsMenu(plugin).open(p);
                        else new AirdropSettingsMenu(plugin, loh.getId()).open(p);
                        break;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof LootEditorHolder) {
            LootEditorHolder loh = (LootEditorHolder) holder;
            saveLoot(e.getInventory(), loh.getId(), loh.getPage());
        }
    }

    private void saveLoot(Inventory top, String id, int page) {
        // Preserve slot positions within the page so multi-page layout stays stable.
        ItemStack[] pageSlots = new ItemStack[LootEditorMenu.SLOTS_PER_PAGE];
        int lastFilled = -1;
        for (int i = 0; i < LootEditorMenu.SLOTS_PER_PAGE; i++) {
            ItemStack it = top.getItem(i);
            if (it != null && it.getType() != Material.AIR) {
                pageSlots[i] = it.clone();
                lastFilled = i;
            }
        }

        List<ItemStack> all = plugin.getAirdropManager().getLootFileService().load(id);
        if (all == null) all = new ArrayList<>();
        else all = new ArrayList<>(all);

        int start = page * LootEditorMenu.SLOTS_PER_PAGE;

        // Ensure list is long enough to hold this page's slot positions
        while (all.size() < start) {
            all.add(null);
        }

        // Replace this page range
        int oldEnd = Math.min(start + LootEditorMenu.SLOTS_PER_PAGE, all.size());
        List<ItemStack> result = new ArrayList<>(start + LootEditorMenu.SLOTS_PER_PAGE);
        for (int i = 0; i < start; i++) {
            result.add(i < all.size() ? all.get(i) : null);
        }

        if (lastFilled >= 0) {
            for (int i = 0; i <= lastFilled; i++) {
                result.add(pageSlots[i]);
            }
        }

        for (int i = oldEnd; i < all.size(); i++) {
            result.add(all.get(i));
        }

        // Compact nulls only at the very end; keep mid-list nulls out by filtering empties
        // but preserve order of real items across pages.
        List<ItemStack> cleaned = new ArrayList<>(result.size());
        for (ItemStack it : result) {
            if (it != null && it.getType() != Material.AIR) cleaned.add(it);
        }

        plugin.getAirdropManager().getLootFileService().save(id, cleaned);
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
