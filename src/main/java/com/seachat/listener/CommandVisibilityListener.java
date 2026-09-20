package com.seachat.listener;

import com.seachat.config.ChatSettings;
import com.seachat.poll.PollManager;
import com.seachat.privatechat.PrivateChatManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

public final class CommandVisibilityListener implements Listener {
    private final ChatSettings settings;
    private final PrivateChatManager privateChatManager;

    public CommandVisibilityListener(ChatSettings settings, PrivateChatManager privateChatManager) {
        this.settings = settings;
        this.privateChatManager = privateChatManager;
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        privateChatManager.filterVisibleCommands(event);

        if (event.getPlayer().hasPermission("seachat.toggle")
                || event.getPlayer().hasPermission("seachat.toggle.colors")
                || event.getPlayer().hasPermission("seachat.slowmode")
                || event.getPlayer().hasPermission("seachat.reload")
                || event.getPlayer().hasPermission("seachat.announcement.trigger")
                || PollManager.canCreatePoll(event.getPlayer())) {
            return;
        }

        if (settings.displayEnabled()
                && (event.getPlayer().hasPermission("seachat.display.inventory")
                || event.getPlayer().hasPermission("seachat.display.hand")
                || event.getPlayer().hasPermission("seachat.display.enderchest"))) {
            return;
        }

        event.getCommands().remove("chat");
        event.getCommands().remove("seachat:chat");
    }
}
