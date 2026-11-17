package io.github.universalemoji.spigot;

import io.github.universalemoji.core.EmojiTextProcessor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Replaces emoji inside chat and re-broadcasts the rewritten line.
 *
 * <p>{@code AsyncPlayerChatEvent} is the only chat event plain Spigot still
 * fires on 1.19+; Paper's {@code AsyncChatEvent} does not exist there. Because
 * the event is asynchronous, nothing is touched on the calling thread except
 * the immutable strings built from it - the actual broadcast is handed back to
 * the main thread through the scheduler.
 *
 * <p>Cancel-and-resend also sidesteps the 1.19+ signature system: a component we
 * rewrite no longer matches its Mojang signature, so an in-place rewrite would
 * make every client show a "modified message" warning. A server broadcast
 * carries no signature at all.
 */
public final class SpigotChatListener implements Listener {

    private final SpigotEmojiPlugin plugin;
    private final EmojiTextProcessor processor;

    public SpigotChatListener(SpigotEmojiPlugin plugin, EmojiTextProcessor processor) {
        this.plugin = plugin;
        this.processor = processor;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        String plain = event.getMessage();
        EmojiTextProcessor.Result result = processor.process(plain);

        if (!result.changed()) {
            // nothing to rewrite: keep vanilla chat, signature and all
            return;
        }

        event.setCancelled(true);

        String format = plugin.getConfig().getString("emoji.broadcast-format", "<%player%> %message%");
        final String line = format
                .replace("%player%", event.getPlayer().getName())
                .replace("%message%", result.text());

        final boolean debug = plugin.getConfig().getBoolean("emoji.debug", false);
        final String who = event.getPlayer().getName();

        // async thread: only strings cross over, everything else runs on main
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(line);
            }
            Bukkit.getConsoleSender().sendMessage(line);
            if (debug) {
                plugin.getLogger().info(who + " " + result);
            }
        });
    }
}
