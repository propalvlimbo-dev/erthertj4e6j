package ru.rooyzee.elytrixtrader.trader;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.TradeConfig;
import ru.rooyzee.elytrixtrader.model.TraderInstance;
import ru.rooyzee.elytrixtrader.util.ColorUtil;
import ru.rooyzee.elytrixtrader.util.Sounds;
import ru.rooyzee.elytrixtrader.util.Text;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TraderAnnouncer {

    private final Main plugin;

    public TraderAnnouncer(Main plugin) {
        this.plugin = plugin;
    }

    public void spawned(TraderInstance instance) {
        // Спавн торговца всегда анонсируется в чат
        broadcast("broadcast.trader-spawn", traderPlaceholders(instance));
        Sounds.play(instance.location(), spawnSound(instance), 0.7F, 1.0F);
    }

    // Одно общее сообщение о начале ивента (когда стартует сразу несколько торговцев)
    public void eventStarted(List<TraderInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return;
        }
        TraderInstance first = instances.get(0);
        Map<String, String> placeholders = traderPlaceholders(first);
        StringBuilder names = new StringBuilder();
        for (TraderInstance instance : instances) {
            if (names.length() > 0) {
                names.append("&7, ");
            }
            names.append(instance.config().displayName());
        }
        placeholders.put("trader", ColorUtil.plain(names.toString()));
        placeholders.put("trader_colored", names.toString());
        broadcast("broadcast.event-started", placeholders);
        Sounds.play(first.location(), spawnSound(first), 0.7F, 1.0F);
        Sounds.play(first.location(), plugin.config().eventSound(), 1.0F, 1.0F);
    }

    /** v1.5: сообщение после остановки ивента (ушёл последний торговец). */
    public void eventEnded() {
        broadcast("broadcast.event-ended", java.util.Collections.emptyMap());
    }

    public void expired(TraderInstance instance) {
        // v1.9: сообщение «торговец покинул мир» убрано —
        // остаётся только «Событие завершено» (eventEnded, после последнего).
        Sounds.play(instance.location(), plugin.config().despawnSound(), 0.6F, 0.9F);
    }

    public void purchase(org.bukkit.entity.Player player, TraderInstance instance, TradeConfig trade, int amount, String price) {
        if (!plugin.config().broadcastPurchase()) {
            return;
        }
        if (amount < plugin.config().purchaseBroadcastThreshold()) {
            return;
        }
        Map<String, String> placeholders = traderPlaceholders(instance);
        placeholders.put("reward", ColorUtil.plain(trade.displayName()));
        // показываем реальное число предметов: стак x3 при покупке = «3x»
        int unit = 1;
        java.util.List<org.bukkit.inventory.ItemStack> raw = trade.rawRewards();
        if (!raw.isEmpty()) {
            unit = 0;
            for (org.bukkit.inventory.ItemStack stack : raw) {
                if (stack != null) {
                    unit += Math.max(1, stack.getAmount());
                }
            }
        }
        placeholders.put("amount", String.valueOf(Math.max(1, unit) * Math.max(1, amount)));
        placeholders.put("price", price);
        // Сообщение о покупке видит только покупатель — ник в тексте не нужен.
        // Название предмета уходит TranslatableComponent'ом: клиент покажет
        // его на своём языке («Зелье силы», а не «Potion»).
        sendComponent(player, "broadcast.purchase", placeholders,
                raw.isEmpty() ? null : raw.get(0));
    }

    /**
     * Отправляет строки сообщения, подставляя вместо {item}/{reward}
     * название предмета, переведённое на язык клиента.
     */
    public void sendComponent(org.bukkit.entity.Player player, String key,
                              Map<String, String> placeholders, org.bukkit.inventory.ItemStack item) {
        if (player == null) {
            return;
        }
        java.util.List<String> lines = plugin.messages().lines(key);
        if (lines.isEmpty()) {
            return;
        }
        for (String line : lines) {
            // убираем item/reward из карты, чтобы токен дожил до вставки компонента
            java.util.Map<String, String> forText = new java.util.HashMap<>(placeholders);
            forText.remove("reward");
            forText.remove("item");
            String resolved = Text.apply(line, player, forText);
            if (resolved == null) {
                resolved = "";
            }
            int idx = resolved.indexOf("{reward}");
            String token = "{reward}";
            if (idx < 0) {
                idx = resolved.indexOf("{item}");
                token = "{item}";
            }
            if (idx < 0 || item == null) {
                player.sendMessage(resolved.replace("{reward}", "").replace("{item}", ""));
                continue;
            }
            net.md_5.bungee.api.chat.BaseComponent[] head =
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(resolved.substring(0, idx));
            net.md_5.bungee.api.chat.BaseComponent[] tail =
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(resolved.substring(idx + token.length()));
            net.md_5.bungee.api.chat.BaseComponent[] all =
                    new net.md_5.bungee.api.chat.BaseComponent[head.length + 1 + tail.length];
            System.arraycopy(head, 0, all, 0, head.length);
            all[head.length] = ru.rooyzee.elytrixtrader.util.ItemNames.nameComponentWithHover(item, player);
            System.arraycopy(tail, 0, all, head.length + 1, tail.length);
            player.spigot().sendMessage(all);
        }
    }

    private String spawnSound(TraderInstance instance) {
        String per = instance.config().spawnSound();
        return (per == null || per.trim().isEmpty()) ? plugin.config().spawnSound() : per;
    }

    private void sendToPlayer(org.bukkit.entity.Player player, String key, Map<String, String> placeholders) {
        if (player == null) {
            return;
        }
        List<String> lines = plugin.messages().lines(key);
        if (lines.isEmpty()) {
            lines = defaultLines(key);
        }
        for (String line : lines) {
            String message = Text.apply(line, null, placeholders);
            if (message == null) {
                message = "";
            }
            message = ColorUtil.translate(message);
            player.sendMessage(message);
        }
    }

    private Map<String, String> traderPlaceholders(TraderInstance instance) {
        Map<String, String> placeholders = instance.placeholders();
        return placeholders;
    }

    private void broadcast(String key, Map<String, String> placeholders) {
        List<String> lines = plugin.messages().lines(key);
        if (lines.isEmpty()) {
            lines = defaultLines(key);
        }
        for (String line : lines) {
            String message = Text.apply(line, null, placeholders);
            if (message == null) {
                message = "";
            }
            message = ColorUtil.translate(message);
            // Отправляем каждому игроку напрямую — гарантированно попадает в чат
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    player.sendMessage(message);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private List<String> defaultLines(String key) {
        if (key.endsWith("event-started")) {
            return Arrays.asList(
                    " ",
                    "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBСобытие началось",
                    "&#F8BEFB&l┃ &fТорговец: {trader_colored}",
                    "&#F8BEFB&l┃ &fМир: &#F8BEFB{world} &8| &#F8BEFB{x} {y} {z}",
                    " "
            );
        }
        if (key.endsWith("event-ended")) {
            return Arrays.asList(
                    " ",
                    "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBСобытие завершено",
                    "&#F8BEFB&l┃ &fТорговцы покинули мир — ждите следующего события",
                    " "
            );
        }
        if (key.endsWith("trader-spawn")) {
            return Arrays.asList(
                    " ",
                    "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBТорговец",
                    "&#F8BEFB&l┃ &fТорговец: {trader_colored}",
                    "&#F8BEFB&l┃ &fМир: &#F8BEFB{world} &8| &#F8BEFB{x} {y} {z}",
                    "&#F8BEFB&l┃ &fДоступен: &#F8BEFB{left}",
                    " "
            );
        }
        return Arrays.asList(
                " ",
                "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBПокупка",
                "&#F8BEFB&l┃ &fТовар: &a{amount}x {reward}",
                "&#F8BEFB&l┃ &fТорговец: {trader_colored} &8| &#F8BEFB{price}",
                " "
        );
    }
}
