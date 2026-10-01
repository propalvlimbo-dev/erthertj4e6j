package ru.rooyzee.elytrixtrader.editor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.TradeConfig;
import ru.rooyzee.elytrixtrader.model.TraderConfig;
import ru.rooyzee.elytrixtrader.model.TraderInstance;
import ru.rooyzee.elytrixtrader.util.ColorUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Редактор трейдов — чистый Bukkit Inventory API, без BMenu.
 *
 * Добавление предметов — только на ГЛАВНОЙ странице (список трейдов):
 * Shift+ЛКМ по предмету в своём инвентаре создаёт трейд, как в сундуке.
 * В самом редакторе новые предметы не добавляются — он только настраивает
 * то, что уже есть (Shift+ЛКМ по предмету в ряду — вернуть себе).
 * Название трейда копируется из названия предмета и отдельно не задаётся.
 *
 * Структура GUI редактора (54 слота):
 *
 *  Ряд 0 (0-8):   награды — что получает игрок. Хранятся ровно тем ItemStack,
 *                 каким предмет попал в трейд (зелья со всеми эффектами,
 *                 зачарования, лор, NBT...).
 *  Ряд 1 (9-17):  цены предметами. Тоже хранятся целиком: засчитывается только
 *                 точно такой же предмет, «бутылка воды» вместо зелья не пройдёт.
 *  Ряд 2 (18-26): кнопки управления.
 *  Ряды 3-5:      декор.
 *
 * Все служебные предметы (подсказки, стекло, кнопки) помечаются в
 * PersistentDataContainer — поэтому, в отличие от старой версии, обычная
 * стеклопанель или любой другой предмет игрока больше не принимается за
 * «заглушку» и не пропадает при сохранении.
 */
public class TradeEditorManager implements Listener {

    /* ── цвета ── */
    private static final String C_ACCENT = "&#F8BEFB";
    private static final String C_GRAY   = "&8";
    private static final String C_WHITE  = "&f";
    private static final String C_GREEN  = "&a";
    private static final String C_RED    = "&c";
    private static final String C_YELLOW = "&e";
    private static final String C_GOLD   = "&6";

    /* ── слоты ── */
    /* компактный редактор: 27 слотов — товар сверху, кнопки снизу */
    private static final int ITEM_SLOT = 4;
    private static final int CONTROLS  = 18;

    private static final int BTN_EXP    = 19;
    private static final int BTN_STOCK  = 20;
    private static final int BTN_INFO   = 21;
    private static final int BTN_SAVE   = 22;
    private static final int BTN_DELETE = 23;
    private static final int BTN_BACK   = 24;

    private static final int PER_PAGE = 45;

    private final Main plugin;
    private final File folder;
    private final NamespacedKey uiMarker;
    private final Random random = new Random();

    /** traderId → (tradeId → EditableTrade) */
    private final Map<String, Map<String, EditableTrade>> data = new LinkedHashMap<>();
    /** какие id редактор уже регистрировал в TradeRegistry (чтобы корректно снимать удалённые) */
    private final Map<String, Set<String>> registered = new HashMap<>();
    /** ожидаемый ввод в чате */
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    /** Что ждём от игрока в чате. */
    private static final class PendingInput {
        final String type;
        final String traderId;
        final String tradeId;

        PendingInput(String type, String traderId, String tradeId) {
            this.type = type;
            this.traderId = traderId;
            this.tradeId = tradeId;
        }
    }

    /* ════════════════════════════════════════════════════════════════════════
       Инициализация
       ════════════════════════════════════════════════════════════════════════ */

    public TradeEditorManager(Main plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "trader_trades");
        this.uiMarker = new NamespacedKey(plugin, "editor_ui");
        if (!folder.exists() && !folder.mkdirs()) {
        }
        loadAll();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /* ════════════════════════════════════════════════════════════════════════
       Загрузка / сохранение
       ════════════════════════════════════════════════════════════════════════ */

    public void loadAll() {
        data.clear();
        registered.clear();
        File[] files = folder.listFiles((directory, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String traderId = file.getName().replaceAll("(?i)\\.yml$", "").toLowerCase(Locale.ROOT);
                // свой загрузчик: файлы старого формата не роняют загрузку всего торговца
                YamlConfiguration configuration = ru.rooyzee.elytrixtrader.util.ItemCodec.loadYaml(file);
                ConfigurationSection trades = configuration.getConfigurationSection("trades");
                if (trades == null) {
                    continue;
                }
                Map<String, EditableTrade> map = data.computeIfAbsent(traderId, key -> new LinkedHashMap<>());
                for (String tradeId : trades.getKeys(false)) {
                    ConfigurationSection section = trades.getConfigurationSection(tradeId);
                    if (section == null) {
                        continue;
                    }
                    try {
                        map.put(tradeId.toLowerCase(Locale.ROOT), EditableTrade.from(tradeId, traderId, section));
                    } catch (Throwable throwable) {
                    }
                }
            }
        }
        for (String traderId : new ArrayList<>(data.keySet())) {
            rebuildRegistry(traderId);
        }
    }

    /** Сохраняет файл торговца и обновляет реестр/сток. */
    public void saveTrader(String traderId) {
        if (traderId == null) {
            return;
        }
        traderId = traderId.toLowerCase(Locale.ROOT);
        Map<String, EditableTrade> map = data.get(traderId);
        if (map == null) {
            return;
        }
        File file = new File(folder, traderId + ".yml");
        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection root = configuration.createSection("trades");
        for (EditableTrade trade : map.values()) {
            try {
                trade.saveTo(root.createSection(trade.id()));
            } catch (Throwable throwable) {
            }
        }
        try {
            configuration.save(file);
        } catch (Throwable throwable) {
        }
        rebuildRegistry(traderId);
    }

    /**
     * Перерегистрирует трейды одного торговца и обновляет сток у его живых NPC.
     * Удалённые трейды снимаются с регистрации, чтобы их нельзя было купить.
     */
    private void rebuildRegistry(String traderId) {
        Map<String, EditableTrade> map = data.get(traderId);
        Set<String> alive = new HashSet<>();
        if (map != null) {
            for (EditableTrade trade : map.values()) {
                try {
                    plugin.trades().register(trade.toTradeConfig());
                    alive.add(trade.id().toLowerCase(Locale.ROOT));
                } catch (Throwable throwable) {
                }
            }
        }
        Set<String> previous = registered.put(traderId, alive);
        if (previous != null) {
            for (String id : previous) {
                if (!alive.contains(id)) {
                    plugin.trades().unregister(id);
                }
            }
        }
        try {
            for (TraderInstance instance : plugin.traders().instances()) {
                if (instance == null || instance.config() == null
                        || !instance.config().id().equalsIgnoreCase(traderId)) {
                    continue;
                }
                if (map == null) {
                    continue;
                }
                for (EditableTrade trade : map.values()) {
                    instance.setStock(trade.id(), trade.randomStock(random));
                }
            }
        } catch (Throwable ignored) {
            // живых торговцев может не быть
        }
    }

    /* ════════════════════════════════════════════════════════════════════════
       Публичное API
       ════════════════════════════════════════════════════════════════════════ */

    public Map<String, EditableTrade> getTrades(String traderId) {
        return data.computeIfAbsent(lower(traderId), key -> new LinkedHashMap<>());
    }

    public EditableTrade getTrade(String traderId, String tradeId) {
        Map<String, EditableTrade> map = data.get(lower(traderId));
        return map == null || tradeId == null ? null : map.get(tradeId.toLowerCase(Locale.ROOT));
    }

    /* ════════════════════════════════════════════════════════════════════════
       GUI — список торговцев
       ════════════════════════════════════════════════════════════════════════ */

    public void openTraderList(Player player) {
        if (player == null) {
            return;
        }
        Map<String, TraderConfig> traders = plugin.traderConfigs();
        int size = Math.min(54, Math.max(9, ((traders.size() + 8) / 9) * 9));
        TraderListHolder holder = new TraderListHolder();
        Inventory inventory = Bukkit.createInventory(holder, size,
                ColorUtil.translate(C_ACCENT + "&lВыбери торговца"));
        holder.setInventory(inventory);

        int slot = 0;
        for (String traderId : traders.keySet()) {
            if (slot >= size) {
                break;
            }
            ItemStack icon = new ItemStack(Material.VILLAGER_SPAWN_EGG);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtil.translate(C_ACCENT + "&l" + traderId));
                meta.setLore(Arrays.asList(
                        ColorUtil.translate(C_GRAY + "Трейдов: " + C_WHITE + getTrades(traderId).size()),
                        ColorUtil.translate(C_GRAY + "ЛКМ " + C_WHITE + "— открыть редактор")));
                icon.setItemMeta(meta);
            }
            inventory.setItem(slot, icon);
            holder.map(slot, traderId);
            slot++;
        }
        player.openInventory(inventory);
    }

    /* ════════════════════════════════════════════════════════════════════════
       GUI — список трейдов
       ════════════════════════════════════════════════════════════════════════ */

    public void openTradeList(Player player, String traderId) {
        openTradeList(player, traderId, 0);
    }

    public void openTradeList(Player player, String traderId, int page) {
        if (player == null) {
            return;
        }
        traderId = lower(traderId);
        List<EditableTrade> trades = new ArrayList<>(getTrades(traderId).values());

        int maxPage = Math.max(0, (trades.size() - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, maxPage));

        TradeListHolder holder = new TradeListHolder(traderId, page, maxPage);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                ColorUtil.translate(C_ACCENT + traderId + C_GRAY + " | стр. " + (page + 1) + "/" + (maxPage + 1)));
        holder.setInventory(inventory);

        int start = page * PER_PAGE;
        for (int index = 0; index < PER_PAGE; index++) {
            int position = start + index;
            if (position >= trades.size()) {
                break;
            }
            EditableTrade trade = trades.get(position);
            inventory.setItem(index, buildTradeListIcon(trade));
            holder.map(index, trade.id());
        }

        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, filler());
        }
        inventory.setItem(45, page > 0
                ? button(Material.ARROW, C_WHITE + "← Назад", C_GRAY + "стр. " + page)
                : filler());
        inventory.setItem(53, page < maxPage
                ? button(Material.ARROW, C_WHITE + "Вперёд →", C_GRAY + "стр. " + (page + 2))
                : filler());
        inventory.setItem(46, button(Material.LIME_DYE, C_GREEN + "Добавить из руки",
                C_GRAY + "Или просто " + C_YELLOW + "Shift+ЛКМ",
                C_GRAY + "по предмету в своём инвентаре —",
                C_GRAY + "трейд создастся сразу, как в сундуке"));
        inventory.setItem(48, button(Material.BARRIER, C_RED + "Закрыть", ""));
        inventory.setItem(49, infoBook());

        player.openInventory(inventory);
    }

    /**
     * Инфо-книга в списке трейдов (слот 49): объясняет, как работает лут.
     * Пул из /etr edit — это «склад», торговец берёт из него случайные
     * позиции (random-trades), лут не бесконечный и раскупается.
     */
    private ItemStack infoBook() {
        return button(Material.BOOK, C_ACCENT + "&lИнформация", Arrays.asList(
                C_ACCENT + "&l┃ " + C_WHITE + "Это весь пул предметов —",
                C_ACCENT + "&l┃ " + C_WHITE + "торговец берёт из него " + C_GREEN + "случайные",
                C_ACCENT + "&l┃ " + C_WHITE + "позиции при каждом спавне",
                C_ACCENT + "&l┃ " + C_WHITE + "Лут " + C_RED + "не бесконечный" + C_WHITE + " — раскупается",
                C_ACCENT + "&l┃ ",
                C_GRAY + "● " + C_WHITE + "ЛКМ — редактировать трейд",
                C_GRAY + "● " + C_WHITE + "Shift+ЛКМ — дублировать",
                C_GRAY + "● " + C_WHITE + "ПКМ — удалить",
                C_GRAY + "● " + C_WHITE + "Shift+ЛКМ по предмету в своём",
                C_GRAY + "  " + C_WHITE + "инвентаре — добавить в пул",
                C_GRAY + "● " + C_WHITE + "Больше 45 предметов —",
                C_GRAY + "  " + C_WHITE + "страница 2 (стрелка справа)"));
    }

    /** Иконка трейда — сам предмет награды (без изменений), плюс наш лор поверх. */
    /** Иконка трейда — сам предмет: клиент показывает СВОЁ название, лор только про клики. */
    private ItemStack buildTradeListIcon(EditableTrade trade) {
        List<ItemStack> rewards = trade.rewards();
        ItemStack icon = !rewards.isEmpty() ? rewards.get(0).clone() : new ItemStack(trade.icon());
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) {
            return icon;
        }
        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(ColorUtil.translate(C_YELLOW + "ЛКМ " + C_GRAY + "— редактировать"));
        lore.add(ColorUtil.translate(C_GREEN + "Shift+ЛКМ " + C_GRAY + "— дублировать"));
        lore.add(ColorUtil.translate(C_RED + "ПКМ " + C_GRAY + "— удалить"));
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    /* ════════════════════════════════════════════════════════════════════════
       GUI — редактор трейда
       ════════════════════════════════════════════════════════════════════════ */

    public void openTradeEdit(Player player, String traderId, String tradeId) {
        if (player == null) {
            return;
        }
        traderId = lower(traderId);
        EditableTrade trade = getTrade(traderId, tradeId);
        if (trade == null) {
            player.sendMessage(ColorUtil.translate(C_RED + "Трейд не найден."));
            return;
        }

        TradeEditHolder holder = new TradeEditHolder(traderId, trade.id());
        Inventory inventory = Bukkit.createInventory(holder, 27,
                ColorUtil.translate(C_ACCENT + "✎ " + C_GRAY + "Редактор"));
        holder.setInventory(inventory);

        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, filler());
        }

        /* ── товар: ровно тот ItemStack, что сохранён ── */
        List<ItemStack> rewards = trade.rewards();
        if (!rewards.isEmpty()) {
            inventory.setItem(ITEM_SLOT, rewards.get(0).clone());
        } else {
            inventory.setItem(ITEM_SLOT, hint(Material.LIME_STAINED_GLASS_PANE,
                    C_GREEN + "Товар",
                    Arrays.asList(C_GRAY + "Пока пусто.",
                            C_GRAY + "Новый трейд создаётся",
                            C_YELLOW + "Shift+ЛКМ " + C_GRAY + "в списке трейдов")));
        }

        /* ── кнопки ── */
        inventory.setItem(BTN_EXP, button(Material.EXPERIENCE_BOTTLE,
                C_ACCENT + "Цена опытом: " + C_WHITE
                        + (trade.expCost() > 0 ? trade.expCost() + " ур." : "бесплатно"),
                C_GRAY + "ЛКМ — изменить (ввод в чат)"));

        inventory.setItem(BTN_STOCK, button(Material.COMPARATOR,
                C_ACCENT + "Количество: " + C_WHITE + stockText(trade),
                Arrays.asList(
                        C_GRAY + "Сколько штук у торговца при спавне",
                        "",
                        C_YELLOW + "ЛКМ " + C_GRAY + "MIN +1   " + C_RED + "ПКМ " + C_GRAY + "MIN -1",
                        C_YELLOW + "Shift+ЛКМ " + C_GRAY + "MAX +1   " + C_RED + "Shift+ПКМ " + C_GRAY + "MAX -1",
                        C_GRAY + "Средняя кнопка мыши — ∞")));

        inventory.setItem(BTN_INFO, hint(Material.GRAY_STAINED_GLASS_PANE,
                C_ACCENT + "Справка",
                Arrays.asList(
                        C_GRAY + "Сверху — товар: продаётся ровно таким,",
                        C_GRAY + "каким был добавлен (стак, эффекты, NBT).",
                        C_YELLOW + "Shift+ЛКМ " + C_GRAY + "по товару — вернуть себе.",
                        "",
                        C_YELLOW + "Shift+ЛКМ " + C_GRAY + "по предмету в списке",
                        C_GRAY + "трейдов — создать новый трейд.")));

        inventory.setItem(BTN_SAVE, button(Material.EMERALD, C_GREEN + "✔ Сохранить",
                C_GRAY + "Записывает трейд и обновляет торговца"));

        inventory.setItem(BTN_DELETE, button(Material.TNT, C_RED + "✖ Удалить трейд",
                C_GRAY + "Удалит трейд безвозвратно"));

        inventory.setItem(BTN_BACK, button(Material.ARROW, C_WHITE + "← Назад",
                C_GRAY + "К списку трейдов"));

        player.openInventory(inventory);
    }

    /* ════════════════════════════════════════════════════════════════════════
       События
       ════════════════════════════════════════════════════════════════════════ */

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        Inventory top = event.getView().getTopInventory();

        if (top.getHolder() instanceof TraderListHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return;
            }
            String traderId = ((TraderListHolder) top.getHolder()).trader(event.getRawSlot());
            if (traderId != null) {
                openTradeList(player, traderId);
            }
            return;
        }

        if (top.getHolder() instanceof TradeListHolder) {
            event.setCancelled(true);
            clickTradeList(player, (TradeListHolder) top.getHolder(), event);
            return;
        }

        if (top.getHolder() instanceof TradeEditHolder) {
            clickTradeEdit(player, (TradeEditHolder) top.getHolder(), event);
        }
    }

    private void clickTradeList(Player player, TradeListHolder holder, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (event.getClickedInventory() != holder.getInventory()) {
            // Shift+ЛКМ по предмету в своём инвентаре — новый трейд (как в сундук)
            if (event.getClick() == ClickType.SHIFT_LEFT) {
                ItemStack source = event.getCurrentItem();
                if (source != null && source.getType() != Material.AIR) {
                    addTradeFromStack(player, holder.traderId(), source, holder.page());
                    event.getClickedInventory().setItem(event.getSlot(), null);
                }
            }
            return;
        }
        if (slot == 48) {
            player.closeInventory();
            return;
        }
        if (slot == 49) {
            // Инфо-книга: просто шелест страниц, действий нет
            try {
                player.playSound(player.getLocation(),
                        org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.7F, 1.0F);
            } catch (Throwable ignored) {
            }
            return;
        }
        if (slot == 45 && holder.page() > 0) {
            openTradeList(player, holder.traderId(), holder.page() - 1);
            return;
        }
        if (slot == 53 && holder.page() < holder.maxPages()) {
            openTradeList(player, holder.traderId(), holder.page() + 1);
            return;
        }
        if (slot == 46) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
                player.sendMessage(ColorUtil.translate(C_RED + "Возьми предмет в руку!"));
                return;
            }
            addTrade(player, holder.traderId(), hand);
            return;
        }
        if (slot == 47) {
            addTrade(player, holder.traderId(), new ItemStack(Material.DIAMOND, 1));
            return;
        }
        String tradeId = holder.trade(slot);
        if (tradeId == null) {
            return;
        }
        if (event.isShiftClick()) {
            duplicateTrade(player, holder.traderId(), tradeId, holder.page());
        } else if (event.isRightClick()) {
            deleteTrade(player, holder.traderId(), tradeId, holder.page());
        } else {
            openTradeEdit(player, holder.traderId(), tradeId);
        }
    }

    private void clickTradeEdit(Player player, TradeEditHolder holder, InventoryClickEvent event) {
        Inventory top = holder.getInventory();
        int slot = event.getRawSlot();
        ClickType click = event.getClick();

        if (click == ClickType.DOUBLE_CLICK || click == ClickType.UNKNOWN
                || click == ClickType.SWAP_OFFHAND || click == ClickType.NUMBER_KEY
                || click == ClickType.CREATIVE) {
            event.setCancelled(true);
            return;
        }

        /* ── слот товара: только вернуть себе ── */
        if (slot == ITEM_SLOT) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return;
            }
            ItemStack current = top.getItem(slot);
            if (current == null || current.getType() == Material.AIR || isHint(current)) {
                return;
            }
            if (click == ClickType.SHIFT_LEFT) {
                for (ItemStack left : player.getInventory().addItem(current.clone()).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
                top.setItem(slot, new ItemStack(Material.AIR));
                player.sendMessage(ColorUtil.translate(C_GREEN + "Товар возвращён в инвентарь."));
            }
            return;
        }

        /* ── кнопки ── */
        if (slot >= CONTROLS && slot < top.getSize()) {
            event.setCancelled(true);
            if (event.getClickedInventory() == top) {
                handleButton(player, holder, slot, click);
            }
            return;
        }

        /* ── декор и свой инвентарь: в редакторе ничего не добавляется ── */
        if (slot < top.getSize() || event.isShiftClick()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TradeEditHolder) {
            // в редакторе предметы переносятся только кликами по рядам
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeEditHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        TradeEditHolder holder = (TradeEditHolder) event.getInventory().getHolder();
        EditableTrade trade = getTrade(holder.traderId(), holder.tradeId());
        if (trade == null) {
            return;
        }
        Inventory inventory = event.getInventory();
        List<ItemStack> rewards = readRow(inventory, ITEM_SLOT, ITEM_SLOT);
        if (rewards.isEmpty()) {
            // товара нет — считаем случайным закрытием, трейд не трогаем
            return;
        }
        trade.rewards(rewards);
        trade.itemCosts(new ArrayList<>());
        saveTrader(holder.traderId());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        PendingInput input = pending.get(playerId);
        if (input == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            pending.remove(playerId);
            Player player = event.getPlayer();
            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("отмена")) {
                player.sendMessage(ColorUtil.translate(C_GRAY + "Отменено."));
                openTradeEdit(player, input.traderId, input.tradeId);
                return;
            }
            EditableTrade trade = getTrade(input.traderId, input.tradeId);
            if (trade == null) {
                player.sendMessage(ColorUtil.translate(C_RED + "Трейд не найден."));
                return;
            }
            if ("exp".equals(input.type)) {
                int value;
                try {
                    value = Integer.parseInt(message);
                } catch (NumberFormatException exception) {
                    player.sendMessage(ColorUtil.translate(C_RED + "Нужно число! (или cancel)"));
                    openTradeEdit(player, input.traderId, input.tradeId);
                    return;
                }
                trade.expCost(value);
                saveTrader(input.traderId);
                player.sendMessage(ColorUtil.translate(C_GREEN + "Цена опытом: "
                        + (trade.expCost() > 0 ? trade.expCost() + " ур." : "бесплатно")));
            }
            openTradeEdit(player, input.traderId, input.tradeId);
        });
    }

    /* ════════════════════════════════════════════════════════════════════════
       Кнопки редактора
       ════════════════════════════════════════════════════════════════════════ */

    private void handleButton(Player player, TradeEditHolder holder, int slot, ClickType click) {
        String traderId = holder.traderId();
        String tradeId = holder.tradeId();
        Inventory inventory = holder.getInventory();

        if (slot == BTN_EXP) {
            pending.put(player.getUniqueId(), new PendingInput("exp", traderId, tradeId));
            player.closeInventory();
            player.sendMessage(ColorUtil.translate(C_YELLOW
                    + "Введи цену опытом в уровнях (0 = бесплатно, cancel = отмена):"));
            return;
        }

        EditableTrade trade = getTrade(traderId, tradeId);
        if (trade == null) {
            player.sendMessage(ColorUtil.translate(C_RED + "Трейд не найден."));
            return;
        }

        if (slot == BTN_STOCK) {
            switch (click) {
                case MIDDLE:
                    trade.stockMin(-1);
                    trade.stockMax(-1);
                    break;
                case SHIFT_LEFT:
                    trade.stockMax(trade.stockMax() < 0 ? 1 : trade.stockMax() + 1);
                    break;
                case SHIFT_RIGHT:
                    trade.stockMax(trade.stockMax() <= 0 ? -1 : trade.stockMax() - 1);
                    break;
                case LEFT:
                    trade.stockMin(trade.stockMin() < 0 ? 1 : trade.stockMin() + 1);
                    break;
                case RIGHT:
                    trade.stockMin(trade.stockMin() < 0 ? -1 : trade.stockMin() - 1);
                    break;
                default:
                    return;
            }
            saveTrader(traderId);
            openTradeEdit(player, traderId, tradeId);
            return;
        }

        if (slot == BTN_SAVE) {
            List<ItemStack> rewards = readRow(inventory, ITEM_SLOT, ITEM_SLOT);
            if (rewards.isEmpty()) {
                player.sendMessage(ColorUtil.translate(C_RED + "Нет товара — сохранять нечего."));
                player.sendMessage(ColorUtil.translate(C_GRAY
                        + "Новый трейд: Shift+ЛКМ по предмету в списке трейдов."));
                return;
            }
            trade.rewards(rewards);
            trade.itemCosts(new ArrayList<>());
            saveTrader(traderId);
            player.sendMessage(ColorUtil.translate(C_GREEN + "✔ Сохранено!"));
            openTradeEdit(player, traderId, tradeId);
            return;
        }

        if (slot == BTN_DELETE) {
            deleteTrade(player, traderId, tradeId, 0);
            return;
        }

        if (slot == BTN_BACK) {
            openTradeList(player, traderId);
        }
    }

    /* ════════════════════════════════════════════════════════════════════════
       Добавление / удаление / дублирование
       ════════════════════════════════════════════════════════════════════════ */

    /** Shift+ЛКМ в списке трейдов: тихое создание трейда, остаёмся в списке. */
    private void addTradeFromStack(Player player, String traderId, ItemStack reward, int page) {
        if (reward == null || reward.getType() == Material.AIR) {
            return;
        }
        String tradeId = uniqueTradeId(traderId);
        EditableTrade trade = new EditableTrade(tradeId, traderId);
        List<ItemStack> rewards = new ArrayList<>();
        rewards.add(reward.clone());
        trade.rewards(rewards);
        trade.icon(reward.getType());
        getTrades(traderId).put(tradeId.toLowerCase(Locale.ROOT), trade);
        saveTrader(traderId);
        player.sendMessage(ColorUtil.translate(C_GREEN + "✔ Трейд добавлен: "
                + C_WHITE + name(reward) + " x" + reward.getAmount()
                + C_GRAY + " (предмет сохранён как есть)"));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                openTradeList(player, traderId, page);
            }
        });
    }

    private void addTrade(Player player, String traderId, ItemStack reward) {
        if (reward == null || reward.getType() == Material.AIR) {
            return;
        }
        String tradeId = uniqueTradeId(traderId);
        EditableTrade trade = new EditableTrade(tradeId, traderId);
        List<ItemStack> rewards = new ArrayList<>();
        rewards.add(reward.clone());
        trade.rewards(rewards);
        trade.icon(reward.getType());
        getTrades(traderId).put(tradeId.toLowerCase(Locale.ROOT), trade);
        saveTrader(traderId);
        player.sendMessage(ColorUtil.translate(C_GREEN + "Трейд создан: " + C_WHITE + tradeId
                + C_GRAY + " (предмет сохранён как есть)"));
        openTradeEdit(player, traderId, tradeId);
    }

    private void duplicateTrade(Player player, String traderId, String tradeId, int page) {
        EditableTrade source = getTrade(traderId, tradeId);
        if (source == null) {
            return;
        }
        String newId = uniqueTradeId(traderId);
        EditableTrade copy = new EditableTrade(newId, traderId);
        copy.displayName(ColorUtil.plain(source.displayName()) + " (копия)");
        copy.icon(source.icon());
        copy.expCost(source.expCost());
        copy.coinCost(source.coinCost());
        copy.maxPerTrade(source.maxPerTrade());
        copy.stockMin(source.stockMin());
        copy.stockMax(source.stockMax());
        copy.rewards(source.rewards());
        copy.itemCosts(source.itemCosts());
        getTrades(traderId).put(newId.toLowerCase(Locale.ROOT), copy);
        saveTrader(traderId);
        player.sendMessage(ColorUtil.translate(C_GREEN + "Трейд дублирован: " + C_WHITE + newId));
        openTradeList(player, traderId, page);
    }

    private void deleteTrade(Player player, String traderId, String tradeId, int page) {
        Map<String, EditableTrade> map = getTrades(traderId);
        if (map.remove(tradeId.toLowerCase(Locale.ROOT)) == null) {
            return;
        }
        plugin.trades().unregister(tradeId);
        saveTrader(traderId);
        player.sendMessage(ColorUtil.translate(C_RED + "Трейд удалён: " + tradeId));
        openTradeList(player, traderId, page);
    }

    private String uniqueTradeId(String traderId) {
        Map<String, EditableTrade> map = getTrades(traderId);
        String id;
        int attempts = 0;
        do {
            id = "trade_" + (System.currentTimeMillis() % 10_000_000L) + (attempts == 0 ? "" : "_" + attempts);
            attempts++;
        } while (map.containsKey(id.toLowerCase(Locale.ROOT)) && attempts < 100);
        return id;
    }

    /* ════════════════════════════════════════════════════════════════════════
       Утилиты GUI
       ════════════════════════════════════════════════════════════════════════ */

    /** Читает ряд слотов, пропуская подсказки и воздух. Предметы — копии. */
    private List<ItemStack> readRow(Inventory inventory, int from, int to) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = from; slot <= to; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR || isHint(item)) {
                continue;
            }
            result.add(item.clone());
        }
        return result;
    }

    /** Первый свободный слот ряда (свободным считается и слот с подсказкой). */
    private int findSlot(Inventory inventory, int from, int to) {
        for (int slot = from; slot <= to; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR || isHint(item)) {
                return slot;
            }
        }
        return -1;
    }

    /** Служебный предмет редактора: помечен в PDC, поэтому не путается с предметами игрока. */
    private ItemStack mark(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container != null) {
            container.set(uiMarker, PersistentDataType.BYTE, (byte) 1);
        }
        item.setItemMeta(meta);
        return item;
    }

    private boolean isHint(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container != null && container.has(uiMarker, PersistentDataType.BYTE);
    }

    private ItemStack hint(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.translate(name));
            List<String> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(ColorUtil.translate(line));
            }
            meta.setLore(lines);
            item.setItemMeta(meta);
        }
        return mark(item);
    }

    private ItemStack filler() {
        return hint(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());
    }

    private ItemStack button(Material material, String name, String... lore) {
        return hint(material, name, Arrays.asList(lore));
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        return hint(material, name, lore);
    }

    private String stockText(EditableTrade trade) {
        if (trade.stockMin() < 0) {
            return "∞";
        }
        return trade.stockMin() == trade.stockMax()
                ? String.valueOf(trade.stockMin())
                : trade.stockMin() + "–" + trade.stockMax();
    }

    /** Имя предмета как его видит игрок (без цветовых кодов). */
    private String name(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "—";
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return ColorUtil.plain(item.getItemMeta().getDisplayName());
        }
        if (item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION
                || item.getType() == Material.LINGERING_POTION || item.getType() == Material.TIPPED_ARROW) {
            String potion = potionLabel(item);
            if (potion != null) {
                return potion;
            }
        }
        return prettify(item.getType().name());
    }

    /** Подпись зелья для списка трейдов (например, «Зелье лечения II»). */
    private String potionLabel(ItemStack item) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof org.bukkit.inventory.meta.PotionMeta)) {
                return null;
            }
            org.bukkit.inventory.meta.PotionMeta potion = (org.bukkit.inventory.meta.PotionMeta) meta;
            Object data = potion.getClass().getMethod("getBasePotionData").invoke(potion);
            Object type = data == null ? potion.getClass().getMethod("getBasePotionType").invoke(potion)
                    : data.getClass().getMethod("getType").invoke(data);
            if (type == null) {
                return null;
            }
            String label = prettify(String.valueOf(type));
            boolean upgraded = String.valueOf(type).toUpperCase(Locale.ROOT).startsWith("STRONG_");
            return label + (upgraded ? " II" : "");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String prettify(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "—";
        }
        String clean = raw.toUpperCase(Locale.ROOT);
        if (clean.startsWith("LONG_")) {
            clean = clean.substring(5);
        } else if (clean.startsWith("STRONG_")) {
            clean = clean.substring(7);
        }
        String[] parts = clean.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.length() == 0 ? clean : builder.toString();
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    /** Экспорт для отладки: сколько трейдов держит редактор. */
    public int totalTrades() {
        int total = 0;
        for (Map<String, EditableTrade> map : data.values()) {
            total += map.size();
        }
        return total;
    }

    /** Все трейды редактора как TradeConfig (используется при загрузке/редеплое). */
    public List<TradeConfig> allAsConfigs() {
        List<TradeConfig> result = new ArrayList<>();
        for (Map<String, EditableTrade> map : data.values()) {
            for (EditableTrade trade : map.values()) {
                result.add(trade.toTradeConfig());
            }
        }
        return result;
    }
}
