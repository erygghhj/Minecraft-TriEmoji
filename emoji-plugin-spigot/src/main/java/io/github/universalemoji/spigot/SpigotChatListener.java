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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String plain = event.getMessage();
        EmojiTextProcessor.Result result = processor.process(plain);

        if (!result.changed()) {
            // nothing to rewrite: keep vanilla chat, signature and all
            return;
        }

        event.setCancelled(true);

        final String who = event.getPlayer().getName();
        final String text = result.text();

        // async thread: only strings cross over, config and the broadcast run on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            String format = plugin.getConfig().getString("emoji.broadcast-format", "<%player%> %message%");
            String line = format
                    .replace("%player%", who)
                    .replace("%message%", text);

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(line);
            }
            Bukkit.getConsoleSender().sendMessage(line);
            if (plugin.getConfig().getBoolean("emoji.debug", false)) {
                plugin.getLogger().info(who + " " + result);
            }
        });
    }
}
