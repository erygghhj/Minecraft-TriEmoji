package io.github.universalemoji.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable glyph table shared by every platform build.
 *
 * <p>Input format is produced by tools/build_unified.py:
 * {@code row.column=codepoint-sequence} with 1-based row/column and a 16 column grid.
 * A single emoji occupies exactly one PUA codepoint, so the glyph index is
 * {@code (row - 1) * columns + (column - 1)} and the codepoint is
 * {@code 0xE000 + index}.
 *
 * <p>The table keeps only sequences that all three platform files contain.
 * If one platform is missing an emoji the whole sequence is rejected: a partial
 * replacement would break ZWJ families (they must stay one indivisible glyph).
 */
public final class EmojiTable {

    /** Must match the builder's --cols value. */
    public static final int DEFAULT_COLUMNS = 16;
    public static final int PUA_START = 0xE000;
    public static final int PUA_END = 0xF8FF;
    /** 6400 slots available in the BMP private use area. */
    public static final int PUA_CAPACITY = PUA_END - PUA_START + 1;

    private final Map<String, Integer> supportedGlyphs;
    private final int columns;
    private final int maxSafeIndex;

    private EmojiTable(Map<String, Integer> supportedGlyphs, int columns, int maxSafeIndex) {
        this.supportedGlyphs = supportedGlyphs;
        this.columns = columns;
        this.maxSafeIndex = maxSafeIndex;
    }

    /**
     * Reads the three platform tables and intersects them.
     *
     * <p>Always feed these streams from the plugin jar
     * ({@code getResourceAsStream("ios.txt")}); a {@code new File(...)} path fails
     * once the plugin is packaged.
     */
    public static EmojiTable load(int columns, InputStream ios, InputStream android, InputStream windows)
            throws IOException {
        Map<String, Integer> iosMap = read(columns, ios, "ios.txt");
        Map<String, Integer> androidMap = read(columns, android, "android.txt");
        Map<String, Integer> windowsMap = read(columns, windows, "windows.txt");

        Map<String, Integer> all = new HashMap<>(Math.min(iosMap.size(), Math.min(androidMap.size(), windowsMap.size())));
        int maxIndex = -1;
        for (Integer index : iosMap.values()) {
            maxIndex = Math.max(maxIndex, index);
        }
        for (Integer index : androidMap.values()) {
            maxIndex = Math.max(maxIndex, index);
        }
        for (Integer index : windowsMap.values()) {
            maxIndex = Math.max(maxIndex, index);
        }

        for (Map.Entry<String, Integer> entry : iosMap.entrySet()) {
            Integer androidIndex = androidMap.get(entry.getKey());
            Integer windowsIndex = windowsMap.get(entry.getKey());
            // identical coordinates on all three sides is guaranteed by the builder,
            // but never trust an edited txt blindly
            if (androidIndex != null && windowsIndex != null
                    && androidIndex.equals(entry.getValue()) && windowsIndex.equals(entry.getValue())) {
                all.put(entry.getKey(), entry.getValue());
            }
        }

        return new EmojiTable(Collections.unmodifiableMap(all), columns, maxIndex);
    }

    private static Map<String, Integer> read(int columns, InputStream stream, String name) throws IOException {
        if (stream == null) {
            throw new IOException("Missing resource: " + name + " (it must live in src/main/resources)");
        }
        Map<String, Integer> out = new HashMap<>(8192);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int equals = line.indexOf('=');
                int dot = line.indexOf('.');
                if (equals <= 0 || dot <= 0) {
                    throw new IllegalArgumentException(name + ":" + lineNumber + " malformed line: " + line);
                }
                int row = Integer.parseInt(line.substring(0, dot).trim());
                int column = Integer.parseInt(line.substring(dot + 1, equals).trim());
                String sequence = line.substring(equals + 1).trim().toLowerCase(Locale.ROOT);
                if (row < 1 || column < 1 || column > columns) {
                    throw new IllegalArgumentException(name + ":" + lineNumber + " coordinate out of grid: " + line);
                }
                int index = (row - 1) * columns + (column - 1);
                if (index >= PUA_CAPACITY) {
                    throw new IllegalArgumentException(name + ":" + lineNumber + " PUA overflow at index " + index);
                }
                out.put(sequence, index);
            }
        }
        return out;
    }

    /** @return glyph index or -1 when the sequence is not supported everywhere. */
    public int glyphIndex(String sequence) {
        Integer index = supportedGlyphs.get(sequence);
        return index == null ? -1 : index;
    }

    public boolean isSupported(String sequence) {
        return supportedGlyphs.containsKey(sequence);
    }

    public int columns() {
        return columns;
    }

    /** Highest glyph index that maps to a real emoji (padding cells are excluded). */
    public int maxSafeIndex() {
        return maxSafeIndex;
    }

    public int size() {
        return supportedGlyphs.size();
    }

    /**
     * Converts a glyph index into the private use character.
     *
     * @throws IllegalArgumentException when the index would leave the BMP PUA block
     */
    public char glyphChar(int index) {
        if (index < 0 || index >= PUA_CAPACITY) {
            throw new IllegalArgumentException("PUA index out of range: " + index);
        }
        return (char) (PUA_START + index);
    }

    /** Manual {@code [row.column]} syntax -> glyph index, or -1 when out of range. */
    public int indexForCoordinate(int row, int column) {
        if (row < 1 || column < 1 || column > columns) {
            return -1;
        }
        int index = (row - 1) * columns + (column - 1);
        if (index > maxSafeIndex) {
            return -1;
        }
        return index;
    }
}
