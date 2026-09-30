package ru.rooyzee.elytrixparadise.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.loot.LootType;
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
            if (sender.hasPermission("elytrixparadise.admin")) {
                sender.sendMessage(ColorUtil.colorize("&7● &f/ep loot [shards/sphere] &8- &7Открыть редактор лута (4 страницы)"));
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        // 1. /paradise off — Отключение Actionbar для игрока
        if (sub.equals("off") || sub.equals("disableactionbar")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtil.colorize(adminPrefix + "&cКоманда доступна только игрокам!"));
                return true;
            }
            Player player = (Player) sender;
            plugin.setActionBarDisabled(player.getUniqueId(), true);
            player.sendMessage(ColorUtil.colorize(playerPrefix + "&cОтображение Actionbar отключено! &7(Включить: &#F8BEFB/paradise on&7)"));
            return true;
        }

        // 2. /paradise on — Включение Actionbar для игрока
        if (sub.equals("on") || sub.equals("enableactionbar")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtil.colorize(adminPrefix + "&cКоманда доступна только игрокам!"));
                return true;
            }
            Player player = (Player) sender;
            plugin.setActionBarDisabled(player.getUniqueId(), false);
            player.sendMessage(ColorUtil.colorize(playerPrefix + "&aОтображение Actionbar включено!"));
            return true;
        }

        // 3. /ep tp — Телепортация на ивент
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

        // 4. /ep info — Информация
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

        // 5. /ep schem — Инструкция
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

        // 6. /ep loot [shards|sphere] [страница 1..4]
        if (sub.equals("loot") || sub.equals("editor")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtil.colorize(adminPrefix + "&cКоманда доступна только игрокам!"));
                return true;
            }
            Player player = (Player) sender;
            LootType type = LootType.SHARDS;
            int page = 1;

            if (args.length >= 2) {
                String typeArg = args[1].toLowerCase();
                if (typeArg.startsWith("sph") || typeArg.startsWith("heart") || typeArg.startsWith("серд")) {
                    type = LootType.SPHERE;
                } else {
                    type = LootType.SHARDS;
                }
            }

            if (args.length >= 3) {
                try {
                    page = Integer.parseInt(args[2]);
                } catch (NumberFormatException ignored) {}
            }

            plugin.getLootEditorGUI().open(player, type, page);
            return true;
        }

        // 7. /ep reload
        if (sub.equals("reload")) {
            cm.load();
            plugin.getLootStorageManager().loadAll();
            plugin.getSchematicManager().invalidateCache();
            plugin.getSchematicManager().scanAndRegisterShards();
            if (plugin.getSphereManager() != null) {
                plugin.getSphereManager().init();
            }
            plugin.getRegionManager().createParadiseRegion(
                    cm.getCenterLocation(),
                    cm.getScanRadiusXZ(),
                    cm.getScanMinY(),
                    cm.getScanMaxY()
            );
            sender.sendMessage(ColorUtil.colorize(adminPrefix + "&aКонфигурация, награды, лут, регион и сообщения успешно перезагружены!"));
            return true;
        }

        // 8. /ep scan — Сканирование осколков в радиусе 150 блоков
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

        // 9. /ep paste
        if (sub.equals("paste")) {
            Location center = cm.getCenterLocation();
            sender.sendMessage(ColorUtil.colorize(adminPrefix + "&7Вставка схематики '" + cm.getSchematicFile() + "' на координаты X=0, Y=" + cm.getCenterY() + ", Z=0..."));
            boolean success = plugin.getSchematicManager().pasteSchematic(cm.getSchematicFile(), center);
            if (success) {
                plugin.getRegionManager().createParadiseRegion(
                        center,
                        cm.getScanRadiusXZ(),
                        cm.getScanMinY(),
                        cm.getScanMaxY()
                );
                if (plugin.getSphereManager() != null) {
                    plugin.getSphereManager().init();
                }
                sender.sendMessage(ColorUtil.colorize(adminPrefix + "&aСхематика, Сердце Рая и регион успешно созданы на X=0, Y=" + cm.getCenterY() + ", Z=0!"));
            } else {
                sender.sendMessage(ColorUtil.colorize(adminPrefix + "&cОшибка при вставке схематики! Проверьте консоль сервера."));
            }
            return true;
        }

        // 10. /ep clear
        if (sub.equals("clear") || sub.equals("remove")) {
            if (plugin.getSphereManager() != null) {
                plugin.getSphereManager().clearAll();
            }
            plugin.getSchematicManager().clearSchematic();
            plugin.getRegionManager().removeParadiseRegion(cm.getCenterLocation().getWorld());
            sender.sendMessage(ColorUtil.colorize(adminPrefix + "&aСхематика, Осколки Рая, Сердце и регион WorldGuard успешно удалены!"));
            return true;
        }

        // 11. /ep setcenter
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

        // 12. /ep setspawn
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
            List<String> list = new ArrayList<>(Arrays.asList("help", "off", "on", "tp", "info", "schem"));
            if (sender.hasPermission("elytrixparadise.admin")) {
                list.addAll(Arrays.asList("loot", "reload", "scan", "paste", "clear", "setcenter", "setspawn"));
            }
            return list.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("loot")) {
            return Arrays.asList("shards", "sphere").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("loot")) {
            return Arrays.asList("1", "2", "3", "4").stream()
                    .filter(s -> s.startsWith(args[2]))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
