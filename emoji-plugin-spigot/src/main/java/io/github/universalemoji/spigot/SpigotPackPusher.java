package io.github.universalemoji.spigot;

import io.github.universalemoji.spigot.util.HexHash;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
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
 * <p>Overloads are looked up reflectively, newest first. {@code setResourcePack}
 * gained its {@code boolean force} argument in 1.17 and its {@code String prompt}
 * argument later on, so a compile-time call would either fail to build against a
 * low API or throw {@code NoSuchMethodError} on an old server. Reflection keeps
 * one jar working from 1.17 through 26.3 without any hard link.
 */
public final class SpigotPackPusher implements Listener {

    private final SpigotEmojiPlugin plugin;
    private boolean warned;

    public SpigotPackPusher(SpigotEmojiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        push(event.getPlayer());
    }

    /**
     * Re-sends the pack to players that are already online: without this a
     * {@code /reload} (plugin re-enabled) leaves them without the pack until
     * they reconnect.
     */
    public void pushAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            push(player);
        }
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

        if (!pushPack(player, url, hash, prompt, force)) {
            warnOnce("[UniversalEmoji] no usable Player#setResourcePack overload was found on this server.");
        }
    }

    private boolean pushPack(Player player, String url, byte[] hash, String prompt, boolean force) {
        if (invoke(player, new Class<?>[]{String.class, byte[].class, String.class, boolean.class},
                new Object[]{url, hash, prompt, force})) {
            return true;
        }
        if (invoke(player, new Class<?>[]{String.class, byte[].class, boolean.class},
                new Object[]{url, hash, force})) {
            return true;
        }
        return invoke(player, new Class<?>[]{String.class, byte[].class}, new Object[]{url, hash});
    }

    private static boolean invoke(Player player, Class<?>[] types, Object[] args) {
        try {
            Method method = Player.class.getMethod("setResourcePack", types);
            method.invoke(player, args);
            return true;
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return false;
        } catch (InvocationTargetException target) {
            Throwable cause = target.getCause();
            if (cause != null) {
                cause.printStackTrace();
            }
            return false;
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
