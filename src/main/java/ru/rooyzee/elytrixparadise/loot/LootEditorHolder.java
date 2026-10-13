package ru.rooyzee.elytrixparadise.loot;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class LootEditorHolder implements InventoryHolder {

    private final LootType lootType;
    private int page;
    private Inventory inventory;

    public LootEditorHolder(LootType lootType, int page) {
        this.lootType = lootType;
        this.page = page;
    }

    public LootType getLootType() {
        return lootType;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
