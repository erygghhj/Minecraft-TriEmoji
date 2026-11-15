package io.github.universalemoji.plugin;

import io.github.universalemoji.core.EmojiTextProcessor;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Replaces emoji inside chat and re-broadcasts the rewritten line.
 *
 * <p>Why cancel and re-send instead of mutating the message in place:
 * since 1.19 every player chat line carries a Mojang signature. A component we
 * rewrite no longer matches that signature, so the client shows a red
 * "unsigned / modified message" warning. Cancelling the event and sending a
 * plain server message sidesteps the signature check completely, and because the
 * broadcast goes through {@code Server#sendMessage} (a ForwardingAudience) the
 * sender sees their own rewritten line as well.
 */
public final class ChatListener implements Listener {

    private final UniversalEmojiPlugin plugin;
    private final EmojiTextProcessor processor;

    public ChatListener(UniversalEmojiPlugin plugin, EmojiTextProcessor processor) {
        this.plugin = plugin;
        this.processor = processor;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        // the raw typed text, styles dropped, PUA and emoji code points preserved
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        EmojiTextProcessor.Result result = processor.process(plain);

        if (!result.changed()) {
            // nothing to rewrite: let vanilla (still signed) chat continue untouched
            return;
        }

        event.setCancelled(true);

        String format = plugin.getConfig().getString("emoji.broadcast-format", "<%player%> %message%");
        String line = format
                .replace("%player%", event.getPlayer().getName())
                .replace("%message%", result.text());

        Component out = Component.text(line, NamedTextColor.WHITE);
        Bukkit.getServer().sendMessage(out);

        if (plugin.getConfig().getBoolean("emoji.debug", false)) {
            plugin.getLogger().info(event.getPlayer().getName() + " " + result);
        }
    }
}
