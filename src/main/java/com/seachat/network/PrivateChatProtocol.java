package com.seachat.network;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bounded, versioned protocol shared by the Paper bridge and Velocity plugin. */
public final class PrivateChatProtocol {
    public static final String CHANNEL = "seachat:private_chat";
    public static final int MAX_PACKET_BYTES = 30_000;
    public static final int MAX_RECIPIENTS = 100;
    private static final int MAGIC = 0x53435043;
    private static final int VERSION = 2;
    private PrivateChatProtocol() {}

    public sealed interface Packet permits Render, Rendered, Delivery, Capture, Acknowledgement, Input {}
    public record Render(UUID id, UUID sender, String channel, String command, String format, String message) implements Packet {}
    public record Rendered(UUID id, String normal, String disabled) implements Packet {}
    public record Delivery(UUID id, List<UUID> recipients, String normal, String disabled) implements Packet {
        public Delivery { recipients = List.copyOf(recipients); }
    }
    public record Capture(UUID id, UUID player, boolean enabled) implements Packet {}
    public record Acknowledgement(UUID id) implements Packet {}
    public record Input(UUID id, UUID player, String message) implements Packet {}

    public static byte[] encode(Packet packet) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            switch (packet) {
                case Render value -> {
                    out.writeByte(1); uuid(out, value.id());
                    out.writeBoolean(value.sender() != null);
                    if (value.sender() != null) uuid(out, value.sender());
                    text(out, value.channel(), 128); text(out, value.command(), 128);
                    text(out, value.format(), 8192); text(out, value.message(), 4096);
                }
                case Rendered value -> {
                    out.writeByte(2); uuid(out, value.id()); components(out, value.normal(), value.disabled());
                }
                case Delivery value -> {
                    if (value.recipients().isEmpty() || value.recipients().size() > MAX_RECIPIENTS)
                        throw new IOException("Invalid recipient count");
                    out.writeByte(3); uuid(out, value.id()); out.writeShort(value.recipients().size());
                    for (UUID player : value.recipients()) uuid(out, player);
                    components(out, value.normal(), value.disabled());
                }
                case Capture value -> {
                    out.writeByte(4); uuid(out, value.id()); uuid(out, value.player()); out.writeBoolean(value.enabled());
                }
                case Acknowledgement value -> { out.writeByte(5); uuid(out, value.id()); }
                case Input value -> {
                    out.writeByte(6); uuid(out, value.id()); uuid(out, value.player()); text(out, value.message(), 4096);
                }
            }
        }
        if (bytes.size() > MAX_PACKET_BYTES) throw new IOException("Packet too large");
        return bytes.toByteArray();
    }

    public static Packet decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_PACKET_BYTES) throw new IOException("Packet too large");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION) throw new IOException("Unsupported protocol");
            int type = in.readUnsignedByte();
            UUID id = uuid(in);
            Packet packet = switch (type) {
                case 1 -> new Render(id, in.readBoolean() ? uuid(in) : null, text(in, 128), text(in, 128),
                        text(in, 8192), text(in, 4096));
                case 2 -> new Rendered(id, text(in, 14_000), in.readBoolean() ? text(in, 14_000) : null);
                case 3 -> {
                    int count = in.readUnsignedShort();
                    if (count == 0 || count > MAX_RECIPIENTS) throw new IOException("Invalid recipient count");
                    List<UUID> players = new ArrayList<>();
                    for (int i = 0; i < count; i++) players.add(uuid(in));
                    yield new Delivery(id, players, text(in, 14_000), in.readBoolean() ? text(in, 14_000) : null);
                }
                case 4 -> new Capture(id, uuid(in), in.readBoolean());
                case 5 -> new Acknowledgement(id);
                case 6 -> new Input(id, uuid(in), text(in, 4096));
                default -> throw new IOException("Unknown packet type");
            };
            if (in.available() != 0) throw new IOException("Trailing packet data");
            return packet;
        }
    }

    private static void components(DataOutputStream out, String normal, String disabled) throws IOException {
        text(out, normal, 14_000); out.writeBoolean(disabled != null);
        if (disabled != null) text(out, disabled, 14_000);
    }
    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
    }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void text(DataOutputStream out, String text, int max) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > max) throw new IOException("Invalid field length");
        out.writeShort(bytes.length); out.write(bytes);
    }
    private static String text(DataInputStream in, int max) throws IOException {
        int size = in.readUnsignedShort();
        if (size == 0 || size > max || size > in.available()) throw new IOException("Invalid field length");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(in.readNBytes(size))).toString();
    }
}