package ru.rooyzee.elytrixtrader.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.TraderConfig;
import ru.rooyzee.elytrixtrader.model.TraderInstance;
import ru.rooyzee.elytrixtrader.util.Numbers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ElytrixTraderCommand implements TabExecutor {

    private final Main plugin;

    public ElytrixTraderCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "help":
                help(sender, label);
                return true;
            case "start":
                return start(sender, args);
            case "stop":
                return stop(sender, args);
            case "tp":
            case "teleport":
                return tp(sender, args);
            case "info":
                return info(sender, args);
            case "reload":
                return reload(sender);
            case "edit":
            case "editor":
            case "trades":
                return edit(sender, args);
            case "buy":
                return buy(sender, args);
            default:
                plugin.messages().send(sender, "general.unknown-command", null, map("label", label));
                return true;
        }
    }

    private void help(CommandSender sender, String label) {
        plugin.messages().send(sender, "general.usage", sender instanceof Player ? (Player) sender : null, map("label", label));
    }

    private boolean start(CommandSender sender, String[] args) {
        if (!sender.hasPermission("elytrixtrader.start")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "general.unknown-command");
            return true;
        }
        TraderConfig config = plugin.traderConfigs().get(args[1].toLowerCase(Locale.ROOT));
        if (config == null) {
            plugin.messages().send(sender, "general.unknown-trader", null, map("trader", args[1]));
            return true;
        }
        if (!config.enabled()) {
            plugin.messages().send(sender, "admin.spawn-disabled");
            return true;
        }
        int count = 1;
        if (args.length > 2) {
            count = Math.max(1, Math.min(10, (int) Numbers.parseDouble(args[2], 1.0D)));
        }
        int spawned = 0;
        List<TraderInstance> started = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            TraderInstance instance = null;
            Location randomLoc = plugin.traders().findLocation(config);
            if (randomLoc != null) {
                instance = plugin.traders().spawnForce(config, randomLoc, false);
            }
            if (instance == null) {
                try {
                    for (World w : Bukkit.getWorlds()) {
                        if (!config.worlds().isEmpty() && !config.worlds().contains(w.getName())) continue;
                        Location ws = w.getSpawnLocation();
                        w.loadChunk(ws.getBlockX() >> 4, ws.getBlockZ() >> 4, true);
                        instance = plugin.traders().spawnForce(config, ws, false);
                        if (instance != null) break;
                    }
                } catch (Throwable ignored) {}
            }
            if (instance != null) {
                spawned++;
                started.add(instance);
            }
        }

        if (spawned == 0) {
            plugin.messages().send(sender, "admin.spawn-failed");
        } else {
            plugin.messages().send(sender, "admin.spawned", sender instanceof Player ? (Player) sender : null,
                    map("trader", config.id(), "amount", String.valueOf(spawned)));
            // Ручной запуск анонсируется как начало ивента.
            plugin.announcer().eventStarted(started);
        }
        return true;
    }

    private boolean stop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("elytrixtrader.stop")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        String traderId = args.length > 1 && !args[1].equalsIgnoreCase("all") ? args[1] : null;
        int removed = plugin.traders().stopAll(traderId, true);
        if (removed == 0) {
            removed = plugin.traders().despawnAllImmediate(traderId);
        }
        plugin.messages().send(sender, "admin.killed", null, map("amount", String.valueOf(removed)));
        return true;
    }

    private boolean tp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("elytrixtrader.tp")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        // id торговца обязателен
        if (args.length < 2 || args[1].trim().isEmpty()) {
            plugin.messages().send(sender, "admin.tp-usage");
            return true;
        }
        String traderId = args[1].toLowerCase(Locale.ROOT);
        if (plugin.traderConfigs().get(traderId) == null) {
            plugin.messages().send(sender, "general.unknown-trader", null, map("trader", args[1]));
            return true;
        }

        TraderInstance instance = plugin.traders().nearest(player, traderId);
        if (instance == null) {
            plugin.messages().send(sender, "interaction.not-found");
            return true;
        }

        try {
            Location loc = instance.location().clone().add(0.5, 0, 0.5);
            Location tpLoc = findSafeTpLocation(loc);
            if (tpLoc != null) player.teleport(tpLoc);
            else player.teleport(loc.clone().add(2, 1, 2));
            Map<String, String> map = new HashMap<>(instance.placeholders());
            map.put("distance", Numbers.amount((long) Math.ceil(instance.distance(player))));
            plugin.messages().send(sender, "admin.tped", player, map);
        } catch (Throwable t) {
            plugin.messages().send(sender, "interaction.not-found");
        }
        return true;
    }

    private Location findSafeTpLocation(Location traderLoc) {
        World world = traderLoc.getWorld();
        if (world == null) return null;
        for (int radius = 2; radius <= 6; radius++) {
            for (int angle = 0; angle < 360; angle += 45) {
                double rad = Math.toRadians(angle);
                int x = traderLoc.getBlockX() + (int) (Math.cos(rad) * radius);
                int z = traderLoc.getBlockZ() + (int) (Math.sin(rad) * radius);
                for (int dy = -2; dy <= 2; dy++) {
                    int checkY = traderLoc.getBlockY() + dy;
                    if (checkY < ru.rooyzee.elytrixtrader.model.SchematicConfig.getMinHeight(world) || checkY >= world.getMaxHeight()) continue;
                    org.bukkit.block.Block b1 = world.getBlockAt(x, checkY, z);
                    org.bukkit.block.Block b2 = world.getBlockAt(x, checkY + 1, z);
                    org.bukkit.block.Block below = world.getBlockAt(x, checkY - 1, z);
                    if (b1.getType().isAir() && b2.getType().isAir() && below.getType().isSolid()) {
                        return new Location(world, x + 0.5, checkY, z + 0.5);
                    }
                }
            }
        }
        return null;
    }

    private boolean info(CommandSender sender, String[] args) {
        if (!sender.hasPermission("elytrixtrader.use")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (plugin.config().scheduleEnabled() && !plugin.config().scheduleTimes().isEmpty()) {
            plugin.messages().send(sender, "admin.scheduled", sender instanceof Player ? (Player) sender : null,
                    map("time", plugin.traders().nextEventTime()));
        }
        List<TraderInstance> instances = plugin.traders().instances();
        if (instances.isEmpty()) {
            plugin.messages().send(sender, "admin.none-active");
            return true;
        }
        int limit = args.length > 1 ? (int) Numbers.parseDouble(args[1], instances.size()) : instances.size();
        int shown = 0;
        for (TraderInstance instance : instances) {
            if (shown >= Math.max(1, limit)) break;
            shown++;
            Map<String, String> map = new HashMap<>(instance.placeholders());
            plugin.messages().send(sender, "admin.list-line", sender instanceof Player ? (Player) sender : null, map);
        }
        return true;
    }

    private boolean edit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("elytrixtrader.edit")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            // Open trader list GUI
            plugin.tradeEditor().openTraderList(player);
            return true;
        }
        String traderId = args[1].toLowerCase(Locale.ROOT);
        TraderConfig config = plugin.traderConfigs().get(traderId);
        if (config == null) {
            plugin.messages().send(sender, "general.unknown-trader", null, map("trader", args[1]));
            return true;
        }
        if (args.length > 2) {
            // сразу открываем конкретный трейд
            String tradeId = args[2].toLowerCase(Locale.ROOT);
            if (plugin.tradeEditor().getTrade(traderId, tradeId) == null) {
                plugin.messages().send(sender, "general.unknown-command");
                return true;
            }
            plugin.tradeEditor().openTradeEdit(player, traderId, tradeId);
            return true;
        }
        plugin.tradeEditor().openTradeList(player, traderId);
        return true;
    }

    private boolean buy(CommandSender sender, String[] args) {
        // Внутренняя команда для BMenu: elytrixtrader buy <player> <tradeId>
        // Вызывается только от консоли (через [console] в BMenu).
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            return true;
        }
        if (args.length < 3) {
            return true;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null || !player.isOnline()) {
            return true;
        }
        plugin.menus().buyFromMenu(player, args[2]);
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("elytrixtrader.reload")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        plugin.reloadAll();
        plugin.messages().send(sender, "general.reloaded");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.add("help");
            result.add("start");
            result.add("stop");
            result.add("tp");
            result.add("info");
            result.add("edit");
            if (sender.hasPermission("elytrixtrader.reload")) result.add("reload");
            return filter(result, args[0]);
        }
        if (args.length == 2) {
            String action = args[0].toLowerCase(Locale.ROOT);
            if (action.equals("start") || action.equals("tp") || action.equals("edit")) {
                result.addAll(plugin.traderConfigs().keySet());
                return filter(result, args[1]);
            }
            if (action.equals("stop")) {
                result.add("all");
                result.addAll(plugin.traderConfigs().keySet());
                return filter(result, args[1]);
            }
            if (action.equals("info")) return Collections.emptyList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
            result.add("1");
            result.add("2");
            result.add("3");
            return filter(result, args[2]);
        }
        // намеренно пусто: id трейдов (trade_...) в таб-комплите не нужны
        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<>();
        String start = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(start)) result.add(value);
        }
        return result;
    }

    private Map<String, String> map(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
