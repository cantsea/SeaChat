package com.seachat.config;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import static org.junit.Assert.*;

public class CenteredTextTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    public void boldLinesReceiveLessPadding() {
        assertEquals(" ".repeat(35) + "AAAAA", plain("<center>AAAAA"));
        assertEquals(" ".repeat(34) + "AAAAA", plain("<center><bold>AAAAA</bold>"));
    }

    @Test
    public void mixedNestedAndExplicitlyDisabledBoldUseEffectiveStyle() {
        // Four normal A's (24px) and four bold A's (28px) = 52px.
        assertEquals(" ".repeat(32) + "AAAAAAAA",
                plain("<center><b><red>AAAA</red><!bold>AAAA</!bold></b>"));
        assertEquals(" ".repeat(32) + "AAAAAAAA",
                plain("<center>AAAA<b><blue>AAAA</blue></b>"));
        assertEquals(" ".repeat(32) + "AAAAAAAA", plain("<center><b>AAAA<reset>AAAA"));
    }

    @Test
    public void boldCanSpanLinesButPaddingNeverInheritsBold() {
        Component rendered = CenteredText.render("<center><b>AAAAA\n<center>AAAAA</b>\n<center>AAAAA");
        assertEquals(" ".repeat(34) + "AAAAA\n" + " ".repeat(34) + "AAAAA\n"
                + " ".repeat(35) + "AAAAA", PLAIN.serialize(rendered));
        for (Glyph glyph : glyphs(rendered)) {
            if (glyph.codePoint == ' ') {
                assertNotEquals(TextDecoration.State.TRUE, glyph.style.decoration(TextDecoration.BOLD));
            }
        }
    }

    @Test
    public void boldSpacesCountTowardTheMessageWidth() {
        // 4 * 7 for A and 3 * 5 for the bold spaces = 43px.
        assertEquals(" ".repeat(33) + "A A A A", plain("<center><b>A A A A</b>"));
    }

    @Test
    public void usesActualVanillaBitmapAdvances() {
        assertEquals(7, DefaultFontWidths.advance('@', false));
        assertEquals(7, DefaultFontWidths.advance('~', false));
        assertEquals(4, DefaultFontWidths.advance('*', false));
        assertEquals(6, DefaultFontWidths.advance('é', false));
        assertEquals(10, DefaultFontWidths.advance('Æ', false));
        assertEquals(11, DefaultFontWidths.advance('Æ', true));
        assertEquals(4, DefaultFontWidths.advance(' ', false));
        assertEquals(5, DefaultFontWidths.advance(' ', true));
        assertEquals(" ".repeat(34) + "@~*Æé", plain("<center>@~*Æé"));
    }

    @Test
    public void preservesClickHoverColorsAndDecorations() {
        Component rendered = CenteredText.render(
                "<center><click:run_command:'/help'><hover:show_text:'Help'><red><b>AAAAA</b></red></hover></click>");
        List<Glyph> letters = glyphs(rendered).stream().filter(glyph -> glyph.codePoint == 'A').toList();
        assertEquals(5, letters.size());
        for (Glyph letter : letters) {
            assertEquals(NamedTextColor.RED, letter.style.color());
            assertEquals(TextDecoration.State.TRUE, letter.style.decoration(TextDecoration.BOLD));
            assertEquals(ClickEvent.runCommand("/help"), letter.style.clickEvent());
            assertEquals(HoverEvent.showText(Component.text("Help")), letter.style.hoverEvent());
        }
    }

    @Test
    public void measuresResolvedComponentPlaceholdersIncludingTheirChildren() {
        Component label = Component.text("AAAA", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                .append(Component.text("AAAA").decoration(TextDecoration.BOLD, false));
        Component result = CenteredText.render("<center><label>", Placeholder.component("label", label));
        assertEquals(" ".repeat(32) + "AAAAAAAA", PLAIN.serialize(result));
    }

    @Test
    public void ordinaryLinesAndLongLinesAreNotWrappedOrPadded() {
        String normal = "<b>hello</b>\n<red>world</red>";
        assertEquals(MiniMessage.miniMessage().deserialize(normal), CenteredText.render(normal));
        assertEquals("Normal\n" + "A".repeat(100) + "\nEnd",
                plain("Normal\n<center><b>" + "A".repeat(100) + "</b>\nEnd"));
    }

    @Test
    public void newlineTagsDoNotThrowOffLaterMarkedLines() {
        assertEquals("One\nTwo\n" + " ".repeat(34) + "AAAAA",
                plain("One<newline>Two\n<center><b>AAAAA</b>"));
    }

    @Test
    public void announcementEntryPointMeasuresAfterStringReplacement() {
        ChatSettings settings = ChatSettings.from(new YamlConfiguration(), new YamlConfiguration());
        Component result = settings.announcementMessage(null, "<center><b>{name}</b>",
                java.util.Map.of("name", "AAAAA"));
        assertEquals(" ".repeat(34) + "AAAAA", PLAIN.serialize(result));
    }

    private static String plain(String template) {
        return PLAIN.serialize(CenteredText.render(template));
    }

    private record Glyph(int codePoint, Style style) {}

    private static List<Glyph> glyphs(Component component) {
        List<Glyph> result = new ArrayList<>();
        collect(component, Style.empty(), result);
        return result;
    }

    private static void collect(Component component, Style parent, List<Glyph> result) {
        Style effective = component.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text) {
            text.content().codePoints().forEach(codePoint -> result.add(new Glyph(codePoint, effective)));
        }
        for (Component child : component.children()) {
            collect(child, effective, result);
        }
    }
}
