package com.seachat.command.subcommand;

import com.seachat.command.ChatPermissions;
import com.seachat.command.ChatSubCommand;
import com.seachat.command.CommandContext;
import com.seachat.command.CommandUtils;
import java.util.ArrayList;
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
        return sender.hasPermission(ChatPermissions.TOGGLE) || sender.hasPermission(ChatPermissions.TOGGLE_COLORS);
    }

    @Override
    public List<String> usages(CommandSender sender) {
        List<String> usages = new ArrayList<>();
        if (sender.hasPermission(ChatPermissions.TOGGLE)) {
            usages.add("/chat toggle");
        }
        if (sender.hasPermission(ChatPermissions.TOGGLE_COLORS)) {
            usages.add("/chat toggle colors");
        }
        return usages;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 && sender.hasPermission(ChatPermissions.TOGGLE_COLORS)
                ? CommandUtils.startsWith(List.of("colors"), args[0]) : List.of();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        boolean toggleColors = args.length == 1 && args[0].equalsIgnoreCase("colors");
        if (!(sender instanceof Player player)) {
            sender.sendMessage(context.settings().message(toggleColors ? "only-players-toggle-colors" : "only-players-toggle"));
            return true;
        }

        if (!player.hasPermission(toggleColors ? ChatPermissions.TOGGLE_COLORS : ChatPermissions.TOGGLE)) {
            player.sendMessage(context.settings().message(toggleColors ? "no-permission-toggle-colors" : "no-permission-toggle"));
            return true;
        }

        if (args.length != 0 && !toggleColors) {
            sender.sendMessage(context.settings().message("usage", java.util.Map.of("usage", String.join(", ", usages(sender)))));
            return true;
        }

        if (toggleColors) {
            boolean disabled = context.state().toggleColors(player.getUniqueId());
            player.sendMessage(context.settings().message(disabled ? "chat-colors-disabled" : "chat-colors-enabled"));
            return true;
        }

        boolean hidden = context.state().toggleHidden(player.getUniqueId());
        player.sendMessage(context.settings().message(hidden ? "chat-hidden" : "chat-visible"));
        return true;
    }
}
