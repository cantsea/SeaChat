package com.seachat.velocity;

import com.seachat.network.PrivateChatProtocol;
import com.seachat.network.PrivateChatProtocol.*;
import com.seachat.network.RecentMessages;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** Proxy-authoritative membership, toggles, request correlation and recipient selection. */
final class NetworkChats {
    private static final long TIMEOUT = 5_000_000_000L;
    private final ProxyServer proxy;
    private final LongSupplier clock;
    private final RecentMessages inputs = new RecentMessages();
    private final Map<UUID, String> toggled = new HashMap<>();
    private final Map<UUID, PendingRender> renders = new HashMap<>();
    private final Map<UUID, PendingCapture> captures = new HashMap<>();
    private volatile ProxySettings settings = ProxySettings.defaults();

    NetworkChats(ProxyServer proxy) { this(proxy, System::nanoTime); }
    NetworkChats(ProxyServer proxy, LongSupplier clock) { this.proxy = proxy; this.clock = clock; }

    synchronized void configure(ProxySettings next) {
        settings = next;
        for (PendingRender pending : renders.values()) notice(pending.sender(), "unavailable", pending.channel());
        renders.clear();
        for (UUID id : List.copyOf(toggled.keySet())) {
            proxy.getPlayer(id).ifPresent(player -> {
                NetworkChannel channel = next.channels().get(toggled.get(id));
                if (channel == null || !channel.toggleable() || !allowed(player, channel)) leave(player);
            });
        }
    }

    boolean allowed(CommandSource source, NetworkChannel channel) {
        return channel != null && source.hasPermission(channel.permission())
                && (!(source instanceof Player player) || player.getCurrentServer()
                .filter(connection -> channel.allows(connection.getServerInfo().getName())).isPresent());
    }

    synchronized void execute(CommandSource source, String channelId, String[] arguments) {
        NetworkChannel channel = settings.channels().get(channelId);
        if (!allowed(source, channel)) { notice(source, "denied", channel); return; }
        if (arguments.length == 0) {
            if (!(source instanceof Player player) || !channel.toggleable()) {
                notice(source, "usage", channel); return;
            }
            if (captures.values().stream().anyMatch(pending -> pending.player().getUniqueId().equals(player.getUniqueId()))) {
                notice(source, "pending", channel); return;
            }
            boolean enable = !channel.id().equals(toggled.get(player.getUniqueId()));
            setCapture(player, enable ? channel : null);
        } else render(source, channel, String.join(" ", arguments));
    }

    synchronized void leave(Player player) { setCapture(player, null); }

    private void setCapture(Player player, NetworkChannel channel) {
        ServerConnection connection = player.getCurrentServer().orElse(null);
        if (connection == null || captures.size() >= 1024) { notice(player, "unavailable", channel); return; }
        captures.values().removeIf(pending -> pending.player().getUniqueId().equals(player.getUniqueId()));
        UUID id = UUID.randomUUID();
        PendingCapture pending = new PendingCapture(player, connection, channel, clock.getAsLong());
        captures.put(id, pending);
        if (channel == null) toggled.remove(player.getUniqueId()); else toggled.put(player.getUniqueId(), channel.id());
        if (!send(connection, new Capture(id, player.getUniqueId(), channel != null))) {
            captures.remove(id); notice(player, "unavailable", channel);
        }
    }

    private void render(CommandSource sender, NetworkChannel channel, String message) {
        if (message.isBlank()) { notice(sender, "usage", channel); return; }
        ServerConnection connection;
        if (sender instanceof Player player) connection = player.getCurrentServer().orElse(null);
        else connection = proxy.getAllPlayers().stream().flatMap(player -> player.getCurrentServer().stream())
                .filter(server -> channel.allows(server.getServerInfo().getName())).findFirst().orElse(null);
        if (connection == null || renders.size() >= 1024) { notice(sender, "unavailable", channel); return; }
        UUID id = UUID.randomUUID();
        renders.put(id, new PendingRender(sender, connection, channel, clock.getAsLong()));
        UUID senderId = sender instanceof Player player ? player.getUniqueId() : null;
        if (!send(connection, new Render(id, senderId, channel.id(), channel.command(), channel.format(), message))) {
            renders.remove(id); notice(sender, "unavailable", channel);
        }
    }

    synchronized void accept(ServerConnection source, Packet packet) {
        if (source.getPlayer().getCurrentServer().orElse(null) != source) return;
        switch (packet) {
            case Rendered reply -> {
                PendingRender pending = renders.get(reply.id());
                if (pending == null || pending.connection() != source) return;
                renders.remove(reply.id());
                NetworkChannel channel = settings.channels().get(pending.channel().id());
                if (clock.getAsLong() - pending.created() >= TIMEOUT || channel != pending.channel()
                        || !allowed(pending.sender(), channel)
                        || (pending.sender() instanceof Player player && (player.getCurrentServer().orElse(null) != source
                        || reply.disabled() == null))) {
                    notice(pending.sender(), "unavailable", pending.channel()); return;
                }
                deliver(pending, reply);
            }
            case Input input -> {
                if (!source.getPlayer().getUniqueId().equals(input.player())) return;
                send(source, new Acknowledgement(input.id()));
                if (!inputs.first(input.id())) return;
                String channelId = toggled.get(input.player());
                NetworkChannel channel = channelId == null ? null : settings.channels().get(channelId);
                if (!allowed(source.getPlayer(), channel)) {
                    // Keep backend capture active until an explicit leave succeeds. Never leak a rejected private line.
                    notice(source.getPlayer(), "unavailable", channel); return;
                }
                render(source.getPlayer(), channel, input.message());
            }
            case Acknowledgement ack -> {
                PendingCapture pending = captures.get(ack.id());
                if (pending == null || pending.connection() != source) return;
                captures.remove(ack.id());
                notice(pending.player(), pending.channel() == null ? "toggle-disabled" : "toggle-enabled", pending.channel());
            }
            case Capture capture -> {
                // A local backend channel may replace a network toggle, but cannot grant network membership.
                if (!capture.enabled() && source.getPlayer().getUniqueId().equals(capture.player())) {
                    toggled.remove(capture.player());
                    captures.values().removeIf(pending -> pending.player().getUniqueId().equals(capture.player()));
                }
            }
            default -> { }
        }
    }

    private void deliver(PendingRender pending, Rendered reply) {
        Map<RegisteredServer, List<UUID>> destinations = new HashMap<>();
        for (Player player : proxy.getAllPlayers()) {
            if (!allowed(player, pending.channel())) continue;
            player.getCurrentServer().ifPresent(connection -> destinations
                    .computeIfAbsent(connection.getServer(), ignored -> new ArrayList<>()).add(player.getUniqueId()));
        }
        boolean delivered = true;
        for (var entry : destinations.entrySet()) {
            List<UUID> ids = entry.getValue();
            for (int start = 0; start < ids.size(); start += PrivateChatProtocol.MAX_RECIPIENTS) {
                Delivery packet = new Delivery(UUID.randomUUID(), ids.subList(start, Math.min(ids.size(), start + PrivateChatProtocol.MAX_RECIPIENTS)),
                        reply.normal(), reply.disabled());
                try {
                    delivered &= entry.getKey().sendPluginMessage(SeaChatVelocity.CHANNEL, PrivateChatProtocol.encode(packet));
                } catch (IOException | RuntimeException exception) { delivered = false; }
            }
        }
        if (!(pending.sender() instanceof Player)) {
            pending.sender().sendMessage(net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserialize(reply.normal()));
        }
        if (!delivered) notice(pending.sender(), "unavailable", pending.channel());
    }

    synchronized void serverChanged(Player player) {
        boolean wasToggled = toggled.remove(player.getUniqueId()) != null;
        captures.values().removeIf(pending -> pending.player().getUniqueId().equals(player.getUniqueId()));
        renders.values().removeIf(pending -> pending.sender() == player);
        if (wasToggled) notice(player, "server-changed", null);
    }

    synchronized void disconnected(Player player) {
        toggled.remove(player.getUniqueId());
        captures.values().removeIf(pending -> pending.player().getUniqueId().equals(player.getUniqueId()));
        renders.values().removeIf(pending -> pending.sender() == player);
    }

    synchronized void expire() {
        long now = clock.getAsLong();
        renders.values().removeIf(pending -> {
            if (now - pending.created() < TIMEOUT) return false;
            notice(pending.sender(), "unavailable", pending.channel()); return true;
        });
        captures.values().removeIf(pending -> {
            if (now - pending.created() < TIMEOUT) return false;
            notice(pending.player(), "unavailable", pending.channel()); return true;
        });
    }

    void notice(CommandSource source, String key, NetworkChannel channel) {
        ProxySettings current = settings;
        String message = current.messages().get("prefix") + current.messages().get(key);
        source.sendMessage(MiniMessage.miniMessage().deserialize(message.replace("{chat}", "<chat>").replace("{command}", "<command>"),
                Placeholder.unparsed("chat", channel == null ? "" : channel.id()),
                Placeholder.unparsed("command", channel == null ? "seachatproxy" : channel.command())));
    }

    private boolean send(ServerConnection connection, Packet packet) {
        try { return connection.sendPluginMessage(SeaChatVelocity.CHANNEL, PrivateChatProtocol.encode(packet)); }
        catch (IOException | RuntimeException exception) { return false; }
    }

    private record PendingRender(CommandSource sender, ServerConnection connection, NetworkChannel channel, long created) {}
    private record PendingCapture(Player player, ServerConnection connection, NetworkChannel channel, long created) {}
}
