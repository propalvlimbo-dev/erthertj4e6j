package ru.rooyzee.elytrixtrader.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TradeEditHolder implements InventoryHolder {
    private final String traderId;
    private final String tradeId;
    private Inventory inv;

    public TradeEditHolder(String traderId, String tradeId) {
        this.traderId = traderId;
        this.tradeId  = tradeId;
    }

    public String traderId() { return traderId; }
    public String tradeId()  { return tradeId; }

    @Override public Inventory getInventory() { return inv; }
    public void setInventory(Inventory inv)   { this.inv = inv; }
}
