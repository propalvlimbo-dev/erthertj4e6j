package ru.rooyzee.elytrixtrader.currency;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.CostConfig;
import ru.rooyzee.elytrixtrader.model.ItemConfig;
import ru.rooyzee.elytrixtrader.model.TradeConfig;
import ru.rooyzee.elytrixtrader.model.TraderInstance;
import ru.rooyzee.elytrixtrader.util.ColorUtil;
import ru.rooyzee.elytrixtrader.util.Numbers;
import ru.rooyzee.elytrixtrader.util.Sounds;
import ru.rooyzee.elytrixtrader.util.Text;
import ru.rooyzee.elytrixtrader.util.Worlds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PurchaseService {

    public enum Result {
        SUCCESS,
        TOO_FAR,
        NO_PERMISSION,
        WRONG_WORLD,
        LEVEL_REQUIRED,
        OUT_OF_STOCK,
        NOT_ENOUGH,
        COOLDOWN,
        UNAVAILABLE
    }

    private final Main plugin;

    public PurchaseService(Main plugin) {
        this.plugin = plugin;
    }

    public int clamp(TradeConfig trade, int requested) {
        if (trade == null) {
            return 1;
        }
        return Math.max(1, Math.min(trade.maxPerTrade(), requested));
    }

    public Result buy(Player player, TraderInstance instance, TradeConfig trade, int requested) {
        if (player == null || trade == null || !trade.enabled()) {
            return Result.UNAVAILABLE;
        }
        if (instance == null || !instance.alive()) {
            deny(player, "trade.disabled", null);
            return Result.UNAVAILABLE;
        }
        if (!instance.inRange(player)) {
            Map<String, String> map = new HashMap<>();
            map.put("distance", Numbers.amount((long) instance.distance(player)));
            map.put("radius", String.valueOf((int) Math.ceil(plugin.config().interactionRadius())));
            deny(player, "interaction.too-far", map);
            player.closeInventory();
            return Result.TOO_FAR;
        }
        if (trade.requiredPermission() != null && !trade.requiredPermission().trim().isEmpty()
                && !player.hasPermission(trade.requiredPermission())) {
            Map<String, String> map = new HashMap<>();
            map.put("permission", trade.requiredPermission());
            deny(player, "trade.no-permission", map);
            return Result.NO_PERMISSION;
        }
        if (!trade.worlds().isEmpty() && !contains(trade.worlds(), player.getWorld().getName())) {
            Map<String, String> map = new HashMap<>();
            List<String> displayWorlds = new ArrayList<>();
            for (String world : trade.worlds()) {
                displayWorlds.add(Worlds.display(world));
            }
            map.put("world", String.join(", ", displayWorlds));
            deny(player, "trade.world", map);
            return Result.WRONG_WORLD;
        }
        if (player.getLevel() < trade.requiredLevel()) {
            Map<String, String> map = new HashMap<>();
            map.put("level", String.valueOf(trade.requiredLevel()));
            deny(player, "trade.level", map);
            return Result.LEVEL_REQUIRED;
        }
        int amount = clamp(trade, requested);
        long cooldown = plugin.cooldowns().remaining(player.getUniqueId(), instance.config().id());
        if (cooldown > 0L) {
            Map<String, String> map = new HashMap<>();
            map.put("time", Numbers.duration(cooldown));
            deny(player, "trade.cooldown", map);
            return Result.COOLDOWN;
        }
        if (!instance.hasStock(trade.id(), amount)) {
            deny(player, "trade.out-of-stock", null);
            return Result.OUT_OF_STOCK;
        }
        for (CostConfig cost : trade.costs()) {
            if (!plugin.currency().accepts(cost.type())) {
                deny(player, "trade.disabled", null);
                return Result.UNAVAILABLE;
            }
            double need = cost.amountFor(amount);
            if (!plugin.currency().canAfford(player, cost, need)) {
                Map<String, String> map = placeholders(player, instance, trade, amount);
                map.put("price", format(cost, need));
                map.put("currency", cost.label());
                map.put("balance", plugin.currency().balanceText(player, cost));
                deny(player, "trade.not-enough", map);
                // Если не хватает именно опыта — выкидываем из меню, NPC бьёт рукой.
                if (cost.isExperience()) {
                    player.closeInventory();
                    instance.punch(player);
                }
                return Result.NOT_ENOUGH;
            }
        }
        for (CostConfig cost : trade.costs()) {
            plugin.currency().pay(player, cost, cost.amountFor(amount));
        }
        boolean overflow = false;
        // Если трейд создан через редактор — выдаём rawRewards (полный NBT: зелья, зачарования)
        List<ItemStack> toGive = new ArrayList<>();
        if (!trade.rawRewards().isEmpty()) {
            for (ItemStack raw : trade.rawRewards()) {
                if (raw == null || raw.getType() == org.bukkit.Material.AIR) continue;
                // стак из редактора не разбивается: 3 стакнутых зелья выдаются
                // одним стаком из трёх, а не тремя отдельными
                int maxStack = Math.max(raw.getAmount(), raw.getType().getMaxStackSize());
                int total = Math.max(1, raw.getAmount() * Math.max(1, amount));
                int remaining = total;
                while (remaining > 0) {
                    ItemStack clone = raw.clone();
                    clone.setAmount(Math.min(maxStack, remaining));
                    remaining -= clone.getAmount();
                    toGive.add(clone);
                }
            }
        } else {
            for (ItemConfig item : trade.receive()) {
                toGive.addAll(item.build(amount));
            }
        }
        for (ItemStack stack : toGive) {
            if (stack == null) continue;
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            if (!left.isEmpty()) {
                overflow = true;
                for (ItemStack extra : left.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), extra);
                }
            }
        }
        instance.takeStock(trade.id(), amount);
        int cooldownSeconds = instance.config().tradeCooldownSeconds();
        if (cooldownSeconds > 0) {
            plugin.cooldowns().apply(player.getUniqueId(), instance.config().id(), cooldownSeconds);
        }
        Map<String, String> map = placeholders(player, instance, trade, amount);
        runCommands(player, trade, map);
        Sounds.play(player, trade.sound() == null ? plugin.config().purchaseSound() : trade.sound(), 0.9F, 1.0F);
        if (overflow) {
            plugin.messages().send(player, "trade.inventory-full", player, null);
        }
        // Сообщение об успешной покупке убрано: остаётся только звук и анимация,
        // без текста «Успешно | Получено Nx …» в чате.
        if (!trade.messages().isEmpty()) {
            for (String line : trade.messages()) {
                player.sendMessage(Text.apply(line, player, map));
            }
        }
        plugin.announcer().purchase(player, instance, trade, amount, map.get("price"));
        // Частицы при покупке убраны по запросу: остаётся только звук сделки
        instance.swing();
        if (plugin.config().debug()) {
        }
        return Result.SUCCESS;
    }

    private void runCommands(Player player, TradeConfig trade, Map<String, String> map) {
        List<String> commands = trade.commands();
        if (commands.isEmpty()) {
            return;
        }
        for (String raw : commands) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String command = Text.fill(raw, map)
                    .replace("%player%", player.getName())
                    .replace("{world}", player.getWorld().getName());
            if (command.startsWith("player:")) {
                player.performCommand(command.substring("player:".length()).replace("{player}", player.getName()));
                continue;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.getName()));
        }
    }

    public Map<String, String> placeholders(Player player, TraderInstance instance, TradeConfig trade, int amount) {
        Map<String, String> map = instance == null ? new HashMap<>() : new HashMap<>(instance.placeholders());
        if (player != null) {
            map.put("player", player.getName());
            map.put("levels", String.valueOf(player.getLevel()));
            map.put("exp", String.valueOf(plugin.currency().totalExperience(player)));
            map.put("bulk", String.valueOf(plugin.gui().bulkAmount()));
        }
        map.put("trade", ColorUtil.plain(trade == null ? "" : trade.displayName()));
        map.put("item", ColorUtil.plain(trade == null ? "" : trade.displayName()));
        map.put("amount", String.valueOf(amount));
        if (trade != null) {
            map.put("price", formatAll(trade, amount, player));
            map.put("currency", currencyLabel(trade));
            map.put("unit_price", formatAll(trade, 1, player));
            map.put("balance", balanceLabel(trade, player));
            map.put("stock", instance == null ? "∞" : instance.stockText(trade.id()));
            map.put("permission", trade.requiredPermission() == null ? "" : trade.requiredPermission());
        }
        return map;
    }

    public String formatAll(TradeConfig trade, int amount, Player player) {
        StringBuilder builder = new StringBuilder();
        for (CostConfig cost : trade.costs()) {
            if (builder.length() > 0) {
                builder.append(" + ");
            }
            builder.append(format(cost, cost.amountFor(amount)));
        }
        if (builder.length() == 0) {
            return "бесплатно";
        }
        return builder.toString();
    }

    private String format(CostConfig cost, double amount) {
        return plugin.currency().format(null, cost, amount);
    }

    public String currencyLabel(TradeConfig trade) {
        StringBuilder builder = new StringBuilder();
        for (CostConfig cost : trade.costs()) {
            if (builder.length() > 0) {
                builder.append(" + ");
            }
            builder.append(cost.label());
        }
        if (builder.length() == 0) {
            return "—";
        }
        return builder.toString();
    }

    public String balanceLabel(TradeConfig trade, Player player) {
        if (player == null || trade.costs().isEmpty()) {
            return "0";
        }
        CostConfig first = trade.costs().get(0);
        if (first.type() == CostConfig.Type.ITEM) {
            return plugin.currency().countItem(player, first) + " " + first.itemName();
        }
        return Numbers.amount((long) plugin.currency().balance(player, first.type()));
    }

    private String name(Material material) {
        return material == null ? "items" : material.name();
    }

    private void deny(Player player, String key, Map<String, String> map) {
        Sounds.play(player, plugin.config().denySound(), 0.8F, 0.7F);
        plugin.messages().send(player, key, player, map);
    }

    private boolean contains(List<String> values, String target) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
}
