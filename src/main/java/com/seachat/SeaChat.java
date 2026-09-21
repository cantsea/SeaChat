package com.seachat;

import com.seachat.announcement.AnnouncementManager;
import com.seachat.chat.ChatState;
import com.seachat.command.ChatCommand;
import com.seachat.config.ChatSettings;
import com.seachat.customcommand.CustomCommandManager;
import com.seachat.customcommand.CommandPriorityListener;
import com.seachat.display.InventoryDisplayManager;
import com.seachat.listener.ChatListener;
import com.seachat.listener.CommandRefreshListener;
import com.seachat.listener.CommandVisibilityListener;
import com.seachat.poll.PollManager;
import com.seachat.privatechat.PrivateChatManager;
import java.io.File;
import org.bukkit.entity.Player;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SeaChat extends JavaPlugin {
    private ChatSettings settings;
    private ChatState state;
    private FileConfiguration langConfig;
    private InventoryDisplayManager inventoryDisplayManager;
    private PollManager pollManager;
    private PrivateChatManager privateChatManager;
    private AnnouncementManager announcementManager;
    private CustomCommandManager customCommandManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("lang.yml", false);
        saveResource("announcements.yml", false);
        if (!new File(getDataFolder(), "custom-commands.yml").exists()) {
            saveResource("custom-commands.yml", false);
        }
        reloadLang();

        this.settings = ChatSettings.from(getConfig(), langConfig);
        this.state = new ChatState(settings.slowmodeEnabled());

        PluginCommand chatCommand = getCommand("chat");
        if (chatCommand == null) {
            getLogger().severe("The /chat command is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.inventoryDisplayManager = new InventoryDisplayManager(this, settings);
        this.pollManager = new PollManager(this, settings, state);
        this.privateChatManager = new PrivateChatManager(this, settings, state);
        this.privateChatManager.reloadChannels();
        this.customCommandManager = new CustomCommandManager(this, settings);
        this.customCommandManager.reload();
        this.announcementManager = new AnnouncementManager(this, settings);
        this.announcementManager.reload();
        ChatCommand commandHandler = new ChatCommand(
                this, settings, state, inventoryDisplayManager, pollManager, announcementManager);
        chatCommand.setExecutor(commandHandler);
        chatCommand.setTabCompleter(commandHandler);

        PluginCommand inventoryCommand = getCommand("seachatinv");
        if (inventoryCommand == null) {
            getLogger().severe("The /seachatinv command is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        inventoryCommand.setExecutor(inventoryDisplayManager);

        getServer().getPluginManager().registerEvents(
                new ChatListener(this, settings, state, pollManager, privateChatManager), this);
        getServer().getPluginManager().registerEvents(inventoryDisplayManager, this);
        getServer().getPluginManager().registerEvents(privateChatManager, this);
        getServer().getPluginManager().registerEvents(customCommandManager, this);
        CommandPriorityListener commandPriorityListener = new CommandPriorityListener(this, customCommandManager);
        getServer().getPluginManager().registerEvents(commandPriorityListener, this);
        commandPriorityListener.scheduleRefresh();
        getServer().getPluginManager().registerEvents(new CommandVisibilityListener(settings, privateChatManager), this);
        getServer().getPluginManager().registerEvents(new CommandRefreshListener(), this);

    }

    @Override
    public void onDisable() {
        if (state != null) {
            state.clear();
        }
        if (inventoryDisplayManager != null) {
            inventoryDisplayManager.shutdown();
        }
        if (pollManager != null) {
            pollManager.shutdown();
        }
        // Restore displaced labels before their owning features unregister them.
        if (customCommandManager != null) {
            customCommandManager.shutdown();
        }
        if (privateChatManager != null) {
            privateChatManager.shutdown();
        }
        if (announcementManager != null) {
            announcementManager.shutdown();
        }
    }

    public void reloadSettings() {
        reloadConfig();
        reloadLang();
        this.settings.copyFrom(ChatSettings.from(getConfig(), langConfig));
        this.state.setSlowmodeEnabled(settings.slowmodeEnabled());
        this.inventoryDisplayManager.reloadCleanupTask();
        // Release old labels before private chats and custom commands reclaim them.
        this.customCommandManager.shutdown();
        this.privateChatManager.reloadChannels();
        this.customCommandManager.reload();
        this.announcementManager.reload();
        refreshCommands();
    }

    public void saveSlowmodeEnabled(boolean enabled) {
        getConfig().set("slowmode.enabled", enabled);
        saveConfig();
    }

    private void reloadLang() {
        File langFile = new File(getDataFolder(), "lang.yml");
        this.langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    private void refreshCommands() {
        for (Player player : getServer().getOnlinePlayers()) {
            player.updateCommands();
        }
    }
}
