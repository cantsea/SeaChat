package com.seachat.command.subcommand;

import com.seachat.command.ChatPermissions;
import com.seachat.command.ChatSubCommand;
import com.seachat.command.CommandContext;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ToggleSubCommand implements ChatSubCommand {
    private final CommandContext context;

    public ToggleSubCommand(CommandContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "toggle";
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(ChatPermissions.TOGGLE);
    }

    @Override
    public List<String> usages(CommandSender sender) {
        return canUse(sender) ? List.of("/chat toggle") : List.of();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(context.settings().message("only-players-toggle"));
            return true;
        }

        if (!canUse(player)) {
            player.sendMessage(context.settings().message("no-permission-toggle"));
            return true;
        }

        if (args.length != 0) {
            sender.sendMessage(context.settings().message("usage", java.util.Map.of("usage", "/chat toggle")));
            return true;
        }

        boolean hidden = context.state().toggleHidden(player.getUniqueId());
        player.sendMessage(context.settings().message(hidden ? "chat-hidden" : "chat-visible"));
        return true;
    }
}
