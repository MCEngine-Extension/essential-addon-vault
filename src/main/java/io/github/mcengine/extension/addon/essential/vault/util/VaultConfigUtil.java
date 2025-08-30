package io.github.mcengine.extension.addon.essential.vault.util;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

/**
 * Utility class for creating and ensuring the main configuration file
 * for the Essential Vault AddOn exists.
 *
 * <p>Creates <code>config.yml</code> under a supplied folder path and
 * sets sane defaults on first run (e.g., {@code license: free}).</p>
 */
public final class VaultConfigUtil {

    /**
     * Hidden constructor; utility class only.
     */
    private VaultConfigUtil() {
        // no-op
    }

    /**
     * Creates the default <code>config.yml</code> for the Vault AddOn if it does not exist.
     *
     * @param plugin     The plugin instance used to resolve the data folder.
     * @param folderPath The folder path relative to the plugin's data directory
     *                   (e.g., "extensions/addons/configs/MCEngineVault").
     * @param logger     Logger for reporting creation outcomes.
     */
    public static void createConfig(Plugin plugin, String folderPath, MCEngineExtensionLogger logger) {
        File configFile = new File(plugin.getDataFolder(), folderPath + "/config.yml");

        if (configFile.exists()) {
            return;
        }

        File configDir = configFile.getParentFile();
        if (!configDir.exists() && !configDir.mkdirs()) {
            if (logger != null) {
                logger.warning("Failed to create Vault config directory: " + configDir.getAbsolutePath());
            }
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        config.options().header("Configuration file for Essential Vault AddOn");

        // Required default: license must be "free"
        config.set("license", "free");

        try {
            config.save(configFile);
            if (logger != null) {
                logger.info("Created default Vault config: " + configFile.getAbsolutePath());
            }
        } catch (IOException e) {
            if (logger != null) {
                logger.warning("Failed to save Vault config: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }
}
