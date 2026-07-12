package com.seachat.command.subcommand;

import com.seachat.command.ChatPermissions;
import com.seachat.command.ChatSubCommand;
import com.seachat.command.CommandContext;
import com.seachat.command.CommandUtils;
import java.util.List;
import org.bukkit.command.CommandSender;

public final class SlowmodeSubCommand implements ChatSubCommand {
    private final CommandContext context;

    public SlowmodeSubCommand(CommandContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "slowmode";
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(ChatPermissions.SLOWMODE);
    }

    @Override
    public List<String> usages(CommandSender sender) {
        return canUse(sender) ? List.of("/chat slowmode toggle") : List.of();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!canUse(sender)) {
            sender.sendMessage(context.settings().message("no-permission-slowmode"));
            return true;
        }

        if (args.length != 1 || !args[0].equalsIgnoreCase("toggle")) {
            sender.sendMessage(context.settings().message("usage", java.util.Map.of("usage", "/chat slowmode toggle")));
            return true;
        }

        boolean enabled = context.state().toggleSlowmode();
        context.plugin().saveSlowmodeEnabled(enabled);
        sender.sendMessage(context.settings().message(enabled ? "slowmode-enabled" : "slowmode-disabled"));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender) || args.length != 1) {
            return List.of();
        }
        return CommandUtils.startsWith(List.of("toggle"), args[0]);
    }
}
