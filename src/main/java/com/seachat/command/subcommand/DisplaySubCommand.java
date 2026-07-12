package com.seachat.command.subcommand;

import com.seachat.command.ChatSubCommand;
import com.seachat.command.CommandContext;
import com.seachat.command.CommandUtils;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DisplaySubCommand implements ChatSubCommand {
    private final CommandContext context;
    private final DisplayType type;

    public DisplaySubCommand(CommandContext context, DisplayType type) {
        this.context = context;
        this.type = type;
    }

    @Override
    public String name() {
        return type.commandName();
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return context.settings().displayEnabled() && sender.hasPermission(type.permission());
    }

    @Override
    public List<String> usages(CommandSender sender) {
        return canUse(sender) ? List.of("/chat " + type.commandName()) : List.of();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!context.settings().displayEnabled()) {
            sender.sendMessage(context.settings().message("display-disabled"));
            return true;
        }

        if (args.length != 0) {
            sender.sendMessage(context.settings().message("usage", Map.of("usage", "/chat " + type.commandName())));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(context.settings().message(type.onlyPlayersMessage()));
            return true;
        }

        if (!player.hasPermission(type.permission())) {
            player.sendMessage(context.settings().message(type.noPermissionMessage()));
            return true;
        }

        if (context.state().isHidden(player.getUniqueId())) {
            player.sendMessage(context.settings().message("chat-hidden-cannot-speak"));
            return true;
        }

        if (!checkDisplayCooldown(player)) {
            return true;
        }

        Component displayMessage = createDisplayMessage(player);
        if (displayMessage == null) {
            player.sendMessage(context.settings().message("hand-empty"));
            return true;
        }

        CommandUtils.broadcastToVisibleChat(context, displayMessage);
        context.state().markDisplay(player.getUniqueId());
        return true;
    }

    private Component createDisplayMessage(Player player) {
        return switch (type) {
            case INVENTORY -> context.settings().inventoryChatMessage(
                    player,
                    context.inventoryDisplayManager().createDisplayMessage(player, context.settings())
            );
            case HAND -> {
                Component hand = context.inventoryDisplayManager().createHandDisplayMessage(player, context.settings());
                yield hand == null ? null : context.settings().handChatMessage(player, hand);
            }
            case ENDER_CHEST -> context.settings().enderChestChatMessage(
                    player,
                    context.inventoryDisplayManager().createEnderChestDisplayMessage(player, context.settings())
            );
        };
    }

    private boolean checkDisplayCooldown(Player player) {
        long remainingMillis = context.state().remainingDisplayCooldownMillis(
                player.getUniqueId(),
                context.settings().displayCooldownMillis()
        );
        if (remainingMillis <= 0L) {
            return true;
        }

        player.sendMessage(context.settings().message("display-cooldown",
                Map.of("seconds", String.valueOf(CommandUtils.formatSeconds(remainingMillis)))));
        return false;
    }
}
