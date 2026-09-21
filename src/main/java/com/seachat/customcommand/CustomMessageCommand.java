package com.seachat.customcommand;

import com.seachat.config.ChatSettings;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class CustomMessageCommand extends Command {
    private final ChatSettings settings;
    private final List<String> messages;

    CustomMessageCommand(String name, String permission, List<String> messages, ChatSettings settings) {
        super(name);
        this.settings = settings;
        this.messages = List.copyOf(messages);
        setPermission(permission.isBlank() ? null : permission);
        setDescription("Show the configured " + name + " message.");
        setUsage("/" + name);
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(settings.message("custom-command-only-players"));
            return true;
        }
        if (!testPermissionSilent(player)) {
            player.sendMessage(settings.message(player, "custom-command-no-permission"));
            return true;
        }
        for (String message : messages) {
            player.sendMessage(settings.customCommandMessage(player, message));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return List.of();
    }
}
