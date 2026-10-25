package ru.rooyzee.elytrixcemetery.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.loot.LootEditorGUI;
import ru.rooyzee.elytrixcemetery.loot.LootType;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CemeteryCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public CemeteryCommand(Main plugin) {
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
            case "tp":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Только для игроков");
                    return true;
                }
                Player p = (Player) sender;
                Location center = plugin.getConfigManager().getCenterLocation();
                if (center != null) {
                    p.teleport(center.clone().add(0.5, 1, -18.5));
                    p.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &aВы успешно телепортированы на &fКладбище&a!"));
                } else {
                    p.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cЛокация кладбища не настроена!"));
                }
                break;

            case "info":
                int dug = plugin.getGraveManager().getDugCount();
                int total = plugin.getGraveManager().getTotalCount();
                int ghosts = plugin.getGhostController().getAliveCount();
                String time = plugin.getGraveManager().formatTime(plugin.getGraveManager().getRemainingRefillSeconds());

                sender.sendMessage("");
                sender.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &#F8BEFBИнформация о Кладбище"));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fЛокация: &#F8BEFBКладбище &8(0, 70, 0)"));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fРаскопано могил: &#F8BEFB" + dug + "&f/&#F8BEFB" + total));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fПризраков вокруг: &#F8BEFB" + ghosts));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fОбновление могил через: &#F8BEFB" + time));
                sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &fСтатус: &#F8BEFBАктивно всегда"));
                sender.sendMessage("");
                break;

            case "actionbar":
            case "on":
            case "off":
                if (sender instanceof Player) {
                    plugin.getActionBarTask().togglePlayer((Player) sender);
                }
                break;

            case "loot":
            case "edit":
                if (!sender.hasPermission("elytrixcemetery.admin")) {
                    sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&cУ вас нет прав!"));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Только для игроков");
                    return true;
                }
                LootType type = LootType.GRAVES;
                if (args.length >= 2 && args[1].equalsIgnoreCase("ghosts")) {
                    type = LootType.GHOSTS;
                }
                new LootEditorGUI(plugin).open((Player) sender, type, 1);
                break;

            case "refill":
                if (!sender.hasPermission("elytrixcemetery.admin")) {
                    sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&cУ вас нет прав!"));
                    return true;
                }
                plugin.getGraveManager().refillAndBuryAll();
                sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&aВсе могилы успешно закопаны, лут обновлён!"));
                break;

            case "respawn":
            case "generate":
                if (!sender.hasPermission("elytrixcemetery.admin")) {
                    sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&cУ вас нет прав!"));
                    return true;
                }
                plugin.getSchematicManager().buildOrPasteCemetery();
                sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&aПостройка кладбища успешно пересоздана!"));
                break;

            case "reload":
                if (!sender.hasPermission("elytrixcemetery.admin")) {
                    sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&cУ вас нет прав!"));
                    return true;
                }
                plugin.getConfigManager().reload();
                plugin.getLootStorageManager().loadAll();
                plugin.getRegionManager().createOrUpdateRegion();
                sender.sendMessage(ColorUtil.colorize(plugin.getConfigManager().getAdminPrefix() + "&aКонфигурация и лут успешно перезагружены!"));
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &#F8BEFBКоманды Кладбища"));
        sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/cemetery tp &8— &#F8BEFBТелепортация на Кладбище"));
        sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/cemetery info &8— &#F8BEFBИнформация об ивенте"));
        sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/cemetery actionbar &8— &#F8BEFBВкл/Выкл Actionbar"));
        if (sender.hasPermission("elytrixcemetery.admin")) {
            sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/cemetery loot <graves|ghosts> &8— &#F8BEFBРедактирование лута"));
            sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/cemetery refill &8— &#F8BEFBЗакопать могилы и обновить лут"));
            sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/cemetery respawn &8— &#F8BEFBПересоздать постройку"));
            sender.sendMessage(ColorUtil.colorize("&#F8BEFB&l┃ &f/cemetery reload &8— &#F8BEFBПерезагрузка плагина"));
        }
        sender.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>(Arrays.asList("tp", "info", "actionbar"));
            if (sender.hasPermission("elytrixcemetery.admin")) {
                list.addAll(Arrays.asList("loot", "refill", "respawn", "reload"));
            }
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("loot")) {
            return Arrays.asList("graves", "ghosts");
        }
        return new ArrayList<>();
    }
}
