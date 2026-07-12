package com.seachat.poll;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.scheduler.BukkitTask;

final class ActivePoll {
    final UUID creatorId;
    final String creatorName;
    final String question;
    final long seconds;
    final ConcurrentMap<UUID, Vote> votes = new ConcurrentHashMap<>();
    BukkitTask endTask;

    ActivePoll(UUID creatorId, String creatorName, String question, long seconds) {
        this.creatorId = creatorId;
        this.creatorName = creatorName;
        this.question = question;
        this.seconds = seconds;
    }
}
