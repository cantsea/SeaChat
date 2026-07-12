package com.seachat.chat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ChatState {
    private final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, Long> lastChatTimes = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> lastDisplayTimes = new ConcurrentHashMap<>();
    private volatile boolean slowmodeEnabled;

    public ChatState(boolean slowmodeEnabled) {
        this.slowmodeEnabled = slowmodeEnabled;
    }

    public boolean toggleHidden(UUID playerId) {
        if (hiddenPlayers.remove(playerId)) {
            return false;
        }

        hiddenPlayers.add(playerId);
        return true;
    }

    public boolean isHidden(UUID playerId) {
        return hiddenPlayers.contains(playerId);
    }

    public boolean toggleSlowmode() {
        slowmodeEnabled = !slowmodeEnabled;
        if (!slowmodeEnabled) {
            lastChatTimes.clear();
        }
        return slowmodeEnabled;
    }

    public boolean isSlowmodeEnabled() {
        return slowmodeEnabled;
    }

    public void setSlowmodeEnabled(boolean slowmodeEnabled) {
        this.slowmodeEnabled = slowmodeEnabled;
        if (!slowmodeEnabled) {
            lastChatTimes.clear();
        }
    }

    public long remainingSlowmodeMillis(UUID playerId, long cooldownMillis) {
        Long lastChatTime = lastChatTimes.get(playerId);
        if (lastChatTime == null) {
            return 0L;
        }

        long elapsed = System.currentTimeMillis() - lastChatTime;
        return Math.max(0L, cooldownMillis - elapsed);
    }

    public void markChat(UUID playerId) {
        lastChatTimes.put(playerId, System.currentTimeMillis());
    }

    public long remainingDisplayCooldownMillis(UUID playerId, long cooldownMillis) {
        Long lastDisplayTime = lastDisplayTimes.get(playerId);
        if (lastDisplayTime == null) {
            return 0L;
        }

        long elapsed = System.currentTimeMillis() - lastDisplayTime;
        return Math.max(0L, cooldownMillis - elapsed);
    }

    public void markDisplay(UUID playerId) {
        lastDisplayTimes.put(playerId, System.currentTimeMillis());
    }

    public void clear() {
        hiddenPlayers.clear();
        lastChatTimes.clear();
        lastDisplayTimes.clear();
    }
}
