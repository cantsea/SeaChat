package com.seachat.velocity;

import java.io.IOException;
import java.io.StringReader;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProxySettingsTest {
    private ProxySettings read(String yaml) throws IOException { return ProxySettings.read(new StringReader(yaml)); }

    @Test public void loadsCentralChannelAndBlacklistWins() throws Exception {
        var channel = read("""
                private-chats:
                  staff:
                    enabled: true
                    command: sc
                    permission: seachat.chat.staff
                    toggleable: false
                    servers:
                      whitelist: [Survival, creative]
                      blacklist: [creative]
                """).channels().get("staff");
        assertFalse(channel.toggleable());
        assertEquals("sc", channel.command());
        assertTrue(channel.allows("SURVIVAL"));
        assertFalse(channel.allows("creative"));
        assertFalse(channel.allows("limbo"));
    }

    @Test public void missingOrEmptyListsAllowEveryServer() throws Exception {
        assertTrue(read("private-chats: {staff: {}}").channels().get("staff").allows("any"));
        assertTrue(read("private-chats: {staff: {servers: {whitelist: [], blacklist: []}}}").channels().get("staff").allows("any"));
    }

    @Test public void disabledChannelsAreNotRegistered() throws Exception {
        assertTrue(read("private-chats: {staff: {enabled: false}}").channels().isEmpty());
    }

    @Test public void rejectsBadConfigurationInsteadOfWideningAccess() {
        for (String yaml : new String[]{
                "private-chats: {staff: {servers: {whitelist: survival}}}",
                "private-chats: {staff: {enabled: 'nope'}}",
                "private-chats: {staff: {permission: ''}}",
                "private-chats: {staff: {command: seachatproxy}}",
                "private-chats: {one: {command: sc}, two: {command: sc}}",
                "private-chats: {staff: {}, STAFF: {}}",
                "private-chats: {staff: {command: 'bad command'}}",
                "private-chats: {staff: {enabled: true, enabled: false}}"}) {
            assertThrows(yaml, IOException.class, () -> read(yaml));
        }
    }

    @Test public void defaultConfigIsValidAndHasNoBackendFlag() throws Exception {
        try (var reader = new java.io.InputStreamReader(getClass().getResourceAsStream("/config.yml"), java.nio.charset.StandardCharsets.UTF_8)) {
            assertEquals("sc", ProxySettings.read(reader).channels().get("staff").command());
        }
    }
}
