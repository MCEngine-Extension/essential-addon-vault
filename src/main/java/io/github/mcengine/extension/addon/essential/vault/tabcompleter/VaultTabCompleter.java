package io.github.mcengine.extension.addon.essential.vault.tabcompleter;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tab completer for the {@code /vault} command.
 *
 * <p>Provides simple suggestions. The {@code setrows} and {@code settitle}
 * subcommands have been removed.</p>
 */
public class VaultTabCompleter implements TabCompleter {

    /**
     * Provides tab-completion for the {@code /vault} command.
     *
     * @param sender  The command sender.
     * @param command The command object.
     * @param alias   The alias used.
     * @param args    The command arguments.
     * @return A list of completion strings.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Keep your original extras and include "open"; removed setrows/settitle.
            List<String> base = Arrays.asList("open");
            final String prefix = args[0].toLowerCase();
            return base.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        return Collections.emptyList();
        }
}
