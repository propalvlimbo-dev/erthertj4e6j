package ru.rooyzee.elytrixtrader.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;

public class ParticleUtil {

    public static Particle getParticle(String... names) {
        for (String name : names) {
            try {
                return Particle.valueOf(name);
            } catch (Throwable ignored) {}
        }
        return Particle.FLAME;
    }

    public static final Particle HUGE_EXPLOSION = getParticle("EXPLOSION_EMITTER", "EXPLOSION_HUGE", "EXPLOSION");
    public static final Particle LARGE_EXPLOSION = getParticle("EXPLOSION", "EXPLOSION_LARGE");
    public static final Particle LARGE_SMOKE = getParticle("LARGE_SMOKE", "SMOKE_LARGE", "CAMPFIRE_COSY_SMOKE");
    public static final Particle NORMAL_SMOKE = getParticle("SMOKE", "SMOKE_NORMAL");
    public static final Particle CAMPFIRE_SMOKE = getParticle("CAMPFIRE_COSY_SMOKE", "LARGE_SMOKE", "SMOKE_LARGE");
    public static final Particle DUST_PARTICLE = getParticle("DUST", "REDSTONE");
    public static final Particle BLOCK_CRACK = getParticle("BLOCK", "BLOCK_CRACK");
    public static final Particle CLOUD = getParticle("CLOUD");
    public static final Particle FLAME = getParticle("FLAME");
    public static final Particle SOUL_FLAME = getParticle("SOUL_FIRE_FLAME", "SOUL");
    public static final Particle LAVA = getParticle("LAVA");
    public static final Particle PORTAL = getParticle("PORTAL");
    public static final Particle FLASH = getParticle("FLASH");
    public static final Particle TOTEM = getParticle("TOTEM_OF_UNDYING", "TOTEM");
    public static final Particle CRIT = getParticle("CRIT");
    public static final Particle MAGIC_CRIT = getParticle("ENCHANTED_HIT", "CRIT_MAGIC");
    public static final Particle END_ROD = getParticle("END_ROD");
    public static final Particle FIREWORKS = getParticle("FIREWORK", "FIREWORKS_SPARK");
    public static final Particle ENCHANTMENT_TABLE = getParticle("ENCHANT", "ENCHANTMENT_TABLE");
}
