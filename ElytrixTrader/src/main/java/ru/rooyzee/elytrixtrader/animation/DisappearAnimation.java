package ru.rooyzee.elytrixtrader.animation;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.TraderInstance;
import ru.rooyzee.elytrixtrader.region.TraderRegion;
import ru.rooyzee.elytrixtrader.schematic.SchematicSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Красивое и СДЕРЖАННОЕ завершение ивента (~7 с): «торговец возносится».
 *
 *  1. Блоки схематики мягко растворяются снизу вверх — каждый в свой момент
 *     (высота + лёгкий разброс, граница «дышит»), в тихую крошку своей
 *     текстуры и редкую зелёную пыль.
 *  2. Торговец плавно поднимается (~3.5 бл) и медленно поворачивается
 *     (~1.5 оборота). Движение — smootherstep: без рывка на старте
 *     и без резкой остановки в конце.
 *  3. Под ним — тонкий столб зелёной пыли (как дорожка света), вокруг —
 *     две тихие точки по спирали.
 *  4. Финал: мягкое расходящееся кольцо пыли, немного чар и искр,
 *     негромкие звуки. Без взрывов и салютов — «не через чур».
 */
public class DisappearAnimation {

    private static final int    RISE_TICKS     = 140;   // 7 с — общая длительность
    private static final int    DISSOLVE_FROM  = 12;    // тик начала растворения блоков
    private static final int    DISSOLVE_TO    = 118;   // тик, к которому исчезнут все
    private static final double RISE_HEIGHT    = 3.5;   // итоговая высота подъёма, бл
    private static final double SPIN_DEGREES   = 540.0; // суммарный поворот (1.5 оборота)

    /** Зелёная пыль — та же, что в ауре: единый тихий стиль. */
    private static final Particle.DustOptions DUST =
            new Particle.DustOptions(Color.fromRGB(140, 233, 154), 0.8F);
    /** Мелкая пыль для столба и кольца. */
    private static final Particle.DustOptions DUST_FINE =
            new Particle.DustOptions(Color.fromRGB(190, 245, 200), 0.55F);

    private final Main plugin;
    private final Random random = new Random();

    public DisappearAnimation(Main plugin) {
        this.plugin = plugin;
    }

    public void play(TraderInstance instance, TraderRegion region, SchematicSnapshot snapshot, Runnable onComplete) {
        if (instance == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        Location start = instance.location().clone();
        World world = start.getWorld();
        if (world == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        instance.stopAura();
        instance.hideHolograms();
        instance.raiseHands();
        try {
            // Тихий старт: маяк «просыпается» почти беззвучно
            world.playSound(start, Sound.BLOCK_BEACON_ACTIVATE, 0.3f, 1.7f);
        } catch (Throwable ignored) {}

        List<Shard> shards = collectShards(snapshot);

        new BukkitRunnable() {
            int tick = 0;
            int removed = 0;
            final float startYaw = start.getYaw();

            @Override
            public void run() {
                try {
                    if (!instance.isDespawning()) {
                        cancel();
                        if (onComplete != null) onComplete.run();
                        return;
                    }
                    tick++;
                    double p = Math.min(1.0, tick / (double) RISE_TICKS);

                    // ── Торговец: подъём и вращение одним плавным темпом ──
                    // smootherstep: 0 на старте, разгон в середине, мягкое
                    // торможение к финалу — никаких рывков и «щелчков».
                    double ease = p * p * p * (p * (6.0 * p - 15.0) + 10.0);
                    double height = RISE_HEIGHT * ease;
                    float yaw = startYaw + (float) (SPIN_DEGREES * ease);
                    instance.setPos(new Location(world,
                            start.getX(), start.getY() + height, start.getZ(), yaw, 0.0f));
                    instance.tickViewers();

                    // ── Блоки: мягкое растворение снизу вверх ──
                    if (!shards.isEmpty() && tick >= DISSOLVE_FROM) {
                        double progress = Math.min(1.0, (tick - DISSOLVE_FROM)
                                / (double) (DISSOLVE_TO - DISSOLVE_FROM));
                        int limit = (int) Math.ceil(shards.size() * progress);
                        while (removed < limit && removed < shards.size()) {
                            dissolve(shards.get(removed++), world);
                        }
                    }

                    // ── Тонкий столб пыли под торговцем (дорожка света) ──
                    if (tick % 4 == 0) {
                        double feet = start.getY() + height;
                        int steps = Math.max(2, (int) (height / 0.9));
                        for (int k = 0; k <= steps; k++) {
                            double y = start.getY() + (feet - start.getY()) * (k / (double) steps);
                            world.spawnParticle(Particle.REDSTONE,
                                    start.getX(), y, start.getZ(), 1, 0.05, 0.05, 0.05, 0, DUST_FINE);
                        }
                    }

                    // ── Две тихие точки по спирали вокруг NPC ──
                    if (tick % 5 == 0) {
                        double ang = tick * 0.22;
                        double cy = start.getY() + 0.4 + height;
                        for (int k = 0; k < 2; k++) {
                            double a = ang + k * Math.PI;
                            world.spawnParticle(Particle.REDSTONE,
                                    start.getX() + Math.cos(a) * 0.8, cy, start.getZ() + Math.sin(a) * 0.8,
                                    1, 0.02, 0.04, 0.02, 0, DUST);
                        }
                    }

                    if (tick >= RISE_TICKS) {
                        cancel();
                        vanish(instance, start, world, onComplete);
                    }
                } catch (Throwable t) {
                    cancel();
                    if (onComplete != null) onComplete.run();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Блоки схематики (только её собственные, без террейна), снизу вверх. */
    private List<Shard> collectShards(SchematicSnapshot snapshot) {
        List<Shard> shards = new ArrayList<>();
        if (snapshot == null) return shards;
        for (Location loc : snapshot.placedBlocks()) {
            if (loc == null || loc.getWorld() == null) continue;
            Block block = loc.getWorld().getBlockAt(loc);
            Material type = block.getType();
            if (type == null || type.isAir()) continue;
            shards.add(new Shard(loc.clone(), block.getBlockData(),
                    loc.getBlockY() + random.nextDouble() * 0.55));
        }
        shards.sort((a, b) -> Double.compare(a.sortKey, b.sortKey));
        return shards;
    }

    /** Один блок тихо исчезает: пара крошек текстуры, иногда пылинка. */
    private void dissolve(Shard shard, World world) {
        try {
            Location loc = shard.loc;
            world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())
                    .setType(Material.AIR, false);
            double px = loc.getBlockX() + 0.5;
            double py = loc.getBlockY() + 0.5;
            double pz = loc.getBlockZ() + 0.5;
            world.spawnParticle(Particle.BLOCK_CRACK, px, py, pz, 2, 0.18, 0.18, 0.18, 0.005, shard.data);
            if (random.nextInt(8) == 0) {
                world.spawnParticle(Particle.REDSTONE, px, py + 0.1, pz, 1, 0.2, 0.25, 0.2, 0, DUST_FINE);
            }
        } catch (Throwable ignored) {}
    }

    /** Финал: мягкое кольцо пыли, немного чар и искр, негромкие звуки. */
    private void vanish(TraderInstance instance, Location start, World world, Runnable onComplete) {
        Location at = new Location(world, start.getX(), start.getY() + RISE_HEIGHT + 1.0, start.getZ());
        try {
            // Тихая вспышка чар и пара искр
            world.spawnParticle(Particle.ENCHANTMENT_TABLE, at, 18, 0.5, 0.5, 0.5, 0.1);
            world.spawnParticle(Particle.END_ROD, at, 6, 0.25, 0.6, 0.25, 0.02);
            // Мягкое расходящееся кольцо зелёной пыли
            for (int step = 0; step < 3; step++) {
                double radius = 0.6 + step * 0.7;
                int points = 10 + step * 4;
                for (int k = 0; k < points; k++) {
                    double a = (k / (double) points) * Math.PI * 2.0;
                    world.spawnParticle(Particle.REDSTONE,
                            at.getX() + Math.cos(a) * radius, at.getY() - 0.6, at.getZ() + Math.sin(a) * radius,
                            1, 0.01, 0.01, 0.01, 0, DUST);
                }
            }
            world.playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.5f);
            world.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.35f, 1.1f);
            if (instance.player() != null) {
                instance.player().setInvisible(true);
            }
            instance.tickViewers();
        } catch (Throwable ignored) {}

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (onComplete != null) onComplete.run();
        }, 8L);
    }

    /** Блок схематики: где стоит и чем «крошится». */
    private static final class Shard {
        final Location loc;
        final BlockData data;
        final double sortKey; // высота + разброс: порядок растворения

        Shard(Location loc, BlockData data, double sortKey) {
            this.loc = loc;
            this.data = data;
            this.sortKey = sortKey;
        }
    }
}
