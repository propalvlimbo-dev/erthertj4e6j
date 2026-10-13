package ru.rooyzee.elytrixschalkerpvp.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SchalkerTabCompleter implements TabCompleter {

    private final List<String> commands = Arrays.asList("create", "remove", "edit", "reload", "spawn");
    private final List<String> rarities = Arrays.asList("common", "rare", "mythical", "legendary");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("elytrixschalker.admin")) return new ArrayList<>();
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String cmd : commands) {
                if (cmd.startsWith(partial)) completions.add(cmd);
            }
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            if (subCmd.equals("edit") || subCmd.equals("spawn")) {
                String partial = args[1].toLowerCase();
                for (String r : rarities) {
                    if (r.startsWith(partial)) completions.add(r);
                }
            }
        }

        return completions;
    }
}