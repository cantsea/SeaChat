package com.seachat.customcommand;

import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

enum CustomCommandType {
    CHAT("messages"), PLAYER("commands"), CONSOLE("commands");

    private final String contentKey;

    CustomCommandType(String contentKey) {
        this.contentKey = contentKey;
    }

    static CustomCommandType from(ConfigurationSection entry) {
        return switch (entry.getString("type", "chat").trim().toLowerCase(Locale.ROOT)) {
            case "chat" -> CHAT;
            case "player" -> PLAYER;
            case "console" -> CONSOLE;
            default -> null;
        };
    }

    String contentKey() {
        return contentKey;
    }

    List<String> content(ConfigurationSection entry) {
        List<String> lines = entry.getStringList(contentKey);
        if (this == CHAT) {
            return lines;
        }
        return lines.stream()
                .map(String::trim)
                .map(line -> line.startsWith("/") ? line.substring(1).trim() : line)
                .filter(line -> !line.isBlank())
                .toList();
    }
}
