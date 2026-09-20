package com.seachat.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public final class ChatColors {
    private ChatColors() {
    }

    public static Component withoutColors(Component message) {
        return withoutColors(message, NamedTextColor.GRAY);
    }

    public static Component withoutColors(Component message, TextColor replacementColor) {
        // Override colors inherited from the surrounding chat format as well.
        return clearColors(message).color(replacementColor);
    }

    public static Component replaceMessage(Component rendered, Component message, Component replacement) {
        if (rendered == message) {
            return replacement;
        }
        Component result = rendered.children(rendered.children().stream()
                .map(child -> replaceMessage(child, message, replacement)).toList());
        if (result instanceof TranslatableComponent translatable) {
            result = translatable.arguments(translatable.arguments().stream()
                    .map(argument -> argument.value() instanceof Component component
                            ? TranslationArgument.component(replaceMessage(component, message, replacement)) : argument)
                    .toList());
        }
        return result;
    }

    private static Component clearColors(Component message) {
        Component result = message.color(null)
                .children(message.children().stream().map(ChatColors::clearColors).toList());
        if (result instanceof TranslatableComponent translatable) {
            result = translatable.arguments(translatable.arguments().stream()
                    .map(argument -> argument.value() instanceof Component component
                            ? TranslationArgument.component(clearColors(component)) : argument)
                    .toList());
        }
        return result;
    }
}
