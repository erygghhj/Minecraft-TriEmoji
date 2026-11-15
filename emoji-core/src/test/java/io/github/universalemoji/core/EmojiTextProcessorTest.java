package io.github.universalemoji.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EmojiTextProcessorTest {

    private static EmojiTable table;
    private static EmojiTextProcessor processor;

    private static final String IOS = ""
            + "1.1=1f602\n"
            + "1.2=2764\n"
            + "1.3=1f468-200d-1f469-200d-1f467\n"
            + "2.1=1f44d\n";

    private static final String ANDROID = IOS;

    /** windows deliberately misses 2.1 (thumbs up) so the intersection drops it. */
    private static final String WINDOWS = ""
            + "1.1=1f602\n"
            + "1.2=2764\n"
            + "1.3=1f468-200d-1f469-200d-1f467\n";

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @BeforeAll
    static void setUp() throws IOException {
        table = EmojiTable.load(16, stream(IOS), stream(ANDROID), stream(WINDOWS));
        processor = new EmojiTextProcessor(table, "[unsupported]");
    }

    @Test
    void singleCodepointBecomesPuaGlyph() {
        EmojiTextProcessor.Result result = processor.process("\uD83D\uDE02");
        assertEquals(String.valueOf((char) 0xE000), result.text());
        assertEquals(1, result.nativeGlyphs());
        assertTrue(result.changed());
    }

    @Test
    void manualCoordinateUsesRowMajorMath() {
        EmojiTextProcessor.Result result = processor.process("[1.3]");
        assertEquals(String.valueOf((char) 0xE002), result.text());
        assertEquals(1, result.manualGlyphs());
    }

    @Test
    void manualCoordinateMayPointAtEmojiOtherPlatformsLack() {
        // 1f44d lives only on ios/android, but manual coordinates skip the check
        assertEquals(String.valueOf((char) 0xE010), processor.process("[2.1]").text());
    }

    @Test
    void outOfRangeManualCoordinateDegrades() {
        assertEquals("[unsupported]", processor.process("[9.9]").text());
    }

    @Test
    void zwjFamilyIsReplacedAsOneGlyph() {
        String family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67";
        EmojiTextProcessor.Result result = processor.process(family);
        assertEquals(String.valueOf((char) 0xE002), result.text());
        assertEquals(1, result.nativeGlyphs());
    }

    @Test
    void familyThatIsMissingNeverSplits() {
        // man alone is a known glyph, the family is not: the cluster must not split
        String unknownFamily = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC66";
        EmojiTextProcessor.Result result = processor.process(unknownFamily);
        assertEquals("[unsupported]", result.text());
        assertEquals(1, result.unsupported());
    }

    @Test
    void variationSelectorIsStrippedBeforeMatching() {
        String heartWithVs16 = "\u2764\uFE0F";
        assertEquals(String.valueOf((char) 0xE001), processor.process(heartWithVs16).text());
    }

    @Test
    void triplePlatformMissDegrades() {
        String thumbsUp = "\uD83D\uDC4D"; // only two platforms have it
        EmojiTextProcessor.Result result = processor.process(thumbsUp);
        assertEquals("[unsupported]", result.text());
        assertEquals(1, result.unsupported());
    }

    @Test
    void plainTextIsUntouched() {
        String text = "hello 世界 123 !?";
        EmojiTextProcessor.Result result = processor.process(text);
        assertEquals(text, result.text());
        assertFalse(result.changed());
    }

    @Test
    void mixedLineKeepsOrder() {
        String input = "hi \uD83D\uDE02 [1.3] 世界";
        assertEquals("hi " + (char) 0xE000 + " " + (char) 0xE002 + " 世界",
                processor.process(input).text());
    }

    @Test
    void puaBlockIsNeverExceeded() {
        assertEquals((char) 0xF8FF, table.glyphChar(EmojiTable.PUA_CAPACITY - 1));
        try {
            table.glyphChar(EmojiTable.PUA_CAPACITY);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
