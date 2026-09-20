package com.seachat.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.Test;

import static org.junit.Assert.*;

public class ChatColorsTest {
    @Test
    public void removesNestedColorsWithoutLosingInteractionsOrDecorations() {
        Component message = Component.text("Rank ", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .append(Component.text("Item", NamedTextColor.BLUE)
                        .clickEvent(ClickEvent.runCommand("/seachatinv example"))
                        .hoverEvent(HoverEvent.showText(Component.text("View item"))));

        Component result = ChatColors.withoutColors(message);

        assertEquals(NamedTextColor.WHITE, result.color());
        assertNull(result.children().getFirst().color());
        assertEquals(TextDecoration.State.TRUE, result.decoration(TextDecoration.BOLD));
        assertEquals(message.children().getFirst().clickEvent(), result.children().getFirst().clickEvent());
        assertEquals(message.children().getFirst().hoverEvent(), result.children().getFirst().hoverEvent());
        assertEquals(NamedTextColor.RED, message.color());
        assertEquals(NamedTextColor.BLUE, message.children().getFirst().color());
    }

    @Test
    public void removesColorsFromTranslatedArgumentsAndPreservesTypedArguments() {
        TranslatableComponent message = Component.translatable("example.key")
                .arguments(TranslationArgument.component(Component.text("Player", NamedTextColor.GOLD)),
                        TranslationArgument.numeric(2), TranslationArgument.bool(true));

        TranslatableComponent result = (TranslatableComponent) ChatColors.withoutColors(message);

        assertEquals("example.key", result.key());
        assertNull(((Component) result.arguments().getFirst().value()).color());
        assertEquals(message.arguments().get(1), result.arguments().get(1));
        assertEquals(message.arguments().get(2), result.arguments().get(2));
    }

    @Test
    public void appliesReplacementColorAcrossNestedMessageText() {
        Component message = Component.text("Hello ", NamedTextColor.RED)
                .append(Component.text("world", NamedTextColor.BLUE));

        Component result = ChatColors.withoutColors(message, NamedTextColor.GRAY);

        assertEquals(NamedTextColor.GRAY, result.color());
        assertNull(result.children().getFirst().color());
        assertEquals(NamedTextColor.RED, message.color());
    }
}
