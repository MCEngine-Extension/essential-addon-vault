package io.github.mcengine.extension.addon.essential.vault.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Handles {@code /vault} command logic for the Vault extension.
 */
public class VaultCommand implements CommandExecutor {

    /**
     * Executes the {@code /vault} command.
     *
     * @param sender  The source of the command.
     * @param command The command which was executed.
     * @param label   The alias used.
     * @param args    The command arguments.
     * @return true if command executed successfully.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("§aVault command executed!");
        return true;
    }
}
