package com.seachat.command;

import java.util.List;
import org.bukkit.command.CommandSender;

public interface ChatSubCommand {
    String name();

    boolean canUse(CommandSender sender);

    List<String> usages(CommandSender sender);

    boolean execute(CommandSender sender, String[] args);

    default List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
