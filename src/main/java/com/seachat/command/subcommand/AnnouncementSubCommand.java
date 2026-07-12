package com.seachat.command.subcommand;

import com.seachat.command.ChatPermissions;
import com.seachat.command.ChatSubCommand;
import com.seachat.command.CommandContext;
import com.seachat.command.CommandUtils;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AnnouncementSubCommand implements ChatSubCommand {
    private final CommandContext context;

    public AnnouncementSubCommand(CommandContext context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "announce";
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(ChatPermissions.ANNOUNCEMENT_TRIGGER);
    }

    @Override
    public List<String> usages(CommandSender sender) {
        if (!canUse(sender)) {
            return List.of();
        }
        return List.of(
                "/chat announce tebex <player> <package>",
                "/chat announce test <announcement> [player] [package]"
        );
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = CommandUtils.senderPlayer(sender);
        if (!canUse(sender)) {
            sender.sendMessage(context.settings().message(player, "no-permission-announcement-trigger"));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("test")) {
            return testAnnouncement(sender, args);
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("tebex")) {
            sender.sendMessage(context.settings().message(player, "announcement-trigger-usage"));
            return true;
        }

        String playerName = args[1];
        String packageName = CommandUtils.joinArgs(args, 2);
        String trigger = "tebex-purchase";
        boolean triggered = context.announcementManager().trigger(trigger, Map.of(
                "player", context.settings().escape(playerName),
                "package", context.settings().escape(packageName),
                "package_name", context.settings().escape(packageName)
        ));

        sender.sendMessage(context.settings().message(player,
                triggered ? "announcement-triggered" : "announcement-trigger-not-found",
                Map.of("trigger", context.settings().escape(trigger))));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) {
            return List.of();
        }

        if (args.length == 1) {
            return CommandUtils.startsWith(List.of("tebex", "test"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            return CommandUtils.startsWith(context.announcementManager().announcementIds(), args[1]);
        }

        return List.of();
    }

    private boolean testAnnouncement(CommandSender sender, String[] args) {
        Player player = CommandUtils.senderPlayer(sender);
        if (args.length < 2) {
            sender.sendMessage(context.settings().message(player, "announcement-trigger-usage"));
            return true;
        }

        String announcement = args[1];
        String playerName = args.length >= 3
                ? args[2]
                : player != null ? player.getName() : "TestPlayer";
        String packageName = args.length >= 4 ? CommandUtils.joinArgs(args, 3) : "Test Package";
        boolean tested = context.announcementManager().test(announcement, Map.of(
                "player", context.settings().escape(playerName),
                "package", context.settings().escape(packageName),
                "package_name", context.settings().escape(packageName)
        ));

        sender.sendMessage(context.settings().message(player,
                tested ? "announcement-tested" : "announcement-test-not-found",
                Map.of("announcement", context.settings().escape(announcement))));
        return true;
    }
}
