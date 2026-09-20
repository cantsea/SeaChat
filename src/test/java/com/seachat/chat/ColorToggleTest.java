package com.seachat.chat;

import com.seachat.command.ChatPermissions;
import com.seachat.command.CommandContext;
import com.seachat.command.subcommand.ToggleSubCommand;
import com.seachat.config.ChatSettings;
import com.seachat.listener.ChatListener;
import com.seachat.listener.CommandVisibilityListener;
import com.seachat.poll.PollManager;
import com.seachat.privatechat.PrivateChatManager;
import com.seachat.privatechat.PrivateChatChannel;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ColorToggleTest {
    private ChatState state;
    private ChatSettings settings;
    private ToggleSubCommand command;
    private final List<Component> responses = new ArrayList<>();

    @Before
    public void setUp() {
        state = new ChatState(false);
        YamlConfiguration config = new YamlConfiguration();
        config.set("chat-format.enabled", false);
        settings = ChatSettings.from(config, new YamlConfiguration());
        command = new ToggleSubCommand(new CommandContext(null, settings, state, null, null, null));
    }

    @Test
    public void colorsPermissionWorksIndependentlyAndOnlyChangesOwnView() {
        Player player = player(ChatPermissions.TOGGLE_COLORS);
        Player other = player();
        Component message = Component.text("Hello", NamedTextColor.RED);
        assertSame(message, state.forViewer(player, message));

        command.execute(player, new String[]{"colors"});

        assertTrue(state.areColorsDisabled(player.getUniqueId()));
        assertFalse(state.isHidden(player.getUniqueId()));
        assertEquals(NamedTextColor.WHITE, state.forViewer(player, message).color());
        assertSame(message, state.forViewer(other, message));
        assertSame(message, state.forViewer(Audience.empty(), message));
        assertEquals(settings.message("chat-colors-disabled"), responses.getLast());

        command.execute(player, new String[]{"COLORS"});
        assertFalse(state.areColorsDisabled(player.getUniqueId()));
        assertSame(message, state.forViewer(player, message));
        assertEquals(settings.message("chat-colors-enabled"), responses.getLast());
    }

    @Test
    public void visibilityPermissionDoesNotGrantColorsPermission() {
        Player player = player(ChatPermissions.TOGGLE);
        command.execute(player, new String[]{"colors"});
        assertFalse(state.areColorsDisabled(player.getUniqueId()));
        assertEquals(settings.message("no-permission-toggle-colors"), responses.getLast());

        command.execute(player, new String[]{});
        assertTrue(state.isHidden(player.getUniqueId()));
        assertFalse(state.areColorsDisabled(player.getUniqueId()));
    }

    @Test
    public void colorsPermissionDoesNotGrantVisibilityPermission() {
        Player player = player(ChatPermissions.TOGGLE_COLORS);
        command.execute(player, new String[]{});
        assertFalse(state.isHidden(player.getUniqueId()));
        assertEquals(settings.message("no-permission-toggle"), responses.getLast());
    }

    @Test
    public void rejectsExtraArgumentsWithoutChangingPreferences() {
        Player player = player(ChatPermissions.TOGGLE, ChatPermissions.TOGGLE_COLORS);
        command.execute(player, new String[]{"colors", "extra"});
        assertFalse(state.areColorsDisabled(player.getUniqueId()));
        assertFalse(state.isHidden(player.getUniqueId()));
    }

    @Test
    public void helpCompletionAndCommandVisibilityRespectSeparatePermission() {
        Player player = player(ChatPermissions.TOGGLE_COLORS);
        assertTrue(command.canUse(player));
        assertEquals(List.of("/chat toggle colors"), command.usages(player));
        assertEquals(List.of("colors"), command.tabComplete(player, new String[]{"co"}));
        assertEquals(List.of(), command.tabComplete(player(ChatPermissions.TOGGLE), new String[]{""}));
        assertEquals(List.of(), command.usages(player()));

        PlayerCommandSendEvent event = new PlayerCommandSendEvent(player, new HashSet<>(List.of("chat", "seachat:chat")));
        new CommandVisibilityListener(settings, new PrivateChatManager(null, settings, state)).onCommandSend(event);
        assertTrue(event.getCommands().contains("chat"));
    }

    @Test
    public void consoleCannotToggleColors() {
        CommandSender console = (CommandSender) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage") && args[0] instanceof Component response) {
                        responses.add(response);
                    }
                    return null;
                });
        command.execute(console, new String[]{"colors"});
        assertEquals(settings.message("only-players-toggle-colors"), responses.getLast());
    }

    @Test
    public void wrapsExistingRendererWhenSeaChatFormattingIsDisabled() {
        Player sender = player();
        Player plainViewer = player();
        Player coloredViewer = player();
        state.toggleColors(plainViewer.getUniqueId());
        Component message = Component.text("hello", NamedTextColor.RED);
        Component rendered = Component.text("Custom format: ", NamedTextColor.BLUE).append(message);
        AsyncChatEvent event = new AsyncChatEvent(true, sender,
                new HashSet<>(Set.of(plainViewer, coloredViewer)),
                (source, name, text, viewer) -> Component.text("Custom format: ", NamedTextColor.BLUE).append(text),
                message, message, null);
        ChatListener listener = new ChatListener(null, settings, state,
                new PollManager(null, settings, state), new PrivateChatManager(null, settings, state));

        listener.onChat(event);

        assertEquals(Component.text("Custom format: ", NamedTextColor.BLUE).append(ChatColors.withoutColors(message)),
                event.renderer().render(sender, Component.text("Alex"), message, plainViewer));
        assertEquals(rendered, event.renderer().render(sender, Component.text("Alex"), message, coloredViewer));
    }

    @Test
    public void defaultRendererKeepsNameColorsAndRespectsOwnPreferenceWithoutSharingCachedResults() {
        Player sender = player();
        Player other = player();
        state.toggleColors(sender.getUniqueId());
        Component message = Component.text("hello", NamedTextColor.RED);
        // Identical text and color must not cause the name to be mistaken for the message.
        Component name = Component.text("hello", NamedTextColor.RED);
        AsyncChatEvent event = new AsyncChatEvent(true, sender, new HashSet<>(Set.of(sender, other)),
                ChatRenderer.defaultRenderer(), message, message, null);
        new ChatListener(null, settings, state, new PollManager(null, settings, state),
                new PrivateChatManager(null, settings, state)).onChat(event);

        TranslatableComponent own = (TranslatableComponent) event.renderer().render(sender, name, message, sender);
        TranslatableComponent others = (TranslatableComponent) event.renderer().render(sender, name, message, other);
        assertEquals(name, own.arguments().getFirst().value());
        assertEquals(ChatColors.withoutColors(message), own.arguments().get(1).value());
        assertEquals(message, others.arguments().get(1).value());
    }

    @Test
    public void privateChatOnlyOverridesMessageColorInheritedFromFormat() {
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> "Alex");
        PrivateChatChannel channel = new PrivateChatChannel("local", true, true,
                "<red>[LOCAL] {sender}: <message> <blue>suffix", "seachat.chat.local", "lc");

        Component result = settings.privateChatMessage(sender, channel, "hello", true);

        assertEquals(MiniMessage.miniMessage().deserialize("<red>[LOCAL] Alex: <white>hello</white> <blue>suffix").compact(),
                result.compact());
    }

    @Test
    public void cachedCustomRendererDoesNotShareColorPreferenceBetweenViewers() {
        Player sender = player();
        Player other = player();
        state.toggleColors(sender.getUniqueId());
        Component message = Component.text("hello", NamedTextColor.RED);
        Component prefix = Component.text("[Rank] ", NamedTextColor.GOLD);
        AsyncChatEvent event = new AsyncChatEvent(true, sender, new HashSet<>(Set.of(sender, other)),
                ChatRenderer.viewerUnaware((source, name, text) -> prefix.append(text)), message, message, null);
        new ChatListener(null, settings, state, new PollManager(null, settings, state),
                new PrivateChatManager(null, settings, state)).onChat(event);

        assertEquals(prefix.append(ChatColors.withoutColors(message)),
                event.renderer().render(sender, Component.text("Alex"), message, sender));
        assertEquals(prefix.append(message),
                event.renderer().render(sender, Component.text("Alex"), message, other));
    }

    @Test
    public void configuredColorAndReloadOnlyAffectOptedOutMessageText() {
        Player sender = player();
        Player other = player();
        state.toggleColors(sender.getUniqueId());
        YamlConfiguration config = new YamlConfiguration();
        config.set("chat-format.enabled", false);
        config.set("chat-format.disabled-color", "gray");
        settings.copyFrom(ChatSettings.from(config, new YamlConfiguration()));
        Component name = Component.text("Alex", NamedTextColor.GREEN);
        Component message = Component.text("hello", NamedTextColor.RED);
        AsyncChatEvent event = new AsyncChatEvent(true, sender, new HashSet<>(Set.of(sender, other)),
                ChatRenderer.defaultRenderer(), message, message, null);
        new ChatListener(null, settings, state, new PollManager(null, settings, state),
                new PrivateChatManager(null, settings, state)).onChat(event);

        TranslatableComponent own = (TranslatableComponent) event.renderer().render(sender, name, message, sender);
        assertEquals(name, own.arguments().getFirst().value());
        assertEquals(message.color(NamedTextColor.GRAY), own.arguments().get(1).value());

        config.set("chat-format.disabled-color", "#AABBCC");
        settings.copyFrom(ChatSettings.from(config, new YamlConfiguration()));
        // In-flight messages keep one consistent color; new events use the reloaded setting.
        assertSame(own, event.renderer().render(sender, name, message, sender));
        AsyncChatEvent nextEvent = new AsyncChatEvent(true, sender, new HashSet<>(Set.of(sender, other)),
                ChatRenderer.defaultRenderer(), message, message, null);
        new ChatListener(null, settings, state, new PollManager(null, settings, state),
                new PrivateChatManager(null, settings, state)).onChat(nextEvent);
        TranslatableComponent reloaded = (TranslatableComponent) nextEvent.renderer().render(sender, name, message, sender);
        assertEquals(name, reloaded.arguments().getFirst().value());
        assertEquals(message.color(TextColor.color(0xAABBCC)), reloaded.arguments().get(1).value());
        TranslatableComponent others = (TranslatableComponent) event.renderer().render(sender, name, message, other);
        assertEquals(message, others.arguments().get(1).value());
        assertTrue(state.areColorsDisabled(sender.getUniqueId()));
    }

    @Test
    public void privateChatUsesConfiguredReplacementColorOnlyForMessage() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("chat-format.disabled-color", "gray");
        settings.copyFrom(ChatSettings.from(config, new YamlConfiguration()));
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> "Alex");
        PrivateChatChannel channel = new PrivateChatChannel("local", true, true,
                "<red>[LOCAL] {sender}: <message> <blue>suffix", "seachat.chat.local", "lc");

        assertEquals(MiniMessage.miniMessage().deserialize("<red>[LOCAL] Alex: <gray>hello</gray> <blue>suffix").compact(),
                settings.privateChatMessage(sender, channel, "hello", true).compact());
        assertEquals(MiniMessage.miniMessage().deserialize("<red>[LOCAL] Alex: hello <blue>suffix").compact(),
                settings.privateChatMessage(sender, channel, "hello", false).compact());
    }

    private Player player(String... permissions) {
        UUID id = UUID.randomUUID();
        Set<String> allowed = Set.of(permissions);
        return (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> "Alex";
                    case "hasPermission" -> allowed.contains(args[0]);
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Player[" + id + "]";
                    case "sendMessage" -> {
                        if (args[0] instanceof Component response) {
                            responses.add(response);
                        }
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
