package ru.rooyzee.elytrixtrader.editor;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixtrader.model.CostConfig;
import ru.rooyzee.elytrixtrader.model.IconConfig;
import ru.rooyzee.elytrixtrader.model.ItemConfig;
import ru.rooyzee.elytrixtrader.model.TradeConfig;
import ru.rooyzee.elytrixtrader.util.ItemCodec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Модель одной сделки из редактора.
 *
 * Главное правило: ItemStack хранится РОВНО в том виде, в котором его положили.
 * Ни одно поле не «пересобирается» из материала и имени — сериализацией занимается
 * {@link ItemCodec}, который пишет предмет по полям (зелья со всеми эффектами,
 * зачарования, лор, CustomModelData, прочность, атрибуты, содержимое шалкеров...)
 * и умеет читать старые форматы конфигов.
 *
 * Это же касается и цен предметами: ItemStack оплаты тоже хранится целиком,
 * поэтому «Зелье лечения II» нельзя заменить обычной бутылкой воды.
 */
public class EditableTrade {

    private final String id;
    private final String traderId;
    private String displayName;
    private Material icon;
    private int expCost;
    private double coinCost;
    private int stockMin;
    private int stockMax;
    private int maxPerTrade;
    private List<ItemStack> rewards;
    private List<ItemStack> itemCosts;

    public EditableTrade(String id, String traderId) {
        this.id          = id;
        this.traderId    = traderId == null ? "" : traderId.toLowerCase(Locale.ROOT);
        this.displayName = id;
        this.icon        = Material.DIAMOND;
        this.expCost     = 0;
        this.stockMin    = -1;
        this.stockMax    = -1;
        this.maxPerTrade = 1;
        this.rewards     = new ArrayList<>();
        this.itemCosts   = new ArrayList<>();
        this.coinCost    = 0;
    }

    /* ───────────────────────── доступы ───────────────────────── */

    public String id()       { return id; }
    public String traderId() { return traderId; }

    public String displayName()         { return displayName; }
    public void   displayName(String v) { this.displayName = v == null || v.isEmpty() ? id : v; }

    public Material icon()            { return icon; }
    public void     icon(Material m)  { this.icon = m == null || m == Material.AIR ? Material.DIAMOND : m; }

    public int  expCost()         { return expCost; }
    public void expCost(int v)    { this.expCost = Math.max(0, v); }

    public int  stockMin()        { return stockMin; }
    public int  stockMax()        { return stockMax; }
    public void stockMin(int v)   {
        this.stockMin = v;
        if (this.stockMax >= 0 && this.stockMax < this.stockMin) {
            this.stockMax = this.stockMin;
        }
    }
    public void stockMax(int v)   {
        this.stockMax = v < 0 ? -1 : Math.max(this.stockMin < 0 ? 0 : this.stockMin, v);
    }

    public int  maxPerTrade()         { return Math.max(1, maxPerTrade); }
    public void maxPerTrade(int v)    { this.maxPerTrade = Math.max(1, v); }

    /** Награды — копии хранящихся ItemStack (изменять их извне нельзя). */
    public List<ItemStack> rewards()   { return ItemCodec.copy(rewards); }
    public void rewards(List<ItemStack> list)    { this.rewards = ItemCodec.copy(list); }

    /** Живой список наград — только для редактора (EditSession). */
    public List<ItemStack> rewardsLive() { return rewards; }

    /** Предметы-цены — тоже целиком, с мета. */
    public List<ItemStack> itemCosts() { return ItemCodec.copy(itemCosts); }
    public void itemCosts(List<ItemStack> list)  { this.itemCosts = ItemCodec.copy(list); }

    /** Живой список цен — только для редактора (EditSession). */
    public List<ItemStack> itemCostsLive() { return itemCosts; }

    public ItemStack firstReward() {
        for (ItemStack item : ItemCodec.clean(rewards)) {
            return item.clone();
        }
        return null;
    }

    public int randomStock(Random random) {
        if (stockMin < 0) {
            return -1;
        }
        if (stockMax <= stockMin) {
            return stockMin;
        }
        return stockMin + random.nextInt(stockMax - stockMin + 1);
    }

    public double coinCost() { return coinCost; }

    public void coinCost(double value) { this.coinCost = Math.max(0, value); }

    /* ───────────────────────── YAML ───────────────────────── */

    public void saveTo(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        section.set("display-name", displayName);
        section.set("icon", icon.name());
        section.set("exp-cost", expCost);
        section.set("coin-cost", coinCost);
        section.set("stock-min", stockMin);
        section.set("stock-max", stockMax);
        section.set("max-per-trade", maxPerTrade);
        ItemCodec.writeList(section, "rewards", rewards);
        ItemCodec.writeList(section, "item-costs", itemCosts);
    }

    public static EditableTrade from(String id, String traderId, ConfigurationSection section) {
        EditableTrade trade = new EditableTrade(id, traderId);
        if (section == null) {
            return trade;
        }
        trade.displayName(section.getString("display-name", id));
        String icon = section.getString("icon", null);
        if (icon != null) {
            try {
                trade.icon(Material.valueOf(icon.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // иконка останется дефолтной
            }
        }
        trade.expCost(section.getInt("exp-cost", 0));
        trade.coinCost(section.getDouble("coin-cost", 0));
        trade.stockMin(section.getInt("stock-min", -1));
        trade.stockMax(section.getInt("stock-max", section.getInt("stock-min", -1)));
        trade.maxPerTrade(section.getInt("max-per-trade", 1));
        trade.rewards(ItemCodec.readList(section, "rewards"));
        trade.itemCosts(ItemCodec.readList(section, "item-costs"));
        if (trade.rewards.isEmpty()) {
            return trade;
        }
        if (icon == null || icon.trim().isEmpty()) {
            trade.icon(trade.rewards.get(0).getType());
        }
        return trade;
    }

    /* ───────────────────────── в TradeConfig ───────────────────────── */

    /**
     * Преобразование в «боевой» TradeConfig.
     * И награды, и цены несут в себе исходный ItemStack (rawItem), поэтому
     * и в меню, и при выдаче предмет остаётся ровно тем, каким его положили.
     */
    public TradeConfig toTradeConfig() {
        List<ItemStack> rawRewards = ItemCodec.copy(rewards);
        List<ItemStack> rawCosts   = ItemCodec.copy(itemCosts);

        Material material = rawRewards.isEmpty() ? icon : rawRewards.get(0).getType();
        String name = displayName == null || displayName.isEmpty() ? id : displayName;

        List<ItemConfig> receive = new ArrayList<>();
        for (ItemStack item : rawRewards) {
            receive.add(new ItemConfig(item.clone()));
        }

        List<CostConfig> costs = new ArrayList<>();
        if (expCost > 0) {
            costs.add(new CostConfig(CostConfig.Type.EXPERIENCE_LEVELS, expCost, null, null));
        }
        for (ItemStack item : rawCosts) {
            costs.add(new CostConfig(CostConfig.Type.ITEM, Math.max(1, item.getAmount()),
                    item.getType(), null, item.clone()));
        }

        int iconAmount = rawRewards.isEmpty() ? 1 : Math.max(1, rawRewards.get(0).getAmount());
        IconConfig iconConfig = new IconConfig(material, iconAmount, false, 0, name,
                Collections.emptyList(), null, null, -1);

        return new TradeConfig(
                id, name, Collections.emptyList(), iconConfig,
                costs, receive,
                Collections.emptyList(), Collections.emptyList(),
                "ENTITY_EXPERIENCE_ORB_PICKUP",
                maxPerTrade, stockMin, stockMin, stockMax,
                null, 0, Collections.emptyList(), true,
                rawRewards);
    }
}
