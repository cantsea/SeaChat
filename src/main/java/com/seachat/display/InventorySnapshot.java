package com.seachat.display;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

record InventorySnapshot(String title, int size, ItemStack[] storage, ItemStack[] armor, ItemStack offhand) {
    static InventorySnapshot fromPlayerInventory(Player player) {
        return new InventorySnapshot(
                player.getName() + "'s Inventory",
                54,
                cloneContents(player.getInventory().getStorageContents()),
                cloneContents(player.getInventory().getArmorContents()),
                cloneItem(player.getInventory().getItemInOffHand())
        );
    }

    static InventorySnapshot fromEnderChest(Player player) {
        return new InventorySnapshot(
                player.getName() + "'s Ender Chest",
                player.getEnderChest().getSize(),
                cloneContents(player.getEnderChest().getContents()),
                new ItemStack[0],
                null
        );
    }

    static InventorySnapshot fromHand(Player player) {
        return new InventorySnapshot(
                player.getName() + "'s Held Item",
                9,
                new ItemStack[] {
                        null,
                        null,
                        null,
                        null,
                        cloneItem(player.getInventory().getItemInMainHand()),
                        null,
                        null,
                        null,
                        null
                },
                new ItemStack[0],
                null
        );
    }

    Inventory createInventory() {
        DisplayInventoryHolder holder = new DisplayInventoryHolder(this);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);
        for (int slot = 0; slot < storage.length && slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, cloneItem(storage[slot]));
        }

        if (inventory.getSize() >= 54) {
            inventory.setItem(45, cloneItem(armor.length > 3 ? armor[3] : null));
            inventory.setItem(46, cloneItem(armor.length > 2 ? armor[2] : null));
            inventory.setItem(47, cloneItem(armor.length > 1 ? armor[1] : null));
            inventory.setItem(48, cloneItem(armor.length > 0 ? armor[0] : null));
            inventory.setItem(50, cloneItem(offhand));
        }
        return inventory;
    }

    ItemStack itemAt(int slot) {
        if (size >= 54) {
            return switch (slot) {
                case 45 -> cloneItem(armor.length > 3 ? armor[3] : null);
                case 46 -> cloneItem(armor.length > 2 ? armor[2] : null);
                case 47 -> cloneItem(armor.length > 1 ? armor[1] : null);
                case 48 -> cloneItem(armor.length > 0 ? armor[0] : null);
                case 50 -> cloneItem(offhand);
                default -> storageItemAt(slot);
            };
        }
        return storageItemAt(slot);
    }

    static ItemStack cloneItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        return item.clone();
    }

    private ItemStack storageItemAt(int slot) {
        if (slot < 0 || slot >= storage.length) {
            return null;
        }
        return cloneItem(storage[slot]);
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] cloned = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            cloned[i] = cloneItem(contents[i]);
        }
        return cloned;
    }
}
