package ru.rooyzee.elytrixairdrop.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class LootEditorHolder implements InventoryHolder {

    private final String id;
    private final String title;
    private int page;
    private Inventory inventory;

    public LootEditorHolder(String id, String title, int page) {
        this.id = id;
        this.title = title;
        this.page = page;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    @Override
    public Inventory getInventory() { return inventory; }
}
