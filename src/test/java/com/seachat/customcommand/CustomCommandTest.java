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
import java.util.function.BiFunction;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.Plugin;
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
    private ConsoleCommandSender console;
    private final List<Invocation> invocations = new ArrayList<>();
    private BiFunction<CommandSender, String, Boolean> dispatch;

    private record Invocation(CommandSender sender, String command) {}

    @Before
    public void setUp() throws Exception {
        invocations.clear();
        dispatch = (sender, command) -> true;
        console = (ConsoleCommandSender) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ConsoleCommandSender.class}, (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
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
                    case "getConsoleSender" -> console;
                    case "dispatchCommand" -> {
                        CommandSender sender = (CommandSender) args[0];
                        String command = (String) args[1];
                        invocations.add(new Invocation(sender, command));
                        yield dispatch.apply(sender, command);
                    }
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
        new CustomCommand("discord", "", List.of("Player message"), settings)
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
        Command existing = new CustomCommand("discord", "", List.of("Other plugin"), settings);
        commandMap.register("other", existing);
        YamlConfiguration config = config("discord", "", List.of("Must not replace"));
        config.set("override-existing", false);
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
        Command existing = new CustomCommand("other", "", List.of("Other"), settings);
        commandMap.getKnownCommands().put("seachat:discord", existing);
        YamlConfiguration config = config("discord", "", List.of("New"));
        config.set("override-existing", false);
        manager.loadCommands(config);
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
        config.set("commands.discord.enabled", false);
        manager.loadCommands(config);
        assertNull(commandMap.getCommand("discord"));
        config.set("commands.discord.enabled", true);
        manager.loadCommands(config);
        List<Component> received = new ArrayList<>();
        commandMap.getCommand("discord").execute(player("Alex", false, received), "discord", new String[0]);
        assertEquals(config.getStringList("commands.discord.messages").size(), received.size());
        assertTrue(received.stream().anyMatch(component -> findLink(component) != null));
    }

    @Test
    public void aliasesSendSameMessagesAndSharePermissionsAndVisibility() {
        YamlConfiguration config = config("discord", "seachat.command.discord", List.of("Hello {player}"));
        config.set("commands.discord.aliases", List.of("DC", " disc ", "dc", "discord"));
        manager.loadCommands(config);
        Command main = commandMap.getCommand("discord");
        assertEquals(List.of("dc", "disc"), main.getAliases());
        List<Component> received = new ArrayList<>();
        Player allowed = player("Alex", true, received);
        Player denied = player("Alex", false, received);
        Set<String> labels = new HashSet<>();
        for (String label : List.of("discord", "dc", "disc", "seachat:discord", "seachat:dc", "seachat:disc")) {
            Command command = commandMap.getCommand(label);
            assertSame(main, command);
            received.clear();
            command.execute(allowed, label, new String[0]);
            assertEquals(List.of("Hello Alex"), received.stream().map(this::plain).toList());
            received.clear();
            command.execute(denied, label, new String[0]);
            assertEquals(1, received.size());
            assertTrue(plain(received.getFirst()).contains("do not have permission"));
            labels.add(label);
        }
        manager.onCommandSend(new PlayerCommandSendEvent(allowed, labels));
        assertEquals(6, labels.size());
        labels.add("help");
        manager.onCommandSend(new PlayerCommandSendEvent(denied, labels));
        assertEquals(Set.of("help"), labels);
    }

    @Test
    public void aliasConflictsDoNotReplaceCommandsOrPreventValidAliases() {
        Command existing = new CustomCommand("taken", "", List.of("Other plugin"), settings);
        commandMap.register("other", existing);
        commandMap.getKnownCommands().put("seachat:reserved", existing);
        YamlConfiguration config = config("discord", "", List.of("Discord"));
        config.set("commands.discord.aliases", List.of("taken", "reserved", "store", "/bad", "two words", "", "dc"));
        config.set("override-existing", false);
        config.set("commands.store.messages", List.of("Store"));
        config.set("commands.store.aliases", List.of("dc", "shop"));
        manager.loadCommands(config);
        Command discord = commandMap.getCommand("discord");
        Command store = commandMap.getCommand("store");
        assertNotSame(discord, store);
        assertEquals(List.of("dc"), discord.getAliases());
        assertEquals(List.of("shop"), store.getAliases());
        assertSame(discord, commandMap.getCommand("dc"));
        assertSame(store, commandMap.getCommand("shop"));
        assertSame(existing, commandMap.getCommand("taken"));
        assertSame(existing, commandMap.getCommand("seachat:reserved"));
        assertNull(commandMap.getCommand("reserved"));
        assertNull(commandMap.getCommand("seachat:taken"));
        manager.shutdown();
        assertSame(existing, commandMap.getCommand("taken"));
        assertSame(existing, commandMap.getCommand("seachat:reserved"));
    }

    @Test
    public void reloadAndDisableRemoveAliasesAndTheirNamespacedLabels() {
        YamlConfiguration config = config("discord", "", List.of("Original"));
        config.set("commands.discord.aliases", List.of("dc", "disc"));
        manager.loadCommands(config);
        config.set("commands.discord.messages", List.of("Updated"));
        config.set("commands.discord.aliases", List.of("dc", "community"));
        manager.loadCommands(config);
        assertNull(commandMap.getCommand("disc"));
        assertNull(commandMap.getCommand("seachat:disc"));
        List<Component> received = new ArrayList<>();
        commandMap.getCommand("dc").execute(player("Alex", false, received), "dc", new String[0]);
        assertEquals("Updated", plain(received.getFirst()));
        assertSame(commandMap.getCommand("discord"), commandMap.getCommand("community"));
        config.set("commands.discord.enabled", false);
        manager.loadCommands(config);
        for (String label : List.of("discord", "dc", "community")) {
            assertNull(commandMap.getCommand(label));
            assertNull(commandMap.getCommand("seachat:" + label));
        }
        config.set("commands.discord.enabled", true);
        manager.loadCommands(config);
        assertNotNull(commandMap.getCommand("community"));
        manager.shutdown();
        for (String label : List.of("discord", "dc", "community")) {
            assertNull(commandMap.getCommand(label));
            assertNull(commandMap.getCommand("seachat:" + label));
        }
    }

    @Test
    public void playerActionsRunInOrderAsInvokingPlayerThroughAliases() {
        YamlConfiguration config = actionConfig("travel", " PLAYER ", "", List.of(" /spawn ", "", "/", "tell {player} Hello"));
        config.set("commands.travel.aliases", List.of("go"));
        config.set("commands.travel.messages", List.of("Must not send"));
        manager.loadCommands(config);
        List<Component> received = new ArrayList<>();
        Player player = player("Alex", false, received);
        commandMap.getCommand("go").execute(player, "go", new String[]{"ignored", "arguments"});
        assertEquals(List.of("spawn", "tell Alex Hello"), invocations.stream().map(Invocation::command).toList());
        assertTrue(invocations.stream().allMatch(invocation -> invocation.sender() == player));
        assertTrue(received.isEmpty());
    }

    @Test
    public void consoleActionsUseConsoleAndRequireConfiguredPermission() {
        YamlConfiguration config = actionConfig("cookie", "console", "seachat.command.cookie",
                List.of("minecraft:give {player} minecraft:cookie 1", "minecraft:tell {player} Enjoy!"));
        config.set("commands.cookie.aliases", List.of("snack"));
        manager.loadCommands(config);
        List<Component> received = new ArrayList<>();
        Command command = commandMap.getCommand("seachat:snack");
        command.execute(player("Alex", false, received), "seachat:snack", new String[0]);
        assertTrue(invocations.isEmpty());
        assertTrue(plain(received.getFirst()).contains("do not have permission"));
        received.clear();
        command.execute(player("Alex", true, received), "seachat:snack", new String[]{"SomeoneElse"});
        assertEquals(List.of("minecraft:give Alex minecraft:cookie 1", "minecraft:tell Alex Enjoy!"),
                invocations.stream().map(Invocation::command).toList());
        assertTrue(invocations.stream().allMatch(invocation -> invocation.sender() == console));
        assertTrue(received.isEmpty());
    }

    @Test
    public void explicitChatUsesMessagesAndNeverDispatchesCommands() {
        YamlConfiguration config = config("hello", "", List.of("Hello {player}"));
        config.set("commands.hello.type", "chat");
        config.set("commands.hello.commands", List.of("must-not-run"));
        manager.loadCommands(config);
        List<Component> received = new ArrayList<>();
        commandMap.getCommand("hello").execute(player("Alex", false, received), "hello", new String[0]);
        assertEquals("Hello Alex", plain(received.getFirst()));
        assertTrue(invocations.isEmpty());
    }

    @Test
    public void unknownTypesAndMissingActionListsAreSkipped() {
        YamlConfiguration config = actionConfig("invalid", "typo", "", List.of("spawn"));
        config.set("commands.invalid.messages", List.of("Do not fall back to chat"));
        config.set("commands.empty.type", "console");
        config.set("commands.empty.commands", List.of(" ", "/"));
        config.set("commands.missing.type", "player");
        config.set("commands.missing.messages", List.of("Wrong list"));
        config.set("commands.disabled.type", "console");
        config.set("commands.disabled.enabled", false);
        config.set("commands.disabled.commands", List.of("spawn"));
        manager.loadCommands(config);
        for (String name : List.of("invalid", "empty", "missing", "disabled")) {
            assertNull(commandMap.getCommand(name));
        }
    }

    @Test
    public void actionCommandPrimaryNameWinsOverEarlierAlias() {
        YamlConfiguration config = config("discord", "", List.of("Discord"));
        config.set("commands.discord.aliases", List.of("travel"));
        config.set("commands.travel.type", "player");
        config.set("commands.travel.commands", List.of("spawn"));
        manager.loadCommands(config);
        assertTrue(commandMap.getCommand("discord").getAliases().isEmpty());
        commandMap.getCommand("travel").execute(player("Alex", false, new ArrayList<>()), "travel", new String[0]);
        assertEquals("spawn", invocations.getFirst().command());
    }

    @Test
    public void reloadChangesTypeBehindExistingAlias() {
        YamlConfiguration config = config("hello", "", List.of("Hello"));
        config.set("commands.hello.aliases", List.of("hi"));
        manager.loadCommands(config);
        List<Component> received = new ArrayList<>();
        Player player = player("Alex", false, received);
        commandMap.getCommand("hi").execute(player, "hi", new String[0]);
        assertEquals("Hello", plain(received.getFirst()));
        config.set("commands.hello.type", "player");
        config.set("commands.hello.commands", List.of("spawn"));
        manager.loadCommands(config);
        commandMap.getCommand("hi").execute(player, "hi", new String[0]);
        assertSame(player, invocations.getFirst().sender());
        config.set("commands.hello.type", "console");
        manager.loadCommands(config);
        commandMap.getCommand("seachat:hi").execute(player, "seachat:hi", new String[0]);
        assertSame(console, invocations.getLast().sender());
        manager.shutdown();
        assertNull(commandMap.getCommand("hi"));
        assertNull(commandMap.getCommand("seachat:hi"));
    }

    @Test
    public void dispatchFailuresStopRemainingActionsAndAllowFutureExecutions() {
        manager.loadCommands(actionConfig("travel", "player", "", List.of("missing", "spawn")));
        List<Component> received = new ArrayList<>();
        Player player = player("Alex", false, received);
        Command command = commandMap.getCommand("travel");
        dispatch = (sender, line) -> false;
        command.execute(player, "travel", new String[0]);
        assertEquals(1, invocations.size());
        assertTrue(plain(received.getFirst()).contains("could not be run"));
        invocations.clear();
        received.clear();
        dispatch = (sender, line) -> { throw new CommandException("Test failure"); };
        command.execute(player, "travel", new String[0]);
        assertEquals(1, invocations.size());
        assertTrue(plain(received.getFirst()).contains("could not be run"));
        invocations.clear();
        dispatch = (sender, line) -> true;
        command.execute(player, "travel", new String[0]);
        assertEquals(2, invocations.size());
    }

    @Test
    public void directAndIndirectAliasLoopsAreBlockedWithoutBlockingLaterCalls() {
        YamlConfiguration config = actionConfig("first", "player", "", List.of("seachat:one"));
        config.set("commands.first.aliases", List.of("one"));
        config.set("commands.second.type", "player");
        config.set("commands.second.commands", List.of("one"));
        manager.loadCommands(config);
        dispatch = (sender, line) -> commandMap.getCommand(line).execute(sender, line, new String[0]);
        List<Component> received = new ArrayList<>();
        Player player = player("Alex", false, received);
        commandMap.getCommand("first").execute(player, "first", new String[0]);
        assertEquals(1, invocations.size());
        assertTrue(plain(received.getFirst()).contains("loop"));
        config.set("commands.first.commands", List.of("second"));
        manager.loadCommands(config);
        invocations.clear();
        received.clear();
        commandMap.getCommand("one").execute(player, "one", new String[0]);
        assertEquals(2, invocations.size());
        assertTrue(plain(received.getFirst()).contains("loop"));
        invocations.clear();
        received.clear();
        dispatch = (sender, line) -> true;
        commandMap.getCommand("one").execute(player, "one", new String[0]);
        assertEquals(1, invocations.size());
        assertTrue(received.isEmpty());
    }

    @Test
    public void defaultOverrideClaimsPrimaryAndAliasesAndRestoresThemOnShutdown() {
        Command original = new CustomCommand("fly", "", List.of("Other plugin"), settings);
        Command aliasOwner = new CustomCommand("help", "", List.of("Other help"), settings);
        commandMap.register("other", original);
        commandMap.register("other", aliasOwner);
        commandMap.getKnownCommands().put("seachat:fly", aliasOwner);
        commandMap.getKnownCommands().put("seachat:help", aliasOwner);
        YamlConfiguration config = config("fly", "", List.of("SeaChat"));
        config.set("commands.fly.aliases", List.of("help"));
        manager.loadCommands(config);
        Command custom = commandMap.getCommand("fly");
        assertNotSame(original, custom);
        for (String label : List.of("help", "seachat:help", "seachat:fly")) {
            assertSame(custom, commandMap.getCommand(label));
        }
        assertSame(original, commandMap.getCommand("other:fly"));
        assertSame(aliasOwner, commandMap.getCommand("other:help"));
        assertTrue(original.isRegistered());
        List<Component> received = new ArrayList<>();
        custom.execute(player("Alex", false, received), "help", new String[0]);
        assertEquals("SeaChat", plain(received.getFirst()));
        manager.shutdown();
        assertSame(original, commandMap.getCommand("fly"));
        for (String label : List.of("help", "seachat:help", "seachat:fly")) {
            assertSame(aliasOwner, commandMap.getCommand(label));
        }
        assertFalse(manager.refreshPriority());
    }

    @Test
    public void entryOverrideCanOptInOrOutOfGlobalDefault() {
        Command original = new CustomCommand("fly", "", List.of("Original"), settings);
        commandMap.register("other", original);
        YamlConfiguration config = config("fly", "", List.of("SeaChat"));
        config.set("commands.fly.override-existing", false);
        manager.loadCommands(config);
        assertSame(original, commandMap.getCommand("fly"));
        config.set("override-existing", false);
        config.set("commands.fly.override-existing", true);
        manager.loadCommands(config);
        assertNotSame(original, commandMap.getCommand("fly"));
        config.set("commands.fly.override-existing", null);
        manager.loadCommands(config);
        assertSame(original, commandMap.getCommand("fly"));
        assertFalse(manager.refreshPriority());
    }

    @Test
    public void reloadDisableAndRemovalRestoreDisplacedCommandsWithoutLosingOriginals() {
        Command original = new CustomCommand("fly", "", List.of("Original"), settings);
        Command aliasOwner = new CustomCommand("help", "", List.of("Original help"), settings);
        commandMap.register("other", original);
        commandMap.register("other", aliasOwner);
        YamlConfiguration config = config("fly", "", List.of("SeaChat"));
        config.set("commands.fly.aliases", List.of("help"));
        manager.loadCommands(config);
        manager.loadCommands(config);
        config.set("commands.fly.aliases", List.of());
        manager.loadCommands(config);
        assertSame(aliasOwner, commandMap.getCommand("help"));
        assertNull(commandMap.getCommand("seachat:help"));
        config.set("commands.fly.enabled", false);
        manager.loadCommands(config);
        assertSame(original, commandMap.getCommand("fly"));
        assertNull(commandMap.getCommand("seachat:fly"));
        config.set("commands.fly.enabled", true);
        manager.loadCommands(config);
        manager.loadCommands(new YamlConfiguration());
        assertSame(original, commandMap.getCommand("fly"));
        assertFalse(manager.refreshPriority());
    }

    @Test
    public void priorityRefreshReclaimsLateRegistrationsAndRestoresLatestOwner() {
        YamlConfiguration config = config("guide", "", List.of("SeaChat"));
        config.set("commands.guide.aliases", List.of("help"));
        manager.loadCommands(config);
        Command custom = commandMap.getCommand("guide");
        Command late = new CustomCommand("help", "", List.of("Late plugin"), settings);
        commandMap.getKnownCommands().put("help", late);
        commandMap.getKnownCommands().put("guide", late);
        commandMap.getKnownCommands().remove("seachat:help");
        assertTrue(manager.refreshPriority());
        assertSame(custom, commandMap.getCommand("help"));
        assertSame(custom, commandMap.getCommand("guide"));
        assertSame(custom, commandMap.getCommand("seachat:help"));
        assertFalse(manager.refreshPriority());
        manager.shutdown();
        assertSame(late, commandMap.getCommand("help"));
        assertSame(late, commandMap.getCommand("guide"));
        assertNull(commandMap.getCommand("seachat:help"));
    }

    @Test
    public void shutdownDoesNotOverwriteACommandThatReplacedSeaChatLater() {
        Command original = new CustomCommand("fly", "", List.of("Original"), settings);
        commandMap.register("other", original);
        manager.loadCommands(config("fly", "", List.of("SeaChat")));
        Command newer = new CustomCommand("fly", "", List.of("Newer"), settings);
        commandMap.getKnownCommands().put("fly", newer);
        manager.shutdown();
        assertSame(newer, commandMap.getCommand("fly"));
        assertSame(original, commandMap.getCommand("other:fly"));
        assertNull(commandMap.getCommand("seachat:fly"));
    }

    @Test
    public void overrideDoesNotStealOtherCustomEntriesOrAliases() {
        YamlConfiguration config = config("first", "", List.of("First"));
        config.set("commands.first.aliases", List.of("second", "shared"));
        config.set("commands.second.messages", List.of("Second"));
        config.set("commands.second.aliases", List.of("shared"));
        config.set("commands.FIRST.messages", List.of("Duplicate"));
        manager.loadCommands(config);
        assertNotSame(commandMap.getCommand("first"), commandMap.getCommand("second"));
        assertSame(commandMap.getCommand("first"), commandMap.getCommand("shared"));
        assertTrue(commandMap.getCommand("second").getAliases().isEmpty());
        List<Component> received = new ArrayList<>();
        commandMap.getCommand("first").execute(player("Alex", false, received), "first", new String[0]);
        assertEquals("First", plain(received.getFirst()));
    }

    @Test
    public void failedRegistrationRollsBackDisplacedLabels() {
        Command original = new CustomCommand("fly", "", List.of("Original"), settings);
        commandMap.register("other", original);
        CommandMap failingMap = (CommandMap) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CommandMap.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getKnownCommands" -> commandMap.getKnownCommands();
                    case "getCommand" -> commandMap.getCommand((String) args[0]);
                    case "register" -> {
                        Command command = (Command) args[1];
                        commandMap.getKnownCommands().put("seachat:fly", command);
                        yield false;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        CustomCommandManager failingManager = new CustomCommandManager(new File("unused.yml"), failingMap,
                settings, Logger.getAnonymousLogger(), "seachat");
        failingManager.loadCommands(config("fly", "", List.of("SeaChat")));
        assertSame(original, commandMap.getCommand("fly"));
        assertNull(commandMap.getCommand("seachat:fly"));
        assertFalse(failingManager.refreshPriority());
    }

    @Test
    public void shutdownDoesNotRestoreDisabledPluginsCommands() {
        boolean[] enabled = {true};
        Plugin owner = (Plugin) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isEnabled")) return enabled[0];
                    throw new UnsupportedOperationException(method.getName());
                });
        class OwnedCommand extends Command implements PluginIdentifiableCommand {
            OwnedCommand() { super("fly"); }
            @Override public Plugin getPlugin() { return owner; }
            @Override public boolean execute(CommandSender sender, String label, String[] args) { return true; }
        }
        Command original = new OwnedCommand();
        commandMap.getKnownCommands().put("fly", original);
        manager.loadCommands(config("fly", "", List.of("SeaChat")));
        enabled[0] = false;
        manager.shutdown();
        assertNull(commandMap.getCommand("fly"));
    }

    private YamlConfiguration actionConfig(String name, String type, String permission, List<String> actions) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("commands." + name + ".type", type);
        config.set("commands." + name + ".permission", permission);
        config.set("commands." + name + ".commands", actions);
        return config;
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
