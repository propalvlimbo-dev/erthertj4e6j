package ru.rooyzee.elytrixairdrop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.gui.holders.SettingsMenuHolder;
import ru.rooyzee.elytrixairdrop.utils.ColorUtil;

import java.util.Arrays;
import java.util.Collections;

public class AirdropSettingsMenu {

    public static final String TITLE_PREFIX = ColorUtil.colorize("&#F8BEFBᴀɪʀᴅʀᴏᴘ &7» &f");
    private final Main plugin;
    private final String id;

    public AirdropSettingsMenu(Main plugin, String id) { this.plugin = plugin; this.id = id; }
    public String getId() { return id; }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(new SettingsMenuHolder(id), 45, TITLE_PREFIX + id);
        String prefix = "airdrops." + id + ".";

        inv.setItem(10, valueItem(Material.CLOCK, "&fКД (минуты)", plugin.getConfigManager().getConfig().getInt(prefix + "cooldown-minutes"), "cooldown"));
        inv.setItem(12, valueItem(Material.REPEATER, "&fДлительность (минуты)", plugin.getConfigManager().getConfig().getInt(prefix + "duration-minutes"), "duration"));
        inv.setItem(14, valueItem(Material.COMPASS, "&fРадиус региона", plugin.getConfigManager().getConfig().getInt(prefix + "region-radius"), "radius"));
        inv.setItem(16, valueItem(Material.HOPPER, "&fМин. предметов", plugin.getConfigManager().getConfig().getInt(prefix + "loot-min-items"), "min"));
        inv.setItem(28, valueItem(Material.CHEST, "&fМакс. предметов", plugin.getConfigManager().getConfig().getInt(prefix + "loot-max-items"), "max"));

        boolean enabled = plugin.getConfigManager().getConfig().getBoolean(prefix + "enabled");
        inv.setItem(30, toggleItem(enabled));

        ItemStack loot = new ItemStack(Material.ENDER_CHEST);
        ItemMeta lm = loot.getItemMeta();
        lm.setDisplayName(ColorUtil.colorize("&#F8BEFBРедактор лута"));
        lm.setLore(Arrays.asList(
                ColorUtil.colorize("&7● &fКлик — открыть редактор"),
                ColorUtil.colorize("&8key:loot_editor")
        ));
        loot.setItemMeta(lm);
        inv.setItem(32, loot);

        ItemStack back = new ItemStack(Material.RED_DYE);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ColorUtil.colorize("&cНазад"));
        bm.setLore(Collections.singletonList(ColorUtil.colorize("&8key:back")));
        back.setItemMeta(bm);
        inv.setItem(40, back);

        p.openInventory(inv);
    }

    private ItemStack valueItem(Material m, String name, int value, String key) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ColorUtil.colorize("&#F8BEFB" + name));
        meta.setLore(Arrays.asList(
                ColorUtil.colorize("&#F8BEFB&l┃ &fЗначение: &#F8BEFB" + value),
                "",
                ColorUtil.colorize("&7● &fЛКМ: &a+1"),
                ColorUtil.colorize("&7● &fПКМ: &c-1"),
                ColorUtil.colorize("&7● &fShift+ЛКМ: &a+10"),
                ColorUtil.colorize("&7● &fShift+ПКМ: &c-10"),
                ColorUtil.colorize("&8key:" + key)
        ));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack toggleItem(boolean enabled) {
        ItemStack it = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ColorUtil.colorize(enabled ? "&aВключен" : "&cВыключен"));
        meta.setLore(Arrays.asList(
                ColorUtil.colorize("&7● &fКлик — переключить"),
                ColorUtil.colorize("&8key:toggle")
        ));
        it.setItemMeta(meta);
        return it;
    }
}