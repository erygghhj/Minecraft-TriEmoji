package io.github.universalemoji.legacy;

import io.github.universalemoji.core.EmojiTextProcessor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * 1.10 - 1.15 chat hook.
 *
 * <p>There is no chat signature before 1.19, so rewriting the message in place is
 * safe: the normal broadcast still carries the sender name and reaches the sender.
 */
public final class LegacyChatListener implements Listener {

    private final LegacyEmojiPlugin plugin;
    private final EmojiTextProcessor processor;

    public LegacyChatListener(LegacyEmojiPlugin plugin, EmojiTextProcessor processor) {
        this.plugin = plugin;
        this.processor = processor;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        if (message == null || message.isEmpty()) {
            return;
        }
        EmojiTextProcessor.Result result = processor.process(message);
        if (!result.changed()) {
            return;
        }
        event.setMessage(result.text());
        if (plugin.getConfig().getBoolean("emoji.debug", false)) {
            plugin.getLogger().info(event.getPlayer().getName() + " " + result);
        }
    }
}
