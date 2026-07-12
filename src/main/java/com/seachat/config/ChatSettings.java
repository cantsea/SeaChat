package com.seachat.config;

import com.seachat.privatechat.PrivateChatChannel;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public final class ChatSettings {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final Pattern PLACEHOLDER_API_PATTERN = Pattern.compile("%[^%\\s]+%");
    private static final Pattern CENTER_TAG_PATTERN = Pattern.compile("(?i)</?center\\s*/?>");
    private static final Pattern MINI_MESSAGE_TAG_PATTERN =
            Pattern.compile("(?i)</?(?:#[0-9a-f]{6}|[a-z][a-z0-9_-]*)(?::[^<>]*)?/?>");
    private static final LegacyComponentSerializer PLACEHOLDER_LEGACY_SERIALIZER =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexCharacter('#')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();
    private static volatile Method placeholderMethod;
    private static volatile boolean placeholderLookupFailed;
    private static final String DEFAULT_PREFIX = "<#ffffff><b>[<#1E90FF>SALTY<#ffffff>MC]</b><#ffffff>";
    private static final String DEFAULT_CHAT_FORMAT = "%luckperms_prefix%<reset> {player} %luckperms_suffix% <message>";
    private static final Map<String, String> DEFAULT_MESSAGES = Map.ofEntries(
            Map.entry("only-players-toggle", "{prefix} Only players can toggle their chat visibility."),
            Map.entry("only-players-inventory-display", "{prefix} Only players can share their inventory in chat."),
            Map.entry("only-players-hand-display", "{prefix} Only players can share their held item in chat."),
            Map.entry("only-players-enderchest-display", "{prefix} Only players can share their ender chest in chat."),
            Map.entry("no-permission-toggle", "{prefix} You do not have permission to toggle chat."),
            Map.entry("no-permission-inventory-display", "{prefix} You do not have permission to share your inventory in chat."),
            Map.entry("no-permission-hand-display", "{prefix} You do not have permission to share your held item in chat."),
            Map.entry("no-permission-enderchest-display", "{prefix} You do not have permission to share your ender chest in chat."),
            Map.entry("hand-empty", "{prefix} You are not holding an item."),
            Map.entry("chat-hidden", "{prefix} Public chat is now <red>hidden</red>."),
            Map.entry("chat-visible", "{prefix} Public chat is now <green>visible</green>."),
            Map.entry("chat-hidden-cannot-speak", "{prefix} Your chat is hidden, so you cannot speak in public chat. Use <yellow>/chat toggle</yellow> to use chat again."),
            Map.entry("no-permission-slowmode", "{prefix} You do not have permission to toggle slowmode."),
            Map.entry("slowmode-enabled", "{prefix} Slowmode is now <green>enabled</green>."),
            Map.entry("slowmode-disabled", "{prefix} Slowmode is now <red>disabled</red>."),
            Map.entry("no-permission-reload", "{prefix} You do not have permission to reload SeaChat."),
            Map.entry("reloaded", "{prefix} SeaChat has been reloaded."),
            Map.entry("no-permission-poll-create", "{prefix} You do not have permission to create or stop polls."),
            Map.entry("no-permission-command", "{prefix} You do not have permission to use SeaChat commands."),
            Map.entry("usage", "{prefix} Usage: <white>{usage}</white>"),
            Map.entry("display-disabled", "{prefix} Display commands are currently disabled."),
            Map.entry("display-cooldown", "{prefix} Wait <yellow>{seconds}</yellow> before using another display command."),
            Map.entry("display-expired", "{prefix} That shared display has expired."),
            Map.entry("blocked-bad-word", "{prefix} Your message was blocked."),
            Map.entry("blocked-caps", "{prefix} Please avoid sending messages in excessive caps."),
            Map.entry("filter-notify", "{prefix} <red><b>{player}'s message was blocked:</b></red> <gray>{message}</gray>"),
            Map.entry("inventory-display", "<green>{player}'s Inventory</green>"),
            Map.entry("inventory-display-chat", "%luckperms_prefix%<reset> {player} » [<inventory>]"),
            Map.entry("inventory-display-hover", "<gray>Click to view this inventory.</gray>"),
            Map.entry("hand-display", "<green>{player}'s <item></green> <gray>x{amount}</gray>"),
            Map.entry("hand-display-chat", "%luckperms_prefix%<reset> {player} » [<hand>]"),
            Map.entry("hand-display-hover", "<gray>Click to view this item.</gray>"),
            Map.entry("enderchest-display", "<green>{player}'s Ender Chest</green>"),
            Map.entry("enderchest-display-chat", "%luckperms_prefix%<reset> {player} » [<enderchest>]"),
            Map.entry("enderchest-display-hover", "<gray>Click to view this ender chest.</gray>"),
            Map.entry("poll-created", "{prefix} New Poll created by <yellow>{creator}</yellow>! <white>{question}</white> Type <green>yes</green> or <red>no</red> in chat in the next <yellow>{seconds}</yellow> seconds to submit a response."),
            Map.entry("poll-already-active", "{prefix} There is already an active poll."),
            Map.entry("poll-invalid-time", "{prefix} Poll time must be a positive number of seconds."),
            Map.entry("poll-stopped", "{prefix} Poll stopped."),
            Map.entry("poll-none-active", "{prefix} There is no active poll to stop."),
            Map.entry("poll-response-counted", "{prefix} Your <yellow>{response}</yellow> response has been counted."),
            Map.entry("poll-response-no-permission", "{prefix} You do not have permission to respond to polls."),
            Map.entry("poll-ended", "{prefix} The poll has ended."),
            Map.entry("poll-results", "{prefix} Poll results for <white>{question}</white>: <green>Yes: {yes}</green> <red>No: {no}</red> <gray>Total: {total}</gray>"),
            Map.entry("private-chat-no-permission", "{prefix} You do not have permission to use this private chat."),
            Map.entry("private-chat-usage", "{prefix} Usage: <white>/{command} <message></white>"),
            Map.entry("private-chat-toggle-enabled", "{prefix} You are now chatting in <yellow>{chat}</yellow>. Use <yellow>/{command}</yellow> again to leave."),
            Map.entry("private-chat-toggle-disabled", "{prefix} You are no longer chatting in <yellow>{chat}</yellow>."),
            Map.entry("no-permission-announcement-trigger", "{prefix} You do not have permission to trigger announcements."),
            Map.entry("announcement-trigger-usage", "{prefix} Usage: <white>/chat announce tebex <player> <package></white> or <white>/chat announce test <announcement> [player] [package]</white>"),
            Map.entry("announcement-triggered", "{prefix} Triggered <yellow>{trigger}</yellow> announcement."),
            Map.entry("announcement-trigger-not-found", "{prefix} No enabled announcement trigger named <yellow>{trigger}</yellow> was found."),
            Map.entry("announcement-tested", "{prefix} Tested <yellow>{announcement}</yellow> announcement."),
            Map.entry("announcement-test-not-found", "{prefix} No enabled announcement named <yellow>{announcement}</yellow> was found."),
            Map.entry("slowmode-wait", "{prefix} Slowmode is enabled. Wait <yellow>{seconds}</yellow> before chatting again.")
    );

    private volatile String prefix;
    private volatile Map<String, String> messages;
    private volatile boolean chatFormatEnabled;
    private volatile String chatFormat;
    private volatile boolean announcementsEnabled;
    private volatile boolean slowmodeEnabled;
    private volatile long slowmodeCooldownMillis;
    private volatile boolean displayEnabled;
    private volatile long displayCooldownMillis;
    private volatile boolean snapshotExpiryEnabled;
    private volatile long snapshotExpireMillis;
    private volatile long snapshotCleanupIntervalTicks;
    private volatile boolean capsEnabled;
    private volatile double capsThresholdPercent;
    private volatile int capsMinimumLetters;
    private volatile boolean badWordsEnabled;
    private volatile List<String> badWords;
    private volatile List<PrivateChatChannel> privateChatChannels;

    private ChatSettings(
            String prefix,
            Map<String, String> messages,
            boolean chatFormatEnabled,
            String chatFormat,
            boolean announcementsEnabled,
            boolean slowmodeEnabled,
            long slowmodeCooldownMillis,
            boolean displayEnabled,
            long displayCooldownMillis,
            boolean snapshotExpiryEnabled,
            long snapshotExpireMillis,
            long snapshotCleanupIntervalTicks,
            boolean capsEnabled,
            double capsThresholdPercent,
            int capsMinimumLetters,
            boolean badWordsEnabled,
            List<String> badWords,
            List<PrivateChatChannel> privateChatChannels
    ) {
        this.prefix = prefix;
        this.messages = messages;
        this.chatFormatEnabled = chatFormatEnabled;
        this.chatFormat = chatFormat;
        this.announcementsEnabled = announcementsEnabled;
        this.slowmodeEnabled = slowmodeEnabled;
        this.slowmodeCooldownMillis = slowmodeCooldownMillis;
        this.displayEnabled = displayEnabled;
        this.displayCooldownMillis = displayCooldownMillis;
        this.snapshotExpiryEnabled = snapshotExpiryEnabled;
        this.snapshotExpireMillis = snapshotExpireMillis;
        this.snapshotCleanupIntervalTicks = snapshotCleanupIntervalTicks;
        this.capsEnabled = capsEnabled;
        this.capsThresholdPercent = capsThresholdPercent;
        this.capsMinimumLetters = capsMinimumLetters;
        this.badWordsEnabled = badWordsEnabled;
        this.badWords = badWords;
        this.privateChatChannels = privateChatChannels;
    }

    public static ChatSettings from(FileConfiguration config, FileConfiguration lang) {
        return new ChatSettings(
                lang.getString("prefix", DEFAULT_PREFIX),
                loadMessages(lang),
                config.getBoolean("chat-format.enabled", true),
                config.getString("chat-format.format", DEFAULT_CHAT_FORMAT),
                config.getBoolean("announcements.enabled", true),
                config.getBoolean("slowmode.enabled", false),
                Math.max(0L, config.getLong("slowmode.cooldown-seconds", 5L)) * 1000L,
                config.getBoolean("display.enabled", true),
                Math.max(0L, config.getLong("display.cooldown-seconds", 10L)) * 1000L,
                config.getBoolean("display.snapshots.enabled", true),
                TimeUnit.SECONDS.toMillis(Math.max(1L, config.getLong("display.snapshots.expire-seconds", 300L))),
                Math.max(1L, config.getLong("display.snapshots.cleanup-interval-seconds", 300L)) * 20L,
                config.getBoolean("filters.caps.enabled", true),
                clamp(config.getDouble("filters.caps.threshold-percent", 80.0D), 0.0D, 100.0D),
                Math.max(1, config.getInt("filters.caps.minimum-letters", 5)),
                config.getBoolean("filters.bad-words.enabled", true),
                config.getStringList("filters.bad-words.words").stream()
                        .map(word -> word.toLowerCase(Locale.ROOT))
                        .filter(word -> !word.isBlank())
                        .toList(),
                loadPrivateChatChannels(config)
        );
    }

    public void copyFrom(ChatSettings other) {
        this.prefix = other.prefix;
        this.messages = other.messages;
        this.chatFormatEnabled = other.chatFormatEnabled;
        this.chatFormat = other.chatFormat;
        this.announcementsEnabled = other.announcementsEnabled;
        this.slowmodeEnabled = other.slowmodeEnabled;
        this.slowmodeCooldownMillis = other.slowmodeCooldownMillis;
        this.displayEnabled = other.displayEnabled;
        this.displayCooldownMillis = other.displayCooldownMillis;
        this.snapshotExpiryEnabled = other.snapshotExpiryEnabled;
        this.snapshotExpireMillis = other.snapshotExpireMillis;
        this.snapshotCleanupIntervalTicks = other.snapshotCleanupIntervalTicks;
        this.capsEnabled = other.capsEnabled;
        this.capsThresholdPercent = other.capsThresholdPercent;
        this.capsMinimumLetters = other.capsMinimumLetters;
        this.badWordsEnabled = other.badWordsEnabled;
        this.badWords = other.badWords;
        this.privateChatChannels = other.privateChatChannels;
    }

    public Component message(String key) {
        return message(null, key, Map.of());
    }

    public Component message(String key, Map<String, String> placeholders) {
        return message(null, key, placeholders);
    }

    public Component message(Player player, String key) {
        return message(player, key, Map.of());
    }

    public Component message(Player player, String key, Map<String, String> placeholders) {
        return message(player, key, placeholders, Map.of());
    }

    public Component message(
            Player player,
            String key,
            Map<String, String> placeholders,
            Map<String, Component> componentPlaceholders
    ) {
        String template = messages.getOrDefault(key, DEFAULT_MESSAGES.getOrDefault(key, ""))
                .replace("{prefix}", prefix);
        return renderTemplate(player, template, placeholders, componentPlaceholders);
    }

    public Component chatMessage(Player player, Component displayName, Component message) {
        return renderTemplate(player,
                chatFormat,
                Map.of("player", escape(player.getName())),
                Map.of("display_name", displayName, "message", message));
    }

    public Component privateChatMessage(CommandSender sender, PrivateChatChannel channel, String message) {
        Player player = sender instanceof Player senderPlayer ? senderPlayer : null;
        return renderTemplate(player, normalizeLegacyFormatting(channel.format()),
                Map.of(
                        "sender", escape(sender.getName()),
                        "player", escape(sender.getName()),
                        "chat", escape(channel.id()),
                        "command", escape(channel.command())
                ),
                Map.of("message", Component.text(message)));
    }

    public Component announcementMessage(Player player, String template) {
        return announcementMessage(player, template, Map.of());
    }

    public Component announcementMessage(Player player, String template, Map<String, String> placeholders) {
        return renderTemplate(player, template, placeholders, Map.of(), true);
    }

    private Component renderTemplate(
            Player player,
            String template,
            Map<String, String> placeholders,
            Map<String, Component> componentPlaceholders
    ) {
        return renderTemplate(player, template, placeholders, componentPlaceholders, false);
    }

    private Component renderTemplate(
            Player player,
            String template,
            Map<String, String> placeholders,
            Map<String, Component> componentPlaceholders,
            boolean centerLines
    ) {
        template = template.replace("{prefix}", prefix);
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            template = template.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        template = applyPlaceholderApi(player, template);

        TagResolver[] resolvers = componentPlaceholders.entrySet().stream()
                .map(entry -> Placeholder.component(entry.getKey(), entry.getValue()))
                .toArray(TagResolver[]::new);
        if (centerLines) {
            template = centerLines(template, resolvers);
        }
        return MINI_MESSAGE.deserialize(template, resolvers);
    }

    public String escape(String input) {
        return MINI_MESSAGE.escapeTags(input);
    }

    public Component inventoryChatMessage(Player player, Component inventory) {
        return message(player, "inventory-display-chat",
                Map.of("player", escape(player.getName())),
                Map.of("inventory", inventory));
    }

    public Component handChatMessage(Player player, Component hand) {
        return message(player, "hand-display-chat",
                Map.of("player", escape(player.getName())),
                Map.of("hand", hand));
    }

    public Component enderChestChatMessage(Player player, Component enderChest) {
        return message(player, "enderchest-display-chat",
                Map.of("player", escape(player.getName())),
                Map.of("enderchest", enderChest));
    }

    public long slowmodeCooldownMillis() {
        return slowmodeCooldownMillis;
    }

    public long displayCooldownMillis() {
        return displayCooldownMillis;
    }

    public boolean displayEnabled() {
        return displayEnabled;
    }

    public boolean snapshotExpiryEnabled() {
        return snapshotExpiryEnabled;
    }

    public long snapshotExpireMillis() {
        return snapshotExpireMillis;
    }

    public long snapshotCleanupIntervalTicks() {
        return snapshotCleanupIntervalTicks;
    }

    public boolean chatFormatEnabled() {
        return chatFormatEnabled;
    }

    public boolean announcementsEnabled() {
        return announcementsEnabled;
    }

    public boolean slowmodeEnabled() {
        return slowmodeEnabled;
    }

    public boolean capsEnabled() {
        return capsEnabled;
    }

    public double capsThresholdPercent() {
        return capsThresholdPercent;
    }

    public int capsMinimumLetters() {
        return capsMinimumLetters;
    }

    public boolean badWordsEnabled() {
        return badWordsEnabled;
    }

    public List<String> badWords() {
        return badWords;
    }

    public List<PrivateChatChannel> privateChatChannels() {
        return privateChatChannels;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Map<String, String> loadMessages(FileConfiguration lang) {
        return DEFAULT_MESSAGES.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> lang.getString("messages." + entry.getKey(), entry.getValue())
                ));
    }

    private static List<PrivateChatChannel> loadPrivateChatChannels(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("private-chats");
        if (section == null) {
            return List.of();
        }

        List<PrivateChatChannel> channels = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection channelSection = section.getConfigurationSection(id);
            if (channelSection == null) {
                continue;
            }

            String normalizedId = id.toLowerCase(Locale.ROOT);
            String command = normalizeCommand(channelSection.getString("command", normalizedId));
            String permission = channelSection.getString("permission", "seachat.chat." + normalizedId);
            String format = channelSection.getString("format",
                    "<aqua><bold>[" + normalizedId.toUpperCase(Locale.ROOT) + "]</bold></aqua> <gray>{sender}:</gray> <white><message>");
            if (command.isBlank() || permission.isBlank()) {
                continue;
            }

            channels.add(new PrivateChatChannel(
                    normalizedId,
                    channelSection.getBoolean("enabled", true),
                    channelSection.getBoolean("toggleable", true),
                    format,
                    permission,
                    command
            ));
        }
        return List.copyOf(channels);
    }

    private static String normalizeCommand(String command) {
        if (command == null) {
            return "";
        }

        String normalized = command.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String applyPlaceholderApi(Player player, String text) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }

        try {
            Method method = placeholderMethod;
            if (method == null && !placeholderLookupFailed) {
                method = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                        .getMethod("setPlaceholders", OfflinePlayer.class, String.class);
                placeholderMethod = method;
            }

            if (method == null) {
                return text;
            }

            Matcher matcher = PLACEHOLDER_API_PATTERN.matcher(text);
            StringBuilder expandedText = new StringBuilder();
            while (matcher.find()) {
                String placeholder = matcher.group();
                Object result = method.invoke(null, player, placeholder);
                String replacement = result instanceof String expanded ? expanded : placeholder;
                matcher.appendReplacement(expandedText, Matcher.quoteReplacement(formatPlaceholderReplacement(replacement)));
            }
            matcher.appendTail(expandedText);
            return expandedText.toString();
        } catch (ReflectiveOperationException | ClassCastException exception) {
            placeholderLookupFailed = true;
            return text;
        }
    }

    private static String legacyToMiniMessage(String text) {
        return MINI_MESSAGE.serialize(PLACEHOLDER_LEGACY_SERIALIZER.deserialize(text.replace('\u00A7', '&')));
    }

    private static String formatPlaceholderReplacement(String replacement) {
        if (MINI_MESSAGE_TAG_PATTERN.matcher(replacement).find()) {
            return replacement;
        }
        return legacyToMiniMessage(replacement);
    }

    private static String normalizeLegacyFormatting(String text) {
        if (Pattern.compile("(?i)&(?:#[0-9a-f]{6}|[0-9a-fk-or])").matcher(text).find()) {
            return legacyToMiniMessage(text);
        }
        return text;
    }

    private static String centerLines(String template, TagResolver[] resolvers) {
        if (!CENTER_TAG_PATTERN.matcher(template).find()) {
            return template;
        }

        String[] lines = template.split("\\R", -1);
        StringBuilder centered = new StringBuilder(template.length());
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                centered.append('\n');
            }

            String line = lines[index];
            if (!CENTER_TAG_PATTERN.matcher(line).find()) {
                centered.append(line);
                continue;
            }

            String content = CENTER_TAG_PATTERN.matcher(line).replaceAll("");
            centered.append(" ".repeat(centerSpaceCount(content, resolvers))).append(content);
        }
        return centered.toString();
    }

    private static int centerSpaceCount(String miniMessageLine, TagResolver[] resolvers) {
        String plainText;
        try {
            plainText = PLAIN_TEXT.serialize(MINI_MESSAGE.deserialize(miniMessageLine, resolvers));
        } catch (RuntimeException exception) {
            plainText = miniMessageLine.replaceAll("<[^>]+>", "");
        }

        int width = 0;
        for (int index = 0; index < plainText.length(); index++) {
            width += characterWidth(plainText.charAt(index));
        }

        int spaces = (154 - (width / 2)) / 4;
        return Math.max(0, spaces);
    }

    private static int characterWidth(char character) {
        return switch (character) {
            case ' ', '\u00A0' -> 4;
            case '!', '.', ',', ':', ';', '|', 'i', '\'' -> 2;
            case '`', 'l' -> 3;
            case '"', '(', ')', '[', ']', '{', '}', 'I', 't' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            default -> 6;
        };
    }
}
