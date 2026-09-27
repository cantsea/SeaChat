package com.seachat.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Centers explicitly marked lines without changing the message's component structure. */
final class CenteredText {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern CENTER_TAG = Pattern.compile("(?i)</?center\\s*/?>");
    private static final int CENTER_PIXEL = 154;
    private static final int SPACE_WIDTH = 4;

    private CenteredText() {}

    static Component render(String template, TagResolver... resolvers) {
        if (!CENTER_TAG.matcher(template).find()) {
            return MINI_MESSAGE.deserialize(template, resolvers);
        }

        var tags = new ArrayList<>(Arrays.asList(resolvers));
        Map<String, Line> markers = new HashMap<>();
        String[] lines = template.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!CENTER_TAG.matcher(lines[i]).find()) {
                continue;
            }
            String marker = "seachat_center_padding_" + i;
            // A structural marker survives MiniMessage parsing without contributing visible text.
            tags.add(Placeholder.component(marker, Component.translatable(marker)));
            markers.put(marker, null);
            lines[i] = "<" + marker + ">" + CENTER_TAG.matcher(lines[i]).replaceAll("");
        }

        // Parse once as a whole: styles may be inherited from parents or earlier lines.
        Component parsed = MINI_MESSAGE.deserialize(String.join("\n", lines), tags.toArray(TagResolver[]::new));
        new Measurement(markers).visit(parsed, false);
        return replaceMarkers(parsed, markers);
    }

    private static Component replaceMarkers(Component component, Map<String, Line> markers) {
        if (component instanceof TranslatableComponent translated && markers.containsKey(translated.key())) {
            Line line = markers.get(translated.key());
            int spaces = line == null ? 0 : Math.max(0,
                    (int) Math.round((CENTER_PIXEL - line.width / 2.0) / SPACE_WIDTH));
            // Padding must stay four pixels per space even inside an inherited bold/font style.
            return Component.text(" ".repeat(spaces))
                    .font(Key.key("minecraft", "default"))
                    .decoration(TextDecoration.BOLD, false);
        }
        if (component.children().isEmpty()) {
            return component;
        }
        return component.children(component.children().stream()
                .map(child -> replaceMarkers(child, markers)).toList());
    }

    private static final class Line {
        private int width;
    }

    private static final class Measurement {
        private final Map<String, Line> markers;
        private Line current = new Line();

        private Measurement(Map<String, Line> markers) {
            this.markers = markers;
        }

        private void visit(Component component, boolean inheritedBold) {
            boolean bold = switch (component.decoration(TextDecoration.BOLD)) {
                case TRUE -> true;
                case FALSE -> false;
                case NOT_SET -> inheritedBold;
            };
            if (component instanceof TranslatableComponent translated && markers.containsKey(translated.key())) {
                markers.put(translated.key(), current);
                return;
            }
            String text = component instanceof TextComponent literal ? literal.content()
                    : PlainTextComponentSerializer.plainText().serialize(component.children(java.util.List.of()));
            text.codePoints().forEach(codePoint -> {
                if (codePoint == '\n' || codePoint == '\r') {
                    current = new Line();
                } else {
                    current.width += DefaultFontWidths.advance(codePoint, bold);
                }
            });
            for (Component child : component.children()) {
                visit(child, bold);
            }
        }
    }
}
