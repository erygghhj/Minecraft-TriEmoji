package io.github.universalemoji.legacy;

import io.github.universalemoji.core.EmojiTextProcessor;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Converts emoji inside sign lines and book pages, next to chat.
 *
 * <p>Only the plain string API is used: it exists on every Spigot version this
 * plugin targets (1.13 - 1.18).
 */
public final class LegacyContentListener implements Listener {

    private final EmojiTextProcessor processor;

    public LegacyContentListener(EmojiTextProcessor processor) {
        this.processor = processor;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        for (int i = 0; i < event.getLines().length; i++) {
            String line = event.getLine(i);
            if (line == null || line.isEmpty()) {
                continue;
            }
            EmojiTextProcessor.Result result = processor.process(line);
            if (result.changed()) {
                event.setLine(i, result.text());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBookEdit(PlayerEditBookEvent event) {
        // a signed book is final: rewriting its pages there is rejected by the server,
        // and the completed draft was already converted one step before signing
        if (event.isSigning()) {
            return;
        }
        BookMeta meta = (BookMeta) event.getNewBookMeta().clone();
        List<String> pages = meta.getPages();
        List<String> converted = new ArrayList<String>(pages.size());
        boolean changed = false;
        for (String page : pages) {
            EmojiTextProcessor.Result result = processor.process(page);
            changed |= result.changed();
            converted.add(result.text());
        }
        if (changed) {
            meta.setPages(converted);
            event.setNewBookMeta(meta);
        }
    }
}
