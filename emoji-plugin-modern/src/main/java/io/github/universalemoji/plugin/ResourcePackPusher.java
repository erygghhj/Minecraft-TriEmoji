package io.github.universalemoji.plugin;

import io.github.universalemoji.plugin.util.HexHash;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Sends the resource pack reference on join.
 *
 * <p>Only the URL and the SHA-1 are used. The zip is never read from disk and
 * never streamed by the server: the client downloads it over HTTP. A local
 * {@code new File("Universal_Emoji.zip")} would be invisible to the client.
 *
 * <p>Signature fallbacks keep the same jar usable on 1.16.5 - 1.18.2 servers
 * where {@code setResourcePack} has fewer overloads.
 */
public final class ResourcePackPusher implements Listener {

    private final UniversalEmojiPlugin plugin;
    private boolean warned;

    public ResourcePackPusher(UniversalEmojiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        push(event.getPlayer());
    }

    private void push(Player player) {
        String url = plugin.getConfig().getString("resourcepack.url", "");
        String sha1 = plugin.getConfig().getString("resourcepack.sha1", "");
        String prompt = plugin.getConfig().getString("resourcepack.prompt", "");
        boolean force = plugin.getConfig().getBoolean("resourcepack.force", true);

        if (url == null || url.isBlank() || url.startsWith("https://example.com")) {
            warnOnce("[UniversalEmoji] resourcepack.url is not configured - skipping the pack push.");
            return;
        }

        byte[] hash = HexHash.decode(sha1);
        if (hash == null) {
            warnOnce("[UniversalEmoji] resourcepack.sha1 must be a 40 character hex SHA-1 - skipping the pack push.");
            return;
        }

        try {
            player.setResourcePack(url, hash, Component.text(prompt), force);
        } catch (NoSuchMethodError noComponentPrompt) {
            try {
                player.setResourcePack(url, hash, prompt, force);
            } catch (NoSuchMethodError noStringPrompt) {
                try {
                    player.setResourcePack(url, hash, force);
                } catch (NoSuchMethodError noForceFlag) {
                    player.setResourcePack(url, hash);
                }
            }
        }
    }

    private void warnOnce(String message) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning(message);
    }
}
