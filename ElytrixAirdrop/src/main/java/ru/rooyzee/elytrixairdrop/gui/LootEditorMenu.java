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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LootEditorMenu {

    private final Main plugin;
    private final String id;
    private final String displayTitle;

    public LootEditorMenu(Main plugin, String id) {
        this(plugin, id, resolveTitle(id));
    }

    public LootEditorMenu(Main plugin, String id, String displayTitle) {
        this.plugin = plugin;
        this.id = id;
        this.displayTitle = displayTitle;
    }

    private static String resolveTitle(String id) {
        if ("peaceful".equalsIgnoreCase(id)) return "Мирный аирдроп";
        if ("fire".equalsIgnoreCase(id)) return "Огненный аирдроп";
        if ("sky".equalsIgnoreCase(id)) return "Небесный аирдроп";
        if (id.startsWith("sky_")) return "Небесный (" + id.replace("sky_", "") + ")";
        return id;
    }

    public void open(Player player, int page) {
        if (page < 1) page = 1;
        if (page > 4) page = 4;

        String pageName;
        String chanceDesc;
        switch (page) {
            case 1:
                pageName = "&#F8BEFBОбычный лут &8(Стр. 1/4)";
                chanceDesc = "&7Шанс выпадения: &aВысокий";
                break;
            case 2:
                pageName = "&#F8BEFBРедкий лут &8(Стр. 2/4)";
                chanceDesc = "&7Шанс выпадения: &#F8BEFBСредний";
                break;
            case 3:
                pageName = "&#F8BEFBЭпический лут &8(Стр. 3/4)";
                chanceDesc = "&7Шанс выпадения: &dНизкий";
                break;
            default:
                pageName = "&#F8BEFBЛегендарный лут &8(Стр. 4/4)";
                chanceDesc = "&7Шанс выпадения: &6Очень редкий";
                break;
        }

        String title = ColorUtil.colorize("&8[" + displayTitle + "&8] " + pageName);
        if (title.length() > 32) {
            title = ColorUtil.colorize(displayTitle + " &8» &fСтр. " + page + "/4");
        }

        LootEditorHolder holder = new LootEditorHolder(id, displayTitle, page);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        // 45 слотов предметов
        List<ItemStack> items = plugin.getAirdropManager().getLootFileService().getItems(id, page);
        for (int i = 0; i < Math.min(45, items.size()); i++) {
            ItemStack is = items.get(i);
            if (is != null && is.getType() != Material.AIR) {
                inv.setItem(i, is.clone());
            }
        }

        // Нижняя панель управления (45..53)
        ItemStack filler = createButton(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // Предыдущая страница (45)
        if (page > 1) {
            inv.setItem(45, createButton(Material.ARROW, "&#F8BEFB◀ Предыдущая страница",
                    Arrays.asList("&7Перейти на страницу " + (page - 1))));
        } else {
            inv.setItem(45, createButton(Material.BARRIER, "&7Первая страница", Collections.emptyList()));
        }

        // Информация о тире (47)
        inv.setItem(47, createButton(Material.BOOK, "&#F8BEFBИнформация о странице",
                Arrays.asList(
                        "&fКатегория: " + pageName,
                        chanceDesc,
                        "",
                        "&7Положите в слоты выше любые предметы,",
                        "&7которые должны выпадать из этой категории!"
                )));

        // Сохранить (49)
        inv.setItem(49, createButton(Material.NETHER_STAR, "&a&l★ Сохранить изменения",
                Arrays.asList(
                        "&7Нажмите, чтобы сохранить и применить",
                        "&7все предметы на этой странице!"
                )));

        // Следующая страница (53)
        if (page < 4) {
            inv.setItem(53, createButton(Material.ARROW, "&#F8BEFBСледующая страница ▶",
                    Arrays.asList("&7Перейти на страницу " + (page + 1))));
        } else {
            inv.setItem(53, createButton(Material.BARRIER, "&7Последняя страница", Collections.emptyList()));
        }

        player.openInventory(inv);
    }

    public void saveFromInventory(Inventory inv, int page) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            ItemStack is = inv.getItem(i);
            if (is != null && is.getType() != Material.AIR) {
                items.add(is.clone());
            }
        }
        plugin.getAirdropManager().getLootFileService().setItems(id, page, items);
    }

    private ItemStack createButton(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            List<String> coloredLore = new ArrayList<>();
            for (String l : lore) {
                coloredLore.add(ColorUtil.colorize(l));
            }
            meta.setLore(coloredLore);
            is.setItemMeta(meta);
        }
        return is;
    }
}
