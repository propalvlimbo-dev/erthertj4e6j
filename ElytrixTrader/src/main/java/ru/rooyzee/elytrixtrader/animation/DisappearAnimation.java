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
import ru.rooyzee.elytrixtrader.util.ParticleUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Единый стиль безопасного поблочного исчезновения ивентов со схематикой.
 *
 * 1. Блоки схематики мягко растворяются волнами снизу вверх в крошку
 *    своей текстуры (BLOCK_CRACK) и частицы дыма/пыли (единый стиль с огненным аирдропом).
 * 2. Торговец плавно возносится и растворяется в воздухе.
 * 3. Все оригинальные блоки мира (террейн) гарантированно восстанавливаются
 *    в исходное состояние без повреждений ландшафта.
 */
public class DisappearAnimation {

    private static final int    RISE_TICKS     = 100;   // ~5 с общая длительность
    private static final int    DISSOLVE_FROM  = 10;    // тик начала растворения блоков
    private static final int    DISSOLVE_TO    = 85;    // тик завершения растворения блоков
    private static final double RISE_HEIGHT    = 3.5;   // высота подъема торговца
    private static final double SPIN_DEGREES   = 540.0; // поворот торговца

    private static final Particle.DustOptions DUST =
            new Particle.DustOptions(Color.fromRGB(248, 190, 251), 0.8F);
    private static final Particle.DustOptions DUST_FINE =
            new Particle.DustOptions(Color.fromRGB(248, 190, 251), 0.55F);

    private final Main plugin;
    private final Random random = new Random();

    public DisappearAnimation(Main plugin) {
        this.plugin = plugin;
    }

    public void play(TraderInstance instance, TraderRegion region, SchematicSnapshot snapshot, Runnable onComplete) {
        if (instance == null) {
            if (snapshot != null) snapshot.restore();
            if (onComplete != null) onComplete.run();
            return;
        }
        Location start = instance.location().clone();
        World world = start.getWorld();
        if (world == null) {
            if (snapshot != null) snapshot.restore();
            if (onComplete != null) onComplete.run();
            return;
        }

        instance.stopAura();
        instance.hideHolograms();
        instance.raiseHands();

        try {
            world.playSound(start, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
            world.playSound(start, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.8f);
        } catch (Throwable ignored) {}

        List<Shard> shards = collectShards(snapshot);

        new BukkitRunnable() {
            int tick = 0;
            int removed = 0;
            final float startYaw = start.getYaw();

            @Override
            public void run() {
                try {
                    if (!instance.isDespawning() || !plugin.isEnabled()) {
                        cancel();
                        if (snapshot != null) snapshot.restore();
                        if (onComplete != null) onComplete.run();
                        return;
                    }
                    tick++;
                    double p = Math.min(1.0, tick / (double) RISE_TICKS);

                    // Плавный подъем и вращение торговца
                    double ease = p * p * (3.0 - 2.0 * p);
                    double height = RISE_HEIGHT * ease;
                    float yaw = startYaw + (float) (SPIN_DEGREES * ease);
                    instance.setPos(new Location(world,
                            start.getX(), start.getY() + height, start.getZ(), yaw, 0.0f));
                    instance.tickViewers();

                    // Растворение блоков схематики волной снизу вверх
                    if (!shards.isEmpty() && tick >= DISSOLVE_FROM) {
                        double progress = Math.min(1.0, (tick - DISSOLVE_FROM)
                                / (double) (DISSOLVE_TO - DISSOLVE_FROM));
                        int limit = (int) Math.ceil(shards.size() * progress);
                        while (removed < limit && removed < shards.size()) {
                            dissolve(shards.get(removed++), world);
                        }
                    }

                    // Частицы восхождения
                    if (tick % 3 == 0) {
                        double feet = start.getY() + height;
                        world.spawnParticle(ParticleUtil.DUST_PARTICLE,
                                start.getX(), feet + 0.2, start.getZ(), 2, 0.15, 0.2, 0.15, 0, DUST_FINE);
                        if (random.nextInt(3) == 0) {
                            world.spawnParticle(ParticleUtil.CAMPFIRE_SMOKE,
                                    start.getX(), feet, start.getZ(), 1, 0.1, 0.1, 0.1, 0.01);
                        }
                    }

                    if (tick >= RISE_TICKS) {
                        cancel();
                        vanish(instance, start, world, snapshot, onComplete);
                    }
                } catch (Throwable t) {
                    cancel();
                    if (snapshot != null) snapshot.restore();
                    if (onComplete != null) onComplete.run();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private List<Shard> collectShards(SchematicSnapshot snapshot) {
        List<Shard> shards = new ArrayList<>();
        if (snapshot == null) return shards;
        for (Location loc : snapshot.placedBlocks()) {
            if (loc == null || loc.getWorld() == null) continue;
            Block block = loc.getWorld().getBlockAt(loc);
            Material type = block.getType();
            if (type == null || type.isAir()) continue;
            shards.add(new Shard(loc.clone(), block.getBlockData(),
                    loc.getBlockY() + random.nextDouble() * 0.4));
        }
        shards.sort((a, b) -> Double.compare(a.sortKey, b.sortKey));
        return shards;
    }

    private void dissolve(Shard shard, World world) {
        try {
            Location loc = shard.loc;
            world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())
                    .setType(Material.AIR, false);
            double px = loc.getBlockX() + 0.5;
            double py = loc.getBlockY() + 0.5;
            double pz = loc.getBlockZ() + 0.5;

            try {
                world.spawnParticle(ParticleUtil.BLOCK_CRACK, px, py, pz, 4, 0.2, 0.2, 0.2, 0.02, shard.data);
            } catch (Throwable ignored) {}

            if (random.nextInt(4) == 0) {
                world.spawnParticle(ParticleUtil.CLOUD, px, py + 0.1, pz, 1, 0.15, 0.15, 0.15, 0.01);
            }
        } catch (Throwable ignored) {}
    }

    private void vanish(TraderInstance instance, Location start, World world, SchematicSnapshot snapshot, Runnable onComplete) {
        Location at = new Location(world, start.getX(), start.getY() + RISE_HEIGHT + 0.8, start.getZ());
        try {
            world.spawnParticle(ParticleUtil.ENCHANTMENT_TABLE, at, 25, 0.6, 0.6, 0.6, 0.1);
            world.spawnParticle(ParticleUtil.END_ROD, at, 10, 0.3, 0.5, 0.3, 0.03);
            world.spawnParticle(ParticleUtil.CLOUD, at, 15, 0.5, 0.3, 0.5, 0.04);
            world.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);

            if (instance.player() != null) {
                instance.player().setInvisible(true);
            }
            instance.tickViewers();
        } catch (Throwable ignored) {}

        // Гарантированно восстанавливаем оригинальный террейн мира
        if (snapshot != null) {
            snapshot.restore();
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (onComplete != null) onComplete.run();
        }, 5L);
    }

    private static final class Shard {
        final Location loc;
        final BlockData data;
        final double sortKey;

        Shard(Location loc, BlockData data, double sortKey) {
            this.loc = loc;
            this.data = data;
            this.sortKey = sortKey;
        }
    }
}
