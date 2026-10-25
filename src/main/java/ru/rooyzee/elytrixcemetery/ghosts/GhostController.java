package ru.rooyzee.elytrixcemetery.ghosts;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.graves.Grave;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;
import ru.rooyzee.elytrixcemetery.utils.ParticleUtil;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class GhostController {

    private final Main plugin;
    private final NamespacedKey ghostPdc;
    private final NamespacedKey guardianPdc;
    private final Set<UUID> activeGhosts = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> activeGuardians = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Map<UUID, Grave> guardianGraves = new ConcurrentHashMap<>();
    private long nextRespawnTime = 0L;

    private static EntityType GHOST_ENTITY_TYPE;
    private static Sound AMBIENT_SOUND;
    private static Sound DEATH_SOUND;

    static {
        try {
            GHOST_ENTITY_TYPE = EntityType.valueOf("ALLAY");
        } catch (Throwable t) {
            try {
                GHOST_ENTITY_TYPE = EntityType.valueOf("VEX");
            } catch (Throwable t2) {
                GHOST_ENTITY_TYPE = EntityType.BAT;
            }
        }

        try {
            AMBIENT_SOUND = Sound.valueOf("ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM");
        } catch (Throwable t) {
            try {
                AMBIENT_SOUND = Sound.valueOf("ENTITY_VEX_AMBIENT");
            } catch (Throwable t2) {
                AMBIENT_SOUND = Sound.ENTITY_BAT_TAKEOFF;
            }
        }

        try {
            DEATH_SOUND = Sound.valueOf("ENTITY_ALLAY_DEATH");
        } catch (Throwable t) {
            try {
                DEATH_SOUND = Sound.valueOf("ENTITY_VEX_DEATH");
            } catch (Throwable t2) {
                DEATH_SOUND = Sound.ENTITY_BAT_DEATH;
            }
        }
    }

    public GhostController(Main plugin) {
        this.plugin = plugin;
        this.ghostPdc = new NamespacedKey(plugin, "cemetery_ghost");
        this.guardianPdc = new NamespacedKey(plugin, "cemetery_guardian");
    }

    public boolean isGhost(Entity entity) {
        if (entity == null) return false;
        if (activeGhosts.contains(entity.getUniqueId())) return true;
        try {
            return entity.getPersistentDataContainer().has(ghostPdc, PersistentDataType.BYTE);
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isGuardian(Entity entity) {
        if (entity == null) return false;
        if (activeGuardians.contains(entity.getUniqueId())) return true;
        try {
            return entity.getPersistentDataContainer().has(guardianPdc, PersistentDataType.BYTE);
        } catch (Throwable t) {
            return false;
        }
    }

    public int getAliveCount() {
        return activeGhosts.size();
    }

    public void tick() {
        Location center = plugin.getConfigManager().getCenterLocation();
        if (center == null || center.getWorld() == null) return;
        World world = center.getWorld();

        int maxRadius = plugin.getConfigManager().getBoundaryRadius();

        // 1. Проверяем живых призраков и удерживаем их в границах кладбища
        Iterator<UUID> it = activeGhosts.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity ent = Bukkit.getEntity(id);
            if (ent == null || ent.isDead() || !ent.isValid()) {
                it.remove();
                continue;
            }

            // Шлейф частиц за призраком
            ParticleUtil.spawn(world, ParticleUtil.SOUL, ent.getLocation().clone().add(0, 0.3, 0), 2, 0.1, 0.1, 0.1, 0.01);

            // Если призрак улетел за ограду кладбища — возвращаем его внутрь
            double distSq = ent.getLocation().distanceSquared(center);
            if (distSq > (maxRadius - 3) * (maxRadius - 3)) {
                Location back = getRandomSpawnPoint(center, 25);
                ent.teleport(back);
                ParticleUtil.spawn(world, ParticleUtil.PORTAL, back, 15, 0.3, 0.3, 0.3, 0.05);
            }
        }

        // 2. Проверяем стражей могил
        Iterator<UUID> git = activeGuardians.iterator();
        while (git.hasNext()) {
            UUID id = git.next();
            Entity ent = Bukkit.getEntity(id);
            if (ent == null || ent.isDead() || !ent.isValid()) {
                git.remove();
                guardianGraves.remove(id);
            }
        }

        // 3. Спавн / респавн новой волны призраков
        int targetCount = plugin.getConfigManager().getGhostCount();
        if (activeGhosts.size() < targetCount) {
            long now = System.currentTimeMillis();
            if (now >= nextRespawnTime) {
                spawnUpTo(center, targetCount);
                nextRespawnTime = now + plugin.getConfigManager().getGhostRespawnCooldownSeconds() * 1000L;
            }
        }
    }

    public void spawnUpTo(Location center, int targetCount) {
        World world = center.getWorld();
        if (world == null) return;

        double hp = plugin.getConfigManager().getGhostHealth();

        while (activeGhosts.size() < targetCount) {
            Location loc = getRandomSpawnPoint(center, 35);
            Entity raw = world.spawnEntity(loc, GHOST_ENTITY_TYPE);
            if (!(raw instanceof LivingEntity)) {
                raw.remove();
                break;
            }

            LivingEntity ghost = (LivingEntity) raw;
            try {
                ghost.getPersistentDataContainer().set(ghostPdc, PersistentDataType.BYTE, (byte) 1);
            } catch (Throwable ignored) {}

            ghost.setRemoveWhenFarAway(false);
            ghost.setCanPickupItems(false);
            ghost.setCustomName(ColorUtil.colorize("&#F8BEFB&lПризрак"));
            ghost.setCustomNameVisible(true);

            AttributeInstance maxHp = ghost.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHp != null) maxHp.setBaseValue(hp);
            ghost.setHealth(hp);

            disableAllayDuplication(ghost);

            activeGhosts.add(ghost.getUniqueId());

            ParticleUtil.spawn(world, ParticleUtil.SOUL, loc, 12, 0.3, 0.3, 0.3, 0.05);
            world.playSound(loc, AMBIENT_SOUND, 1.0f, 0.8f);
        }
    }

    public void spawnGuardian(Location loc, Player player, Grave grave) {
        World world = loc.getWorld();
        if (world == null) return;

        Entity raw = world.spawnEntity(loc.clone().add(0, 0.2, 0), EntityType.WITHER_SKELETON);
        if (!(raw instanceof LivingEntity)) return;

        LivingEntity le = (LivingEntity) raw;
        try {
            le.getPersistentDataContainer().set(guardianPdc, PersistentDataType.BYTE, (byte) 1);
        } catch (Throwable ignored) {}

        le.setRemoveWhenFarAway(false);
        le.setCustomName(ColorUtil.colorize("&#F8BEFB&lОсквернённый Страж"));
        le.setCustomNameVisible(true);

        double hp = 80.0;
        AttributeInstance maxHp = le.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHp != null) maxHp.setBaseValue(hp);
        le.setHealth(hp);

        // Экипировка стража
        EntityEquipment eq = le.getEquipment();
        if (eq != null) {
            ItemStack sword = new ItemStack(matchMaterial("NETHERITE_SWORD", "DIAMOND_SWORD"));
            sword.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 5);
            sword.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
            eq.setItemInMainHand(sword);
            eq.setItemInMainHandDropChance(0.0f);

            ItemStack helm = new ItemStack(matchMaterial("NETHERITE_HELMET", "DIAMOND_HELMET"));
            helm.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
            eq.setHelmet(helm);
            eq.setHelmetDropChance(0.0f);

            ItemStack chest = new ItemStack(matchMaterial("NETHERITE_CHESTPLATE", "DIAMOND_CHESTPLATE"));
            eq.setChestplate(chest);
            eq.setChestplateDropChance(0.0f);
        }

        le.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        le.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 0, false, false));
        le.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));

        if (le instanceof Monster && player != null) {
            ((Monster) le).setTarget(player);
        }

        activeGuardians.add(le.getUniqueId());
        if (grave != null) {
            guardianGraves.put(le.getUniqueId(), grave);
        }

        // Эффекты появления
        world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.7f);
        world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.9f);
        ParticleUtil.spawn(world, ParticleUtil.SOUL, loc.clone().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.1);
        ParticleUtil.spawn(world, ParticleUtil.PORTAL, loc.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.2);

        if (player != null) {
            player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cИз раскопанной могилы восстал &#F8BEFB&lОсквернённый Страж&c!"));
        }
    }

    private void disableAllayDuplication(LivingEntity ghost) {
        try {
            Class<?> allayClass = Class.forName("org.bukkit.entity.Allay");
            if (allayClass.isInstance(ghost)) {
                Method m1 = allayClass.getMethod("setCanDuplicate", boolean.class);
                m1.invoke(ghost, false);
                Method m2 = allayClass.getMethod("setDuplicationCooldown", long.class);
                m2.invoke(ghost, Long.MAX_VALUE);
            }
        } catch (Throwable ignored) {}
    }

    public void handleDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (isGhost(dead)) {
            activeGhosts.remove(dead.getUniqueId());
            event.getDrops().clear();
            event.setDroppedExp(15);

            List<ItemStack> drops = plugin.getLootStorageManager().rollGhostDrops();
            for (ItemStack is : drops) {
                if (is != null) event.getDrops().add(is.clone());
            }

            Location loc = dead.getLocation();
            dead.getWorld().playSound(loc, DEATH_SOUND, 1.2f, 0.7f);
            dead.getWorld().playSound(loc, Sound.BLOCK_SOUL_SAND_BREAK, 1.5f, 0.8f);
            ParticleUtil.spawn(dead.getWorld(), ParticleUtil.SOUL, loc.clone().add(0, 0.5, 0), 25, 0.4, 0.4, 0.4, 0.05);

            Player killer = dead.getKiller();
            if (killer != null) {
                killer.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &aВы повергли &fПризрака&a и получили ценный дроп!"));
            }
        } else if (isGuardian(dead)) {
            activeGuardians.remove(dead.getUniqueId());
            Grave g = guardianGraves.remove(dead.getUniqueId());

            event.getDrops().clear();
            event.setDroppedExp(50);

            List<ItemStack> drops = plugin.getLootStorageManager().rollGuardianDrops();
            for (ItemStack is : drops) {
                if (is != null) event.getDrops().add(is.clone());
            }

            Location loc = dead.getLocation();
            dead.getWorld().playSound(loc, Sound.ENTITY_WITHER_DEATH, 1.5f, 0.8f);
            ParticleUtil.spawn(dead.getWorld(), ParticleUtil.SOUL, loc.clone().add(0, 1.0, 0), 50, 0.6, 1.0, 0.6, 0.1);

            Player killer = dead.getKiller();
            if (killer != null) {
                killer.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &aВы уничтожили &#F8BEFB&lОсквернённого Стража&a! Получен легендарный лут."));
            }
        }
    }

    public void clearAll() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity ent : world.getEntities()) {
                if (isGhost(ent) || isGuardian(ent)) {
                    ent.remove();
                }
            }
        }
        activeGhosts.clear();
        activeGuardians.clear();
        guardianGraves.clear();
    }

    private Location getRandomSpawnPoint(Location center, int radius) {
        World world = center.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int dx = r.nextInt(-radius, radius + 1);
        int dz = r.nextInt(-radius, radius + 1);
        int x = center.getBlockX() + dx;
        int z = center.getBlockZ() + dz;
        int y = plugin.getSchematicManager().getGenerator().findSurfaceY(world, x, z) + 2 + r.nextInt(3);
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private Material matchMaterial(String primary, String fallback) {
        Material m = Material.matchMaterial(primary);
        if (m != null) return m;
        m = Material.matchMaterial(fallback);
        return m != null ? m : Material.DIAMOND_SWORD;
    }
}
