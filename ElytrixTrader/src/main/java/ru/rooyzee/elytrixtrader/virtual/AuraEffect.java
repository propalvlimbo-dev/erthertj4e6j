package ru.rooyzee.elytrixtrader.virtual;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.rooyzee.elytrixtrader.Main;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Аура торговца — ТИХИЙ вихрь зелёных частиц вокруг NPC.
 *
 * Требование: красиво, зелёное, вращается, но не бросается в глаза.
 * Что сделано и почему:
 *
 *  - Основа — мелкая цветная «пыль» Particle.REDSTONE (DustOptions):
 *    размер 0.65 (заметно меньше стандартного), цвет #8CE99A — акцентный
 *    зелёный торговца. Пыль неподвижна и не мерцает — просто мягкие точки.
 *  - 7 орбит с индивидуальным наклоном плоскости, пульсом радиуса и
 *    вертикальной волной — вихрь «живой», но спокойный; спавн раз в 3 тика,
 *    точки не сливаются в шлейф (не «слизь» и не сплошное кольцо).
 *  - Только пыль и ничего кроме пыли: изумрудные искры VILLAGER_HAPPY,
 *    END_ROD и PORTAL убраны — вокруг летает одна тихая зелёная «дымка».
 *
 * Частицы не тикаются клиентом (в отличие от VirtualExperienceOrb —
 * см. историю в README), поэтому движение идеально плавное всегда.
 */
public final class AuraEffect {

    private static final int    ORB_COUNT    = 7;     // точек на вихре
    private static final double RADIUS       = 1.3;   // радиус орбиты
    private static final double HEIGHT       = 1.15;  // высота центра вихря над ногами NPC
    private static final double SPEED        = 0.02;  // рад/тик — медленное вращение
    private static final double VIEW_RADIUS  = 40.0;  // кому показывать ауру
    private static final double VIEW_RADIUS_SQ = VIEW_RADIUS * VIEW_RADIUS;

    /** Мелкая зелёная пыль — основной элемент ауры. */
    private static final Particle.DustOptions DUST =
            new Particle.DustOptions(Color.fromRGB(140, 233, 154), 0.65F);

    private final Main plugin;
    private final Supplier<Location> anchor; // живая позиция торговца

    private BukkitTask task;
    private int tick;

    public AuraEffect(Main plugin, Supplier<Location> anchor) {
        this.plugin = plugin;
        this.anchor = anchor;
    }

    public void start() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 2L, 1L);
    }

    public void stop() {
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignored) {}
            task = null;
        }
    }

    public boolean running() {
        return task != null;
    }

    private void tick() {
        Location base = anchor.get();
        if (base == null || base.getWorld() == null) {
            stop();
            return;
        }
        World world = base.getWorld();
        double time = tick * SPEED;
        tick++;

        List<Player> visible = viewers(world, base);
        if (visible.isEmpty()) {
            return;
        }

        // 1. Зелёная пыль по орбитам — раз в 3 тика, по одной точке:
        //    тихо, без шлейфа, читается именно вращение.
        if (tick % 3 == 0) {
            for (int i = 0; i < ORB_COUNT; i++) {
                double[] p = orbPosition(base, i, time);
                for (Player viewer : visible) {
                    viewer.spawnParticle(Particle.REDSTONE, p[0], p[1], p[2],
                            1, 0.01, 0.01, 0.01, 0, DUST);
                }
            }
        }

        // Изумрудные искры (VILLAGER_HAPPY) убраны по запросу: в ауре
        // остаётся ТОЛЬКО зелёная пыль, летающая по орбитам вокруг NPC.
    }

    /** Позиция точки №index на вихре в момент time. */
    private double[] orbPosition(Location base, int index, double time) {
        double angle = (index / (double) ORB_COUNT) * Math.PI * 2.0 + time;
        double tilt = Math.PI * 0.25 * (1.0 + Math.sin(index * 1.37)); // свой наклон плоскости
        double r = RADIUS + 0.12 * Math.sin(index * 2.09 + 0.5);
        double radius = r + 0.06 * Math.sin(time * 0.40 + index * 0.83); // мягкий пульс
        double yOrbit = Math.sin(angle) * radius * Math.sin(tilt);
        double yWave = 0.12 * Math.sin(time * 0.80 + index * 1.61);
        double x = base.getX() + Math.cos(angle) * radius;
        double z = base.getZ() + Math.sin(angle) * radius * Math.cos(tilt);
        double y = Math.max(base.getY() + 0.55, base.getY() + HEIGHT + yOrbit + yWave);
        return new double[]{x, y, z};
    }

    /** Игроки мира, которым видна аура (в радиусе {@link #VIEW_RADIUS}). */
    private List<Player> viewers(World world, Location base) {
        List<Player> result = new ArrayList<>(4);
        for (Player online : world.getPlayers()) {
            if (!online.isOnline()) continue;
            if (online.getLocation().distanceSquared(base) <= VIEW_RADIUS_SQ) {
                result.add(online);
            }
        }
        return result;
    }
}
