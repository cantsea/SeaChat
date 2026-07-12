package com.seachat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class ChatState {
    private final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, Long> lastChatTimes = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> lastDisplayTimes = new ConcurrentHashMap<>();
    private volatile boolean slowmodeEnabled;

    ChatState(boolean slowmodeEnabled) {
        this.slowmodeEnabled = slowmodeEnabled;
    }

    boolean toggleHidden(UUID playerId) {
        if (hiddenPlayers.remove(playerId)) {
            return false;
        }

        hiddenPlayers.add(playerId);
        return true;
    }

    boolean isHidden(UUID playerId) {
        return hiddenPlayers.contains(playerId);
    }

    boolean toggleSlowmode() {
        slowmodeEnabled = !slowmodeEnabled;
        if (!slowmodeEnabled) {
            lastChatTimes.clear();
        }
        return slowmodeEnabled;
    }

    boolean isSlowmodeEnabled() {
        return slowmodeEnabled;
    }

    void setSlowmodeEnabled(boolean slowmodeEnabled) {
        this.slowmodeEnabled = slowmodeEnabled;
        if (!slowmodeEnabled) {
            lastChatTimes.clear();
        }
    }

    long remainingSlowmodeMillis(UUID playerId, long cooldownMillis) {
        Long lastChatTime = lastChatTimes.get(playerId);
        if (lastChatTime == null) {
            return 0L;
        }

        long elapsed = System.currentTimeMillis() - lastChatTime;
        return Math.max(0L, cooldownMillis - elapsed);
    }

    void markChat(UUID playerId) {
        lastChatTimes.put(playerId, System.currentTimeMillis());
    }

    long remainingDisplayCooldownMillis(UUID playerId, long cooldownMillis) {
        Long lastDisplayTime = lastDisplayTimes.get(playerId);
        if (lastDisplayTime == null) {
            return 0L;
        }

        long elapsed = System.currentTimeMillis() - lastDisplayTime;
        return Math.max(0L, cooldownMillis - elapsed);
    }

    void markDisplay(UUID playerId) {
        lastDisplayTimes.put(playerId, System.currentTimeMillis());
    }

    void clear() {
        hiddenPlayers.clear();
        lastChatTimes.clear();
        lastDisplayTimes.clear();
    }
}
