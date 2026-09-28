package com.seachat.velocity;

import com.seachat.network.PrivateChatProtocol;
import com.seachat.network.PrivateChatProtocol.*;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.*;

public class SeaChatVelocityTest {
    private final List<Client> clients = new ArrayList<>();
    private final Endpoint survival = new Endpoint("survival");
    private final Endpoint creative = new Endpoint("creative");
    private final Endpoint limbo = new Endpoint("limbo");
    private final Client sender = new Client(survival, true);
    private final Client recipient = new Client(creative, true);
    private final Client denied = new Client(creative, false);
    private final Client excluded = new Client(limbo, true);
    private final AtomicLong now = new AtomicLong();
    private final ProxyServer proxy = mock(ProxyServer.class, (method, args) -> switch (method) {
        case "getAllPlayers" -> clients.stream().map(client -> client.player).toList();
        case "getPlayer" -> clients.stream().filter(client -> client.id.equals(args[0])).map(client -> client.player).findFirst();
        default -> unsupported(method);
    });
    private final NetworkChats chats = new NetworkChats(proxy, now::get);
    private final NetworkChannel staff = channel(Set.of(), Set.of("limbo"));

    public SeaChatVelocityTest() { chats.configure(settings(staff)); }

    private NetworkChannel channel(Set<String> whitelist, Set<String> blacklist) {
        return new NetworkChannel("staff", "sc", "seachat.chat.staff", "<yellow>{sender} <message>", true, whitelist, blacklist);
    }
    private ProxySettings settings(NetworkChannel channel) { return new ProxySettings(Map.of(channel.id(), channel), ProxySettings.DEFAULT_MESSAGES); }
    private Render render(Client client) {
        chats.execute(client.player, "staff", new String[]{"hello"});
        return (Render) client.out.getLast();
    }
    private void reply(Client client, Render render) {
        chats.accept(client.connection, new Rendered(render.id(), "{\"text\":\"hello\"}", "{\"text\":\"gray\"}"));
    }
    private List<Delivery> deliveries(Endpoint endpoint) {
        return endpoint.messages.stream().filter(Delivery.class::isInstance).map(Delivery.class::cast).toList();
    }

    @Test public void centralCommandRendersAtSourceAndSelectsRecipientsAcrossServers() {
        Render request = render(sender);
        assertEquals(staff.format(), request.format());
        assertEquals(sender.id, request.sender());
        reply(sender, request);
        assertEquals(List.of(sender.id), deliveries(survival).getFirst().recipients());
        assertEquals(List.of(recipient.id), deliveries(creative).getFirst().recipients());
        assertTrue(deliveries(limbo).isEmpty());
        assertEquals(0, denied.out.size());
        reply(sender, request);
        assertEquals(1, deliveries(creative).size());
    }

    @Test public void blocksSendingWithoutPermissionOrFromExcludedServer() {
        chats.execute(denied.player, "staff", new String[]{"secret"});
        chats.execute(excluded.player, "staff", new String[]{"secret"});
        assertTrue(denied.out.isEmpty());
        assertTrue(excluded.out.isEmpty());
        assertTrue(denied.notices.getLast().contains("cannot use"));
    }

    @Test public void whitelistAppliesToSendingAndReceivingAndBlacklistWins() {
        chats.configure(settings(channel(Set.of("survival", "creative"), Set.of("creative"))));
        chats.execute(recipient.player, "staff", new String[]{"secret"});
        assertTrue(recipient.out.isEmpty());
        reply(sender, render(sender));
        assertEquals(1, deliveries(survival).size());
        assertTrue(deliveries(creative).isEmpty());
        assertTrue(deliveries(limbo).isEmpty());
    }

    @Test public void permissionsAreRecheckedWhenRenderedMessageReturns() {
        Render request = render(sender);
        sender.authorized = false;
        reply(sender, request);
        assertTrue(deliveries(survival).isEmpty());
        assertTrue(deliveries(creative).isEmpty());
    }

    @Test public void recipientPermissionsAreNotCached() {
        Render request = render(sender);
        recipient.authorized = false;
        reply(sender, request);
        assertTrue(deliveries(creative).isEmpty());
    }

    @Test public void rejectsUnsolicitedAndWrongBackendRenderReplies() {
        Render request = render(sender);
        chats.accept(recipient.connection, new Rendered(request.id(), "{}", "{}"));
        chats.accept(sender.connection, new Rendered(UUID.randomUUID(), "{}", "{}"));
        assertTrue(deliveries(survival).isEmpty());
        reply(sender, request);
        assertEquals(1, deliveries(survival).size());
    }

    @Test public void toggleConfirmationWaitsForBackendAckAndTypedChatIsPrivate() {
        chats.execute(sender.player, "staff", new String[0]);
        Capture capture = (Capture) sender.out.getLast();
        assertTrue(capture.enabled());
        assertTrue(sender.notices.isEmpty());
        chats.accept(sender.connection, new Acknowledgement(capture.id()));
        assertTrue(sender.notices.getLast().contains("Now chatting"));
        Input input = new Input(UUID.randomUUID(), sender.id, "private line");
        chats.accept(sender.connection, input);
        Render request = (Render) sender.out.getLast();
        assertEquals("private line", request.message());
        long before = sender.out.stream().filter(Render.class::isInstance).count();
        chats.accept(sender.connection, input);
        assertEquals(before, sender.out.stream().filter(Render.class::isInstance).count());
        reply(sender, request);
        assertEquals(1, deliveries(creative).size());
        chats.execute(sender.player, "staff", new String[0]);
        assertFalse(((Capture) sender.out.getLast()).enabled());
    }

    @Test public void rejectsForgedInputIdentityAndInputWithoutToggle() {
        chats.accept(sender.connection, new Input(UUID.randomUUID(), recipient.id, "forged"));
        chats.accept(sender.connection, new Input(UUID.randomUUID(), sender.id, "untoggled"));
        assertEquals(0, sender.out.stream().filter(Render.class::isInstance).count());
        assertTrue(deliveries(creative).isEmpty());
    }

    @Test public void serverSwitchEndsToggleAndInvalidatesInFlightMessages() {
        chats.execute(sender.player, "staff", new String[0]);
        Render request = render(sender);
        chats.serverChanged(sender.player);
        reply(sender, request);
        chats.accept(sender.connection, new Input(UUID.randomUUID(), sender.id, "after switch"));
        assertTrue(deliveries(creative).isEmpty());
        assertTrue(sender.notices.stream().anyMatch(notice -> notice.contains("changed servers")));
    }

    @Test public void reloadDropsInFlightOldFormatAndRemovedChannels() {
        Render request = render(sender);
        chats.configure(new ProxySettings(Map.of(), ProxySettings.DEFAULT_MESSAGES));
        reply(sender, request);
        assertTrue(deliveries(creative).isEmpty());
        chats.execute(sender.player, "staff", new String[]{"secret"});
        assertTrue(sender.notices.getLast().contains("cannot use"));
    }

    @Test public void timeoutReportsFailureAndLateResponseCannotDeliver() {
        Render request = render(sender);
        now.set(5_000_000_000L);
        chats.expire();
        assertTrue(sender.notices.getLast().contains("could not be confirmed"));
        reply(sender, request);
        assertTrue(deliveries(survival).isEmpty());
    }

    @Test public void leaveWorksWithoutAnActiveChannelAndDoesNotNeedItsPermission() {
        sender.authorized = false;
        chats.leave(sender.player);
        assertFalse(((Capture) sender.out.getLast()).enabled());
    }

    @Test public void localChannelCanEndNetworkCaptureButCannotEnableIt() {
        chats.execute(sender.player, "staff", new String[0]);
        chats.accept(sender.connection, new Capture(UUID.randomUUID(), sender.id, false));
        chats.accept(sender.connection, new Input(UUID.randomUUID(), sender.id, "local"));
        assertEquals(0, sender.out.stream().filter(Render.class::isInstance).count());
        chats.accept(sender.connection, new Capture(UUID.randomUUID(), sender.id, true));
        chats.accept(sender.connection, new Input(UUID.randomUUID(), sender.id, "forged enable"));
        assertEquals(0, sender.out.stream().filter(Render.class::isInstance).count());
    }

    @Test public void largeRecipientSetsAreChunkedWithUniqueDeliveryIds() {
        for (int i = 0; i < 250; i++) new Client(creative, true);
        reply(sender, render(sender));
        List<Delivery> packets = deliveries(creative);
        assertEquals(3, packets.size());
        assertEquals(251, packets.stream().mapToInt(packet -> packet.recipients().size()).sum());
        assertEquals(3, new HashSet<>(packets.stream().map(Delivery::id).toList()).size());
    }

    @Test public void clientPluginPacketsAreConsumedWithoutBeingForwarded() throws Exception {
        SeaChatVelocity plugin = new SeaChatVelocity(proxy, LoggerFactory.getLogger("test"), Path.of("unused"));
        PluginMessageEvent event = new PluginMessageEvent(sender.player, sender.connection, SeaChatVelocity.CHANNEL,
                PrivateChatProtocol.encode(new Input(UUID.randomUUID(), sender.id, "forged")));
        plugin.onPluginMessage(event);
        assertFalse(event.getResult().isAllowed());
        assertTrue(sender.out.isEmpty());
    }

    private final class Endpoint {
        final ServerInfo info;
        final RegisteredServer server;
        final List<Packet> messages = new ArrayList<>();
        Endpoint(String name) {
            info = new ServerInfo(name, new InetSocketAddress("localhost", 25565));
            server = mock(RegisteredServer.class, (method, args) -> switch (method) {
                case "getServerInfo" -> info;
                case "getPlayersConnected" -> clients.stream().filter(client -> client.endpoint == this).map(client -> client.player).toList();
                case "sendPluginMessage" -> { messages.add(decode((byte[]) args[1])); yield true; }
                default -> unsupported(method);
            });
        }
    }
    private final class Client {
        final UUID id = UUID.randomUUID();
        final List<Packet> out = new ArrayList<>();
        final List<String> notices = new ArrayList<>();
        final Player player;
        final ServerConnection connection;
        final Endpoint endpoint;
        boolean authorized;
        Client(Endpoint endpoint, boolean authorized) {
            this.endpoint = endpoint; this.authorized = authorized;
            player = mock(Player.class, (method, args) -> switch (method) {
                case "getCurrentServer" -> Optional.ofNullable(current());
                case "getUniqueId" -> id;
                case "hasPermission" -> this.authorized;
                case "sendMessage" -> { notices.add(PlainTextComponentSerializer.plainText().serialize((Component) args[0])); yield null; }
                default -> unsupported(method);
            });
            connection = mock(ServerConnection.class, (method, args) -> switch (method) {
                case "getPlayer" -> player;
                case "getServer" -> endpoint.server;
                case "getServerInfo" -> endpoint.info;
                case "sendPluginMessage" -> { out.add(decode((byte[]) args[1])); yield true; }
                default -> unsupported(method);
            });
            clients.add(this);
        }
        ServerConnection current() { return connection; }
    }
    private static Packet decode(byte[] bytes) {
        try { return PrivateChatProtocol.decode(bytes); } catch (Exception exception) { throw new AssertionError(exception); }
    }
    private static Object unsupported(String method) { throw new UnsupportedOperationException(method); }
    private static <T> T mock(Class<T> type, BiFunction<String, Object[], Object> call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("toString")) return type.getSimpleName();
            return call.apply(method.getName(), args);
        }));
    }
}