package com.seachat;

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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("lang.yml", false);
        saveResource("announcements.yml", false);
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
        this.privateChatManager = new PrivateChatManager(this, settings);
        this.privateChatManager.reloadChannels();
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
        if (privateChatManager != null) {
            privateChatManager.shutdown();
        }
        if (announcementManager != null) {
            announcementManager.shutdown();
        }
    }

    void reloadSettings() {
        reloadConfig();
        reloadLang();
        this.settings.copyFrom(ChatSettings.from(getConfig(), langConfig));
        this.state.setSlowmodeEnabled(settings.slowmodeEnabled());
        this.inventoryDisplayManager.reloadCleanupTask();
        this.privateChatManager.reloadChannels();
        this.announcementManager.reload();
        refreshCommands();
    }

    void saveSlowmodeEnabled(boolean enabled) {
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
