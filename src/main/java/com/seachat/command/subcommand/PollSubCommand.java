package com.seachat.command.subcommand;

import com.seachat.command.ChatSubCommand;
import com.seachat.command.CommandContext;
import com.seachat.command.CommandUtils;
import com.seachat.poll.PollManager;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PollSubCommand implements ChatSubCommand {
    private final CommandContext context;

    public PollSubCommand(CommandContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "poll";
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return PollManager.canCreatePoll(sender);
    }

    @Override
    public List<String> usages(CommandSender sender) {
        if (!canUse(sender)) {
            return List.of();
        }
        return List.of("/chat poll create <seconds> <question>", "/chat poll stop");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player senderPlayer = CommandUtils.senderPlayer(sender);
        if (!canUse(sender)) {
            sender.sendMessage(context.settings().message(senderPlayer, "no-permission-poll-create"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("stop")) {
            return context.pollManager().stopPoll(sender);
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("create")) {
            if (senderPlayer != null && context.state().isHidden(senderPlayer.getUniqueId())) {
                senderPlayer.sendMessage(context.settings().message("chat-hidden-cannot-speak"));
                return true;
            }

            long seconds;
            try {
                seconds = Long.parseLong(args[1]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(context.settings().message(senderPlayer, "poll-invalid-time"));
                return true;
            }

            return context.pollManager().createPoll(sender, seconds, CommandUtils.joinArgs(args, 2));
        }

        sender.sendMessage(context.settings().message("usage",
                Map.of("usage", "/chat poll create <seconds> <question>, /chat poll stop")));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender) || args.length != 1) {
            return List.of();
        }
        return CommandUtils.startsWith(List.of("create", "stop"), args[0]);
    }
}
