package com.seachat.customcommand;

import com.seachat.config.ChatSettings;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class CustomCommand extends Command {
    // Shared across nested custom commands so aliases and indirect loops are caught too.
    private static final ThreadLocal<Set<CustomCommand>> EXECUTING = ThreadLocal.withInitial(HashSet::new);
    private final ChatSettings settings;
    private final CustomCommandType type;
    private final List<String> content;

    CustomCommand(String name, String permission, List<String> messages, ChatSettings settings) {
        this(name, permission, CustomCommandType.CHAT, messages, settings);
    }

    CustomCommand(String name, String permission, CustomCommandType type, List<String> content, ChatSettings settings) {
        super(name);
        this.settings = settings;
        this.type = type;
        this.content = List.copyOf(content);
        setPermission(permission.isBlank() ? null : permission);
        setDescription("Run the configured " + name + " action.");
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
        Set<CustomCommand> executing = EXECUTING.get();
        if (!executing.add(this)) {
            player.sendMessage(settings.message(player, "custom-command-recursion"));
            Bukkit.getLogger().warning("SeaChat blocked a custom command loop at '/" + getName() + "'.");
            return true;
        }
        try {
            if (type == CustomCommandType.CHAT) {
                for (String message : content) {
                    player.sendMessage(settings.customCommandMessage(player, message));
                }
            } else {
                CommandSender executor = type == CustomCommandType.CONSOLE ? Bukkit.getConsoleSender() : player;
                for (String template : content) {
                    // Only the configured commands and the invoking player's name are used.
                    String commandLine = template.replace("{player}", player.getName());
                    if (!Bukkit.dispatchCommand(executor, commandLine)) {
                        player.sendMessage(settings.message(player, "custom-command-failed"));
                        Bukkit.getLogger().warning("SeaChat could not dispatch an action for '/" + getName() + "'.");
                        break;
                    }
                }
            }
        } catch (CommandException exception) {
            player.sendMessage(settings.message(player, "custom-command-failed"));
            Bukkit.getLogger().warning("SeaChat action failed for '/" + getName() + "': " + exception.getMessage());
        } finally {
            executing.remove(this);
            if (executing.isEmpty()) {
                EXECUTING.remove();
            }
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return List.of();
    }
}
