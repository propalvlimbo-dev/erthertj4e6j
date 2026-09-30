package ru.rooyzee.elytrixparadise.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.managers.ConfigManager;
import ru.rooyzee.elytrixparadise.shards.ParadiseShard;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ParadiseCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public ParadiseCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager cm = plugin.getConfigManager();
        String adminPrefix = cm.getAdminPrefix();
        String playerPrefix = cm.getPlayerPrefix();

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            for (String line : cm.getMessageList("help")) {
                sender.sendMessage(line);
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        // 1. /ep tp — Телепортация на ивент
        if (sub.equals("tp") || sub.equals("teleport")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtil.colorize(adminPrefix + "&cКоманда доступна только игрокам!"));
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("elytrixparadise.tp") && !player.hasPermission("elytrixparadise.use")) {
                player.sendMessage(ColorUtil.colorize(playerPrefix + "&cУ вас нет прав на телепортацию в Райское место!"));
                return true;
            }

            Location spawn = cm.getSpawnLocation();
            player.teleport(spawn);
            player.sendMessage(ColorUtil.colorize(playerPrefix + "&aВы успешно телепортированы в &#F8BEFBРайское место&a!"));
            return true;
        }

        // 2. /ep info — Информация
        if (sub.equals("info")) {
            int active = plugin.getShardManager().getActiveShardsCount();
            int total = plugin.getShardManager().getAllShards().size();
            for (String line : cm.getMessageList("info")) {
                sender.sendMessage(line
                        .replace("{x}", String.valueOf(cm.getCenterX()))
                        .replace("{y}", String.valueOf(cm.getCenterY()))
                        .replace("{z}", String.valueOf(cm.getCenterZ()))
                        .replace("{world}", cm.getWorldName())
                        .replace("{schem}", cm.getSchematicFile())
                        .replace("{active_shards}", String.valueOf(active))
                        .replace("{total_shards}", String.valueOf(total)));
            }
            return true;
        }

        // 3. /ep schem — Инструкция
        if (sub.equals("schem") || sub.equals("schematic")) {
            sender.sendMessage(ColorUtil.colorize(adminPrefix + "&fСхематика должна находиться по пути:"));
            sender.sendMessage(ColorUtil.colorize("&eplugins/ElytrixParadise/schematics/" + cm.getSchematicFile()));
            sender.sendMessage(ColorUtil.colorize("&7Координаты вставки: &#F8BEFBX=" + cm.getCenterX() + ", Y=" + cm.getCenterY() + ", Z=" + cm.getCenterZ() + " &7в мире &f" + cm.getWorldName()));
            return true;
        }

        // --- Команды администратора ---
        if (!sender.hasPermission("elytrixparadise.admin")) {
            sender.sendMessage(ColorUtil.colorize(adminPrefix + "&cУ вас недостаточно прав для выполнения этой команды!"));
            return true;
        }

        // 4. /ep reload
        if (sub.equals("reload")) {
            cm.load();
            plugin.getSchematicManager().invalidateCache();
            plugin.getSchematicManager().scanAndRegisterShards();
            sender.sendMessage(ColorUtil.colorize(adminPrefix + "&aКонфигурация, награды и сообщения успешно перезагружены!"));
            return true;
        }

        // 5. /ep scan — Сканирование осколков в радиусе 120 блоков
        if (sub.equals("scan")) {
            sender.sendMessage(ColorUtil.colorize(adminPrefix + "&7Сканирование мира на наличие Осколков Рая (радиус: " + cm.getScanRadiusXZ() + " блоков)..."));
            int count = plugin.getSchematicManager().scanAndRegisterShards();
            sender.sendMessage(ColorUtil.colorize(adminPrefix + "&aСканирование завершено! Найдено и активно осколков: &#F8BEFB" + count));
            for (ParadiseShard s : plugin.getShardManager().getAllShards()) {
                Location l = s.getLocation();
                sender.sendMessage(ColorUtil.colorize("&7● &fОсколок: &#F8BEFBX: " + l.getBlockX() + ", Y: " + l.getBlockY() + ", Z: " + l.getBlockZ()
                        + " &8(&a" + s.getState().name() + "&8)"));
            }
            return true;
        }

        // 6. /ep paste
        if (sub.equals("paste")) {
            Location center = cm.getCenterLocation();
            sender.sendMessage(ColorUtil.colorize(adminPrefix + "&7Вставка схематики '" + cm.getSchematicFile() + "' на координаты X=0, Y=" + cm.getCenterY() + ", Z=0..."));
            boolean success = plugin.getSchematicManager().pasteSchematic(cm.getSchematicFile(), center);
            if (success) {
                sender.sendMessage(ColorUtil.colorize(adminPrefix + "&aСхематика успешно вставлена на X=0, Y=" + cm.getCenterY() + ", Z=0!"));
            } else {
                sender.sendMessage(ColorUtil.colorize(adminPrefix + "&cОшибка при вставке схематики! Проверьте консоль сервера."));
            }
            return true;
        }

        // 7. /ep clear
        if (sub.equals("clear") || sub.equals("remove")) {
            plugin.getSchematicManager().clearSchematic();
            sender.sendMessage(ColorUtil.colorize(adminPrefix + "&aСхематика и Осколки Рая успешно удалены!"));
            return true;
        }

        // 8. /ep setcenter
        if (sub.equals("setcenter")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtil.colorize(adminPrefix + "&cКоманда доступна только игрокам!"));
                return true;
            }
            Player player = (Player) sender;
            cm.setCenterLocation(player.getLocation());
            sender.sendMessage(ColorUtil.colorize(adminPrefix + "&aЦентральные координаты обновлены на: X="
                    + player.getLocation().getBlockX() + ", Y=" + player.getLocation().getBlockY() + ", Z=" + player.getLocation().getBlockZ()));
            return true;
        }

        // 9. /ep setspawn
        if (sub.equals("setspawn")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtil.colorize(adminPrefix + "&cКоманда доступна только игрокам!"));
                return true;
            }
            Player player = (Player) sender;
            cm.setSpawnLocation(player.getLocation());
            sender.sendMessage(ColorUtil.colorize(adminPrefix + "&aТочка спавна игроков обновлена!"));
            return true;
        }

        sender.sendMessage(ColorUtil.colorize(adminPrefix + "&cНеизвестная подкоманда. Введите &f/ep help&c."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>(Arrays.asList("help", "tp", "info", "schem"));
            if (sender.hasPermission("elytrixparadise.admin")) {
                list.addAll(Arrays.asList("reload", "scan", "paste", "clear", "setcenter", "setspawn"));
            }
            return list.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
