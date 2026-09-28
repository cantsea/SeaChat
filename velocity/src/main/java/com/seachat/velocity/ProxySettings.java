package com.seachat.velocity;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

record ProxySettings(Map<String, NetworkChannel> channels, Map<String, String> messages) {
    static final Map<String, String> DEFAULT_MESSAGES = Map.ofEntries(
            Map.entry("prefix", "<aqua>[SeaChat]</aqua> "),
            Map.entry("denied", "<red>You cannot use this channel on this server.</red>"),
            Map.entry("unavailable", "<red>Private chat could not be confirmed. Check the backend bridge; use /seachatproxy leave to return to public chat.</red>"),
            Map.entry("toggle-enabled", "<yellow>Now chatting in {chat}. Use /{command} again to leave.</yellow>"),
            Map.entry("toggle-disabled", "<yellow>Private chat mode is off.</yellow>"),
            Map.entry("server-changed", "<yellow>Private chat mode was turned off because you changed servers.</yellow>"),
            Map.entry("pending", "<yellow>Please wait for the previous toggle to finish.</yellow>"),
            Map.entry("usage", "<yellow>Usage: /{command} <message></yellow>"),
            Map.entry("admin-usage", "<yellow>Usage: /seachatproxy <leave|reload></yellow>"),
            Map.entry("reload-success", "<green>Proxy chat configuration reloaded.</green>"),
            Map.entry("reload-failed", "<red>Reload failed. The previous configuration is still active; check the proxy log.</red>"));

    ProxySettings {
        channels = Map.copyOf(channels);
        messages = Map.copyOf(messages);
    }

    static ProxySettings defaults() { return new ProxySettings(Map.of(), DEFAULT_MESSAGES); }

    static ProxySettings read(Reader reader) throws IOException {
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setCodePointLimit(1_000_000);
            Map<?, ?> root = map(new Yaml(new SafeConstructor(options)).load(reader));
            Map<String, NetworkChannel> channels = new HashMap<>();
            Set<String> commands = new HashSet<>(Set.of("seachatproxy"));
            for (var entry : map(root.get("private-chats")).entrySet()) {
                String id = entry.getKey().toString().toLowerCase(Locale.ROOT);
                Map<?, ?> values = map(entry.getValue());
                if (!bool(values, "enabled", true)) continue;
                String command = text(values, "command", id).toLowerCase(Locale.ROOT);
                if (command.startsWith("/")) command = command.substring(1);
                if (!id.matches("[a-z0-9][a-z0-9_-]{0,127}") || !command.matches("[a-z0-9][a-z0-9_-]{0,127}"))
                    throw new IOException("Invalid channel ID or command: " + id);
                if (channels.containsKey(id) || !commands.add(command)) throw new IOException("Duplicate channel/command: " + id);
                String permission = text(values, "permission", "seachat.chat." + id);
                String format = text(values, "format", "<aqua>[{chat}] {sender} » <message>");
                if (format.getBytes(StandardCharsets.UTF_8).length > 8192) throw new IOException("Format too long: " + id);
                Map<?, ?> servers = map(values.get("servers"));
                channels.put(id, new NetworkChannel(id, command, permission, format, bool(values, "toggleable", true),
                        names(servers.get("whitelist")), names(servers.get("blacklist"))));
            }
            Map<String, String> messages = new HashMap<>(DEFAULT_MESSAGES);
            for (var entry : map(root.get("messages")).entrySet()) {
                if (!(entry.getValue() instanceof String message)) throw new IOException("Messages must be strings");
                messages.put(entry.getKey().toString(), message);
            }
            return new ProxySettings(channels, messages);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid proxy configuration", exception);
        }
    }

    private static Map<?, ?> map(Object value) throws IOException {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> map) return map;
        throw new IOException("Expected a configuration section");
    }
    private static String text(Map<?, ?> map, String key, String fallback) throws IOException {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (value instanceof String text && !text.isBlank()) return text;
        throw new IOException("Expected nonempty string: " + key);
    }
    private static boolean bool(Map<?, ?> map, String key, boolean fallback) throws IOException {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean flag) return flag;
        throw new IOException("Expected true/false: " + key);
    }
    private static Set<String> names(Object value) throws IOException {
        if (value == null) return Set.of();
        if (!(value instanceof List<?> list)) throw new IOException("Server lists must be YAML lists");
        Set<String> names = new HashSet<>();
        for (Object entry : list) {
            if (!(entry instanceof String name) || name.isBlank()) throw new IOException("Invalid server name");
            names.add(name.trim().toLowerCase(Locale.ROOT));
        }
        return names;
    }
}
