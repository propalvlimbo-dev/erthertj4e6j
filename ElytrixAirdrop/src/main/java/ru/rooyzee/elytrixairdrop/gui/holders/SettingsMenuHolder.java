package ru.rooyzee.elytrixairdrop.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class SettingsMenuHolder implements InventoryHolder {
    private final String id;
    public SettingsMenuHolder(String id) { this.id = id; }
    public String getId() { return id; }
    @Override
    public Inventory getInventory() { return null; }
}