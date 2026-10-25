package ru.rooyzee.elytrixcemetery.graves;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;
import ru.rooyzee.elytrixcemetery.utils.ParticleUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GraveManager {

    private final Main plugin;
    private final Map<String, Grave> gravesByDig = new ConcurrentHashMap<>();
    private final Map<String, Grave> gravesByChest = new ConcurrentHashMap<>();
    private long nextRefillTime;

    public GraveManager(Main plugin) {
        this.plugin = plugin;
        this.nextRefillTime = System.currentTimeMillis() + plugin.getConfigManager().getRefillIntervalSeconds() * 1000L;
    }

    public void registerGraves(List<Grave> graveList) {
        clearAll();
        for (Grave g : graveList) {
            gravesByDig.put(locKey(g.getDigLocation()), g);
            gravesByChest.put(locKey(g.getChestLocation()), g);
            g.updateHologram(plugin);
        }
    }

    public Grave getGraveAt(Location loc) {
        if (loc == null) return null;
        return gravesByDig.get(locKey(loc));
    }

    public Grave getGraveByChest(Location loc) {
        if (loc == null) return null;
        return gravesByChest.get(locKey(loc));
    }

    public Collection<Grave> getAllGraves() {
        return gravesByDig.values();
    }

    public int getDugCount() {
        int count = 0;
        for (Grave g : gravesByDig.values()) {
            if (g.isDug()) count++;
        }
        return count;
    }

    public int getTotalCount() {
        return gravesByDig.size();
    }

    public long getRemainingRefillSeconds() {
        return Math.max(0, (nextRefillTime - System.currentTimeMillis()) / 1000L);
    }

    public String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (now >= nextRefillTime) {
            refillAndBuryAll();
            nextRefillTime = now + plugin.getConfigManager().getRefillIntervalSeconds() * 1000L;
        }
    }

    public void refillAndBuryAll() {
        if (gravesByDig.isEmpty()) return;

        int buried = 0;
        for (Grave g : gravesByDig.values()) {
            if (g.isDug()) {
                g.bury(plugin);
                buried++;
            }
        }

        Location center = plugin.getConfigManager().getCenterLocation();
        World world = center != null ? center.getWorld() : null;

        if (world != null) {
            world.playSound(center, Sound.BLOCK_BELL_RESONATE, 2.0f, 0.8f);
            world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.5f);
            ParticleUtil.spawn(world, ParticleUtil.SOUL, center.clone().add(0, 2, 0), 50, 15, 2, 15, 0.05);

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &#F8BEFBМогилы на кладбище закопаны! Лут обновлён."));
            }
        }

        plugin.getLogger().info("Могилы на кладбище обновлены и закопаны (" + buried + " раскопанных).");
    }

    public void clearAll() {
        for (Grave g : gravesByDig.values()) {
            g.removeHologram();
        }
        gravesByDig.clear();
        gravesByChest.clear();
    }

    private String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
