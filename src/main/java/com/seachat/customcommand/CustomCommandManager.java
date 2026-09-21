package com.seachat.customcommand;

import com.seachat.SeaChat;
import com.seachat.config.ChatSettings;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.command.CommandMap;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
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
    private final List<CustomCommand> commands = new ArrayList<>();
    private final Map<String, CustomCommand> priorityLabels = new LinkedHashMap<>();
    private final Map<String, Command> displacedCommands = new HashMap<>();

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
        boolean defaultOverride = config.getBoolean("override-existing", true);
        // Reserve primary names first so aliases cannot claim a later entry's command.
        Set<String> primaryNames = section.getKeys(false).stream()
                .filter(id -> {
                    ConfigurationSection entry = section.getConfigurationSection(id);
                    if (entry == null || !entry.getBoolean("enabled", true)) {
                        return false;
                    }
                    CustomCommandType type = CustomCommandType.from(entry);
                    return type != null && !type.content(entry).isEmpty();
                })
                .map(id -> id.toLowerCase(Locale.ROOT))
                .filter(name -> COMMAND_NAME.matcher(name).matches())
                .collect(Collectors.toSet());
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
            CustomCommandType type = CustomCommandType.from(entry);
            if (type == null) {
                logger.warning("Skipping custom command '/" + name + "': type must be chat, player or console.");
                continue;
            }
            List<String> content = type.content(entry);
            if (content.isEmpty()) {
                logger.warning("Skipping custom command '/" + name + "' because it has no " + type.contentKey() + ".");
                continue;
            }
            boolean override = entry.getBoolean("override-existing", defaultOverride);
            if (ownedLabel(name) || (!override && conflictingLabel(name))) {
                logger.warning("Skipping custom command '/" + name + "' because it is already registered.");
                continue;
            }
            CustomCommand command = new CustomCommand(name,
                    entry.getString("permission", "").trim(), type, content, settings);
            command.setAliases(loadAliases(name, entry.getStringList("aliases"), primaryNames, override));
            List<String> labels = new ArrayList<>(List.of(name, namespace + ":" + name));
            for (String alias : command.getAliases()) {
                labels.add(alias);
                labels.add(namespace + ":" + alias);
            }
            Map<String, Command> displaced = new HashMap<>();
            if (override) {
                for (String label : labels) {
                    Command existing = commandMap.getKnownCommands().remove(label);
                    if (existing != null) {
                        displaced.put(label, existing);
                    }
                }
            }
            boolean registered = false;
            try {
                registered = commandMap.register(namespace, command);
                if (registered) {
                    commands.add(command);
                    displacedCommands.putAll(displaced);
                    if (override) {
                        labels.forEach(label -> priorityLabels.put(label, command));
                    }
                } else {
                    logger.warning("Could not register custom command '/" + name + "'.");
                }
            } finally {
                if (!registered) {
                    unregister(command);
                    displaced.forEach(this::restore);
                }
            }
        }
        logger.info("Loaded " + commands.size() + " custom command(s).");
    }

    private boolean conflictingLabel(String name) {
        return commandMap.getCommand(name) != null || commandMap.getCommand(namespace + ":" + name) != null;
    }

    private boolean ownedLabel(String name) {
        return commands.contains(commandMap.getCommand(name))
                || commands.contains(commandMap.getCommand(namespace + ":" + name));
    }

    private List<String> loadAliases(String name, List<String> configuredAliases, Set<String> primaryNames, boolean override) {
        Set<String> aliases = new LinkedHashSet<>();
        for (String configuredAlias : configuredAliases) {
            String alias = configuredAlias.trim().toLowerCase(Locale.ROOT);
            if (!COMMAND_NAME.matcher(alias).matches()) {
                logger.warning("Skipping alias '" + configuredAlias + "' for '/" + name
                        + "': use letters, numbers, hyphens or underscores, without a slash.");
                continue;
            }
            if (alias.equals(name) || aliases.contains(alias)) {
                continue;
            }
            if (primaryNames.contains(alias) || ownedLabel(alias) || (!override && conflictingLabel(alias))) {
                logger.warning("Skipping alias '/" + alias + "' for '/" + name
                        + "' because it is already used by another command.");
                continue;
            }
            aliases.add(alias);
        }
        return new ArrayList<>(aliases);
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        for (CustomCommand command : commands) {
            if (!command.testPermissionSilent(event.getPlayer())) {
                event.getCommands().remove(command.getName());
                event.getCommands().remove(namespace + ":" + command.getName());
                for (String alias : command.getAliases()) {
                    event.getCommands().remove(alias);
                    event.getCommands().remove(namespace + ":" + alias);
                }
            }
        }
    }

    public void shutdown() {
        for (CustomCommand command : commands) {
            unregister(command);
        }
        commands.clear();
        priorityLabels.clear();
        displacedCommands.clear();
    }

    // Called after startup/plugin registration so load order cannot take our labels back.
    public boolean refreshPriority() {
        boolean changed = false;
        for (Map.Entry<String, CustomCommand> entry : priorityLabels.entrySet()) {
            Command existing = commandMap.getKnownCommands().get(entry.getKey());
            if (existing == entry.getValue()) {
                continue;
            }
            if (existing != null) {
                displacedCommands.put(entry.getKey(), existing);
            }
            commandMap.getKnownCommands().put(entry.getKey(), entry.getValue());
            changed = true;
        }
        return changed;
    }

    private void restore(String label, Command previous) {
        // Do not revive commands belonging to a plugin that has since been disabled.
        if (previous instanceof PluginIdentifiableCommand identifiable && !identifiable.getPlugin().isEnabled()) {
            return;
        }
        if (!commandMap.getKnownCommands().containsKey(label)) {
            commandMap.getKnownCommands().put(label, previous);
        }
    }

    private void unregister(CustomCommand command) {
        command.unregister(commandMap);
        List<String> labels = commandMap.getKnownCommands().entrySet().stream()
                .filter(entry -> entry.getValue() == command)
                .map(java.util.Map.Entry::getKey)
                .toList();
        for (String label : labels) {
            commandMap.getKnownCommands().remove(label);
            Command displaced = displacedCommands.remove(label);
            if (displaced != null) {
                restore(label, displaced);
            }
        }
    }
}
