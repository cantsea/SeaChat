package com.seachat.listener;

import com.seachat.SeaChat;
import com.seachat.chat.ChatColorRenderer;
import com.seachat.chat.ChatState;
import com.seachat.config.ChatSettings;
import com.seachat.poll.PollManager;
import com.seachat.privatechat.PrivateChatManager;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatListener implements Listener {
    private static final String SLOWMODE_BYPASS_PERMISSION = "seachat.bypass.slowmode";
    private static final String CAPS_BYPASS_PERMISSION = "seachat.bypass.caps";
    private static final String NOTIFY_PERMISSION = "seachat.notify";

    private final SeaChat plugin;
    private final ChatSettings settings;
    private final ChatState state;
    private final PollManager pollManager;
    private final PrivateChatManager privateChatManager;

    public ChatListener(
            SeaChat plugin,
            ChatSettings settings,
            ChatState state,
            PollManager pollManager,
            PrivateChatManager privateChatManager
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.state = state;
        this.pollManager = pollManager;
        this.privateChatManager = privateChatManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (privateChatManager.handleToggledChat(player, message)) {
            event.setCancelled(true);
            return;
        }

        if (state.isHidden(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(settings.message("chat-hidden-cannot-speak"));
            return;
        }

        if (pollManager.handleChatResponse(player, message)) {
            event.setCancelled(true);
            return;
        }

        if (shouldCancelForBadWord(message)) {
            event.setCancelled(true);
            player.sendMessage(settings.message("blocked-bad-word"));
            notifyFilteredMessage(player, message);
            return;
        }

        if (!player.hasPermission(CAPS_BYPASS_PERMISSION) && shouldCancelForCaps(message)) {
            event.setCancelled(true);
            player.sendMessage(settings.message("blocked-caps"));
            notifyFilteredMessage(player, message);
            return;
        }

        if (state.isSlowmodeEnabled() && !player.hasPermission(SLOWMODE_BYPASS_PERMISSION)) {
            long remainingMillis = state.remainingSlowmodeMillis(player.getUniqueId(), settings.slowmodeCooldownMillis());
            if (remainingMillis > 0) {
                event.setCancelled(true);
                player.sendMessage(settings.message("slowmode-wait",
                        Map.of("seconds", String.valueOf(formatSeconds(remainingMillis)))));
                return;
            }

            state.markChat(player.getUniqueId());
        }

        event.viewers().removeIf(audience ->
                audience instanceof Player viewer && state.isHidden(viewer.getUniqueId()));

        ChatRenderer renderer = settings.chatFormatEnabled()
                ? (source, sourceDisplayName, renderedMessage, viewer) ->
                        settings.chatMessage(source, sourceDisplayName, renderedMessage)
                : event.renderer();
        event.renderer(new ChatColorRenderer(renderer, state, settings.disabledChatColor()));
    }

    private boolean shouldCancelForBadWord(String message) {
        if (!settings.badWordsEnabled()) {
            return false;
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        return settings.badWords().stream().anyMatch(normalized::contains);
    }

    private boolean shouldCancelForCaps(String message) {
        if (!settings.capsEnabled()) {
            return false;
        }

        int letters = 0;
        int uppercase = 0;
        for (int i = 0; i < message.length(); i++) {
            char character = message.charAt(i);
            if (!Character.isLetter(character)) {
                continue;
            }

            letters++;
            if (Character.isUpperCase(character)) {
                uppercase++;
            }
        }

        if (letters < settings.capsMinimumLetters()) {
            return false;
        }

        double percent = (uppercase * 100.0D) / letters;
        return percent >= settings.capsThresholdPercent();
    }

    private long formatSeconds(long millis) {
        return Math.max(1L, (long) Math.ceil(millis / 1000.0D));
    }

    private void notifyFilteredMessage(Player sender, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission(NOTIFY_PERMISSION)) {
                    continue;
                }

                player.sendMessage(settings.message("filter-notify", Map.of(
                        "player", settings.escape(sender.getName()),
                        "message", settings.escape(message)
                )));
            }
        });
    }
}
