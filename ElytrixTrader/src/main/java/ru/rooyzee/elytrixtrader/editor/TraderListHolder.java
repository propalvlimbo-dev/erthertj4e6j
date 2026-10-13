package ru.rooyzee.elytrixtrader.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** Holder списка торговцев: помнит, какой трейд-ид лежит в каком слоте. */
public class TraderListHolder implements InventoryHolder {

    private final Map<Integer, String> slotToTrader = new HashMap<>();
    private Inventory inv;

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void setInventory(Inventory inv) {
        this.inv = inv;
    }

    public void map(int slot, String traderId) {
        slotToTrader.put(slot, traderId);
    }

    public String trader(int slot) {
        return slotToTrader.get(slot);
    }
}
