package com.seachat.customcommand;

import com.seachat.config.ChatSettings;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CustomCommandTest {
    private Field serverField;
    private Server originalServer;
    private SimpleCommandMap commandMap;
    private CustomCommandManager manager;
    private ChatSettings settings;

    @Before
    public void setUp() throws Exception {
        PluginManager plugins = (PluginManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isPluginEnabled")) return false;
                    if (method.getName().equals("getPlugin")) return null;
                    throw new UnsupportedOperationException(method.getName());
                });
        Server server = (Server) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Server.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPluginManager" -> plugins;
                    case "getLogger" -> Logger.getAnonymousLogger();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        originalServer = (Server) serverField.get(null);
        serverField.set(null, server);
        commandMap = new SimpleCommandMap(server, new HashMap<>());
        settings = ChatSettings.from(new YamlConfiguration(), new YamlConfiguration());
        manager = new CustomCommandManager(new File("unused.yml"), commandMap, settings,
                Logger.getAnonymousLogger(), "seachat");
    }

    @After
    public void tearDown() throws Exception {
        serverField.set(null, originalServer);
    }

    @Test
    public void sendsEveryLineOnlyToInvokingPlayerWithPlayerPlaceholder() {
        manager.loadCommands(config("discord", "", List.of("", "<center><aqua>Hello {player}</aqua>", "Last line")));
        List<Component> received = new ArrayList<>();
        Player player = player("Alex", false, received);
        assertTrue(commandMap.getCommand("discord").execute(player, "discord", new String[0]));
        assertEquals(List.of("", "Hello Alex", "Last line"), received.stream().map(this::plain).map(String::trim).toList());
        // The server proxy rejects recipient enumeration/broadcast calls; only this sender can receive messages.
    }

    @Test
    public void permissionBlocksMessagesAndHidesBothCommandLabels() {
        manager.loadCommands(config("discord", "seachat.command.discord", List.of("Secret message")));
        List<Component> received = new ArrayList<>();
        Player denied = player("Alex", false, received);
        Command command = commandMap.getCommand("discord");
        command.execute(denied, "discord", new String[0]);
        assertEquals(1, received.size());
        assertTrue(plain(received.getFirst()).contains("do not have permission"));
        Set<String> labels = new HashSet<>(Set.of("discord", "seachat:discord", "help"));
        manager.onCommandSend(new PlayerCommandSendEvent(denied, labels));
        assertEquals(Set.of("help"), labels);
        received.clear();
        Player allowed = player("Alex", true, received);
        command.execute(allowed, "discord", new String[0]);
        assertEquals("Secret message", plain(received.getFirst()));
        labels = new HashSet<>(Set.of("discord", "seachat:discord"));
        manager.onCommandSend(new PlayerCommandSendEvent(allowed, labels));
        assertEquals(2, labels.size());
    }

    @Test
    public void consoleGetsPlayerOnlyResponse() {
        List<Component> received = new ArrayList<>();
        CommandSender console = (CommandSender) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage") && args[0] instanceof Component component) {
                        received.add(component);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        new CustomMessageCommand("discord", "", List.of("Player message"), settings)
                .execute(console, "discord", new String[0]);
        assertEquals(1, received.size());
        assertTrue(plain(received.getFirst()).contains("Only players"));
    }

    @Test
    public void reloadUpdatesMessagesAndRemovesOldNamespacedLabels() {
        manager.loadCommands(config("Discord", "", List.of("Old")));
        Command old = commandMap.getCommand("discord");
        assertSame(old, commandMap.getCommand("seachat:discord"));
        manager.loadCommands(config("discord", "", List.of("Updated")));
        assertFalse(old.isRegistered());
        List<Component> received = new ArrayList<>();
        commandMap.getCommand("discord").execute(player("Alex", false, received), "discord", new String[0]);
        assertEquals("Updated", plain(received.getFirst()));
        manager.loadCommands(config("rules", "", List.of("Rules")));
        assertNull(commandMap.getCommand("discord"));
        assertNull(commandMap.getCommand("seachat:discord"));
        assertNotNull(commandMap.getCommand("rules"));
        manager.shutdown();
        assertNull(commandMap.getCommand("rules"));
        assertNull(commandMap.getCommand("seachat:rules"));
    }

    @Test
    public void skipsConflictsDisabledInvalidAndEmptyCommands() {
        Command existing = new CustomMessageCommand("discord", "", List.of("Other plugin"), settings);
        commandMap.register("other", existing);
        YamlConfiguration config = config("discord", "", List.of("Must not replace"));
        config.set("commands.disabled.enabled", false);
        config.set("commands.disabled.messages", List.of("Hidden"));
        config.set("commands./invalid.messages", List.of("Invalid"));
        config.set("commands.empty.messages", List.of());
        manager.loadCommands(config);
        assertSame(existing, commandMap.getCommand("discord"));
        assertNull(commandMap.getCommand("seachat:discord"));
        assertNull(commandMap.getCommand("disabled"));
        assertNull(commandMap.getCommand("/invalid"));
        assertNull(commandMap.getCommand("empty"));
        manager.shutdown();
        assertSame(existing, commandMap.getCommand("discord"));
    }

    @Test
    public void namespacedConflictIsNotOverwritten() {
        Command existing = new CustomMessageCommand("other", "", List.of("Other"), settings);
        commandMap.getKnownCommands().put("seachat:discord", existing);
        manager.loadCommands(config("discord", "", List.of("New")));
        assertSame(existing, commandMap.getCommand("seachat:discord"));
        assertNull(commandMap.getCommand("discord"));
    }

    @Test
    public void centeredAnnouncementsAndCommandsKeepClickableLabelAndHover() {
        String template = "<center>Join <click:open_url:'https://discord.gg/example'>"
                + "<hover:show_text:'Click to join!'><aqua>Discord</aqua></hover></click> today!";
        Player player = player("Alex", false, new ArrayList<>());
        for (Component rendered : List.of(settings.announcementMessage(player, template),
                settings.customCommandMessage(player, template))) {
            assertEquals("Join Discord today!", plain(rendered).trim());
            assertTrue(plain(rendered).startsWith(" "));
            Component link = findLink(rendered);
            assertNotNull(link);
            assertEquals("Discord", plain(link));
            assertEquals(ClickEvent.openUrl("https://discord.gg/example"), link.clickEvent());
            assertNotNull(link.hoverEvent());
        }
    }

    @Test
    public void bundledExampleLoadsAndRendersAfterEnabling() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.load(new File("src/main/resources/custom-commands.yml"));
        manager.loadCommands(config);
        assertNull(commandMap.getCommand("discord"));
        config.set("commands.discord.enabled", true);
        manager.loadCommands(config);
        List<Component> received = new ArrayList<>();
        commandMap.getCommand("discord").execute(player("Alex", false, received), "discord", new String[0]);
        assertEquals(4, received.size());
        assertEquals("Hey Alex, join our Discord!", plain(received.get(2)).trim());
        assertNotNull(findLink(received.get(2)));
    }

    private YamlConfiguration config(String name, String permission, List<String> messages) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("commands." + name + ".permission", permission);
        config.set("commands." + name + ".messages", messages);
        return config;
    }

    private Player player(String name, boolean permitted, List<Component> received) {
        return (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("hasPermission")) return permitted;
                    if (method.getName().equals("sendMessage") && args[0] instanceof Component component) {
                        received.add(component);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private Component findLink(Component component) {
        if (component.clickEvent() != null) return component;
        for (Component child : component.children()) {
            Component match = findLink(child);
            if (match != null) return match;
        }
        return null;
    }

    private String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
