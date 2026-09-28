package com.seachat.velocity;

import com.google.inject.Inject;
import com.seachat.network.PrivateChatProtocol;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

public final class SeaChatVelocity {
    static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(PrivateChatProtocol.CHANNEL);
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final NetworkChats chats;
    private final Map<String, CommandMeta> registered = new HashMap<>();
    private volatile Map<String, NetworkChannel> commands = Map.of();
    private ScheduledTask expiry;

    @Inject
    public SeaChatVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy; this.logger = logger; this.dataDirectory = dataDirectory;
        this.chats = new NetworkChats(proxy);
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(CHANNEL);
        try {
            Files.createDirectories(dataDirectory);
            Path config = dataDirectory.resolve("config.yml");
            if (!Files.exists(config)) {
                try (var source = getClass().getResourceAsStream("/config.yml")) {
                    if (source == null) throw new IOException("Missing default proxy configuration");
                    Files.copy(source, config);
                }
            }
            registerAdmin();
            reload();
        } catch (IOException | RuntimeException exception) {
            logger.error("Could not load SeaChat proxy configuration", exception);
        }
        expiry = proxy.getScheduler().buildTask(this, chats::expire).repeat(1, TimeUnit.SECONDS).schedule();
    }

    synchronized void reload() throws IOException {
        ProxySettings next;
        try (var reader = Files.newBufferedReader(dataDirectory.resolve("config.yml"))) {
            next = ProxySettings.read(reader);
        }
        // Validate every label before replacing the active configuration.
        for (NetworkChannel channel : next.channels().values()) {
            CommandMeta current = proxy.getCommandManager().getCommandMeta(channel.command());
            if (current != null && current != registered.get(channel.command()))
                throw new IOException("Proxy command /" + channel.command() + " is already registered");
        }
        for (var entry : Map.copyOf(registered).entrySet()) {
            if (entry.getKey().equals("seachatproxy")) continue;
            if (proxy.getCommandManager().getCommandMeta(entry.getKey()) == entry.getValue())
                proxy.getCommandManager().unregister(entry.getValue());
            registered.remove(entry.getKey());
        }
        chats.configure(next);
        for (NetworkChannel channel : next.channels().values()) {
            CommandMeta meta = proxy.getCommandManager().metaBuilder(channel.command()).plugin(this).build();
            proxy.getCommandManager().register(meta, new SimpleCommand() {
                @Override public void execute(Invocation invocation) {
                    chats.execute(invocation.source(), channel.id(), invocation.arguments());
                }
                @Override public boolean hasPermission(Invocation invocation) {
                    // Always intercept this label: Velocity forwards a false result to the backend,
                    // which could expose a private message through a conflicting backend command.
                    return true;
                }
            });
            registered.put(channel.command(), meta);
        }
        commands = next.channels().values().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(NetworkChannel::command, channel -> channel));
        logger.info("Loaded {} centrally configured private channel(s).", next.channels().size());
    }

    private void registerAdmin() throws IOException {
        if (proxy.getCommandManager().hasCommand("seachatproxy")) throw new IOException("/seachatproxy is already registered");
        CommandMeta meta = proxy.getCommandManager().metaBuilder("seachatproxy").plugin(this).build();
        proxy.getCommandManager().register(meta, new SimpleCommand() {
            @Override public void execute(Invocation invocation) {
                String[] args = invocation.arguments();
                if (args.length == 1 && args[0].equalsIgnoreCase("leave") && invocation.source() instanceof Player player) {
                    chats.leave(player);
                } else if (args.length == 1 && args[0].equalsIgnoreCase("reload")
                        && invocation.source().hasPermission("seachat.proxy.reload")) {
                    try {
                        reload(); chats.notice(invocation.source(), "reload-success", null);
                    } catch (IOException | RuntimeException exception) {
                        logger.error("SeaChat proxy reload failed", exception);
                        chats.notice(invocation.source(), "reload-failed", null);
                    }
                } else chats.notice(invocation.source(), "admin-usage", null);
            }
        });
        registered.put("seachatproxy", meta);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection source)) return;
        try { chats.accept(source, PrivateChatProtocol.decode(event.getData())); }
        catch (IOException | RuntimeException ignored) { /* Never forward untrusted or incompatible packets. */ }
    }

    @Subscribe
    public void onAvailableCommands(PlayerAvailableCommandsEvent event) {
        event.getRootNode().getChildren().removeIf(node -> {
            NetworkChannel channel = commands.get(node.getName());
            return channel != null && !chats.allowed(event.getPlayer(), channel);
        });
    }

    @Subscribe
    public void onServerChanged(ServerConnectedEvent event) { chats.serverChanged(event.getPlayer()); }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) { chats.disconnected(event.getPlayer()); }

    @Subscribe
    public synchronized void onShutdown(ProxyShutdownEvent event) {
        if (expiry != null) expiry.cancel();
        for (var entry : registered.entrySet()) {
            if (proxy.getCommandManager().getCommandMeta(entry.getKey()) == entry.getValue())
                proxy.getCommandManager().unregister(entry.getValue());
        }
        registered.clear();
        proxy.getChannelRegistrar().unregister(CHANNEL);
    }
}
