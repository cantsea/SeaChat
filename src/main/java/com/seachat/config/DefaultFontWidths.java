package com.seachat.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Vanilla 1.21.11 bitmap glyph advances, including the one-pixel character spacing. */
final class DefaultFontWidths {
    private static final Map<Integer, Integer> WIDTHS = load();

    private DefaultFontWidths() {}

    static int advance(int codePoint, boolean bold) {
        // Resource-pack fonts and client-selected Unicode fonts cannot be measured server-side.
        // Keep the previous six-pixel estimate for glyphs outside the bundled bitmap metrics.
        return WIDTHS.getOrDefault(codePoint, 6) + (bold ? 1 : 0);
    }

    private static Map<Integer, Integer> load() {
        Map<Integer, Integer> widths = new HashMap<>();
        try (var stream = DefaultFontWidths.class.getResourceAsStream("/font/default-widths.txt")) {
            if (stream == null) {
                throw new IllegalStateException("Missing default font widths");
            }
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    String[] entry = line.split("=", 2);
                    int width = Integer.parseInt(entry[0]);
                    for (String codePoint : entry[1].split(",")) {
                        widths.put(Integer.parseInt(codePoint, 16), width);
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load default font widths", exception);
        }
        return Map.copyOf(widths);
    }
}
