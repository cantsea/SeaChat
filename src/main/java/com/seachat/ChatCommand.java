package com.seachat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class ChatCommand implements CommandExecutor, TabCompleter {
    private static final String TOGGLE_PERMISSION = "seachat.toggle";
    private static final String SLOWMODE_PERMISSION = "seachat.slowmode";
    private static final String RELOAD_PERMISSION = "seachat.reload";
    private static final String INVENTORY_DISPLAY_PERMISSION = "seachat.display.inventory";
    private static final String HAND_DISPLAY_PERMISSION = "seachat.display.hand";
    private static final String ENDER_CHEST_DISPLAY_PERMISSION = "seachat.display.enderchest";
    private static final String ANNOUNCEMENT_TRIGGER_PERMISSION = "seachat.announcement.trigger";

    private final SeaChat plugin;
    private final ChatSettings settings;
    private final ChatState state;
    private final InventoryDisplayManager inventoryDisplayManager;
    private final PollManager pollManager;
    private final AnnouncementManager announcementManager;

    ChatCommand(
            SeaChat plugin,
            ChatSettings settings,
            ChatState state,
            InventoryDisplayManager inventoryDisplayManager,
            PollManager pollManager,
            AnnouncementManager announcementManager
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.state = state;
        this.inventoryDisplayManager = inventoryDisplayManager;
        this.pollManager = pollManager;
        this.announcementManager = announcementManager;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (args.length == 1 && args[0].equalsIgnoreCase("toggle")) {
            return toggleChat(sender);
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("slowmode")
                && args[1].equalsIgnoreCase("toggle")) {
            return toggleSlowmode(sender);
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            return reload(sender);
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("inventory")) {
            return displayInventory(sender);
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("hand")) {
            return displayHand(sender);
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("enderchest")) {
            return displayEnderChest(sender);
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("poll")) {
            return poll(sender, args);
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("announce")) {
            return announce(sender, args);
        }

        sendUsage(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission(TOGGLE_PERMISSION)) {
                completions.add("toggle");
            }
            if (sender.hasPermission(SLOWMODE_PERMISSION)) {
                completions.add("slowmode");
            }
            if (sender.hasPermission(RELOAD_PERMISSION)) {
                completions.add("reload");
            }
            if (settings.displayEnabled() && sender.hasPermission(INVENTORY_DISPLAY_PERMISSION)) {
                completions.add("inventory");
            }
            if (settings.displayEnabled() && sender.hasPermission(HAND_DISPLAY_PERMISSION)) {
                completions.add("hand");
            }
            if (settings.displayEnabled() && sender.hasPermission(ENDER_CHEST_DISPLAY_PERMISSION)) {
                completions.add("enderchest");
            }
            if (PollManager.canCreatePoll(sender)) {
                completions.add("poll");
            }
            if (sender.hasPermission(ANNOUNCEMENT_TRIGGER_PERMISSION)) {
                completions.add("announce");
            }
            return startsWith(completions, args[0]);
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("slowmode")
                && sender.hasPermission(SLOWMODE_PERMISSION)) {
            return startsWith(List.of("toggle"), args[1]);
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("poll")
                && PollManager.canCreatePoll(sender)) {
            return startsWith(List.of("create", "stop"), args[1]);
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("announce")
                && sender.hasPermission(ANNOUNCEMENT_TRIGGER_PERMISSION)) {
            return startsWith(List.of("tebex", "test"), args[1]);
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("announce")
                && args[1].equalsIgnoreCase("test")
                && sender.hasPermission(ANNOUNCEMENT_TRIGGER_PERMISSION)) {
            return startsWith(announcementManager.announcementIds(), args[2]);
        }

        return List.of();
    }

    private boolean toggleChat(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(settings.message("only-players-toggle"));
            return true;
        }

        if (!player.hasPermission(TOGGLE_PERMISSION)) {
            player.sendMessage(settings.message("no-permission-toggle"));
            return true;
        }

        boolean hidden = state.toggleHidden(player.getUniqueId());
        player.sendMessage(settings.message(hidden ? "chat-hidden" : "chat-visible"));
        return true;
    }

    private boolean toggleSlowmode(CommandSender sender) {
        if (!sender.hasPermission(SLOWMODE_PERMISSION)) {
            sender.sendMessage(settings.message("no-permission-slowmode"));
            return true;
        }

        boolean enabled = state.toggleSlowmode();
        plugin.saveSlowmodeEnabled(enabled);
        sender.sendMessage(settings.message(enabled ? "slowmode-enabled" : "slowmode-disabled"));
        return true;
    }

    private boolean displayInventory(CommandSender sender) {
        if (!settings.displayEnabled()) {
            sender.sendMessage(settings.message("display-disabled"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(settings.message("only-players-inventory-display"));
            return true;
        }

        if (!player.hasPermission(INVENTORY_DISPLAY_PERMISSION)) {
            player.sendMessage(settings.message("no-permission-inventory-display"));
            return true;
        }

        if (state.isHidden(player.getUniqueId())) {
            player.sendMessage(settings.message("chat-hidden-cannot-speak"));
            return true;
        }

        if (!checkDisplayCooldown(player)) {
            return true;
        }

        broadcastToVisibleChat(settings.inventoryChatMessage(
                player,
                inventoryDisplayManager.createDisplayMessage(player, settings)
        ));
        state.markDisplay(player.getUniqueId());
        return true;
    }

    private boolean displayHand(CommandSender sender) {
        if (!settings.displayEnabled()) {
            sender.sendMessage(settings.message("display-disabled"));
            return true;
        }

        Player player = requirePlayer(sender, "only-players-hand-display");
        if (player == null) {
            return true;
        }

        if (!player.hasPermission(HAND_DISPLAY_PERMISSION)) {
            player.sendMessage(settings.message("no-permission-hand-display"));
            return true;
        }

        if (state.isHidden(player.getUniqueId())) {
            player.sendMessage(settings.message("chat-hidden-cannot-speak"));
            return true;
        }

        if (!checkDisplayCooldown(player)) {
            return true;
        }

        Component hand = inventoryDisplayManager.createHandDisplayMessage(player, settings);
        if (hand == null) {
            player.sendMessage(settings.message("hand-empty"));
            return true;
        }

        broadcastToVisibleChat(settings.handChatMessage(player, hand));
        state.markDisplay(player.getUniqueId());
        return true;
    }

    private boolean displayEnderChest(CommandSender sender) {
        if (!settings.displayEnabled()) {
            sender.sendMessage(settings.message("display-disabled"));
            return true;
        }

        Player player = requirePlayer(sender, "only-players-enderchest-display");
        if (player == null) {
            return true;
        }

        if (!player.hasPermission(ENDER_CHEST_DISPLAY_PERMISSION)) {
            player.sendMessage(settings.message("no-permission-enderchest-display"));
            return true;
        }

        if (state.isHidden(player.getUniqueId())) {
            player.sendMessage(settings.message("chat-hidden-cannot-speak"));
            return true;
        }

        if (!checkDisplayCooldown(player)) {
            return true;
        }

        broadcastToVisibleChat(settings.enderChestChatMessage(
                player,
                inventoryDisplayManager.createEnderChestDisplayMessage(player, settings)
        ));
        state.markDisplay(player.getUniqueId());
        return true;
    }

    private boolean checkDisplayCooldown(Player player) {
        long remainingMillis = state.remainingDisplayCooldownMillis(
                player.getUniqueId(),
                settings.displayCooldownMillis()
        );
        if (remainingMillis <= 0L) {
            return true;
        }

        player.sendMessage(settings.message("display-cooldown",
                Map.of("seconds", String.valueOf(formatSeconds(remainingMillis)))));
        return false;
    }

    private boolean poll(CommandSender sender, String[] args) {
        if (!PollManager.canCreatePoll(sender)) {
            sender.sendMessage(settings.message(sender instanceof Player player ? player : null, "no-permission-poll-create"));
            return true;
        }

        if (args.length == 2 && args[1].equalsIgnoreCase("stop")) {
            return pollManager.stopPoll(sender);
        }

        if (args.length >= 4 && args[1].equalsIgnoreCase("create")) {
            if (sender instanceof Player player && state.isHidden(player.getUniqueId())) {
                player.sendMessage(settings.message("chat-hidden-cannot-speak"));
                return true;
            }

            long seconds;
            try {
                seconds = Long.parseLong(args[2]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(settings.message(sender instanceof Player player ? player : null, "poll-invalid-time"));
                return true;
            }

            return pollManager.createPoll(sender, seconds, joinArgs(args, 3));
        }

        sender.sendMessage(settings.message("usage",
                Map.of("usage", "/chat poll create <seconds> <question>, /chat poll stop")));
        return true;
    }

    private boolean announce(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ANNOUNCEMENT_TRIGGER_PERMISSION)) {
            sender.sendMessage(settings.message(sender instanceof Player player ? player : null,
                    "no-permission-announcement-trigger"));
            return true;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("test")) {
            return testAnnouncement(sender, args);
        }

        if (args.length < 4 || !args[1].equalsIgnoreCase("tebex")) {
            sender.sendMessage(settings.message(sender instanceof Player player ? player : null,
                    "announcement-trigger-usage"));
            return true;
        }

        String playerName = args[2];
        String packageName = joinArgs(args, 3);
        String trigger = "tebex-purchase";
        boolean triggered = announcementManager.trigger(trigger, Map.of(
                "player", settings.escape(playerName),
                "package", settings.escape(packageName),
                "package_name", settings.escape(packageName)
        ));

        sender.sendMessage(settings.message(sender instanceof Player player ? player : null,
                triggered ? "announcement-triggered" : "announcement-trigger-not-found",
                Map.of("trigger", settings.escape(trigger))));
        return true;
    }

    private boolean testAnnouncement(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(settings.message(sender instanceof Player player ? player : null,
                    "announcement-trigger-usage"));
            return true;
        }

        String announcement = args[2];
        String playerName = args.length >= 4
                ? args[3]
                : sender instanceof Player player ? player.getName() : "TestPlayer";
        String packageName = args.length >= 5 ? joinArgs(args, 4) : "Test Package";
        boolean tested = announcementManager.test(announcement, Map.of(
                "player", settings.escape(playerName),
                "package", settings.escape(packageName),
                "package_name", settings.escape(packageName)
        ));

        sender.sendMessage(settings.message(sender instanceof Player player ? player : null,
                tested ? "announcement-tested" : "announcement-test-not-found",
                Map.of("announcement", settings.escape(announcement))));
        return true;
    }

    private Player requirePlayer(CommandSender sender, String messageKey) {
        if (sender instanceof Player player) {
            return player;
        }

        sender.sendMessage(settings.message(messageKey));
        return null;
    }

    private void broadcastToVisibleChat(Component message) {
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!state.isHidden(viewer.getUniqueId())) {
                viewer.sendMessage(message);
            }
        }
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            sender.sendMessage(settings.message("no-permission-reload"));
            return true;
        }

        plugin.reloadSettings();
        sender.sendMessage(settings.message("reloaded"));
        return true;
    }

    private void sendUsage(CommandSender sender) {
        List<String> usages = new ArrayList<>();
        if (sender.hasPermission(TOGGLE_PERMISSION)) {
            usages.add("/chat toggle");
        }
        if (sender.hasPermission(SLOWMODE_PERMISSION)) {
            usages.add("/chat slowmode toggle");
        }
        if (sender.hasPermission(RELOAD_PERMISSION)) {
            usages.add("/chat reload");
        }
        if (settings.displayEnabled() && sender.hasPermission(INVENTORY_DISPLAY_PERMISSION)) {
            usages.add("/chat inventory");
        }
        if (settings.displayEnabled() && sender.hasPermission(HAND_DISPLAY_PERMISSION)) {
            usages.add("/chat hand");
        }
        if (settings.displayEnabled() && sender.hasPermission(ENDER_CHEST_DISPLAY_PERMISSION)) {
            usages.add("/chat enderchest");
        }
        if (PollManager.canCreatePoll(sender)) {
            usages.add("/chat poll create <seconds> <question>");
            usages.add("/chat poll stop");
        }
        if (sender.hasPermission(ANNOUNCEMENT_TRIGGER_PERMISSION)) {
            usages.add("/chat announce tebex <player> <package>");
            usages.add("/chat announce test <announcement> [player] [package]");
        }

        if (usages.isEmpty()) {
            sender.sendMessage(settings.message("no-permission-command"));
            return;
        }

        sender.sendMessage(settings.message("usage", Map.of("usage", String.join(", ", usages))));
    }

    private List<String> startsWith(List<String> options, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.startsWith(normalized))
                .toList();
    }

    private long formatSeconds(long millis) {
        return Math.max(1L, (long) Math.ceil(millis / 1000.0D));
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder joined = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (!joined.isEmpty()) {
                joined.append(' ');
            }
            joined.append(args[i]);
        }
        return joined.toString();
    }
}
