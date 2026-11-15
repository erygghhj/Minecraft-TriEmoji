package io.github.universalemoji.plugin.util;

import java.util.Locale;

/**
 * Converts the 40 character SHA-1 string from pack.mcmeta / sha1.txt into the
 * 20 byte array Bukkit expects.
 */
public final class HexHash {

    private HexHash() {
    }

    /** @return the decoded hash, or {@code null} when the input is not a valid SHA-1 */
    public static byte[] decode(String sha1) {
        if (sha1 == null) {
            return null;
        }
        String cleaned = sha1.replace("-", "").replace(" ", "").trim().toLowerCase(Locale.ROOT);
        if (cleaned.length() != 40) {
            return null;
        }
        byte[] out = new byte[20];
        for (int i = 0; i < 20; i++) {
            int high = Character.digit(cleaned.charAt(i * 2), 16);
            int low = Character.digit(cleaned.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }
}
