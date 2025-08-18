package io.github.mcengine.extension.addon.essential.vault;

import io.github.mcengine.api.core.MCEngineCoreApi;
import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.api.essential.extension.addon.IMCEngineEssentialAddOn;

import io.github.mcengine.extension.addon.essential.vault.VaultCommand;
import io.github.mcengine.extension.addon.essential.vault.VaultListener;
import io.github.mcengine.extension.addon.essential.vault.VaultTabCompleter;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Main class for the Vault extension.
 * <p>
 * Registers the {@code /vault} command and event listeners.
 */
public class Vault implements IMCEngineEssentialAddOn {

    /**
     * Logger instance for the Vault extension.
     */
    private MCEngineExtensionLogger logger;

    /**
     * Initializes the Vault extension.
     * Called automatically by the MCEngine core plugin.
     *
     * @param plugin The Bukkit plugin instance.
     */
    @Override
    public void onLoad(Plugin plugin) {
        logger = new MCEngineExtensionLogger(plugin, "Vault", "EssentialVault");

        try {
            // Register event listener
            PluginManager pluginManager = Bukkit.getPluginManager();
            pluginManager.registerEvents(new VaultListener(plugin), plugin);

            // Reflectively access Bukkit's CommandMap
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            // Define the /vault command
            Command vaultCommand = new Command("vault") {

                /**
                 * Handles command execution for {@code /vault}.
                 */
                private final VaultCommand handler = new VaultCommand();

                /**
                 * Handles tab-completion for {@code /vault}.
                 */
                private final VaultTabCompleter completer = new VaultTabCompleter();

                /**
                 * Executes the {@code /vault} command.
                 *
                 * @param sender The command sender.
                 * @param label  The command label.
                 * @param args   The command arguments.
                 * @return true if successful.
                 */
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return handler.onCommand(sender, this, label, args);
                }

                /**
                 * Handles tab-completion for the {@code /vault} command.
                 *
                 * @param sender The command sender.
                 * @param alias  The alias used.
                 * @param args   The current arguments.
                 * @return A list of possible completions.
                 */
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
