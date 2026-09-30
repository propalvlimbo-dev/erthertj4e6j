package ru.rooyzee.elytrixparadise.shards;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.holograms.ShardHologramHandler;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ShardManager {

    private final Main plugin;
    private final ShardHologramHandler hologramHandler;
    private final Map<String, ParadiseShard> shards = new ConcurrentHashMap<>();

    public ShardManager(Main plugin) {
        this.plugin = plugin;
        this.hologramHandler = new ShardHologramHandler(plugin);
    }

    private String getBlockKey(Location loc) {
        return loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    public ParadiseShard getShard(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return shards.get(getBlockKey(loc));
    }

    public Collection<ParadiseShard> getAllShards() {
        return shards.values();
    }

    public int getActiveShardsCount() {
        int count = 0;
        for (ParadiseShard shard : shards.values()) {
            if (shard.getState() == ParadiseShard.ShardState.ACTIVE) {
                count++;
            }
        }
        return count;
    }

    public ParadiseShard registerShard(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        String key = getBlockKey(loc);
        ParadiseShard existing = shards.get(key);
        if (existing != null) {
            hologramHandler.createOrUpdateHologram(existing);
            return existing;
        }

        ParadiseShard shard = new ParadiseShard(loc, plugin.getConfigManager().getShardBaseExplosionChance());
        shards.put(key, shard);

        // Ensure block is red glazed terracotta on start
        Block block = loc.getBlock();
        if (block.getType() != Material.RED_GLAZED_TERRACOTTA) {
            block.setType(Material.RED_GLAZED_TERRACOTTA, false);
        }

        hologramHandler.createOrUpdateHologram(shard);
        return shard;
    }

    public void tick() {
        for (ParadiseShard shard : shards.values()) {
            if (shard.getState() == ParadiseShard.ShardState.COOLDOWN) {
                shard.decrementCooldown();
                if (shard.getCooldownRemaining() <= 0) {
                    // Restore to active state
                    shard.setState(ParadiseShard.ShardState.ACTIVE);
                    shard.resetExplosionChance(plugin.getConfigManager().getShardBaseExplosionChance());

                    Location loc = shard.getLocation();
                    Block block = loc.getBlock();
                    block.setType(Material.RED_GLAZED_TERRACOTTA, false);

                    World world = loc.getWorld();
                    if (world != null) {
                        world.spawnParticle(Particle.TOTEM, loc.clone().add(0.5, 1.0, 0.5), 35, 0.4, 0.4, 0.4, 0.1);
                        world.spawnParticle(Particle.FLASH, loc.clone().add(0.5, 1.0, 0.5), 2);
                        world.playSound(loc, Sound.BLOCK_BELL_RESONATE, 1.5f, 1.2f);
                        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.5f);
                    }
                }
                hologramHandler.createOrUpdateHologram(shard);
            }
        }
    }

    /**
     * Вызывается при полном вскапывании/ломании блока осколка.
     */
    public boolean handleShardMined(Player player, Location blockLoc) {
        ParadiseShard shard = getShard(blockLoc);
        if (shard == null) return false;

        String prefix = plugin.getConfigManager().getAdminPrefix();

        if (shard.getState() != ParadiseShard.ShardState.ACTIVE) {
            player.sendMessage(ColorUtil.colorize(prefix + "&cОсколок находится на перезарядке! &fОсталось: &#F8BEFB"
                    + ColorUtil.formatTimeShort(shard.getCooldownRemaining())));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_HIT, 0.8f, 0.5f);
            return true;
        }

        // Check tool (must be a pickaxe)
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem == null || !handItem.getType().name().endsWith("_PICKAXE")) {
            player.sendMessage(ColorUtil.colorize(prefix + "&cДля добычи Осколка Рая необходима кирка!"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return true;
        }

        World world = blockLoc.getWorld();
        Location center = blockLoc.clone().add(0.5, 0.5, 0.5);

        // Visual & audio feedback
        world.spawnParticle(Particle.BLOCK_CRACK, center, 20, 0.3, 0.3, 0.3, Material.RED_GLAZED_TERRACOTTA.createBlockData());
        world.playSound(center, Sound.BLOCK_ANVIL_USE, 0.7f, 1.8f);

        // Roll explosion chance
        double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
        double currentChance = shard.getCurrentExplosionChance();

        if (roll < currentChance) {
            // TRIGGER EXPLOSION!
            triggerShardExplosion(shard, player);
            return true;
        }

        // SUCCESSFUL MINING DROP
        shard.incrementHitCount();
        shard.increaseExplosionChance(
                plugin.getConfigManager().getShardChanceIncreasePerHit(),
                plugin.getConfigManager().getShardMaxExplosionChance()
        );

        // Drop experience
        int expMin = plugin.getConfigManager().getShardExpMin();
        int expMax = plugin.getConfigManager().getShardExpMax();
        int exp = ThreadLocalRandom.current().nextInt(expMin, expMax + 1);
        if (exp > 0) {
            ExperienceOrb orb = (ExperienceOrb) world.spawn(center.clone().add(0, 0.5, 0), ExperienceOrb.class);
            orb.setExperience(exp);
        }

        // Drop loot items on ground
        List<LootItem> lootTable = plugin.getConfigManager().getLootItems();
        boolean droppedAny = false;
        for (LootItem lootItem : lootTable) {
            double lootRoll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
            if (lootRoll <= lootItem.getChance()) {
                ItemStack is = lootItem.createItemStack();
                if (is != null && is.getType() != Material.AIR) {
                    Location dropLoc = blockLoc.clone().add(0.5, 1.1, 0.5);
                    org.bukkit.entity.Item dropped = world.dropItem(dropLoc, is);
                    dropped.setVelocity(new Vector(
                            ThreadLocalRandom.current().nextDouble(-0.06, 0.06),
                            0.2,
                            ThreadLocalRandom.current().nextDouble(-0.06, 0.06)
                    ));
                    droppedAny = true;
                }
            }
        }

        if (droppedAny) {
            world.playSound(center, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        }

        double riskPercent = Math.round(shard.getCurrentExplosionChance() * 10.0) / 10.0;
        ColorUtil.sendActionBar(player, "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &#F8BEFBОсколок Рая &8| &fДобыча... &8| &fРиск: &#F8BEFB" + riskPercent + "%");
        hologramHandler.createOrUpdateHologram(shard);

        return true;
    }

    private void triggerShardExplosion(ParadiseShard shard, Player triggerPlayer) {
        Location loc = shard.getLocation();
        World world = loc.getWorld();
        Location center = loc.clone().add(0.5, 0.5, 0.5);

        // Turn block into gray glazed terracotta
        Block block = loc.getBlock();
        block.setType(Material.GRAY_GLAZED_TERRACOTTA, false);

        // Set cooldown & reset chances
        shard.setState(ParadiseShard.ShardState.COOLDOWN);
        shard.setCooldownRemaining(plugin.getConfigManager().getShardCooldownSeconds());
        shard.resetExplosionChance(plugin.getConfigManager().getShardBaseExplosionChance());

        // Massive explosion effects (sound & particles)
        world.spawnParticle(Particle.EXPLOSION_HUGE, center, 3, 0.5, 0.5, 0.5);
        world.spawnParticle(Particle.FLAME, center, 50, 0.8, 0.8, 0.8, 0.15);
        world.spawnParticle(Particle.SMOKE_LARGE, center, 30, 0.5, 0.5, 0.5, 0.1);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.8f);
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.8f, 0.9f);

        // AOE Damage & knockback (lethal to naked, iron, and half-diamond) WITHOUT block damage
        double radius = plugin.getConfigManager().getShardExplosionRadius();
        double damage = plugin.getConfigManager().getShardExplosionDamage();
        double radiusSq = radius * radius;

        for (Player p : world.getPlayers()) {
            Location pLoc = p.getLocation();
            if (pLoc.distanceSquared(center) <= radiusSq) {
                p.damage(damage);
                Vector knockback = pLoc.toVector().subtract(center.toVector()).normalize();
                knockback.setY(0.55);
                knockback.multiply(1.4);
                p.setVelocity(knockback);
            }
        }

        hologramHandler.createOrUpdateHologram(shard);
    }

    public void clearAllShards() {
        for (ParadiseShard shard : shards.values()) {
            hologramHandler.removeHologram(shard);
        }
        shards.clear();
        hologramHandler.removeAllHolograms();
    }

    public ShardHologramHandler getHologramHandler() {
        return hologramHandler;
    }
}
