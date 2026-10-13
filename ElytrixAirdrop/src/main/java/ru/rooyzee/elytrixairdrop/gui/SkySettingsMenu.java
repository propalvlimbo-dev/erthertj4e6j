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

public class SkySettingsMenu {

    public static final String TITLE = ColorUtil.colorize("&#F8BEFBᴀɪʀᴅʀᴏᴘ &7» &bВоздушный");
    private final Main plugin;

    public SkySettingsMenu(Main plugin) { this.plugin = plugin; }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(new SettingsMenuHolder("sky"), 45, TITLE);
        String prefix = "airdrops.sky.";

        inv.setItem(10, valueItem(Material.CLOCK, "КД (минуты)", plugin.getConfigManager().getConfig().getInt(prefix + "cooldown-minutes"), "cooldown"));
        inv.setItem(12, valueItem(Material.REPEATER, "Длительность (минуты)", plugin.getConfigManager().getConfig().getInt(prefix + "duration-minutes"), "duration"));
        inv.setItem(14, valueItem(Material.COMPASS, "Радиус региона", plugin.getConfigManager().getConfig().getInt(prefix + "region-radius"), "radius"));
        inv.setItem(16, toggleItem(plugin.getConfigManager().getConfig().getBoolean(prefix + "enabled")));

        inv.setItem(20, lootItem(Material.GRAY_SHULKER_BOX, "&7Обычный лут", "sky_common"));
        inv.setItem(21, lootItem(Material.BLUE_SHULKER_BOX, "&9Редкий лут", "sky_rare"));
        inv.setItem(23, lootItem(Material.RED_SHULKER_BOX, "&cМифический лут", "sky_mythic"));
        inv.setItem(24, lootItem(Material.YELLOW_SHULKER_BOX, "&eЛегендарный лут", "sky_legendary"));

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

    private ItemStack lootItem(Material m, String name, String lootId) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ColorUtil.colorize(name));
        meta.setLore(Arrays.asList(
                ColorUtil.colorize("&7● &fКлик — редактор лута"),
                ColorUtil.colorize("&8key:loot_" + lootId)
        ));
        it.setItemMeta(meta);
        return it;
    }
}