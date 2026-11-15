package io.github.universalemoji.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a raw chat line into a line that contains PUA glyph references.
 *
 * <p>Two passes, in this order:
 * <ol>
 *   <li>Manual {@code [row.column]} tokens win. They are converted straight to
 *       {@code (row-1)*16 + (column-1) + 0xE000} and never go through the
 *       three-platform check.</li>
 *   <li>Native emoji are matched longest first inside a ZWJ cluster.</li>
 * </ol>
 *
 * <p>U+FE0F is removed before anything else (it is a rendering hint and would
 * otherwise double every key), U+200D is kept so families and professions stay
 * one glyph.
 *
 * <p>A cluster is never split: if the complete cluster is unknown the whole
 * cluster degrades to the "unsupported" text instead of emitting partial glyphs.
 * That is what keeps {@code family} from turning into {@code man + woman + girl}.
 */
public final class EmojiTextProcessor {

    private static final int VS16 = 0xFE0F;
    private static final int ZWJ = 0x200D;

    private static final Pattern MANUAL = Pattern.compile("\\[([0-9]{1,3})\\.([0-9]{1,3})]");

    private final EmojiTable table;
    private final String unsupportedText;

    public EmojiTextProcessor(EmojiTable table, String unsupportedText) {
        this.table = table;
        this.unsupportedText = unsupportedText == null || unsupportedText.isEmpty()
                ? "[unsupported emoji]" : unsupportedText;
    }

    /** Outcome of one message. */
    public static final class Result {
        private final String text;
        private final boolean changed;
        private final int manualGlyphs;
        private final int nativeGlyphs;
        private final int unsupported;

        Result(String text, boolean changed, int manualGlyphs, int nativeGlyphs, int unsupported) {
            this.text = text;
            this.changed = changed;
            this.manualGlyphs = manualGlyphs;
            this.nativeGlyphs = nativeGlyphs;
            this.unsupported = unsupported;
        }

        public String text() {
            return text;
        }

        public boolean changed() {
            return changed;
        }

        public int manualGlyphs() {
            return manualGlyphs;
        }

        public int nativeGlyphs() {
            return nativeGlyphs;
        }

        public int unsupported() {
            return unsupported;
        }

        @Override
        public String toString() {
            return "Result{changed=" + changed + ", manual=" + manualGlyphs
                    + ", native=" + nativeGlyphs + ", unsupported=" + unsupported + '}';
        }
    }

    public Result process(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new Result(raw == null ? "" : raw, false, 0, 0, 0);
        }
        String stripped = stripVariationSelectors(raw);
        ManualPass manual = replaceManualCoordinates(stripped);
        ClusterPass clusters = replaceClusters(manual.text);
        int changed = manual.replaced + clusters.replaced;
        return new Result(clusters.text, changed > 0 || manual.unsupported + clusters.unsupported > 0,
                manual.replaced, clusters.replaced, manual.unsupported + clusters.unsupported);
    }

    // ------------------------------------------------------------------ pass 0

    private static String stripVariationSelectors(String input) {
        StringBuilder out = new StringBuilder(input.length());
        int[] codePoints = input.codePoints().toArray();
        for (int codePoint : codePoints) {
            if (codePoint != VS16) {
                out.appendCodePoint(codePoint);
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ pass 1

    private static final class ManualPass {
        final String text;
        final int replaced;
        final int unsupported;

        ManualPass(String text, int replaced, int unsupported) {
            this.text = text;
            this.replaced = replaced;
            this.unsupported = unsupported;
        }
    }

    private ManualPass replaceManualCoordinates(String text) {
        Matcher matcher = MANUAL.matcher(text);
        StringBuffer buffer = new StringBuffer(text.length());
        int replaced = 0;
        int unsupported = 0;
        while (matcher.find()) {
            int row = Integer.parseInt(matcher.group(1));
            int column = Integer.parseInt(matcher.group(2));
            int index = table.indexForCoordinate(row, column);
            String replacement;
            if (index < 0) {
                replacement = unsupportedText;
                unsupported++;
            } else {
                replacement = String.valueOf(table.glyphChar(index));
                replaced++;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return new ManualPass(buffer.toString(), replaced, unsupported);
    }

    // ------------------------------------------------------------------ pass 2

    private static final class ClusterPass {
        final String text;
        final int replaced;
        final int unsupported;

        ClusterPass(String text, int replaced, int unsupported) {
            this.text = text;
            this.replaced = replaced;
            this.unsupported = unsupported;
        }
    }

    private ClusterPass replaceClusters(String text) {
        int[] codePoints = text.codePoints().toArray();
        StringBuilder out = new StringBuilder(text.length() * 2);
        int replaced = 0;
        int unsupported = 0;
        int i = 0;
        while (i < codePoints.length) {
            int end = clusterEnd(codePoints, i);
            String sequence = hexRange(codePoints, i, end);
            int glyphIndex = table.glyphIndex(sequence);
            if (glyphIndex >= 0) {
                out.append(table.glyphChar(glyphIndex));
                replaced++;
                i = end;
                continue;
            }
            if (looksLikeEmojiCluster(codePoints, i, end)) {
                out.append(unsupportedText);
                unsupported++;
                i = end;
                continue;
            }
            // ordinary text: copy verbatim, never touch it
            for (int k = i; k < end; k++) {
                out.appendCodePoint(codePoints[k]);
            }
            i = end;
        }
        return new ClusterPass(out.toString(), replaced, unsupported);
    }

    /**
     * End (exclusive) of the grapheme cluster starting at {@code start}:
     * base codepoint, then modifiers / keycap / tag chars, then ZWJ repeats.
     */
    private static int clusterEnd(int[] codePoints, int start) {
        int i = start;
        int n = codePoints.length;
        while (true) {
            if (i < n) {
                i++; // base
            }
            while (i < n && isModifier(codePoints[i])) {
                i++;
            }
            if (i < n && codePoints[i] == ZWJ) {
                i++;
                continue;
            }
            return i;
        }
    }

    private static boolean isModifier(int codePoint) {
        if (codePoint >= 0x1F3FB && codePoint <= 0x1F3FF) {
            return true; // skin tones
        }
        if (codePoint == 0x20E3 || codePoint == VS16 || codePoint == 0xFE0E) {
            return true; // keycap, variation selectors
        }
        return codePoint >= 0xE0020 && codePoint <= 0xE007F; // tag sequences (flags)
    }

    private static boolean looksLikeEmojiCluster(int[] codePoints, int start, int end) {
        for (int i = start; i < end; i++) {
            if (isEmojiBase(codePoints[i])) {
                return true;
            }
        }
        return false;
    }

    /** Conservative rough test: does this codepoint belong to an emoji block. */
    private static boolean isEmojiBase(int codePoint) {
        if (codePoint >= 0x1F000 && codePoint <= 0x1FAFF) {
            return true;
        }
        if (codePoint >= 0x2600 && codePoint <= 0x27BF) {
            return true;
        }
        if (codePoint >= 0x2190 && codePoint <= 0x21FF) {
            return true;
        }
        if (codePoint >= 0x2300 && codePoint <= 0x23FF) {
            return true;
        }
        if (codePoint >= 0x2B00 && codePoint <= 0x2BFF) {
            return true;
        }
        if (codePoint >= 0x2900 && codePoint <= 0x297F) {
            return true;
        }
        switch (codePoint) {
            case 0x00A9: case 0x00AE: case 0x203C: case 0x2049: case 0x2122: case 0x2139:
            case 0x24C2: case 0x25AA: case 0x25AB: case 0x25B6: case 0x25C0:
            case 0x25FB: case 0x25FC: case 0x25FD: case 0x25FE:
            case 0x2934: case 0x2935: case 0x3030: case 0x303D: case 0x3297: case 0x3299:
                return true;
            default:
                return false;
        }
    }

    private static String hexRange(int[] codePoints, int from, int to) {
        StringBuilder builder = new StringBuilder((to - from) * 5);
        for (int i = from; i < to; i++) {
            if (i > from) {
                builder.append('-');
            }
            builder.append(hex(codePoints[i]));
        }
        return builder.toString();
    }

    /** Mirrors the builder's "%04x" formatting, lowercase, zero padded. */
    private static String hex(int codePoint) {
        String raw = Integer.toHexString(codePoint);
        StringBuilder builder = new StringBuilder(4);
        for (int i = raw.length(); i < 4; i++) {
            builder.append('0');
        }
        return builder.append(raw).toString();
    }
}
