package com.seachat.command.subcommand;

import com.seachat.command.ChatPermissions;

public enum DisplayType {
    INVENTORY(
            "inventory",
            ChatPermissions.INVENTORY_DISPLAY,
            "only-players-inventory-display",
            "no-permission-inventory-display"
    ),
    HAND(
            "hand",
            ChatPermissions.HAND_DISPLAY,
            "only-players-hand-display",
            "no-permission-hand-display"
    ),
    ENDER_CHEST(
            "enderchest",
            ChatPermissions.ENDER_CHEST_DISPLAY,
            "only-players-enderchest-display",
            "no-permission-enderchest-display"
    );

    private final String commandName;
    private final String permission;
    private final String onlyPlayersMessage;
    private final String noPermissionMessage;

    DisplayType(String commandName, String permission, String onlyPlayersMessage, String noPermissionMessage) {
        this.commandName = commandName;
        this.permission = permission;
        this.onlyPlayersMessage = onlyPlayersMessage;
        this.noPermissionMessage = noPermissionMessage;
    }

    public String commandName() {
        return commandName;
    }

    public String permission() {
        return permission;
    }

    public String onlyPlayersMessage() {
        return onlyPlayersMessage;
    }

    public String noPermissionMessage() {
        return noPermissionMessage;
    }
}
