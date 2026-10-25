package ru.rooyzee.elytrixcemetery.tasks;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActionBarTask extends BukkitRunnable {

    private final Main plugin;
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();

    public ActionBarTask(Main plugin) {
        this.plugin = plugin;
    }

    public void togglePlayer(Player player) {
        if (disabledPlayers.contains(player.getUniqueId())) {
            disabledPlayers.remove(player.getUniqueId());
            player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &aActionbar включен!"));
        } else {
            disabledPlayers.add(player.getUniqueId());
            player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cActionbar отключен!"));
        }
    }

    @Override
    public void run() {
        Location center = plugin.getConfigManager().getCenterLocation();
        if (center == null || center.getWorld() == null) return;

        int r = plugin.getConfigManager().getBoundaryRadius();
        int dug = plugin.getGraveManager().getDugCount();
        int total = plugin.getGraveManager().getTotalCount();
        int ghosts = plugin.getGhostController().getAliveCount();
        String time = plugin.getGraveManager().formatTime(plugin.getGraveManager().getRemainingRefillSeconds());

        String bar = ColorUtil.colorize("&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &8» &#F8BEFBКладбище &8┃ &fМогил: &#F8BEFB" + dug + "&7/&#F8BEFB" + total
                + " &8┃ &fПризраков: &#F8BEFB" + ghosts
                + " &8┃ &fОбновление: &#F8BEFB" + time);

        for (Player p : center.getWorld().getPlayers()) {
            if (disabledPlayers.contains(p.getUniqueId())) continue;

            int dx = Math.abs(p.getLocation().getBlockX() - center.getBlockX());
            int dz = Math.abs(p.getLocation().getBlockZ() - center.getBlockZ());
            if (dx <= r + 15 && dz <= r + 15) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(bar));
            }
        }
    }
}
