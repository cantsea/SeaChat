package com.seachat.command;

import com.seachat.SeaChat;
import com.seachat.announcement.AnnouncementManager;
import com.seachat.chat.ChatState;
import com.seachat.command.subcommand.AnnouncementSubCommand;
import com.seachat.command.subcommand.DisplaySubCommand;
import com.seachat.command.subcommand.DisplayType;
import com.seachat.command.subcommand.PollSubCommand;
import com.seachat.command.subcommand.ReloadSubCommand;
import com.seachat.command.subcommand.SlowmodeSubCommand;
import com.seachat.command.subcommand.ToggleSubCommand;
import com.seachat.config.ChatSettings;
import com.seachat.display.InventoryDisplayManager;
import com.seachat.poll.PollManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class ChatCommand implements CommandExecutor, TabCompleter {
    private final ChatSettings settings;
    private final List<ChatSubCommand> subCommands;
    private final Map<String, ChatSubCommand> subCommandByName;

    public ChatCommand(
            SeaChat plugin,
            ChatSettings settings,
            ChatState state,
            InventoryDisplayManager inventoryDisplayManager,
            PollManager pollManager,
            AnnouncementManager announcementManager
    ) {
        this.settings = settings;
        CommandContext context = new CommandContext(
                plugin,
                settings,
                state,
                inventoryDisplayManager,
                pollManager,
                announcementManager
        );
        this.subCommands = List.of(
                new ToggleSubCommand(context),
                new SlowmodeSubCommand(context),
                new ReloadSubCommand(context),
                new DisplaySubCommand(context, DisplayType.INVENTORY),
                new DisplaySubCommand(context, DisplayType.HAND),
                new DisplaySubCommand(context, DisplayType.ENDER_CHEST),
                new PollSubCommand(context),
                new AnnouncementSubCommand(context)
        );
        Map<String, ChatSubCommand> commands = new LinkedHashMap<>();
        for (ChatSubCommand subCommand : subCommands) {
            commands.put(subCommand.name(), subCommand);
        }
        this.subCommandByName = Map.copyOf(commands);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        ChatSubCommand subCommand = subCommandByName.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand == null) {
            sendUsage(sender);
            return true;
        }

        return subCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (args.length == 1) {
            return CommandUtils.startsWith(subCommands.stream()
                    .filter(subCommand -> subCommand.canUse(sender))
                    .map(ChatSubCommand::name)
                    .toList(), args[0]);
        }

        ChatSubCommand subCommand = subCommandByName.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand == null || !subCommand.canUse(sender)) {
            return List.of();
        }

        return subCommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    private void sendUsage(CommandSender sender) {
        List<String> usages = new ArrayList<>();
        for (ChatSubCommand subCommand : subCommands) {
            usages.addAll(subCommand.usages(sender));
        }

        if (usages.isEmpty()) {
            sender.sendMessage(settings.message("no-permission-command"));
            return;
        }

        sender.sendMessage(settings.message("usage", Map.of("usage", String.join(", ", usages))));
    }
}
