package ru.rooyzee.elytrixparadise.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.event.ParadiseEvent;
import ru.rooyzee.elytrixparadise.event.ParadisePhase;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
                plugin.getRegionManager().createOrUpdateRegion();
                if (plugin.getEventManager().getEvent() != null) {
                    plugin.getHologramManager().createOrUpdateHologram(plugin.getEventManager().getEvent());
                }
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

            case "start":
                if (!sender.hasPermission("elytrixparadise.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                ParadiseEvent event = plugin.getEventManager().getEvent();
                if (event == null) {
                    plugin.getEventManager().initialize();
                    event = plugin.getEventManager().getEvent();
                }

                if (args.length >= 2) {
                    ParadisePhase phase = ParadisePhase.fromId(args[1]);
                    if (phase == null) {
                        sender.sendMessage(plugin.getConfigManager().getMessage("event.phase-not-found",
                                "%prefix%&cУказанная фаза не найдена! Доступные фазы: waiting, active, climax, reward, cooldown"));
                        return true;
                    }
                    event.setPhase(phase);
                    event.start();
                    sender.sendMessage(plugin.getConfigManager().getMessage("event.force-phase",
                            "%prefix%&aИвент переведён в фазу: ") + event.getPhaseDisplayName());
                } else {
                    plugin.getEventManager().restartEvent();
                    sender.sendMessage(plugin.getConfigManager().getMessage("event.started",
                            "%prefix%&aИвент Рая успешно запущен!"));
                }
                break;

            case "stop":
                if (!sender.hasPermission("elytrixparadise.admin")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                if (plugin.getEventManager().getEvent() != null) {
                    plugin.getEventManager().getEvent().stop();
                }
                sender.sendMessage(plugin.getConfigManager().getMessage("event.stopped",
                        "%prefix%&cИвент Рая приостановлен!"));
                break;

            case "status":
            case "info":
                ParadiseEvent ev = plugin.getEventManager().getEvent();
                Location c = plugin.getConfigManager().getCenterLocation();
                List<String> infoLines = plugin.getConfigManager().getMessageList("info");
                if (infoLines != null && !infoLines.isEmpty()) {
                    for (String line : infoLines) {
                        sender.sendMessage(line
                                .replace("{phase}", ev != null ? ev.getPhaseDisplayName() : "Не активен")
                                .replace("{time}", ev != null ? ev.getRemainingFormatted() : "00:00")
                                .replace("{players}", ev != null ? String.valueOf(ev.getPlayerCount()) : "0")
                                .replace("{x}", String.valueOf(c.getBlockX()))
                                .replace("{y}", String.valueOf(c.getBlockY()))
                                .replace("{z}", String.valueOf(c.getBlockZ()))
                                .replace("{schem}", plugin.getConfigManager().getSchematicFile())
                                .replace("{world}", c.getWorld() != null ? c.getWorld().getName() : "world")
                        );
                    }
                } else {
                    sender.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &#FFFFA0Рай &7» &#F8BEFBИнформация"));
                    sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fФаза: " + (ev != null ? ev.getPhaseDisplayName() : "&cВыключен")));
                    sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fВремя до смены: &#FFFFA0" + (ev != null ? ev.getRemainingFormatted() : "00:00")));
                    sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fИгроков в зоне: &#208BFB" + (ev != null ? ev.getPlayerCount() : 0)));
                    sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fКоординаты: &#FFFFA0" + c.getBlockX() + ", " + c.getBlockY() + ", " + c.getBlockZ()));
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
                if (plugin.getEventManager().getEvent() != null) {
                    plugin.getEventManager().getEvent().setCenter(pCenter.getLocation());
                }
                plugin.getRegionManager().createOrUpdateRegion();
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
                if (plugin.getEventManager().getEvent() != null) {
                    plugin.getEventManager().getEvent().setSafeSpawn(pSpawn.getLocation());
                }
                pSpawn.sendMessage(plugin.getConfigManager().getMessage("spawn-updated",
                        "%prefix%&aТочка телепортации (спавна) в Рай установлена на вашу текущую позицию!"));
                break;

            case "schem":
            case "schematic":
                File schemDir = plugin.getSchematicManager().getSchematicsFolder();
                String targetFile = plugin.getConfigManager().getSchematicFile();
                File f = new File(schemDir, targetFile);

                sender.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &#FFFFA0Рай &7» &#F8BEFBСхематика"));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fПапка для схематик: &e" + schemDir.getPath()));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fФайл из конфига: &b" + targetFile));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fСтатус файла: " + (f.exists() ? "&aНайден (" + f.length() + " байт)" : "&cНе найден (положите файл в папку)")));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fЧтобы вставить схематику, введите: &e/ep paste"));
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
            sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/ep info &8— &#F8BEFBинформация об активном ивенте"));
            sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/ep schem &8— &#F8BEFBкуда класть схематику"));
            if (sender.hasPermission("elytrixparadise.admin")) {
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/ep reload &8— &#F8BEFBперезагрузка конфигурации"));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/ep paste &8— &#F8BEFBпринудительно вставить схематику"));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/ep start [фаза] &8— &#F8BEFBзапустить / сменить фазу"));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/ep stop &8— &#F8BEFBостановить ивент"));
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
                list.add("start");
                list.add("stop");
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

        if (args.length == 2 && args[0].equalsIgnoreCase("start") && sender.hasPermission("elytrixparadise.admin")) {
            List<String> phases = new ArrayList<>();
            for (ParadisePhase phase : ParadisePhase.values()) {
                if (phase.getId().toLowerCase().startsWith(args[1].toLowerCase())) {
                    phases.add(phase.getId());
                }
            }
            return phases;
        }

        return Collections.emptyList();
    }
}
