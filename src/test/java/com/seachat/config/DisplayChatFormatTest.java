package com.seachat.config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
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
    private Field serverField;
    private Field placeholderField;
    private Server originalServer;
    private Method originalPlaceholderMethod;
    private Player player;
    private boolean placeholdersEnabled;

    @Before
    public void setUp() throws Exception {
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
        player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "Alex";
                    case "displayName" -> DISPLAY_NAME;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @After
    public void tearDown() throws Exception {
        serverField.set(null, originalServer);
        placeholderField.set(null, originalPlaceholderMethod);
    }

    public static String setPlaceholders(OfflinePlayer player, String placeholder) {
        return Map.of(
                "%luckperms_prefix%", "%rank_label%",
                "%rank_label%", "<gold>[Admin]</gold>",
                "%luckperms_suffix%", "<gray>[VIP]</gray>"
        ).getOrDefault(placeholder, placeholder);
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
