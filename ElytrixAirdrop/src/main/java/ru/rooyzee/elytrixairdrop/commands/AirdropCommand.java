package ru.rooyzee.elytrixairdrop.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.airdrops.ActiveAirdrop;
import ru.rooyzee.elytrixairdrop.airdrops.Airdrop;
import ru.rooyzee.elytrixairdrop.gui.MainMenu;
import ru.rooyzee.elytrixairdrop.utils.ColorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AirdropCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public AirdropCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("elytrixairdrop.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                sendHelp(sender);
                break;

            case "reload":
                plugin.getConfigManager().reload();
                sender.sendMessage(plugin.getConfigManager().getMessage("reload-success"));
                break;

            case "menu":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("only-player"));
                    return true;
                }
                new MainMenu(plugin).open((Player) sender);
                break;

            case "start":
                if (plugin.getAirdropManager().getActive() != null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("airdrop.already-active"));
                    return true;
                }
                if (!plugin.getAirdropManager().canManualStart()) {
                    sender.sendMessage(ColorUtil.colorize(
                            plugin.getConfigManager().getPrefix() + "&cПодождите перед следующим запуском"));
                    return true;
                }

                if (args.length >= 2) {
                    Airdrop id = Airdrop.fromId(args[1]);
                    if (id == null) {
                        sender.sendMessage(plugin.getConfigManager().getMessage("airdrop.not-found"));
                        return true;
                    }
                    if (plugin.getAirdropManager().start(id)) {
                        String display = ColorUtil.colorize(plugin.getConfigManager().getConfig()
                                .getString("airdrops." + id.getId() + ".display-name", id.getId()));
                        sender.sendMessage(plugin.getConfigManager().getMessage("airdrop.started").replace("{type}", display));
                    } else {
                        sender.sendMessage(plugin.getConfigManager().getMessage("airdrop.spawn-failed"));
                    }
                } else {
                    if (plugin.getAirdropManager().startRandom()) {
                        ActiveAirdrop active = plugin.getAirdropManager().getActive();
                        String typeId = active.getType().getId();
                        String display = ColorUtil.colorize(plugin.getConfigManager().getConfig()
                                .getString("airdrops." + typeId + ".display-name", typeId));
                        sender.sendMessage(plugin.getConfigManager().getMessage("airdrop.started").replace("{type}", display));
                    } else {
                        sender.sendMessage(plugin.getConfigManager().getMessage("airdrop.spawn-failed"));
                    }
                }
                break;

            case "stop":
                if (plugin.getAirdropManager().getActive() == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("airdrop.no-active"));
                    return true;
                }
                plugin.getAirdropManager().stop(false);
                sender.sendMessage(plugin.getConfigManager().getMessage("airdrop.stopped"));
                break;

            case "tp":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("only-player"));
                    return true;
                }
                ActiveAirdrop act = plugin.getAirdropManager().getActive();
                if (act == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("airdrop.no-active"));
                    return true;
                }
                ((Player) sender).teleport(act.getCenter().clone().add(0.5, 2, 0.5));
                sender.sendMessage(plugin.getConfigManager().getMessage("airdrop.tp-success"));
                break;

            case "info":
                ActiveAirdrop ac = plugin.getAirdropManager().getActive();
                if (ac == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("airdrop.no-active"));
                    return true;
                }
                String typeId = ac.getType().getId();
                String display = ColorUtil.colorize(plugin.getConfigManager().getConfig()
                        .getString("airdrops." + typeId + ".display-name", typeId));
                for (String s : plugin.getConfigManager().getMessageList("airdrop.info")) {
                    sender.sendMessage(s
                            .replace("{type}", display)
                            .replace("{x}", String.valueOf(ac.getCenter().getBlockX()))
                            .replace("{y}", String.valueOf(ac.getCenter().getBlockY()))
                            .replace("{z}", String.valueOf(ac.getCenter().getBlockZ()))
                            .replace("{time}", plugin.getAirdropManager().formatTime(ac.getRemainingSeconds())));
                }
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fОнлайн: &#F8BEFB" + Bukkit.getOnlinePlayers().size()));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fНочь (только мирный): &#F8BEFB"
                        + (plugin.getAirdropManager().isNightTime() ? "&aДа" : "&cНет")));
                break;

            default:
                sender.sendMessage(plugin.getConfigManager().getMessage("unknown-command"));
                break;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        for (String s : plugin.getConfigManager().getMessageList("help")) {
            sender.sendMessage(s);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("help", "reload", "menu", "start", "stop", "tp", "info");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            List<String> list = new ArrayList<>();
            for (Airdrop a : Airdrop.values()) list.add(a.getId());
            return list;
        }
        return new ArrayList<>();
    }
}