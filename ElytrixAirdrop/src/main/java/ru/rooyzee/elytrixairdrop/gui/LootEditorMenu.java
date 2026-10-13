package ru.rooyzee.elytrixairdrop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.gui.holders.LootEditorHolder;
import ru.rooyzee.elytrixairdrop.utils.ColorUtil;

import java.util.Collections;
import java.util.List;

public class LootEditorMenu {

    public static final int SLOTS_PER_PAGE = 45;
    public static final String TITLE_PREFIX = ColorUtil.colorize("&#F8BEFBᴀɪʀᴅʀᴏᴘ &7» &fЛут ");

    private final Main plugin;
    private final String id;
    private final int page;
    private List<ItemStack> cachedLoot;

    public LootEditorMenu(Main plugin, String id, int page) {
        this.plugin = plugin;
        this.id = id;
        this.page = Math.max(0, page);
    }

    public String getId() { return id; }
    public int getPage() { return page; }

    private List<ItemStack> loot() {
        if (cachedLoot == null) cachedLoot = plugin.getAirdropManager().getLootFileService().load(id);
        return cachedLoot;
    }

    /**
     * Last page index that already has (or can hold) content.
     * When loot exactly fills N pages, one extra empty page is allowed for adding more.
     */
    public int getMaxPages() {
        int size = loot().size();
        if (size <= 0) return 0;
        int lastContentPage = (size - 1) / SLOTS_PER_PAGE;
        // full last page → allow one empty page after it
        if (size % SLOTS_PER_PAGE == 0) {
            return lastContentPage + 1;
        }
        return lastContentPage;
    }

    public void open(Player p) {
        List<ItemStack> l = loot();
        int start = page * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, l.size());

        int titleMax = Math.max(getMaxPages(), page) + 1;
        Inventory inv = Bukkit.createInventory(
                new LootEditorHolder(id, page), 54,
                TITLE_PREFIX + (page + 1) + "/" + titleMax
        );

        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            int idx = start + i;
            if (idx >= end) break;
            inv.setItem(i, l.get(idx).clone());
        }

        if (page > 0) inv.setItem(45, named(Material.ARROW, "&f← Предыдущая", "prev"));
        else inv.setItem(45, named(Material.GRAY_DYE, "&8—", null));

        // Always allow going to the next page so loot can grow beyond one page.
        // If the current page is the last known one, show "+" as a hint that a new page will be created.
        if (page < getMaxPages()) {
            inv.setItem(53, named(Material.ARROW, "&fСледующая →", "next"));
        } else {
            inv.setItem(53, named(Material.LIME_DYE, "&a+ Новая страница", "next"));
        }

        inv.setItem(49, named(Material.EMERALD, "&aСохранить", "save"));
        inv.setItem(48, named(Material.RED_DYE, "&cНазад", "back"));

        p.openInventory(inv);
    }

    private ItemStack named(Material m, String name, String key) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ColorUtil.colorize(name));
        if (key != null) meta.setLore(Collections.singletonList(ColorUtil.colorize("&8key:" + key)));
        it.setItemMeta(meta);
        return it;
    }
}
