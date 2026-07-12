package com.seachat.command;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CommandUtils {
    private CommandUtils() {
    }

    public static List<String> startsWith(List<String> options, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.startsWith(normalized))
                .toList();
    }

    public static String joinArgs(String[] args, int start) {
        StringBuilder joined = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (!joined.isEmpty()) {
                joined.append(' ');
            }
            joined.append(args[i]);
        }
        return joined.toString();
    }

    public static long formatSeconds(long millis) {
        return Math.max(1L, (long) Math.ceil(millis / 1000.0D));
    }

    public static Player senderPlayer(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }

    public static void broadcastToVisibleChat(CommandContext context, Component message) {
        for (Player viewer : context.plugin().getServer().getOnlinePlayers()) {
            if (!context.state().isHidden(viewer.getUniqueId())) {
                viewer.sendMessage(message);
            }
        }
    }
}
