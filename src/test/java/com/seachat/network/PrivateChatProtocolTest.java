package com.seachat.network;

import com.seachat.network.PrivateChatProtocol.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrivateChatProtocolTest {
    private final UUID id = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    private List<Packet> packets() {
        return List.of(new Render(id, player, "staff", "sc", "<message>", "é 中文 😀"),
                new Render(id, null, "staff", "sc", "<message>", "Console"),
                new Rendered(id, "{}", "{}"), new Rendered(id, "{}", null),
                new Delivery(id, List.of(player), "{}", "{}"), new Capture(id, player, true),
                new Capture(id, player, false), new Acknowledgement(id), new Input(id, player, "Hello"));
    }
    @Test public void roundTripsAllPackets() throws Exception {
        for (Packet packet : packets()) assertEquals(packet, PrivateChatProtocol.decode(PrivateChatProtocol.encode(packet)));
    }
    @Test public void rejectsEveryTruncationOfEveryPacket() throws Exception {
        for (Packet packet : packets()) {
            byte[] encoded = PrivateChatProtocol.encode(packet);
            for (int size = 0; size < encoded.length; size++) {
                byte[] truncated = Arrays.copyOf(encoded, size);
                assertThrows(IOException.class, () -> PrivateChatProtocol.decode(truncated));
            }
        }
    }
    @Test public void rejectsOldVersionUnknownTypeAndTrailingData() throws Exception {
        byte[] bytes = PrivateChatProtocol.encode(new Acknowledgement(id));
        bytes[4] = 1;
        assertThrows(IOException.class, () -> PrivateChatProtocol.decode(bytes));
        bytes[4] = 2; bytes[5] = 99;
        assertThrows(IOException.class, () -> PrivateChatProtocol.decode(bytes));
        assertThrows(IOException.class, () -> PrivateChatProtocol.decode(Arrays.copyOf(bytes, bytes.length + 1)));
    }
    @Test public void boundsPacketFieldsAndRecipientLists() throws Exception {
        assertThrows(IOException.class, () -> PrivateChatProtocol.encode(new Input(id, player, "é".repeat(2049))));
        assertThrows(IOException.class, () -> PrivateChatProtocol.decode(new byte[30001]));
        assertThrows(IOException.class, () -> PrivateChatProtocol.encode(new Delivery(id, List.of(), "{}", null)));
        List<UUID> players = java.util.Collections.nCopies(100, player);
        byte[] maximum = PrivateChatProtocol.encode(new Delivery(id, players, "x".repeat(14000), "x".repeat(14000)));
        assertTrue(maximum.length <= 30000);
        assertEquals(100, ((Delivery) PrivateChatProtocol.decode(maximum)).recipients().size());
        assertThrows(IOException.class, () -> PrivateChatProtocol.encode(new Delivery(id,
                java.util.Collections.nCopies(101, player), "{}", null)));
    }
    @Test public void rejectsInvalidUtf8() throws Exception {
        byte[] bytes = PrivateChatProtocol.encode(new Input(id, player, "hello"));
        bytes[40] = (byte) 0xFF;
        assertThrows(IOException.class, () -> PrivateChatProtocol.decode(bytes));
    }
    @Test public void duplicateCacheExpiresAndRemainsBounded() {
        AtomicLong now = new AtomicLong();
        RecentMessages recent = new RecentMessages(2, 100, now::get);
        assertTrue(recent.first(id));
        assertFalse(recent.first(id));
        assertTrue(recent.first(player));
        assertTrue(recent.first(UUID.randomUUID()));
        assertTrue(recent.first(id));
        now.set(100);
        assertTrue(recent.first(id));
    }
}