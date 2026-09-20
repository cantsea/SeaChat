package com.seachat.config;

import com.seachat.chat.ChatState;
import com.seachat.listener.ChatListener;
import com.seachat.poll.PollManager;
import com.seachat.privatechat.PrivateChatChannel;
import com.seachat.privatechat.PrivateChatManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DisplayChatFormatTest {
    private static final Component DISPLAY_NAME = Component.text("Nickname", NamedTextColor.AQUA);
    private static BiFunction<OfflinePlayer, String, String> placeholderResolver;
    private Field serverField;
    private Field placeholderField;
    private Server originalServer;
    private Method originalPlaceholderMethod;
    private Player player;
    private boolean placeholdersEnabled;

    @Before
    public void setUp() throws Exception {
        placeholderResolver = (sender, placeholder) -> Map.of(
                "%luckperms_prefix%", "%rank_label%",
                "%rank_label%", "<gold>[Admin]</gold>",
                "%luckperms_suffix%", "<gray>[VIP]</gray>"
        ).getOrDefault(placeholder, placeholder);
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        originalServer = (Server) serverField.get(null);
        placeholderField = ChatSettings.class.getDeclaredField("placeholderMethod");
        placeholderField.setAccessible(true);
        originalPlaceholderMethod = (Method) placeholderField.get(null);
        placeholderField.set(null, DisplayChatFormatTest.class.getMethod("setPlaceholders", OfflinePlayer.class, String.class));
        PluginManager plugins = (PluginManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isPluginEnabled")) {
                        return placeholdersEnabled && args[0].equals("PlaceholderAPI");
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        Server server = (Server) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Server.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getPluginManager")) {
                        return plugins;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        serverField.set(null, server);
        player = player("Alex");
    }

    @After
    public void tearDown() throws Exception {
        serverField.set(null, originalServer);
        placeholderField.set(null, originalPlaceholderMethod);
    }

    public static String setPlaceholders(OfflinePlayer player, String placeholder) {
        return placeholderResolver.apply(player, placeholder);
    }

    @Test
    public void allDisplaysUseMainFormatWithClickAndHoverIntact() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("chat-format.format", "<gold>[Chat]</gold> <display_name> ({player}) :: <message> <gray>tail");
        YamlConfiguration lang = new YamlConfiguration();
        // Existing installations can keep these obsolete keys without overriding the main format.
        lang.set("messages.inventory-display-chat", "OLD INVENTORY FORMAT");
        lang.set("messages.hand-display-chat", "OLD HAND FORMAT");
        lang.set("messages.enderchest-display-chat", "OLD ENDER CHEST FORMAT");
        ChatSettings settings = ChatSettings.from(config, lang);
        Component display = display();

        for (var formatter : formatters(settings)) {
            Component rendered = formatter.apply(player, display);
            assertEquals("[Chat] Nickname (Alex) :: Display tail", plain(rendered));
            assertEquals(settings.chatMessage(player, DISPLAY_NAME, display), rendered);
            assertTrue(hasInteraction(rendered, display));
            assertNull(rendered.clickEvent());
        }
    }

    @Test
    public void allDisplaysResolveNestedPrefixAndSuffixPlaceholders() {
        placeholdersEnabled = true;
        YamlConfiguration config = new YamlConfiguration();
        config.set("chat-format.format", "%luckperms_prefix% {player} %luckperms_suffix% <message>");
        ChatSettings settings = ChatSettings.from(config, new YamlConfiguration());

        for (var formatter : formatters(settings)) {
            Component rendered = formatter.apply(player, display());
            assertEquals("[Admin] Alex [VIP] Display", plain(rendered));
            assertTrue(hasInteraction(rendered, display()));
        }
    }

    @Test
    public void reloadedFormatAppliesToEveryDisplayEvenWhenPublicChatFormattingIsDisabled() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("chat-format.format", "Before <message>");
        ChatSettings settings = ChatSettings.from(config, new YamlConfiguration());
        var formatters = formatters(settings);
        for (var formatter : formatters) {
            assertEquals("Before Display", plain(formatter.apply(player, display())));
        }

        config.set("chat-format.enabled", false);
        config.set("chat-format.format", "After {player}: <message>");
        settings.copyFrom(ChatSettings.from(config, new YamlConfiguration()));
        for (var formatter : formatters) {
            assertEquals("After Alex: Display", plain(formatter.apply(player, display())));
        }
    }

    @Test
    public void disabledColorUsesSenderSuffixAndPreservesStandaloneColorCodes() {
        placeholdersEnabled = true;
        placeholderResolver = (sender, placeholder) -> sender.getName().equals("Staff") ? "&f" : "";
        ChatSettings settings = placeholderColorSettings();

        assertEquals(NamedTextColor.WHITE, settings.disabledChatColor(player("Staff")));
        assertEquals(NamedTextColor.GRAY, settings.disabledChatColor(player("Regular")));
    }

    @Test
    public void disabledColorExpandsNestedPlaceholdersAndFallsBackForUnknownOrCircularValues() {
        placeholdersEnabled = true;
        ChatSettings settings = placeholderColorSettings();
        placeholderResolver = (sender, placeholder) -> switch (placeholder) {
            case "%luckperms_suffix%" -> "%rank_color%";
            case "%rank_color%" -> "&f";
            default -> placeholder;
        };
        assertEquals(NamedTextColor.WHITE, settings.disabledChatColor(player));

        placeholderResolver = (sender, placeholder) -> placeholder;
        assertEquals(NamedTextColor.GRAY, settings.disabledChatColor(player));
        placeholderResolver = (sender, placeholder) -> placeholder.equals("%luckperms_suffix%")
                ? "%rank_color%" : "%luckperms_suffix%";
        assertEquals(NamedTextColor.GRAY, settings.disabledChatColor(player));
        placeholderResolver = (sender, placeholder) -> "Staff";
        assertEquals(NamedTextColor.GRAY, settings.disabledChatColor(player));
    }

    @Test
    public void unavailablePlaceholderApiOrMissingSenderUsesGray() {
        placeholderResolver = (sender, placeholder) -> { throw new AssertionError("Expansion should not run"); };
        ChatSettings settings = placeholderColorSettings();
        assertEquals(NamedTextColor.GRAY, settings.disabledChatColor(player));
        placeholdersEnabled = true;
        assertEquals(NamedTextColor.GRAY, settings.disabledChatColor(null));
    }

    @Test
    public void publicChatResolvesColorOnceForSenderAndKeepsOtherViewersOriginal() {
        placeholdersEnabled = true;
        AtomicInteger calls = new AtomicInteger();
        placeholderResolver = (sender, placeholder) -> {
            calls.incrementAndGet();
            return sender.getName().equals("Staff") ? "&f" : "";
        };
        ChatSettings settings = placeholderColorSettings();
        ChatState state = new ChatState(false);
        Player staff = player("Staff");
        Player regular = player("Regular");
        state.toggleColors(staff.getUniqueId());
        state.toggleColors(regular.getUniqueId());
        ChatListener listener = new ChatListener(null, settings, state,
                new PollManager(null, settings, state), new PrivateChatManager(null, settings, state));
        Component message = Component.text("Hello", NamedTextColor.RED);
        AsyncChatEvent event = new AsyncChatEvent(true, staff, new HashSet<>(),
                (source, name, text, viewer) -> text, message, message, null);
        listener.onChat(event);
        assertEquals(0, calls.get());
        assertSame(message, event.renderer().render(staff, DISPLAY_NAME, message, player("ColorsEnabled")));
        assertEquals(0, calls.get());

        Component replacement = event.renderer().render(staff, DISPLAY_NAME, message, regular);
        assertEquals(NamedTextColor.WHITE, replacement.color());
        assertSame(replacement, event.renderer().render(staff, DISPLAY_NAME, message, staff));
        assertEquals(1, calls.get());

        AsyncChatEvent nextEvent = new AsyncChatEvent(true, regular, new HashSet<>(),
                (source, name, text, viewer) -> text, message, message, null);
        listener.onChat(nextEvent);
        assertEquals(NamedTextColor.GRAY, nextEvent.renderer().render(regular, DISPLAY_NAME, message, staff).color());
        assertEquals(2, calls.get());
    }

    @Test
    public void privateChatUsesSendersColorWithoutInsertingSuffixTextIntoMessage() {
        placeholdersEnabled = true;
        placeholderResolver = (sender, placeholder) -> "&6[Staff] &f";
        ChatSettings settings = placeholderColorSettings();
        PrivateChatChannel channel = new PrivateChatChannel("local", true, true, "<message>", "local", "lc");
        Component rendered = settings.privateChatMessage(player, channel, "Hello", true);
        assertEquals("Hello", plain(rendered));
        assertEquals(Component.text("Hello", NamedTextColor.WHITE), rendered.compact());
    }

    private ChatSettings placeholderColorSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("chat-format.enabled", false);
        config.set("chat-format.disabled-color", "%luckperms_suffix%");
        return ChatSettings.from(config, new YamlConfiguration());
    }

    private Player player(String name) {
        UUID id = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUniqueId" -> id;
                    case "hasPermission" -> false;
                    case "displayName" -> DISPLAY_NAME;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private List<BiFunction<Player, Component, Component>> formatters(ChatSettings settings) {
        return List.of(settings::inventoryChatMessage, settings::handChatMessage, settings::enderChestChatMessage);
    }

    private Component display() {
        return Component.text("Display", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/seachatinv example"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to view")));
    }

    private boolean hasInteraction(Component component, Component expected) {
        return expected.clickEvent().equals(component.clickEvent()) && expected.hoverEvent().equals(component.hoverEvent())
                || component.children().stream().anyMatch(child -> hasInteraction(child, expected));
    }

    private String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
