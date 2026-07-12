package com.seachat.poll;

import com.seachat.SeaChat;
import com.seachat.chat.ChatState;
import com.seachat.config.ChatSettings;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class PollManager {
    public static final String CREATE_PERMISSION = "seachat.poll.create";
    public static final String RESPOND_PERMISSION = "seachat.poll.respond";

    private final SeaChat plugin;
    private final ChatSettings settings;
    private final ChatState state;
    private final Object pollLock = new Object();
    private ActivePoll activePoll;

    public PollManager(SeaChat plugin, ChatSettings settings, ChatState state) {
        this.plugin = plugin;
        this.settings = settings;
        this.state = state;
    }

    public boolean createPoll(CommandSender sender, long seconds, String question) {
        if (seconds <= 0L || seconds > Long.MAX_VALUE / 20L) {
            sender.sendMessage(settings.message(senderPlayer(sender), "poll-invalid-time"));
            return true;
        }

        ActivePoll poll = new ActivePoll(creatorId(sender), sender.getName(), question, seconds);
        synchronized (pollLock) {
            if (activePoll != null) {
                sender.sendMessage(settings.message(senderPlayer(sender), "poll-already-active"));
                return true;
            }

            activePoll = poll;
            poll.endTask = Bukkit.getScheduler().runTaskLater(plugin, () -> finishPoll(poll), seconds * 20L);
        }

        broadcastPollCreated(poll, senderPlayer(sender));
        return true;
    }

    public boolean stopPoll(CommandSender sender) {
        ActivePoll poll;
        synchronized (pollLock) {
            poll = activePoll;
        }

        if (poll == null) {
            sender.sendMessage(settings.message(senderPlayer(sender), "poll-none-active"));
            return true;
        }

        sender.sendMessage(settings.message(senderPlayer(sender), "poll-stopped"));
        finishPoll(poll);
        return true;
    }

    public boolean handleChatResponse(Player player, String message) {
        Vote vote = Vote.from(message);
        if (vote == null) {
            return false;
        }

        ActivePoll poll;
        synchronized (pollLock) {
            poll = activePoll;
        }

        if (poll == null) {
            return false;
        }

        if (!player.hasPermission(RESPOND_PERMISSION)) {
            sendToPlayer(player.getUniqueId(), "poll-response-no-permission", Map.of());
            return true;
        }

        poll.votes.put(player.getUniqueId(), vote);
        sendToPlayer(player.getUniqueId(), "poll-response-counted", Map.of("response", vote.messageValue));
        return true;
    }

    public void shutdown() {
        ActivePoll poll;
        synchronized (pollLock) {
            poll = activePoll;
            activePoll = null;
        }

        if (poll != null && poll.endTask != null) {
            poll.endTask.cancel();
        }
    }

    private void finishPoll(ActivePoll poll) {
        synchronized (pollLock) {
            if (activePoll != poll) {
                return;
            }

            activePoll = null;
        }

        if (poll.endTask != null) {
            poll.endTask.cancel();
        }

        broadcastPollEnded(poll);
        sendResultsToCreator(poll);
    }

    private void broadcastPollCreated(ActivePoll poll, Player creator) {
        Component message = settings.message(creator, "poll-created", Map.of(
                "creator", settings.escape(poll.creatorName),
                "question", settings.escape(poll.question),
                "seconds", String.valueOf(poll.seconds)
        ));

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!state.isHidden(viewer.getUniqueId())) {
                viewer.sendMessage(message);
            }
        }
    }

    private void broadcastPollEnded(ActivePoll poll) {
        Component message = settings.message(null, "poll-ended", Map.of(
                "creator", settings.escape(poll.creatorName),
                "question", settings.escape(poll.question)
        ));

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!state.isHidden(viewer.getUniqueId())) {
                viewer.sendMessage(message);
            }
        }
    }

    private void sendResultsToCreator(ActivePoll poll) {
        long yesVotes = poll.votes.values().stream().filter(vote -> vote == Vote.YES).count();
        long noVotes = poll.votes.values().stream().filter(vote -> vote == Vote.NO).count();
        Player creator = poll.creatorId == null ? null : Bukkit.getPlayer(poll.creatorId);
        CommandSender recipient = creator != null ? creator : poll.creatorId == null ? Bukkit.getConsoleSender() : null;
        if (recipient == null) {
            return;
        }

        recipient.sendMessage(settings.message(creator, "poll-results", Map.of(
                "question", settings.escape(poll.question),
                "yes", String.valueOf(yesVotes),
                "no", String.valueOf(noVotes),
                "total", String.valueOf(poll.votes.size())
        )));
    }

    private void sendToPlayer(UUID playerId, String messageKey, Map<String, String> placeholders) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(settings.message(player, messageKey, placeholders));
            }
        });
    }

    private static boolean hasCreatePermission(CommandSender sender) {
        return sender.hasPermission(CREATE_PERMISSION);
    }

    public static boolean canCreatePoll(CommandSender sender) {
        return hasCreatePermission(sender);
    }

    private static UUID creatorId(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : null;
    }

    private static Player senderPlayer(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }

}
