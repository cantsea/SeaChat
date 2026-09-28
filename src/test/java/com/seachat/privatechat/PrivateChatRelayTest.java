package com.seachat.privatechat;

import com.seachat.chat.ChatState;
import com.seachat.config.ChatSettings;
import com.seachat.network.PrivateChatProtocol;
import com.seachat.network.PrivateChatProtocol.*;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrivateChatRelayTest {
    private final UUID id = UUID.randomUUID();
    private final List<Packet> sent = new ArrayList<>();
    private final List<Component> notices = new ArrayList<>();
    private final AtomicInteger clearLocal = new AtomicInteger();
    private boolean sendFails;
    private Player player;
    private PrivateChatRelay relay;
    private Field serverField;
    private Server previous;

    @Before public void setup() throws Exception {
        player = mock(Player.class, (method, args) -> switch (method) {
            case "getUniqueId" -> id;
            case "getName" -> "Alex";
            case "sendMessage" -> { notices.add((Component) args[0]); yield null; }
            case "sendPluginMessage" -> {
                if (sendFails) throw new IllegalStateException("Unavailable connection");
                try { sent.add(PrivateChatProtocol.decode((byte[]) args[2])); }
                catch (Exception exception) { throw new AssertionError(exception); }
                yield null;
            }
            default -> unsupported(method);
        });
        BukkitScheduler scheduler = mock(BukkitScheduler.class, (method, args) -> {
            if (!method.equals("runTask")) throw new UnsupportedOperationException(method);
            ((Runnable) args[1]).run(); return null;
        });
        PluginManager plugins = mock(PluginManager.class, (method, args) -> false);
        Server server = mock(Server.class, (method, args) -> switch (method) {
            case "isPrimaryThread" -> true;
            case "getPlayer" -> id.equals(args[0]) ? player : null;
            case "getScheduler" -> scheduler;
            case "getPluginManager" -> plugins;
            default -> unsupported(method);
        });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previous = (Server) serverField.get(null);
        serverField.set(null, server);
        relay = new PrivateChatRelay(null, ChatSettings.from(new YamlConfiguration(), new YamlConfiguration()),
                new ChatState(false), ignored -> clearLocal.incrementAndGet());
    }
    @After public void cleanup() throws Exception { serverField.set(null, previous); }
    private void receive(Packet packet) throws Exception {
        relay.onPluginMessageReceived(PrivateChatProtocol.CHANNEL, player, PrivateChatProtocol.encode(packet));
    }
    @Test public void confirmedCaptureInterceptsChatAndClearsPreviousLocalToggle() throws Exception {
        assertFalse(relay.handleToggledChat(player, "public"));
        Capture capture = new Capture(UUID.randomUUID(), id, true);
        receive(capture);
        assertEquals(new Acknowledgement(capture.id()), sent.getLast());
        assertTrue(relay.handleToggledChat(player, "secret"));
        assertEquals("secret", ((Input) sent.getLast()).message());
        assertEquals(1, clearLocal.get());
        assertTrue(notices.isEmpty());
        receive(capture);
        assertEquals(1, clearLocal.get());
    }
    @Test public void failedBridgeStillInterceptsPrivateText() throws Exception {
        receive(new Capture(UUID.randomUUID(), id, true));
        sendFails = true;
        assertTrue(relay.handleToggledChat(player, "secret"));
        String notice = PlainTextComponentSerializer.plainText().serialize(notices.getLast());
        assertTrue(notice.contains("could not be confirmed"));
        assertFalse(notice.contains("secret"));
    }
    @Test public void captureForDifferentPlayerCannotAffectCarrier() throws Exception {
        receive(new Capture(UUID.randomUUID(), UUID.randomUUID(), true));
        assertFalse(relay.handleToggledChat(player, "public"));
        assertTrue(sent.isEmpty());
    }
    @Test public void leaveLocalSwitchAndQuitReleaseCapture() throws Exception {
        receive(new Capture(UUID.randomUUID(), id, true));
        receive(new Capture(UUID.randomUUID(), id, false));
        assertFalse(relay.handleToggledChat(player, "public"));
        receive(new Capture(UUID.randomUUID(), id, true));
        relay.leaveForLocalChannel(player);
        assertFalse(((Capture) sent.getLast()).enabled());
        assertFalse(relay.handleToggledChat(player, "local"));
        receive(new Capture(UUID.randomUUID(), id, true));
        relay.onQuit(id);
        assertFalse(relay.handleToggledChat(player, "public"));
    }
    @Test public void rendersProxyFormatWithoutBackendChannelAndKeepsMessageLiteral() throws Exception {
        Render request = new Render(UUID.randomUUID(), id, "staff", "sc", "<gold>[CENTRAL] {sender}: <message>", "<red>literal");
        receive(request);
        Rendered reply = (Rendered) sent.getLast();
        assertEquals(request.id(), reply.id());
        Component result = GsonComponentSerializer.gson().deserialize(reply.normal());
        assertEquals("[CENTRAL] Alex: <red>literal", PlainTextComponentSerializer.plainText().serialize(result));
        assertNotNull(reply.disabled());
        receive(request);
        assertEquals(1, sent.size());
    }
    private static Object unsupported(String method) { throw new UnsupportedOperationException(method); }
    private static <T> T mock(Class<T> type, BiFunction<String, Object[], Object> call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> call.apply(method.getName(), args)));
    }
}