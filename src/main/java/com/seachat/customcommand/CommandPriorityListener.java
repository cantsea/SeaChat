package com.seachat.customcommand;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class CommandPriorityListener implements Listener {
    private final Plugin plugin;
    private final CustomCommandManager manager;
    private BukkitTask pendingRefresh;

    public CommandPriorityListener(Plugin plugin, CustomCommandManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        refresh();
        scheduleRefresh();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        scheduleRefresh();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != plugin) {
            scheduleRefresh();
        }
    }

    public void scheduleRefresh() {
        if (!plugin.isEnabled()) {
            return;
        }
        if (pendingRefresh != null) {
            pendingRefresh.cancel();
        }
        // Run after registration callbacks and refresh clients only when labels changed.
        pendingRefresh = plugin.getServer().getScheduler().runTask(plugin, () -> {
            pendingRefresh = null;
            refresh();
        });
    }

    private void refresh() {
        if (plugin.isEnabled() && manager.refreshPriority()) {
            plugin.getServer().getOnlinePlayers().forEach(player -> player.updateCommands());
        }
    }
}
