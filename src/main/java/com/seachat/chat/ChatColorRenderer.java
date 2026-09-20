package com.seachat.chat;

import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/** A message-scoped renderer: never share an instance between chat events. */
public final class ChatColorRenderer implements ChatRenderer {
    private final ChatRenderer delegate;
    private final ChatState state;
    private final TextColor replacementColor;
    private volatile MessageVariant messageVariant;
    private volatile RenderedVariant renderedVariant;

    public ChatColorRenderer(ChatRenderer delegate, ChatState state, TextColor replacementColor) {
        this.delegate = delegate;
        this.state = state;
        this.replacementColor = replacementColor;
    }

    @Override
    public Component render(Player source, Component sourceDisplayName, Component message, Audience viewer) {
        if (!(viewer instanceof Player player) || !state.areColorsDisabled(player.getUniqueId())) {
            return delegate.render(source, sourceDisplayName, message, viewer);
        }

        if (delegate instanceof ChatRenderer.ViewerUnaware) {
            // Paper caches these renderers' results, so always supply the original message.
            Component rendered = delegate.render(source, sourceDisplayName, message, viewer);
            return recolorRendered(message, rendered);
        }
        return delegate.render(source, sourceDisplayName, recolorMessage(message), viewer);
    }

    private Component recolorMessage(Component original) {
        MessageVariant cached = messageVariant;
        if (cached != null && cached.original() == original) {
            return cached.replacement();
        }
        synchronized (this) {
            cached = messageVariant;
            if (cached == null || cached.original() != original) {
                cached = new MessageVariant(original, ChatColors.withoutColors(original, replacementColor));
                messageVariant = cached;
            }
            return cached.replacement();
        }
    }

    private Component recolorRendered(Component message, Component original) {
        RenderedVariant cached = renderedVariant;
        if (cached != null && cached.message() == message && cached.original() == original) {
            return cached.replacement();
        }
        synchronized (this) {
            cached = renderedVariant;
            if (cached == null || cached.message() != message || cached.original() != original) {
                cached = new RenderedVariant(message, original,
                        ChatColors.replaceMessage(original, message, recolorMessage(message)));
                renderedVariant = cached;
            }
            return cached.replacement();
        }
    }

    private record MessageVariant(Component original, Component replacement) {
    }

    private record RenderedVariant(Component message, Component original, Component replacement) {
    }
}
