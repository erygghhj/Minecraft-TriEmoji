package io.github.universalemoji.plugin;

import io.github.universalemoji.core.EmojiTable;
import io.github.universalemoji.core.EmojiTextProcessor;
import java.io.IOException;
import java.io.InputStream;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for Paper 1.19.4 - 1.21.x.
 *
 * <p>The three platform tables are read through {@code getResourceAsStream}. They are
 * bundled in {@code src/main/resources} and land inside the jar, so a path based
 * {@code new File(...)} would throw once the plugin is packaged.
 */
public final class UniversalEmojiPlugin extends JavaPlugin {

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

        getServer().getPluginManager().registerEvents(new ChatListener(this, processor), this);
        getServer().getPluginManager().registerEvents(new ResourcePackPusher(this), this);

        getLogger().info("Universal Emoji ready: " + table.size()
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
