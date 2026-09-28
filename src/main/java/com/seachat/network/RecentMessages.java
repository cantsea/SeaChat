package com.seachat.network;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Bounded duplicate suppression; no persistent message history. */
public final class RecentMessages {
    private final LinkedHashMap<UUID, Long> seen = new LinkedHashMap<>();
    private final LongSupplier clock;
    private final int capacity;
    private final long lifetime;

    public RecentMessages() {
        this(4096, 60_000_000_000L, System::nanoTime);
    }

    public RecentMessages(int capacity, long lifetime, LongSupplier clock) {
        if (capacity < 1 || lifetime < 1) throw new IllegalArgumentException("Invalid cache bounds");
        this.capacity = capacity;
        this.lifetime = lifetime;
        this.clock = clock;
    }

    public synchronized boolean first(UUID id) {
        long now = clock.getAsLong();
        var entries = seen.entrySet().iterator();
        while (entries.hasNext()) {
            if (now - entries.next().getValue() < lifetime) break;
            entries.remove();
        }
        if (seen.containsKey(id)) return false;
        if (seen.size() >= capacity) seen.remove(seen.keySet().iterator().next());
        seen.put(id, now);
        return true;
    }
}
