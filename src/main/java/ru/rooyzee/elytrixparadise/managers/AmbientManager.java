package ru.rooyzee.elytrixparadise.managers;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.event.ParadiseEvent;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AmbientManager {

    private final Main plugin;
    private BossBar bossBar;
    private double particleAngle = 0.0;

    public AmbientManager(Main plugin) {
        this.plugin = plugin;
        initBossBar();
    }

    private void initBossBar() {
        if (!plugin.getConfigManager().isBossBarEnabled()) return;

        BarColor color;
        try {
            color = BarColor.valueOf(plugin.getConfigManager().getBossBarColor().toUpperCase());
        } catch (Exception e) {
            color = BarColor.PURPLE;
        }

        BarStyle style;
        try {
            style = BarStyle.valueOf(plugin.getConfigManager().getBossBarStyle().toUpperCase());
        } catch (Exception e) {
            style = BarStyle.SOLID;
        }

        bossBar = Bukkit.createBossBar(
                ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &#FFFFA0Рай"),
                color,
                style
        );
        bossBar.setVisible(true);
    }

    public void tick(ParadiseEvent event) {
        if (event == null || !event.isRunning()) {
            if (bossBar != null) bossBar.removeAll();
            return;
        }

        Location center = event.getCenter();
        if (center == null || center.getWorld() == null) return;

        List<Player> insidePlayers = event.getOnlinePlayersInside();
        Set<Player> currentSet = new HashSet<>(insidePlayers);

        // Update BossBar
        if (bossBar != null && plugin.getConfigManager().isBossBarEnabled()) {
            String title = plugin.getConfigManager().getBossBarTitle()
                    .replace("{phase}", event.getPhaseDisplayName())
                    .replace("{time}", event.getRemainingFormatted())
                    .replace("{players}", String.valueOf(insidePlayers.size()));
            bossBar.setTitle(ColorUtil.colorize(title));
            bossBar.setProgress(event.getProgress());

            // Synchronize boss bar viewers
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (currentSet.contains(p)) {
                    if (!bossBar.getPlayers().contains(p)) {
                        bossBar.addPlayer(p);
                    }
                } else {
                    if (bossBar.getPlayers().contains(p)) {
                        bossBar.removePlayer(p);
                    }
                }
            }
        }

        // Actionbar & Ambient for players on island
        for (Player p : insidePlayers) {
            if (plugin.getConfigManager().isActionbarEnabled()) {
                String actionbarText = ColorUtil.colorize("&f☁ &#F8BEFBРайские Облака &7| "
                        + event.getPhaseDisplayName() + " &7(&f" + event.getRemainingFormatted() + "&7)");
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionbarText));
            }

            // Void save check
            if (plugin.getConfigManager().isVoidSaveEnabled()) {
                if (p.getLocation().getY() < center.getY() - 15) {
                    p.setVelocity(new Vector(0, 1.2, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 1, false, false));
                    p.sendMessage(plugin.getConfigManager().getMessage("event.cloud-bounce",
                            "&f☁ &#F8BEFBРайские облака подхватили вас!"));
                    p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.8f, 1.2f);
                }
            }
        }

        // Spawn cloud ambient particles
        if (plugin.getConfigManager().isCloudsParticlesEnabled()) {
            spawnCloudParticles(center);
        }
    }

    private void spawnCloudParticles(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        particleAngle += Math.PI / 16;
        if (particleAngle > Math.PI * 2) particleAngle = 0;

        int radius = 12;
        double px = center.getX() + 0.5 + Math.cos(particleAngle) * radius;
        double pz = center.getZ() + 0.5 + Math.sin(particleAngle) * radius;
        double py = center.getY() + 0.5 + Math.sin(particleAngle * 2) * 0.5;

        world.spawnParticle(Particle.CLOUD, px, py, pz, 3, 0.2, 0.2, 0.2, 0.01);
        world.spawnParticle(Particle.END_ROD, px, py + 0.5, pz, 1, 0.1, 0.1, 0.1, 0.01);

        // Center altar particles
        world.spawnParticle(Particle.SPELL_MOB, center.clone().add(0.5, 1.5, 0.5), 2, 0.3, 0.3, 0.3, 1);
    }

    public void remove() {
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }
}
