package com.seachat.chat;

import io.papermc.paper.chat.ChatRenderer;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.junit.Test;

import static org.junit.Assert.*;

public class ChatColorRendererTest {
    private static final Component NAME = Component.text("Alex", NamedTextColor.GREEN);
    private static final ChatRenderer MESSAGE_ONLY = (source, name, message, viewer) -> message;

    @Test
    public void recolorsOnlyOnDemandAndReusesOneCopyFor500Viewers() {
        ChatState state = new ChatState(false);
        ChatColorRenderer renderer = new ChatColorRenderer(MESSAGE_ONLY, state, NamedTextColor.GRAY);
        AtomicInteger traversals = new AtomicInteger();
        Component message = countedMessage(traversals);
        Player source = player();

        assertSame(message, renderer.render(source, NAME, message, source));
        assertSame(message, renderer.render(source, NAME, message, Audience.empty()));
        assertEquals(0, traversals.get());

        state.toggleColors(source.getUniqueId());
        Component shared = renderer.render(source, NAME, message, source);
        assertEquals(NamedTextColor.GRAY, shared.color());
        for (int index = 0; index < 500; index++) {
            Player viewer = player();
            state.toggleColors(viewer.getUniqueId());
            assertSame(shared, renderer.render(source, NAME, message, viewer));
        }
        assertEquals(1, traversals.get());

        state.toggleColors(source.getUniqueId());
        assertSame(message, renderer.render(source, NAME, message, source));
    }

    @Test
    public void concurrentViewersCreateOnlyOneReplacement() throws Exception {
        ChatState state = new ChatState(false);
        ChatColorRenderer renderer = new ChatColorRenderer(MESSAGE_ONLY, state, NamedTextColor.GRAY);
        AtomicInteger traversals = new AtomicInteger();
        Component message = countedMessage(traversals);
        Player source = player();
        List<Callable<Component>> renders = new ArrayList<>();
        for (int index = 0; index < 500; index++) {
            Player viewer = player();
            state.toggleColors(viewer.getUniqueId());
            renders.add(() -> renderer.render(source, NAME, message, viewer));
        }

        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = executor.invokeAll(renders);
            Component shared = results.getFirst().get();
            for (var result : results) {
                assertSame(shared, result.get());
            }
        }
        assertEquals(1, traversals.get());
    }

    @Test
    public void viewerSpecificFormattingStillRunsForEachViewer() {
        ChatState state = new ChatState(false);
        Player source = player();
        Player other = player();
        state.toggleColors(source.getUniqueId());
        state.toggleColors(other.getUniqueId());
        Component message = Component.text("Hello", NamedTextColor.RED);
        ChatColorRenderer renderer = new ChatColorRenderer(
                (sender, name, text, viewer) -> Component.text(viewer == source ? "Own: " : "Other: ", NamedTextColor.BLUE)
                        .append(text), state, NamedTextColor.GRAY);

        Component own = renderer.render(source, NAME, message, source);
        Component others = renderer.render(source, NAME, message, other);

        assertEquals(Component.text("Own: ", NamedTextColor.BLUE).append(message.color(NamedTextColor.GRAY)), own);
        assertEquals(Component.text("Other: ", NamedTextColor.BLUE).append(message.color(NamedTextColor.GRAY)), others);
        assertSame(own.children().getFirst(), others.children().getFirst());
    }

    @Test
    public void viewerUnawareRendererReusesTheReplacementLayoutToo() {
        ChatState state = new ChatState(false);
        Player source = player();
        Player other = player();
        state.toggleColors(source.getUniqueId());
        Component message = Component.text("Hello", NamedTextColor.RED);
        Component original = Component.text("[Rank] ", NamedTextColor.GOLD).append(message);
        ChatColorRenderer renderer = new ChatColorRenderer(
                ChatRenderer.viewerUnaware((sender, name, text) -> original), state, NamedTextColor.GRAY);

        Component shared = renderer.render(source, NAME, message, source);
        assertEquals(Component.text("[Rank] ", NamedTextColor.GOLD).append(message.color(NamedTextColor.GRAY)), shared);
        assertSame(original, renderer.render(source, NAME, message, other));
        state.toggleColors(other.getUniqueId());
        assertSame(shared, renderer.render(source, NAME, message, other));
    }

    @Test
    public void separateMessagesNeverShareCachedTextOrColor() {
        ChatState state = new ChatState(false);
        Player source = player();
        state.toggleColors(source.getUniqueId());
        ChatColorRenderer first = new ChatColorRenderer(MESSAGE_ONLY, state, NamedTextColor.GRAY);
        ChatColorRenderer second = new ChatColorRenderer(MESSAGE_ONLY, state, NamedTextColor.YELLOW);
        Component firstMessage = Component.text("First", NamedTextColor.RED);
        Component secondMessage = Component.text("Second", NamedTextColor.BLUE);

        assertEquals(firstMessage.color(NamedTextColor.GRAY), first.render(source, NAME, firstMessage, source));
        assertEquals(secondMessage.color(NamedTextColor.YELLOW), second.render(source, NAME, secondMessage, source));
        // Even if a caller supplies different message text to the same renderer, it must not reuse stale text.
        assertEquals(secondMessage.color(NamedTextColor.GRAY), first.render(source, NAME, secondMessage, source));
    }

    private Component countedMessage(AtomicInteger traversals) {
        Component actual = Component.text("Hello", NamedTextColor.RED);
        return (Component) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Component.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("children") && method.getParameterCount() == 0) {
                        traversals.incrementAndGet();
                    }
                    return method.invoke(actual, args);
                });
    }

    private Player player() {
        UUID id = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) {
                        return id;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
