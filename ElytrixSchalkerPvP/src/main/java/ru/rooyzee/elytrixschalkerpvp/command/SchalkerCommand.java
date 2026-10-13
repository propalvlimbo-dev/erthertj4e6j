package ru.rooyzee.elytrixschalkerpvp.command;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixschalkerpvp.Main;
import ru.rooyzee.elytrixschalkerpvp.manager.ConfigManager;
import ru.rooyzee.elytrixschalkerpvp.manager.SchalkerManager;
import ru.rooyzee.elytrixschalkerpvp.model.Rarity;
import ru.rooyzee.elytrixschalkerpvp.model.SchalkerData;
import ru.rooyzee.elytrixschalkerpvp.util.ColorUtil;

public class SchalkerCommand implements CommandExecutor {

    private final Main plugin;

    public SchalkerCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Только для игроков");
            return true;
        }

        Player player = (Player) sender;
        ConfigManager cm = plugin.getConfigManager();

        if (!player.hasPermission("elytrixschalker.admin")) {
            player.sendMessage(ColorUtil.colorize(cm.getMessage("no-permission")));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ColorUtil.colorize(cm.getMessage("usage")));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
                handleCreate(player, cm);
                break;
            case "remove":
                handleRemove(player, cm);
                break;
            case "edit":
                handleEdit(player, args, cm);
                break;
            case "reload":
                handleReload(player, cm);
                break;
            case "spawn":
                handleSpawn(player, args, cm);
                break;
            default:
                player.sendMessage(ColorUtil.colorize(cm.getMessage("usage")));
                break;
        }

        return true;
    }

    private void handleCreate(Player player, ConfigManager cm) {
        Block below = findShulkerBelow(player);
        if (below == null) {
            player.sendMessage(ColorUtil.colorize(cm.getMessage("stand-on-shulker")));
            return;
        }

        SchalkerData existing = plugin.getSchalkerManager().getSchalkerAt(below.getLocation());
        if (existing != null) {
            player.sendMessage(ColorUtil.colorize(cm.getMessage("already-exists")));
            return;
        }

        SchalkerData data = plugin.getSchalkerManager().createSchalker(below.getLocation());
        if (data != null) {
            player.sendMessage(ColorUtil.colorize(cm.getMessage("schalker-created")));
        } else {
            player.sendMessage(ColorUtil.colorize(cm.getMessage("already-exists")));
        }
    }

    private void handleRemove(Player player, ConfigManager cm) {
        SchalkerData data = plugin.getSchalkerManager().findSchalkerBelow(player);
        if (data == null) {
            player.sendMessage(ColorUtil.colorize(cm.getMessage("schalker-not-found")));
            return;
        }

        if (plugin.getSchalkerManager().removeSchalker(data.getLocation())) {
            player.sendMessage(ColorUtil.colorize(cm.getMessage("schalker-removed")));
        } else {
            player.sendMessage(ColorUtil.colorize(cm.getMessage("schalker-not-found")));
        }
    }

    private void handleEdit(Player player, String[] args, ConfigManager cm) {
        if (args.length < 2) {
            player.sendMessage(ColorUtil.colorize(cm.getMessage("invalid-rarity")));
            return;
        }

        Rarity rarity = Rarity.fromString(args[1]);
        if (rarity == null) {
            player.sendMessage(ColorUtil.colorize(cm.getMessage("invalid-rarity")));
            return;
        }

        LootEditGUI.openEditor(plugin, player, rarity);
    }

    private void handleReload(Player player, ConfigManager cm) {
        plugin.getConfigManager().reload();
        plugin.getLootManager().loadLoot();
        player.sendMessage(ColorUtil.colorize(cm.getMessage("reload-success")));
    }

    private void handleSpawn(Player player, String[] args, ConfigManager cm) {
        SchalkerData data = plugin.getSchalkerManager().findSchalkerBelow(player);
        if (data == null) {
            player.sendMessage(ColorUtil.colorize(cm.getMessage("schalker-not-found")));
            return;
        }

        Rarity rarity;
        if (args.length >= 2) {
            rarity = Rarity.fromString(args[1]);
            if (rarity == null) {
                player.sendMessage(ColorUtil.colorize(cm.getMessage("invalid-rarity")));
                return;
            }
        } else {
            rarity = Rarity.COMMON;
        }

        plugin.getSchalkerManager().spawnWithRarity(data, rarity);
        String msg = cm.getMessage("schalker-spawned")
                .replace("{rarity}", cm.getRarityName(rarity));
        player.sendMessage(ColorUtil.colorize(msg));
    }

    private Block findShulkerBelow(Player player) {
        Location loc = player.getLocation();
        Block below = loc.clone().subtract(0, 1, 0).getBlock();
        if (SchalkerManager.isShulkerBox(below.getType())) return below;

        Block at = loc.getBlock();
        if (SchalkerManager.isShulkerBox(at.getType())) return at;

        for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block check = loc.clone().add(dx, dy, dz).getBlock();
                    if (SchalkerManager.isShulkerBox(check.getType())) {
                        return check;
                    }
                }
            }
        }
        return null;
    }
}