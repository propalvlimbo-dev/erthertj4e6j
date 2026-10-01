package ru.rooyzee.elytrixtrader.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class TradeListHolder implements InventoryHolder {
    private final String traderId;
    private final int page;
    private final int maxPages;
    private Inventory inv;
    private final Map<Integer, String> slotToTrade = new HashMap<>();

    public TradeListHolder(String traderId, int page, int maxPages) {
        this.traderId = traderId;
        this.page = page;
        this.maxPages = maxPages;
    }

    public String traderId()  { return traderId; }
    public int page()         { return page; }
    public int maxPages()     { return maxPages; }

    @Override public Inventory getInventory() { return inv; }
    public void setInventory(Inventory inv)   { this.inv = inv; }

    public void map(int slot, String tradeId) { slotToTrade.put(slot, tradeId); }
    public String trade(int slot)             { return slotToTrade.get(slot); }
}
