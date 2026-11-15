package io.github.universalemoji.legacy;

import java.util.Locale;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 1.10 - 1.15 resource pack push.
 *
 * <p>Old servers only expose {@code setResourcePack(String url, byte[] hash)}; the
 * prompt / force flags were added later. Only the URL and SHA-1 are sent, the zip
 * itself is hosted over HTTP.
 */
public final class LegacyPackPusher implements Listener {

    private final LegacyEmojiPlugin plugin;
    private boolean warned;

    public LegacyPackPusher(LegacyEmojiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        push(event.getPlayer());
    }

    private void push(Player player) {
        String url = plugin.getConfig().getString("resourcepack.url", "");
        String sha1 = plugin.getConfig().getString("resourcepack.sha1", "");

        if (url == null || url.trim().isEmpty() || url.startsWith("https://example.com")) {
            warnOnce("[UniversalEmoji] resourcepack.url is not configured - skipping the pack push.");
            return;
        }

        byte[] hash = decodeSha1(sha1);
        if (hash == null) {
            warnOnce("[UniversalEmoji] resourcepack.sha1 must be a 40 character hex SHA-1 - skipping the pack push.");
            return;
        }

        player.setResourcePack(url, hash);
    }

    private static byte[] decodeSha1(String sha1) {
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

    private void warnOnce(String message) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning(message);
    }
}
