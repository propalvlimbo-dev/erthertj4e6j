package ru.rooyzee.elytrixairdrop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.gui.holders.MainMenuHolder;
import ru.rooyzee.elytrixairdrop.utils.ColorUtil;

import java.util.Arrays;

public class MainMenu {

    public static final String TITLE = ColorUtil.colorize("&#F8BEFBᴀɪʀᴅʀᴏᴘ &7» &fНастройка");
    private final Main plugin;

    public MainMenu(Main plugin) { this.plugin = plugin; }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(new MainMenuHolder(), 27, TITLE);
        inv.setItem(10, item(Material.LIGHT_BLUE_WOOL, "&#208BFBМирный аирдроп", "peaceful"));
        inv.setItem(13, item(Material.RED_WOOL, "&cОгненный аирдроп", "fire"));
        inv.setItem(16, item(Material.WHITE_WOOL, "&bВоздушный аирдроп", "sky"));
        p.openInventory(inv);
    }

    private ItemStack item(Material m, String name, String id) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ColorUtil.colorize(name));
        String prefix = "airdrops." + id + ".";
        meta.setLore(Arrays.asList(
                ColorUtil.colorize("&#F8BEFB&l┃ &fКД: &#F8BEFB" + plugin.getConfigManager().getConfig().getInt(prefix + "cooldown-minutes") + "&fм"),
                ColorUtil.colorize("&#F8BEFB&l┃ &fДлительность: &#F8BEFB" + plugin.getConfigManager().getConfig().getInt(prefix + "duration-minutes") + "&fм"),
                ColorUtil.colorize("&#F8BEFB&l┃ &fРадиус: &#F8BEFB" + plugin.getConfigManager().getConfig().getInt(prefix + "region-radius")),
                ColorUtil.colorize("&#F8BEFB&l┃ &fВключен: &#F8BEFB" + (plugin.getConfigManager().getConfig().getBoolean(prefix + "enabled") ? "&aДа" : "&cНет")),
                "",
                ColorUtil.colorize("&7● &fКлик — открыть настройки"),
                ColorUtil.colorize("&8key:" + id)
        ));
        it.setItemMeta(meta);
        return it;
    }
}