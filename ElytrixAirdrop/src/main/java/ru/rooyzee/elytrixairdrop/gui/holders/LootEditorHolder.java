package ru.rooyzee.elytrixairdrop.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class LootEditorHolder implements InventoryHolder {
    private final String id;
    private final int page;
    public LootEditorHolder(String id, int page) { this.id = id; this.page = page; }
    public String getId() { return id; }
    public int getPage() { return page; }
    @Override
    public Inventory getInventory() { return null; }
}