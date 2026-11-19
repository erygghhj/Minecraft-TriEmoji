package io.github.universalemoji.spigot;

import io.github.universalemoji.core.EmojiTable;
import io.github.universalemoji.core.EmojiTextProcessor;
import java.io.IOException;
import java.io.InputStream;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for Spigot 1.19 - 26.3.
 *
 * <p>This build deliberately touches nothing but {@code org.bukkit}: no Paper
 * events, no Adventure components. That is what lets the very same jar run on a
 * plain Spigot server, where {@code io.papermc.paper.*} and {@code net.kyori.*}
 * simply do not exist and would fail to link.
 *
 * <p>The three platform tables are read through {@code getResource}, which reads
 * from inside the jar. A path based {@code new File(...)} would throw once the
 * plugin is packaged.
 */
public final class SpigotEmojiPlugin extends JavaPlugin {

    private EmojiTable table;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        int columns = getConfig().getInt("emoji.columns", EmojiTable.DEFAULT_COLUMNS);
        String unsupportedText = getConfig().getString("emoji.unsupported-text", "[unsupported emoji]");

        try (InputStream ios = getResource("ios.txt");
             InputStream android = getResource("android.txt");
             InputStream windows = getResource("windows.txt")) {

            table = EmojiTable.load(columns, ios, android, windows);
        } catch (IOException | IllegalArgumentException exception) {
            getLogger().severe("Could not load the emoji tables: " + exception.getMessage());
            getLogger().severe("ios.txt / android.txt / windows.txt must be in src/main/resources.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        EmojiTextProcessor processor = new EmojiTextProcessor(table, unsupportedText);

        getServer().getPluginManager().registerEvents(new SpigotChatListener(this, processor), this);
        getServer().getPluginManager().registerEvents(new SpigotContentListener(processor), this);
        SpigotPackPusher packPusher = new SpigotPackPusher(this);
        getServer().getPluginManager().registerEvents(packPusher, this);
        packPusher.pushAll();

        getLogger().info("Universal Emoji (Spigot build) ready: " + table.size()
                + " sequences supported on iOS + Android + Windows, "
                + "PUA range U+E000..U+" + String.format("%04X", EmojiTable.PUA_START + Math.max(table.maxSafeIndex(), 0)));
    }

    @Override
    public void onDisable() {
        table = null;
    }

    /** Exposed for /commands or health checks. */
    public EmojiTable table() {
        return table;
    }
}
