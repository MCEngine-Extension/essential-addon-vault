package io.github.mcengine.extension.addon.essential.vault;

import io.github.mcengine.api.core.MCEngineCoreApi;
import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.api.essential.extension.addon.IMCEngineEssentialAddOn;
import io.github.mcengine.common.essential.MCEngineEssentialCommon;
import io.github.mcengine.extension.addon.essential.vault.command.VaultCommand;
import io.github.mcengine.extension.addon.essential.vault.listener.VaultListener;
import io.github.mcengine.extension.addon.essential.vault.tabcompleter.VaultTabCompleter;
import io.github.mcengine.extension.addon.essential.vault.util.VaultConfigUtil;
import io.github.mcengine.extension.addon.essential.vault.util.db.VaultDB;
import io.github.mcengine.extension.addon.essential.vault.util.db.VaultDBMySQL;
import io.github.mcengine.extension.addon.essential.vault.util.db.VaultDBPostgreSQL;
import io.github.mcengine.extension.addon.essential.vault.util.db.VaultDBSQLite;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.List;

/**
 * Main class for the Vault extension.
 * <p>
 * Creates a default config (with {@code license: free}) if missing, validates the license,
 * wires a database accessor based on {@code database.type}, and registers the
 * {@code /vault} command and event listeners.
 */
public class Vault implements IMCEngineEssentialAddOn {

    /**
     * Logger instance for the Vault extension.
     * <p>
     * Used for initialization messages and error reporting.
     */
    private MCEngineExtensionLogger logger;

    /**
     * Database accessor used by commands and listeners.
     */
    private VaultDB vaultDB;

    /**
     * Configuration folder path for the Vault AddOn.
     * Used as the base for {@code config.yml}.
     */
    private final String folderPath = "extensions/addons/configs/MCEngineVault";

    /**
     * Initializes the Vault extension.
     * Called automatically by the MCEngine core plugin.
     *
     * @param plugin The Bukkit plugin instance.
     */
    @Override
    public void onLoad(Plugin plugin) {
        logger = new MCEngineExtensionLogger(plugin, "AddOn", "EssentialVault");

        try {
            // Ensure config.yml exists (with license: free)
            VaultConfigUtil.createConfig(plugin, folderPath, logger);

            // Load and validate license
            File configFile = new File(plugin.getDataFolder(), folderPath + "/config.yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String licenseType = config.getString("license", "free");
            if (!"free".equalsIgnoreCase(licenseType)) {
                logger.warning("License is not 'free'. Disabling Essential Vault AddOn.");
                return;
            }

            // Pick DB implementation from main config: database.type = sqlite|mysql|postgresql
            Connection conn = MCEngineEssentialCommon.getApi().getDBConnection();
            String dbType;
            try {
                dbType = plugin.getConfig().getString("database.type", "sqlite");
            } catch (Throwable t) {
                dbType = "sqlite";
            }
            switch (dbType == null ? "sqlite" : dbType.toLowerCase()) {
                case "mysql" -> vaultDB = new VaultDBMySQL(conn, logger);
                case "postgresql", "postgres" -> vaultDB = new VaultDBPostgreSQL(conn, logger);
                case "sqlite" -> vaultDB = new VaultDBSQLite(conn, logger);
                default -> {
                    logger.warning("Unknown database.type='" + dbType + "', defaulting to SQLite for Vault.");
                    vaultDB = new VaultDBSQLite(conn, logger);
                }
            }

            // Ensure DB schema for the vault is present before usage.
            vaultDB.ensureSchema();

            // Register event listener
            PluginManager pluginManager = Bukkit.getPluginManager();
            pluginManager.registerEvents(new VaultListener(plugin, logger, vaultDB), plugin);

            // Reflectively access Bukkit's CommandMap
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            // Define the /vault command
            Command vaultCommand = new Command("vault") {

                /** Handles command execution for {@code /vault}. */
                private final VaultCommand handler = new VaultCommand(vaultDB);

                /** Handles tab-completion for {@code /vault}. */
                private final VaultTabCompleter completer = new VaultTabCompleter();

                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return handler.onCommand(sender, this, label, args);
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    return completer.onTabComplete(sender, this, alias, args);
                }
            };

            vaultCommand.setDescription("Vault command for the essential add-on.");
            vaultCommand.setUsage("/vault");

            // Dynamically register the /vault command
            commandMap.register(plugin.getName().toLowerCase(), vaultCommand);

            logger.info("Enabled successfully.");
        } catch (Exception e) {
            logger.warning("Failed to initialize Vault: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisload(Plugin plugin) {
        // No specific unload logic
    }

    @Override
    public void setId(String id) {
        MCEngineCoreApi.setId("mcengine-essential-addon-vault");
    }
}
