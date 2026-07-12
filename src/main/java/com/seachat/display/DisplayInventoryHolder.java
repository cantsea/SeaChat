package com.seachat.display;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

final class DisplayInventoryHolder implements InventoryHolder {
    private final InventorySnapshot snapshot;
    private Inventory inventory;

    DisplayInventoryHolder(InventorySnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    ItemStack itemAt(int slot) {
        return snapshot.itemAt(slot);
    }
}
