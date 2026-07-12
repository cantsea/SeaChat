package com.seachat.command.subcommand;

import com.seachat.command.ChatPermissions;
import com.seachat.command.ChatSubCommand;
import com.seachat.command.CommandContext;
import java.util.List;
import org.bukkit.command.CommandSender;

public final class ReloadSubCommand implements ChatSubCommand {
    private final CommandContext context;

    public ReloadSubCommand(CommandContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(ChatPermissions.RELOAD);
    }

    @Override
    public List<String> usages(CommandSender sender) {
        return canUse(sender) ? List.of("/chat reload") : List.of();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!canUse(sender)) {
            sender.sendMessage(context.settings().message("no-permission-reload"));
            return true;
        }

        if (args.length != 0) {
            sender.sendMessage(context.settings().message("usage", java.util.Map.of("usage", "/chat reload")));
            return true;
        }

        context.plugin().reloadSettings();
        sender.sendMessage(context.settings().message("reloaded"));
        return true;
    }
}
