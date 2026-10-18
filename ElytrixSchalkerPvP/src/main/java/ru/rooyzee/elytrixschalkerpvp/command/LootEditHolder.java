package ru.rooyzee.elytrixschalkerpvp.command;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.rooyzee.elytrixschalkerpvp.model.Rarity;

public class LootEditHolder implements InventoryHolder {

    private final Rarity rarity;
    private int page;
    private Inventory inventory;

    public LootEditHolder(Rarity rarity, int page) {
        this.rarity = rarity;
        this.page = page;
    }

    public Rarity getRarity() { return rarity; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    @Override
    public Inventory getInventory() { return inventory; }
}
