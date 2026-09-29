package ru.rooyzee.elytrixparadise.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParadiseCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public ParadiseCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload":
                if (!sender.hasPermission("elytrixparadise.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                plugin.getConfigManager().reload();
                plugin.getSchematicManager().invalidateCache();
                sender.sendMessage(plugin.getConfigManager().getMessage("reload-success",
                        "%prefix%&aКонфигурация и сообщения успешно перезагружены!"));
                break;

            case "paste":
                if (!sender.hasPermission("elytrixparadise.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                Location center = plugin.getConfigManager().getCenterLocation();
                String schemName = plugin.getConfigManager().getSchematicFile();
                sender.sendMessage(plugin.getConfigManager().getMessage("schematic.pasting",
                        "%prefix%&7Вставка схематики &#F8BEFB" + schemName + " &7на координаты &f"
                                + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ() + "..."));

                boolean success = plugin.getSchematicManager().pasteSchematic(schemName, center);
                if (success) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("schematic.paste-success",
                            "%prefix%&aСхематика успешно вставлена на координаты 0, " + center.getBlockY() + ", 0!"));
                } else {
                    sender.sendMessage(plugin.getConfigManager().getMessage("schematic.paste-failed",
                            "%prefix%&cНе удалось вставить схематику. Проверьте консоль сервера!"));
                }
                break;

            case "tp":
            case "teleport":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("only-player"));
                    return true;
                }
                if (!sender.hasPermission("elytrixparadise.tp") && !sender.hasPermission("elytrixparadise.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                Player player = (Player) sender;
                Location spawn = plugin.getConfigManager().getSafeSpawnLocation();
                player.teleport(spawn);
                player.sendMessage(plugin.getConfigManager().getMessage("teleport-success",
                        "%prefix%&aВы успешно телепортированы в Рай!"));
                break;

            case "status":
            case "info":
                Location c = plugin.getConfigManager().getCenterLocation();
                List<String> infoLines = plugin.getConfigManager().getMessageList("info");
                if (infoLines != null && !infoLines.isEmpty()) {
                    for (String line : infoLines) {
                        sender.sendMessage(line
                                .replace("{x}", String.valueOf(c.getBlockX()))
                                .replace("{y}", String.valueOf(c.getBlockY()))
                                .replace("{z}", String.valueOf(c.getBlockZ()))
                                .replace("{schem}", plugin.getConfigManager().getSchematicFile())
                                .replace("{world}", c.getWorld() != null ? c.getWorld().getName() : "world")
                        );
                    }
                } else {
                    sender.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &#FFFFA0Рай &7» &#F8BEFBИнформация"));
                    sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fКоординаты: &#FFFFA0" + c.getBlockX() + ", " + c.getBlockY() + ", " + c.getBlockZ()));
                    sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fМир: &#FFFFA0" + (c.getWorld() != null ? c.getWorld().getName() : "world")));
                    sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fСхематика: &#F8BEFB" + plugin.getConfigManager().getSchematicFile()));
                }
                break;

            case "setcenter":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("only-player"));
                    return true;
                }
                if (!sender.hasPermission("elytrixparadise.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                Player pCenter = (Player) sender;
                plugin.getConfigManager().setCenterLocation(pCenter.getLocation());
                pCenter.sendMessage(plugin.getConfigManager().getMessage("center-updated",
                        "%prefix%&aЦентр Рая установлен на вашу текущую позицию: "
                                + pCenter.getLocation().getBlockX() + ", " + pCenter.getLocation().getBlockY() + ", " + pCenter.getLocation().getBlockZ()));
                break;

            case "setspawn":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("only-player"));
                    return true;
                }
                if (!sender.hasPermission("elytrixparadise.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                Player pSpawn = (Player) sender;
                plugin.getConfigManager().setSpawnLocation(pSpawn.getLocation());
                pSpawn.sendMessage(plugin.getConfigManager().getMessage("spawn-updated",
                        "%prefix%&aТочка спавна игроков в Раю установлена на вашу текущую позицию!"));
                break;

            case "schem":
            case "schematic":
                File schemDir = plugin.getSchematicManager().getSchematicsFolder();
                String targetFile = plugin.getConfigManager().getSchematicFile();
                File f = plugin.getSchematicManager().findSchematicFile(targetFile);

                sender.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &#FFFFA0Рай &7» &#F8BEFBСхематика"));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fПапка: &e" + schemDir.getPath()));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fФайл из конфига: &b" + targetFile));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fСтатус: " + (f != null && f.exists() ? "&aНайден (" + f.getName() + ", " + f.length() + " байт)" : "&cНе найден (положите файл в папку)")));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fКоманда для вставки: &e/ep paste"));
                break;

            default:
                sender.sendMessage(plugin.getConfigManager().getMessage("unknown-command",
                        "%prefix%&cНеизвестная команда. Введите &f/ep help &cдля списка команд."));
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        List<String> helpList = plugin.getConfigManager().getMessageList("help");
        if (helpList != null && !helpList.isEmpty()) {
            for (String s : helpList) {
                sender.sendMessage(s);
            }
        } else {
            sender.sendMessage(ColorUtil.colorize(""));
            sender.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &#FFFFA0Рай &7» &#F8BEFBКоманды"));
            sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/ep help &8— &#F8BEFBсписок доступных команд"));
            sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/ep tp &8— &#F8BEFBтелепортироваться в Рай"));
            sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/ep info &8— &#F8BEFBинформация"));
            sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/ep schem &8— &#F8BEFBкуда класть схематику"));
            if (sender.hasPermission("elytrixparadise.admin")) {
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/ep reload &8— &#F8BEFBперезагрузка конфигурации"));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/ep paste &8— &#F8BEFBпринудительно вставить схематику"));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/ep setcenter &8— &#F8BEFBустановить центр Рая"));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/ep setspawn &8— &#F8BEFBустановить точку спавна"));
            }
            sender.sendMessage(ColorUtil.colorize(""));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("help");
            list.add("tp");
            list.add("info");
            list.add("schem");
            if (sender.hasPermission("elytrixparadise.admin")) {
                list.add("reload");
                list.add("paste");
                list.add("setcenter");
                list.add("setspawn");
            }
            List<String> filtered = new ArrayList<>();
            for (String s : list) {
                if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                    filtered.add(s);
                }
            }
            return filtered;
        }

        return Collections.emptyList();
    }
}
