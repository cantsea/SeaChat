package com.seachat.privatechat;

import com.seachat.chat.ChatState;
import com.seachat.config.ChatSettings;
import com.seachat.network.PrivateChatProtocol;
import com.seachat.network.PrivateChatProtocol.*;
import com.seachat.network.RecentMessages;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

/** Backend rendering and chat interception; definitions and permissions live on Velocity. */
final class PrivateChatRelay implements PluginMessageListener {
    private final JavaPlugin plugin;
    private final ChatSettings settings;
    private final ChatState state;
    private final java.util.function.Consumer<UUID> onCapture;
    private final Set<UUID> capturing = ConcurrentHashMap.newKeySet();
    private final RecentMessages received = new RecentMessages();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private BukkitTask expiryTask;

    PrivateChatRelay(JavaPlugin plugin, ChatSettings settings, ChatState state, java.util.function.Consumer<UUID> onCapture) {
        this.plugin = plugin; this.settings = settings; this.state = state;
        this.onCapture = onCapture;
    }

    void start() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, PrivateChatProtocol.CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, PrivateChatProtocol.CHANNEL, this);
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.nanoTime();
            var entries = pending.values().iterator();
            while (entries.hasNext()) {
                Pending message = entries.next();
                if (now - message.sentAt() >= 5_000_000_000L) {
                    entries.remove(); notifyFailure(message.player());
                }
            }
        }, 20L, 20L);
    }

    boolean handleToggledChat(Player player, String message) {
        UUID playerId = player.getUniqueId();
        if (!capturing.contains(playerId)) return false;
        // Cancel at the backend. Never modify/cancel signed chat on Velocity.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player current = Bukkit.getPlayer(playerId);
            if (current == null) return;
            UUID id = UUID.randomUUID();
            if (pending.size() >= 512 || !send(current, new Input(id, playerId, message))) {
                notifyFailure(playerId);
            } else {
                pending.put(id, new Pending(playerId, System.nanoTime()));
            }
        });
        return true;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player carrier, byte[] data) {
        if (!PrivateChatProtocol.CHANNEL.equals(channel) || data.length > PrivateChatProtocol.MAX_PACKET_BYTES) return;
        if (!Bukkit.isPrimaryThread()) {
            byte[] copy = data.clone();
            Bukkit.getScheduler().runTask(plugin, () -> accept(carrier, copy));
        } else accept(carrier, data);
    }

    private void accept(Player carrier, byte[] data) {
        try {
            switch (PrivateChatProtocol.decode(data)) {
                case Render request -> {
                    if (request.sender() != null && !request.sender().equals(carrier.getUniqueId())) return;
                    if (!received.first(request.id())) return;
                    CommandSender sender = request.sender() == null ? Bukkit.getConsoleSender() : Bukkit.getPlayer(request.sender());
                    if (sender == null) return;
                    var channel = new PrivateChatChannel(request.channel(), true, false, request.format(), "", request.command());
                    var gson = GsonComponentSerializer.gson();
                    String normal = gson.serialize(settings.privateChatMessage(sender, channel, request.message()));
                    String disabled = sender instanceof Player
                            ? gson.serialize(settings.privateChatMessage(sender, channel, request.message(), true)) : null;
                    send(carrier, new Rendered(request.id(), normal, disabled));
                }
                case Delivery delivery -> {
                    if (received.first(delivery.id())) ProxyChatDelivery.deliver(delivery, Bukkit::getPlayer, state);
                }
                case Capture capture -> {
                    if (!carrier.getUniqueId().equals(capture.player())) return;
                    if (received.first(capture.id())) {
                        if (capture.enabled()) {
                            onCapture.accept(capture.player());
                            capturing.add(capture.player());
                        } else capturing.remove(capture.player());
                    }
                    send(carrier, new Acknowledgement(capture.id()));
                }
                case Acknowledgement ack -> pending.remove(ack.id());
                default -> { }
            }
        } catch (IOException | RuntimeException ignored) {
            // A malformed packet cannot become public chat; requests time out at the proxy.
        }
    }

    private boolean send(Player carrier, Packet packet) {
        try {
            carrier.sendPluginMessage(plugin, PrivateChatProtocol.CHANNEL, PrivateChatProtocol.encode(packet));
            return true;
        } catch (IOException | RuntimeException exception) { return false; }
    }

    private void notifyFailure(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) player.sendMessage(settings.message(player, "private-chat-proxy-unavailable"));
    }

    void onQuit(UUID player) { capturing.remove(player); }

    void leaveForLocalChannel(Player player) {
        if (capturing.remove(player.getUniqueId())) send(player, new Capture(UUID.randomUUID(), player.getUniqueId(), false));
    }

    void close() {
        if (expiryTask != null) expiryTask.cancel();
        pending.clear(); capturing.clear();
        var messenger = plugin.getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin, PrivateChatProtocol.CHANNEL, this);
        messenger.unregisterOutgoingPluginChannel(plugin, PrivateChatProtocol.CHANNEL);
    }

    private record Pending(UUID player, long sentAt) {}
}
