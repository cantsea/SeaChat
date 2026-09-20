package com.seachat.privatechat;

import com.seachat.SeaChat;
import com.seachat.chat.ChatState;
import com.seachat.config.ChatSettings;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

public final class PrivateChatManager implements Listener {
    private static final Pattern COMMAND_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    private final SeaChat plugin;
    private final ChatSettings settings;
    private final ChatState state;
    private final Map<String, PrivateChatCommand> registeredCommands = new HashMap<>();
    private final ConcurrentMap<UUID, String> toggledChannels = new ConcurrentHashMap<>();
    private volatile Map<String, PrivateChatChannel> activeChannels = Map.of();

    public PrivateChatManager(SeaChat plugin, ChatSettings settings, ChatState state) {
        this.plugin = plugin;
        this.settings = settings;
        this.state = state;
    }

    public void reloadChannels() {
        unregisterChannels();

        CommandMap commandMap = Bukkit.getCommandMap();
        Map<String, PrivateChatChannel> loadedChannels = new HashMap<>();
        for (PrivateChatChannel channel : settings.privateChatChannels()) {
            if (!channel.enabled()) {
                continue;
            }

            String commandName = channel.command().toLowerCase(Locale.ROOT);
            if (!COMMAND_PATTERN.matcher(commandName).matches()) {
                plugin.getLogger().warning("Skipping private chat '" + channel.id()
                        + "' because command '" + channel.command() + "' is invalid.");
                continue;
            }

            if (registeredCommands.containsKey(commandName)) {
                plugin.getLogger().warning("Skipping private chat '" + channel.id()
                        + "' because command '/" + commandName + "' is already used by another private chat.");
                continue;
            }

            Command existingCommand = commandMap.getKnownCommands().get(commandName);
            if (existingCommand != null) {
                plugin.getLogger().warning("Skipping private chat '" + channel.id()
                        + "' because command '/" + commandName + "' is already registered.");
                continue;
            }

            PrivateChatCommand command = new PrivateChatCommand(channel, this);
            command.setPermission(channel.permission());
            command.setDescription("Send a message to the " + channel.id() + " private chat.");
            command.setUsage("/" + commandName + " <message>");

            if (commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), command)) {
                registeredCommands.put(commandName, command);
                loadedChannels.put(channel.id(), channel);
            } else {
                plugin.getLogger().warning("Could not register private chat command '/" + commandName + "'.");
            }
        }

        activeChannels = Map.copyOf(loadedChannels);
        toggledChannels.entrySet().removeIf(entry -> {
            PrivateChatChannel channel = activeChannels.get(entry.getValue());
            return channel == null || !channel.toggleable();
        });
    }

    public void shutdown() {
        toggledChannels.clear();
        activeChannels = Map.of();
        unregisterChannels();
    }

    public boolean handleToggledChat(Player player, String message) {
        String channelId = toggledChannels.get(player.getUniqueId());
        if (channelId == null) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player currentPlayer = Bukkit.getPlayer(playerId);
            if (currentPlayer == null) {
                toggledChannels.remove(playerId);
                return;
            }

            PrivateChatChannel channel = activeChannels.get(channelId);
            if (channel == null || !channel.toggleable()) {
                toggledChannels.remove(playerId, channelId);
                return;
            }

            if (!currentPlayer.hasPermission(channel.permission())) {
                toggledChannels.remove(playerId, channelId);
                currentPlayer.sendMessage(settings.message(currentPlayer, "private-chat-no-permission"));
                return;
            }

            sendToChannel(channel, currentPlayer, message);
        });
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        toggledChannels.remove(event.getPlayer().getUniqueId());
    }

    public void filterVisibleCommands(PlayerCommandSendEvent event) {
        for (Map.Entry<String, PrivateChatCommand> entry : registeredCommands.entrySet()) {
            if (event.getPlayer().hasPermission(entry.getValue().channel().permission())) {
                continue;
            }

            event.getCommands().remove(entry.getKey());
            event.getCommands().remove(plugin.getName().toLowerCase(Locale.ROOT) + ":" + entry.getKey());
        }
    }

    private void unregisterChannels() {
        CommandMap commandMap = Bukkit.getCommandMap();
        Map<String, Command> knownCommands = commandMap.getKnownCommands();

        for (PrivateChatCommand command : registeredCommands.values()) {
            command.unregister(commandMap);
            List<String> commandKeys = knownCommands.entrySet().stream()
                    .filter(entry -> entry.getValue() == command)
                    .map(Map.Entry::getKey)
                    .toList();
            for (String commandKey : commandKeys) {
                knownCommands.remove(commandKey);
            }
        }
        registeredCommands.clear();
    }

    boolean execute(PrivateChatChannel channel, CommandSender sender, String[] args) {
        if (!sender.hasPermission(channel.permission())) {
            sender.sendMessage(settings.message(sender instanceof Player player ? player : null, "private-chat-no-permission"));
            return true;
        }

        if (args.length == 0) {
            if (channel.toggleable() && sender instanceof Player player) {
                toggleChannel(player, channel);
                return true;
            }

            sender.sendMessage(settings.message(sender instanceof Player player ? player : null, "private-chat-usage",
                    Map.of("command", settings.escape(channel.command()))));
            return true;
        }

        String message = String.join(" ", args);
        sendToChannel(channel, sender, message);
        return true;
    }

    private void toggleChannel(Player player, PrivateChatChannel channel) {
        String previousChannel = toggledChannels.put(player.getUniqueId(), channel.id());
        boolean disabled = channel.id().equals(previousChannel);
        if (disabled) {
            toggledChannels.remove(player.getUniqueId(), channel.id());
        }

        player.sendMessage(settings.message(player,
                disabled ? "private-chat-toggle-disabled" : "private-chat-toggle-enabled",
                Map.of(
                        "chat", settings.escape(channel.id()),
                        "command", settings.escape(channel.command())
                )));
    }

    private void sendToChannel(PrivateChatChannel channel, CommandSender sender, String message) {
        var formattedMessage = settings.privateChatMessage(sender, channel, message);
        Component colorlessMessage = null;
        boolean sentToPlayer = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(channel.permission())) {
                if (sender instanceof Player && state.areColorsDisabled(player.getUniqueId())) {
                    if (colorlessMessage == null) {
                        colorlessMessage = settings.privateChatMessage(sender, channel, message, true);
                    }
                    player.sendMessage(colorlessMessage);
                } else {
                    player.sendMessage(formattedMessage);
                }
                sentToPlayer = true;
            }
        }

        if (!(sender instanceof Player) || !sentToPlayer) {
            sender.sendMessage(sender instanceof Player player && state.areColorsDisabled(player.getUniqueId())
                    ? settings.privateChatMessage(sender, channel, message, true) : formattedMessage);
        }
    }

}
