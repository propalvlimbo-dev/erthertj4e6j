package ru.rooyzee.elytrixtrader.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.rooyzee.elytrixtrader.model.TradeConfig;
import ru.rooyzee.elytrixtrader.model.TraderInstance;

import java.util.HashMap;
import java.util.Map;

public class MenuHolder implements InventoryHolder {

    private final TraderInstance instance;
    private final Map<Integer, TradeConfig> trades = new HashMap<>();
    private final Map<Integer, String> buttons = new HashMap<>();
    private final int page;
    private final int pages;
    private Inventory inventory;

    public MenuHolder(TraderInstance instance, int page, int pages) {
        this.instance = instance;
        this.page = page;
        this.pages = Math.max(1, pages);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** slot -> tradeId (для свежего TradeConfig при покупке) */
    private final Map<Integer, String> tradeIds = new HashMap<>();

    public void mapTrade(int slot, TradeConfig trade) {
        trades.put(slot, trade);
        tradeIds.put(slot, trade.id());
    }

    public String tradeId(int slot) { return tradeIds.get(slot); }

    public void mapButton(int slot, String key) {
        if (slot >= 0) {
            buttons.put(slot, key.toLowerCase());
        }
    }

    public TradeConfig trade(int slot) {
        return trades.get(slot);
    }

    public String button(int slot) {
        return buttons.get(slot);
    }

    public TraderInstance instance() {
        return instance;
    }

    public int page() {
        return page;
    }

    public int pages() {
        return pages;
    }
}
