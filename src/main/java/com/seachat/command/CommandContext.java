package com.seachat.command;

import com.seachat.SeaChat;
import com.seachat.announcement.AnnouncementManager;
import com.seachat.chat.ChatState;
import com.seachat.config.ChatSettings;
import com.seachat.display.InventoryDisplayManager;
import com.seachat.poll.PollManager;

public record CommandContext(
        SeaChat plugin,
        ChatSettings settings,
        ChatState state,
        InventoryDisplayManager inventoryDisplayManager,
        PollManager pollManager,
        AnnouncementManager announcementManager
) {
}
