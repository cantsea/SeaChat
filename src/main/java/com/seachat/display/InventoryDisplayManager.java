package com.seachat.display;

import com.seachat.SeaChat;
import com.seachat.config.ChatSettings;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class InventoryDisplayManager implements CommandExecutor, Listener {
    private final SeaChat plugin;
    private final ChatSettings settings;
    private final Map<String, StoredSnapshot> snapshots = new ConcurrentHashMap<>();
    private BukkitTask cleanupTask;

    public InventoryDisplayManager(SeaChat plugin, ChatSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        reloadCleanupTask();
    }

    public void reloadCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        if (!settings.displayEnabled()) {
            snapshots.clear();
            return;
        }

        if (!settings.snapshotExpiryEnabled()) {
            return;
        }

        long intervalTicks = settings.snapshotCleanupIntervalTicks();
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredSnapshots, intervalTicks, intervalTicks);
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        snapshots.clear();
    }

    public Component createDisplayMessage(Player player, ChatSettings settings) {
        String snapshotId = createInventorySnapshot(player);
        return settings.message(player, "inventory-display", Map.of("player", settings.escape(player.getName())))
                .clickEvent(ClickEvent.runCommand("/seachatinv " + snapshotId))
                .hoverEvent(HoverEvent.showText(settings.message(player, "inventory-display-hover")));
    }

    public Component createEnderChestDisplayMessage(Player player, ChatSettings settings) {
        String snapshotId = createEnderChestSnapshot(player);
        return settings.message(player, "enderchest-display", Map.of("player", settings.escape(player.getName())))
                .clickEvent(ClickEvent.runCommand("/seachatinv " + snapshotId))
                .hoverEvent(HoverEvent.showText(settings.message(player, "enderchest-display-hover")));
    }

    public Component createHandDisplayMessage(Player player, ChatSettings settings) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (isEmpty(item)) {
            return null;
        }

        String snapshotId = createHandSnapshot(player);
        return settings.message(player, "hand-display", Map.of(
                        "player", settings.escape(player.getName()),
                        "amount", String.valueOf(item.getAmount())
                ), Map.of("item", item.displayName()))
                .clickEvent(ClickEvent.runCommand("/seachatinv " + snapshotId))
                .hoverEvent(HoverEvent.showText(settings.message(player, "hand-display-hover")));
    }

    public String createInventorySnapshot(Player player) {
        return storeSnapshot(InventorySnapshot.fromPlayerInventory(player));
    }

    public String createEnderChestSnapshot(Player player) {
        return storeSnapshot(InventorySnapshot.fromEnderChest(player));
    }

    public String createHandSnapshot(Player player) {
        return storeSnapshot(InventorySnapshot.fromHand(player));
    }

    private String storeSnapshot(InventorySnapshot snapshot) {
        String id = UUID.randomUUID().toString();
        snapshots.put(id, new StoredSnapshot(snapshot, System.currentTimeMillis()));
        return id;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return true;
        }

        if (!settings.displayEnabled()) {
            player.sendMessage(settings.message(player, "display-disabled"));
            return true;
        }

        StoredSnapshot storedSnapshot = snapshots.get(args[0]);
        if (storedSnapshot == null) {
            player.sendMessage(settings.message(player, "display-expired"));
            return true;
        }

        if (isExpired(storedSnapshot)) {
            snapshots.remove(args[0], storedSnapshot);
            player.sendMessage(settings.message(player, "display-expired"));
            return true;
        }

        player.openInventory(storedSnapshot.snapshot().createInventory());
        return true;
    }

    private void cleanupExpiredSnapshots() {
        if (!settings.snapshotExpiryEnabled()) {
            return;
        }

        for (Map.Entry<String, StoredSnapshot> entry : snapshots.entrySet()) {
            if (isExpired(entry.getValue())) {
                snapshots.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private boolean isExpired(StoredSnapshot storedSnapshot) {
        return settings.snapshotExpiryEnabled()
                && System.currentTimeMillis() - storedSnapshot.createdAtMillis() >= settings.snapshotExpireMillis();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isDisplayInventory(event.getView().getTopInventory())) {
            return;
        }

        denyInteraction(event);
        DisplayInventoryHolder holder = displayHolder(event.getView().getTopInventory());
        if (holder != null && event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            event.setCurrentItem(holder.itemAt(event.getRawSlot()));
        }
        if (event.getWhoClicked() instanceof Player player) {
            clearCursor(player);
            closeDisplayForCreative(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryCreative(InventoryCreativeEvent event) {
        if (!isDisplayInventory(event.getView().getTopInventory())) {
            return;
        }

        denyInteraction(event);
        DisplayInventoryHolder holder = displayHolder(event.getView().getTopInventory());
        if (holder != null && event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            event.setCurrentItem(holder.itemAt(event.getRawSlot()));
        }
        event.setCursor(air());
        if (event.getWhoClicked() instanceof Player player) {
            clearCursor(player);
            closeDisplayForCreative(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isDisplayInventory(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);
        event.setResult(Result.DENY);
        if (event.getWhoClicked() instanceof Player player) {
            clearCursor(player);
            closeDisplayForCreative(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!isDisplayInventory(event.getView().getTopInventory())) {
            return;
        }

        if (event.getPlayer() instanceof Player player) {
            clearCursor(player);
        }
    }

    private record StoredSnapshot(InventorySnapshot snapshot, long createdAtMillis) {
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private static boolean isDisplayInventory(Inventory inventory) {
        return inventory.getHolder() instanceof DisplayInventoryHolder;
    }

    private static DisplayInventoryHolder displayHolder(Inventory inventory) {
        return inventory.getHolder() instanceof DisplayInventoryHolder holder ? holder : null;
    }

    private static ItemStack air() {
        return new ItemStack(Material.AIR);
    }

    private static void denyInteraction(InventoryClickEvent event) {
        event.setCancelled(true);
        event.setResult(Result.DENY);
        event.setCursor(air());
        event.getWhoClicked().setItemOnCursor(air());
    }

    private static void clearCursor(Player player) {
        player.setItemOnCursor(air());
        Bukkit.getScheduler().runTask(SeaChat.getPlugin(SeaChat.class), () -> {
            player.setItemOnCursor(air());
            player.updateInventory();
        });
    }

    private static void closeDisplayForCreative(Player player) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            return;
        }

        Bukkit.getScheduler().runTask(SeaChat.getPlugin(SeaChat.class), () -> {
            if (isDisplayInventory(player.getOpenInventory().getTopInventory())) {
                player.closeInventory();
            }
        });
    }

}
