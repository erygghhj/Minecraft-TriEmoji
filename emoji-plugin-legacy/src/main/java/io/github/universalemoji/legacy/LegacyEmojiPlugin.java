package io.github.universalemoji.legacy;

import io.github.universalemoji.core.EmojiTable;
import io.github.universalemoji.core.EmojiTextProcessor;
import java.io.IOException;
import java.io.InputStream;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for Spigot / Paper 1.10 - 1.15.
 *
 * <p>Same core logic as the modern build, but chat arrives as a plain
 * {@link org.bukkit.event.player.AsyncPlayerChatEvent} string and there is no
 * signed-chat machinery to bypass.
 */
public final class LegacyEmojiPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        int columns = getConfig().getInt("emoji.columns", EmojiTable.DEFAULT_COLUMNS);
        String unsupportedText = getConfig().getString("emoji.unsupported-text", "[unsupported emoji]");

        EmojiTable table;
        try (InputStream ios = getResource("ios.txt");
             InputStream android = getResource("android.txt");
             InputStream windows = getResource("windows.txt")) {
            table = EmojiTable.load(columns, ios, android, windows);
        } catch (IOException | IllegalArgumentException exception) {
            getLogger().severe("Could not load the emoji tables: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        EmojiTextProcessor processor = new EmojiTextProcessor(table, unsupportedText);
        getServer().getPluginManager().registerEvents(new LegacyChatListener(this, processor), this);
        getServer().getPluginManager().registerEvents(new LegacyContentListener(processor), this);
        LegacyPackPusher packPusher = new LegacyPackPusher(this);
        getServer().getPluginManager().registerEvents(packPusher, this);
        packPusher.pushAll();

        getLogger().info("Universal Emoji (legacy) ready: " + table.size() + " sequences");
    }
}
