package com.seachat.privatechat;

import com.seachat.chat.ChatState;
import com.seachat.network.PrivateChatProtocol.Delivery;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProxyChatDeliveryTest {
    private final ChatState state = new ChatState(false);
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final List<Component> firstMessages = new ArrayList<>();
    private final List<Component> secondMessages = new ArrayList<>();
    private final Map<UUID, Player> players = Map.of(first, player(firstMessages), second, player(secondMessages));
    private final Component normal = Component.text("[Staff] ", NamedTextColor.YELLOW)
            .append(Component.text("Hello", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/help"))
                    .hoverEvent(HoverEvent.showText(Component.text("Hover"))));
    private final Component disabled = normal.children(List.of(normal.children().getFirst().color(NamedTextColor.GRAY)));
    private Delivery delivery(List<UUID> ids, boolean console) {
        return new Delivery(UUID.randomUUID(), ids, GsonComponentSerializer.gson().serialize(normal),
                console ? null : GsonComponentSerializer.gson().serialize(disabled));
    }
    @Test public void onlyProxySelectedPlayersReceiveWithoutBackendChannelOrPermissionChecks() {
        ProxyChatDelivery.deliver(delivery(List.of(first), false), players::get, state);
        assertEquals(List.of(normal), firstMessages);
        assertTrue(secondMessages.isEmpty());
    }
    @Test public void perViewerColorPreferenceIncludesSender() {
        state.toggleColors(first);
        ProxyChatDelivery.deliver(delivery(List.of(first, second), false), players::get, state);
        assertEquals(List.of(disabled), firstMessages);
        assertEquals(List.of(normal), secondMessages);
    }
    @Test public void consoleMessagesRetainTheirColors() {
        state.toggleColors(first);
        ProxyChatDelivery.deliver(delivery(List.of(first), true), players::get, state);
        assertEquals(List.of(normal), firstMessages);
    }
    @Test public void missingPlayersAndDuplicateIdsAreSafe() {
        ProxyChatDelivery.deliver(delivery(List.of(first, first, UUID.randomUUID()), false), players::get, state);
        assertEquals(1, firstMessages.size());
    }
    @Test public void componentsAreReusedAcrossRecipients() {
        ProxyChatDelivery.deliver(delivery(List.of(first, second), false), players::get, state);
        assertSame(firstMessages.getFirst(), secondMessages.getFirst());
    }
    private Player player(List<Component> messages) {
        return (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage")) { messages.add((Component) args[0]); return null; }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}