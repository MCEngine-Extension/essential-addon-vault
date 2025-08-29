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
            // Preserve your original suggestions and add vault-related ones.
            List<String> base = Arrays.asList("open", "setrows", "settitle");
            final String prefix = args[0].toLowerCase();
            return base.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && "setrows".equalsIgnoreCase(args[0])) {
            return Arrays.asList("1", "2", "3", "4", "5", "6")
                    .stream()
                    .filter(s -> s.startsWith(args[1]))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
