package ru.rooyzee.elytrixtrader.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.config.GuiConfig;
import ru.rooyzee.elytrixtrader.model.IconConfig;
import ru.rooyzee.elytrixtrader.model.TradeConfig;
import ru.rooyzee.elytrixtrader.model.TraderInstance;
import ru.rooyzee.elytrixtrader.util.ColorUtil;
import ru.rooyzee.elytrixtrader.util.Numbers;
import ru.rooyzee.elytrixtrader.util.Sounds;
import ru.rooyzee.elytrixtrader.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TradeMenuGui {

    private final Main plugin;
    private final Map<UUID, MenuHolder> opened = new HashMap<>();
    private final Map<UUID, TraderInstance> active = new HashMap<>();

    public TradeMenuGui(Main plugin) {
        this.plugin = plugin;
    }

    public void interact(Player player, TraderInstance instance) {
        if (player == null || instance == null || !instance.alive()) {
            return;
        }
        if (!player.hasPermission("elytrixtrader.trade")) {
            plugin.messages().send(player, "general.no-permission", player, null);
            return;
        }
        if (!instance.inRange(player)) {
            Map<String, String> map = new HashMap<>();
            map.put("distance", Numbers.amount((long) Math.ceil(instance.distance(player))));
            map.put("radius", String.valueOf((int) Math.ceil(plugin.config().interactionRadius())));
            plugin.messages().send(player, "interaction.too-far", player, map);
            return;
        }
        Sounds.play(player, plugin.config().openSound(), 0.6F, 1.1F);
        open(player, instance, 0);
    }

    public void open(Player player, TraderInstance instance, int page) {
        GuiConfig gui = plugin.gui();
        List<TradeConfig> trades = available(player, instance);
        active.put(player.getUniqueId(), instance);
        // Если установлен BMenu — рендерим меню им (как DeluxeMenus), иначе встроенное меню
        if (plugin.bmenu() != null && plugin.bmenu().available()
                && plugin.bmenu().open(player, instance, trades)) {
            return;
        }
        int capacity = gui.pageSize();
        int pages = Math.max(1, (int) Math.ceil(trades.size() / (double) capacity));
        int safePage = Math.max(0, Math.min(pages - 1, page));
        InventoryHolder holder = new MenuHolder(instance, safePage, pages);
        Inventory inventory = Bukkit.createInventory(holder, gui.size(), ColorUtil.translate(Text.apply(gui.title(), player, basePlaceholders(player, instance, safePage, pages))));
        MenuHolder menuHolder = (MenuHolder) holder;
        menuHolder.setInventory(inventory);
        fill(inventory, menuHolder, player, trades);
        opened.put(player.getUniqueId(), menuHolder);
        player.openInventory(inventory);
    }

    private void fill(Inventory inventory, MenuHolder holder, Player player, List<TradeConfig> trades) {
        GuiConfig gui = plugin.gui();
        TraderInstance instance = holder.instance();
        Map<String, String> base = basePlaceholders(player, instance, holder.page(), holder.pages());
        // Фон заливаем только если filler не AIR (иначе слоты остаются пустыми)
        if (gui.filler().material() != null && gui.filler().material() != org.bukkit.Material.AIR) {
            ItemStack filler = IconFactory.build(gui.filler(), player, base, 1);
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }
        // Декоративные панели (как в DeluxeMenus)
        for (GuiConfig.Decoration decoration : gui.decorations()) {
            ItemStack decorItem = IconFactory.build(decoration.icon(), player, base, 1);
            for (int slot : decoration.slots()) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, decorItem);
                }
            }
        }
        List<Integer> slots = gui.tradeSlots();
        int pageStart = holder.page() * gui.pageSize();
        if (!slots.isEmpty()) {
            int index = pageStart;
            for (int slot : slots) {
                if (index >= trades.size()) {
                    break;
                }
                inventory.setItem(slot, tradeIcon(player, instance, trades.get(index), base));
                holder.mapTrade(slot, trades.get(index));
                index++;
            }
        } else {
            int start = gui.startSlot();
            int end = Math.min(gui.endSlot(), inventory.getSize() - 1);
            int index = pageStart;
            for (int slot = start; slot <= end; slot++, index++) {
                if (index >= trades.size()) {
                    break;
                }
                TradeConfig trade = trades.get(index);
                inventory.setItem(slot, tradeIcon(player, instance, trade, base));
                holder.mapTrade(slot, trade);
            }
        }
        // Без кнопок: слоты 44-53 остаются пурпурными стёклами (как в DeluxeMenus)
    }

    private void icon(Inventory inventory, MenuHolder holder, IconConfig icon, Player player, Map<String, String> map, String key) {
        if (icon == null) {
            return;
        }
        int slot = icon.slot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, IconFactory.build(icon, player, map, 1));
        holder.mapButton(slot, key);
    }

    private void button(Inventory inventory, MenuHolder holder, GuiConfig gui, Player player, Map<String, String> base,
                        String key, IconConfig override) {
        IconConfig icon = gui.button(key);
        IconConfig info = override;
        if (info != null && (info.displayName() != null || !info.lore().isEmpty())) {
            int slot = icon != null && icon.slot() >= 0 ? icon.slot() : info.slot();
            icon = new IconConfig(info.material() == null ? (icon == null ? org.bukkit.Material.PAPER : icon.material()) : info.material(),
                    info.amount(), info.glow(), info.customModelData(),
                    info.displayName() == null && icon != null ? icon.displayName() : info.displayName(),
                    info.lore().isEmpty() && icon != null ? icon.lore() : info.lore(),
                    info.skin(), info.sound(), slot);
        }
        if (icon == null) {
            return;
        }
        Map<String, String> map = new HashMap<>(base);
        icon(inventory, holder, icon, player, map, key);
    }

    private ItemStack tradeIcon(Player player, TraderInstance instance, TradeConfig trade, Map<String, String> base) {
        GuiConfig gui = plugin.gui();

        // Если stock == 0 — РАСКУПИЛИ
        int stockVal = instance.stock(trade.id());
        if (stockVal == 0) {
            org.bukkit.inventory.ItemStack barrier = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BARRIER);
            org.bukkit.inventory.meta.ItemMeta bm = barrier.getItemMeta();
            bm.setDisplayName(ColorUtil.translate("&c&lРАСКУПИЛИ"));
            String plainName = ColorUtil.plain(trade.displayName())
                    .replaceAll("^[«»\\s]+", "").replaceAll("[«»\\s]+$", "").trim();
            bm.setLore(java.util.Arrays.asList(
                    ColorUtil.translate("&7" + plainName),
                    ColorUtil.translate("&7Этот товар закончился")
            ));
            barrier.setItemMeta(bm);
            return barrier;
        }

        Map<String, String> placeholders = new HashMap<>(base);
        String plainName = ColorUtil.plain(trade.displayName())
                .replaceAll("^[«»\\s]+", "").replaceAll("[«»\\s]+$", "").trim();
        placeholders.put("trade", plainName);
        placeholders.put("trade_colored", ColorUtil.translate(trade.displayName()));
        placeholders.put("item", plainName);
        placeholders.put("price", plugin.purchases().formatAll(trade, 1, player));
        placeholders.put("currency", plugin.purchases().currencyLabel(trade));
        placeholders.put("balance", plugin.purchases().balanceLabel(trade, player));
        placeholders.put("stock", instance.stockText(trade.id()));
        IconConfig source = trade.icon();
        String name = gui.overrideTradeLore() && gui.tradeDisplayName() != null ? gui.tradeDisplayName() : trade.displayName();
        List<String> lore = gui.overrideTradeLore() && !gui.tradeLore().isEmpty() ? gui.tradeLore() : trade.lore();

        // Если есть rawItem (зелье/enchanted/custom NBT) — строим иконку на его основе
        org.bukkit.inventory.ItemStack rawItem = trade.firstRawItem();
        if (rawItem != null) {
            org.bukkit.inventory.ItemStack icon = rawItem.clone();
            icon.setAmount(Math.max(1, source.amount()));
            org.bukkit.inventory.meta.ItemMeta im = icon.getItemMeta();
            if (im != null) {
                // Название в витрине копируется прямо с предмета: собственное имя
                // ItemStack («Зелье огнестойкости») не перетирается никогда.
                List<String> resolvedLore = new ArrayList<>();
                for (String line : lore) {
                    String l = line;
                    for (java.util.Map.Entry<String, String> e : placeholders.entrySet()) {
                        l = l.replace("{" + e.getKey() + "}", e.getValue());
                    }
                    resolvedLore.add(ColorUtil.translate(l));
                }
                // Пустым лором не затираем собственный лор предмета
                if (!resolvedLore.isEmpty()) {
                    im.setLore(resolvedLore);
                }
                icon.setItemMeta(im);
            }
            return icon;
        }

        IconConfig icon = new IconConfig(source.material(), Math.max(1, source.amount()), source.glow() || gui.tradeGlow(),
                source.customModelData(), name, lore, source.skin(), source.sound(), -1);
        return IconFactory.build(icon, player, placeholders, Math.max(1, source.amount()));
    }

    private List<TradeConfig> available(Player player, TraderInstance instance) {
        // СЛУЧАЙНАЯ ВЫБОРКА этого торговца (offeredTrades): при спавне каждый
        // торговец берёт из пула /etr edit случайные предметы (random-trades).
        // Раньше меню показывало ВЕСЬ пул редактора — выборка игнорировалась.
        List<TradeConfig> result = new ArrayList<>();
        for (TradeConfig trade : instance.offeredTrades()) {
            if (trade.requiredPermission() != null && !trade.requiredPermission().trim().isEmpty()
                    && !player.hasPermission(trade.requiredPermission())) {
                continue;
            }
            result.add(trade);
        }
        return result;
    }

    private Map<String, String> basePlaceholders(Player player, TraderInstance instance, int page, int pages) {
        Map<String, String> map = new HashMap<>(instance.placeholders());
        map.put("page", String.valueOf(page + 1));
        map.put("pages", String.valueOf(pages));
        map.put("previous", String.valueOf(Math.max(1, page)));
        map.put("next", String.valueOf(Math.min(pages, page + 2)));
        if (player != null) {
            map.put("player", player.getName());
            map.put("levels", String.valueOf(player.getLevel()));
            map.put("exp", String.valueOf(plugin.currency().totalExperience(player)));
        }
        return map;
    }

    public void onClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (event.getClickedInventory() == null || event.getClickedInventory() != top) {
            return;
        }
        MenuHolder holder = (MenuHolder) top.getHolder();
        int slot = event.getRawSlot();
        String button = holder.button(slot);
        if (button != null) {
            handleButton(player, holder, button);
            return;
        }
        // Свежий TradeConfig из resolveTrades (не кэш offeredTrades)
        String tradeId = holder.tradeId(slot);
        if (tradeId == null) return;
        TradeConfig trade = null;
        for (TradeConfig tc : plugin.resolveTrades(holder.instance().config())) {
            if (tc.id().equalsIgnoreCase(tradeId)) { trade = tc; break; }
        }
        if (trade == null) trade = holder.trade(slot);
        if (trade == null) return;
        // Покупка: сразу закрываем меню, анимация покупки видна без GUI
        ru.rooyzee.elytrixtrader.currency.PurchaseService.Result result =
                plugin.purchases().buy(player, holder.instance(), trade, 1);
        if (result == ru.rooyzee.elytrixtrader.currency.PurchaseService.Result.SUCCESS) {
            player.closeInventory();
        } else {
            refresh(player, holder);
        }
    }

    private void handleButton(Player player, MenuHolder holder, String key) {
        switch (key) {
            case "close":
                player.closeInventory();
                break;
            case "previous":
                open(player, holder.instance(), holder.page() - 1);
                break;
            case "next":
                open(player, holder.instance(), holder.page() + 1);
                break;
            case "info":
            default:
                Sounds.play(player, "ENTITY_VILLAGER_AMBIENT", 0.8F, 1.0F);
                break;
        }
    }

    private void refresh(Player player, MenuHolder holder) {
        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }
        fill(inventory, holder, player, available(player, holder.instance()));
    }

    public void onClose(Player player) {
        opened.remove(player.getUniqueId());
        active.remove(player.getUniqueId());
    }

    /**
     * Покупка из BMenu: консольная команда elytrixtrader buy <player> <tradeId>
     * вызывает этот метод, логика оплаты/кулдауна остаётся общей.
     */
    public void buyFromMenu(Player player, String tradeId) {
        if (player == null || tradeId == null) {
            return;
        }
        TraderInstance instance = active.get(player.getUniqueId());
        if (instance == null || !instance.alive()) {
            instance = plugin.traders().nearest(player, null);
        }
        if (instance == null) {
            return;
        }
        // Свежий TradeConfig из resolveTrades
        TradeConfig trade = null;
        for (TradeConfig tc : plugin.resolveTrades(instance.config())) {
            if (tc.id().equalsIgnoreCase(tradeId)) { trade = tc; break; }
        }
        if (trade == null) return;
        plugin.purchases().buy(player, instance, trade, 1);
    }

    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    public void openNearest(Player player, String traderId) {
        TraderInstance instance = plugin.traders().nearest(player, traderId);
        if (instance == null) {
            plugin.messages().send(player, "interaction.not-found", player, null);
            return;
        }
        interact(player, instance);
    }
}
