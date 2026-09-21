package com.seachat.customcommand;

import com.seachat.SeaChat;
import com.seachat.config.ChatSettings;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

public final class CustomCommandManager implements Listener {
    private static final Pattern COMMAND_NAME = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    private final File file;
    private final CommandMap commandMap;
    private final ChatSettings settings;
    private final Logger logger;
    private final String namespace;
    private final List<CustomMessageCommand> commands = new ArrayList<>();

    public CustomCommandManager(SeaChat plugin, ChatSettings settings) {
        this(new File(plugin.getDataFolder(), "custom-commands.yml"), plugin.getServer().getCommandMap(),
                settings, plugin.getLogger(), plugin.getName().toLowerCase(Locale.ROOT));
    }

    CustomCommandManager(File file, CommandMap commandMap, ChatSettings settings, Logger logger, String namespace) {
        this.file = file;
        this.commandMap = commandMap;
        this.settings = settings;
        this.logger = logger;
        this.namespace = namespace;
    }

    public void reload() {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            logger.warning("Could not load custom-commands.yml: " + exception.getMessage());
            return;
        }
        loadCommands(config);
    }

    void loadCommands(ConfigurationSection config) {
        shutdown();
        ConfigurationSection section = config.getConfigurationSection("commands");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null || !entry.getBoolean("enabled", true)) {
                continue;
            }
            String name = id.toLowerCase(Locale.ROOT);
            if (!COMMAND_NAME.matcher(name).matches()) {
                logger.warning("Skipping custom command '" + id + "': use letters, numbers, hyphens or underscores, without a slash.");
                continue;
            }
            List<String> messages = entry.getStringList("messages");
            if (messages.isEmpty()) {
                logger.warning("Skipping custom command '/" + name + "' because it has no messages.");
                continue;
            }
            // Check both labels so another plugin's command is never replaced.
            if (commandMap.getCommand(name) != null || commandMap.getCommand(namespace + ":" + name) != null) {
                logger.warning("Skipping custom command '/" + name + "' because it is already registered.");
                continue;
            }
            CustomMessageCommand command = new CustomMessageCommand(name,
                    entry.getString("permission", "").trim(), messages, settings);
            if (commandMap.register(namespace, command)) {
                commands.add(command);
            } else {
                unregister(command);
                logger.warning("Could not register custom command '/" + name + "'.");
            }
        }
        logger.info("Loaded " + commands.size() + " custom command(s).");
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        for (CustomMessageCommand command : commands) {
            if (!command.testPermissionSilent(event.getPlayer())) {
                event.getCommands().remove(command.getName());
                event.getCommands().remove(namespace + ":" + command.getName());
            }
        }
    }

    public void shutdown() {
        for (CustomMessageCommand command : commands) {
            unregister(command);
        }
        commands.clear();
    }

    private void unregister(CustomMessageCommand command) {
        command.unregister(commandMap);
        List<String> labels = commandMap.getKnownCommands().entrySet().stream()
                .filter(entry -> entry.getValue() == command)
                .map(java.util.Map.Entry::getKey)
                .toList();
        for (String label : labels) {
            commandMap.getKnownCommands().remove(label);
        }
    }
}
