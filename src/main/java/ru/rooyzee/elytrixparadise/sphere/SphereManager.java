package ru.rooyzee.elytrixparadise.sphere;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class SphereManager {

    private final Main plugin;
    private SphereState state = SphereState.INTACT;

    private Location sphereHighCenter;
    private Location sphereFallenCenter;

    private final List<SphereBlockInfo> sphereBlocks = new ArrayList<>();
    private final List<ChainNode> chains = new ArrayList<>();

    private int maxSphereHp = 200;
    private int currentSphereHp = 200;

    // Механика 6 взрывов сферы и фазовый барьер
    private int explosionCount = 0;
    private final int maxExplosions = 6;
    private double currentExplosionChance = 8.0;
    private final double baseExplosionChance = 8.0;
    private final double explosionChanceStep = 1.2;

    private long shieldUntil = 0L; // Время окончания фазового барьера
    private final Map<UUID, Long> playerLastHitTime = new ConcurrentHashMap<>();

    private int cooldownSeconds = 10800; // 3 часа (3 * 3600 = 10800 сек)
    private int cooldownRemaining = 0;

    private final Map<String, List<ArmorStand>> fallbackHolograms = new ConcurrentHashMap<>();
    private final boolean decentHologramsPresent;

    private BukkitTask animationTask = null;

    public SphereManager(Main plugin) {
        this.plugin = plugin;
        this.decentHologramsPresent = Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
    }

    public void init() {
        clearAllHolograms();
        Location center = plugin.getConfigManager().getCenterLocation();
        if (center == null || center.getWorld() == null) return;
        World world = center.getWorld();

        this.maxSphereHp = plugin.getConfig().getInt("sphere.max-hp", 200);
        this.currentSphereHp = maxSphereHp;
        this.cooldownSeconds = plugin.getConfig().getInt("sphere.cooldown-seconds", 10800);
        this.explosionCount = 0;
        this.currentExplosionChance = baseExplosionChance;
        this.shieldUntil = 0L;
        this.playerLastHitTime.clear();

        // Сканирование существующей сферы и 6 цепей из схематики
        scanExistingSphereAndChains(center);
        updateAllHolograms();
    }

    public void scanExistingSphereAndChains(Location center) {
        sphereBlocks.clear();
        chains.clear();

        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        int scanRadiusXZ = 25;
        int minY = Math.max(0, cy);
        int maxY = Math.min(255, cy + 40);

        List<Location> foundSphereLocs = new ArrayList<>();
        List<Location> foundChainLocs = new ArrayList<>();

        for (int x = cx - scanRadiusXZ; x <= cx + scanRadiusXZ; x++) {
            for (int z = cz - scanRadiusXZ; z <= cz + scanRadiusXZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    Material type = b.getType();
                    if (type == Material.BLUE_GLAZED_TERRACOTTA || type == Material.GRAY_GLAZED_TERRACOTTA) {
                        foundSphereLocs.add(b.getLocation());
                    } else if (type == Material.CHAIN) {
                        foundChainLocs.add(b.getLocation());
                    }
                }
            }
        }

        if (!foundSphereLocs.isEmpty()) {
            double sumX = 0, sumY = 0, sumZ = 0;
            for (Location loc : foundSphereLocs) {
                sumX += loc.getBlockX();
                sumY += loc.getBlockY();
                sumZ += loc.getBlockZ();
            }
            int avgX = (int) Math.round(sumX / foundSphereLocs.size());
            int avgY = (int) Math.round(sumY / foundSphereLocs.size());
            int avgZ = (int) Math.round(sumZ / foundSphereLocs.size());

            this.sphereHighCenter = new Location(world, avgX, avgY, avgZ);
            this.sphereFallenCenter = new Location(world, avgX, cy + 1, avgZ);

            for (Location loc : foundSphereLocs) {
                Block b = loc.getBlock();
                int rx = loc.getBlockX() - avgX;
                int ry = loc.getBlockY() - avgY;
                int rz = loc.getBlockZ() - avgZ;
                sphereBlocks.add(new SphereBlockInfo(loc, rx, ry, rz, Material.BLUE_GLAZED_TERRACOTTA, Material.BLUE_GLAZED_TERRACOTTA.createBlockData()));
            }

            plugin.getLogger().info("Найдено Сердце Рая: " + sphereBlocks.size() + " блоков на высоте Y=" + avgY);
        } else {
            this.sphereHighCenter = new Location(world, cx, cy + 18, cz);
            this.sphereFallenCenter = new Location(world, cx, cy + 1, cz);
        }

        // 6 Цепей: 4 горизонтальных + 2 вертикальных
        int chainHp = plugin.getConfig().getInt("sphere.chain-hp", 50);
        ChainNode north = new ChainNode(1, "Северная цепь", chainHp);
        ChainNode south = new ChainNode(2, "Южная цепь", chainHp);
        ChainNode east = new ChainNode(3, "Восточная цепь", chainHp);
        ChainNode west = new ChainNode(4, "Западная цепь", chainHp);
        ChainNode top = new ChainNode(5, "Верхняя цепь", chainHp);
        ChainNode bottom = new ChainNode(6, "Нижняя цепь", chainHp);

        int sphereCenterX = sphereHighCenter.getBlockX();
        int sphereCenterY = sphereHighCenter.getBlockY();
        int sphereCenterZ = sphereHighCenter.getBlockZ();

        for (Location cLoc : foundChainLocs) {
            int dx = cLoc.getBlockX() - sphereCenterX;
            int dy = cLoc.getBlockY() - sphereCenterY;
            int dz = cLoc.getBlockZ() - sphereCenterZ;
            Block b = cLoc.getBlock();

            // Вертикальные цепи
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                if (dy > 1) {
                    top.addBlock(cLoc, b.getBlockData());
                    continue;
                } else if (dy < -1) {
                    bottom.addBlock(cLoc, b.getBlockData());
                    continue;
                }
            }

            // Горизонтальные цепи
            if (Math.abs(dz) >= Math.abs(dx)) {
                if (dz > 0) {
                    north.addBlock(cLoc, b.getBlockData());
                } else {
                    south.addBlock(cLoc, b.getBlockData());
                }
            } else {
                if (dx > 0) {
                    east.addBlock(cLoc, b.getBlockData());
                } else {
                    west.addBlock(cLoc, b.getBlockData());
                }
            }
        }

        if (!north.getChainBlocks().isEmpty()) chains.add(north);
        if (!south.getChainBlocks().isEmpty()) chains.add(south);
        if (!east.getChainBlocks().isEmpty()) chains.add(east);
        if (!west.getChainBlocks().isEmpty()) chains.add(west);
        if (!top.getChainBlocks().isEmpty()) chains.add(top);
        if (!bottom.getChainBlocks().isEmpty()) chains.add(bottom);

        plugin.getLogger().info("Зарегистрировано " + chains.size() + " цепей Сердца Рая (всего " + foundChainLocs.size() + " блоков).");
    }

    public void clearChainBlocks(ChainNode chain) {
        for (Location loc : chain.getChainBlocks()) {
            Block b = loc.getBlock();
            if (b.getType() == Material.CHAIN) {
                b.setType(Material.AIR, false);
            }
        }
    }

    public void restoreChainBlocks(ChainNode chain) {
        for (Map.Entry<Location, BlockData> entry : chain.getOriginalData().entrySet()) {
            Block b = entry.getKey().getBlock();
            b.setBlockData(entry.getValue(), false);
        }
    }

    public void drawSphereAt(Location center, Material mat) {
        if (center == null || center.getWorld() == null) return;
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (SphereBlockInfo info : sphereBlocks) {
            Block b = world.getBlockAt(cx + info.getRelX(), cy + info.getRelY(), cz + info.getRelZ());
            b.setType(mat, false);
        }
    }

    public void clearSphereAt(Location center) {
        if (center == null || center.getWorld() == null) return;
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (SphereBlockInfo info : sphereBlocks) {
            Block b = world.getBlockAt(cx + info.getRelX(), cy + info.getRelY(), cz + info.getRelZ());
            b.setType(Material.AIR, false);
        }
    }

    public void renderFallenSphereExplosionState() {
        if (sphereFallenCenter == null || sphereFallenCenter.getWorld() == null) return;
        World world = sphereFallenCenter.getWorld();
        int cx = sphereFallenCenter.getBlockX();
        int cy = sphereFallenCenter.getBlockY();
        int cz = sphereFallenCenter.getBlockZ();

        int total = sphereBlocks.size();
        int grayCount = Math.min(total, (explosionCount * total) / maxExplosions);

        for (int i = 0; i < total; i++) {
            SphereBlockInfo info = sphereBlocks.get(i);
            Block b = world.getBlockAt(cx + info.getRelX(), cy + info.getRelY(), cz + info.getRelZ());
            Material target = (i < grayCount) ? Material.GRAY_GLAZED_TERRACOTTA : Material.BLUE_GLAZED_TERRACOTTA;
            b.setType(target, false);
        }
    }

    public boolean isSphereBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Location activeCenter = (state == SphereState.FALLEN_MINING) ? sphereFallenCenter : sphereHighCenter;
        if (activeCenter == null || !loc.getWorld().equals(activeCenter.getWorld())) return false;

        int dx = loc.getBlockX() - activeCenter.getBlockX();
        int dy = loc.getBlockY() - activeCenter.getBlockY();
        int dz = loc.getBlockZ() - activeCenter.getBlockZ();

        for (SphereBlockInfo info : sphereBlocks) {
            if (info.getRelX() == dx && info.getRelY() == dy && info.getRelZ() == dz) {
                return true;
            }
        }
        return false;
    }

    public ChainNode getChainAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        for (ChainNode chain : chains) {
            if (!chain.isBroken()) {
                for (Location cLoc : chain.getChainBlocks()) {
                    if (cLoc.getBlockX() == loc.getBlockX()
                            && cLoc.getBlockY() == loc.getBlockY()
                            && cLoc.getBlockZ() == loc.getBlockZ()) {
                        return chain;
                    }
                }
            }
        }
        return null;
    }

    public boolean handleChainBreak(Player player, Location blockLoc) {
        if (state != SphereState.INTACT) return false;
        ChainNode chain = getChainAt(blockLoc);
        if (chain == null || chain.isBroken()) return false;

        String prefix = plugin.getConfigManager().getAdminPrefix();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || !hand.getType().name().endsWith("_PICKAXE")) {
            player.sendMessage(ColorUtil.colorize(prefix + "&cДля разрушения цепей Рая необходима кирка!"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return true;
        }

        chain.damage(1);
        World world = blockLoc.getWorld();
        world.playSound(blockLoc, Sound.BLOCK_CHAIN_HIT, 1.2f, 1.0f);
        world.playSound(blockLoc, Sound.BLOCK_ANVIL_LAND, 0.4f, 1.8f);
        world.spawnParticle(Particle.CRIT, blockLoc.clone().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.1);

        if (chain.isBroken()) {
            clearChainBlocks(chain);
            world.playSound(blockLoc, Sound.BLOCK_CHAIN_BREAK, 2.0f, 0.8f);
            world.playSound(blockLoc, Sound.BLOCK_ANVIL_DESTROY, 1.5f, 0.9f);
            world.spawnParticle(Particle.LAVA, blockLoc.clone().add(0.5, 0.5, 0.5), 25, 0.4, 0.4, 0.4);

            removeHologram("ep_chain_" + chain.getId());

            int brokenCount = getBrokenChainsCount();
            int total = chains.size();

            if (brokenCount >= total) {
                startFallingSequence();
            }
        }

        updateAllHolograms();
        return true;
    }

    public int getBrokenChainsCount() {
        int count = 0;
        for (ChainNode c : chains) {
            if (c.isBroken()) count++;
        }
        return count;
    }

    public void startFallingSequence() {
        if (state == SphereState.FALLING || state == SphereState.FALLEN_MINING) return;
        state = SphereState.FALLING;

        removeJumpBoostFromPlayers();

        for (ChainNode c : chains) {
            removeHologram("ep_chain_" + c.getId());
        }
        removeHologram("ep_sphere_hanging");

        final World world = sphereHighCenter.getWorld();
        final int startY = sphereHighCenter.getBlockY();
        final int endY = sphereFallenCenter.getBlockY();
        final int cx = sphereHighCenter.getBlockX();
        final int cz = sphereHighCenter.getBlockZ();

        clearSphereAt(sphereHighCenter);

        if (animationTask != null) animationTask.cancel();

        animationTask = new BukkitRunnable() {
            private int currentY = startY;

            @Override
            public void run() {
                clearSphereAt(new Location(world, cx, currentY, cz));
                currentY--;

                Location curLoc = new Location(world, cx, currentY, cz);
                drawSphereAt(curLoc, Material.BLUE_GLAZED_TERRACOTTA);

                world.playSound(curLoc, Sound.ENTITY_PHANTOM_SWOOP, 1.2f, 0.6f);
                world.playSound(curLoc, Sound.BLOCK_BEACON_AMBIENT, 0.8f, 1.8f);
                world.spawnParticle(Particle.CLOUD, curLoc.clone().add(0, -1.0, 0), 25, 1.2, 0.3, 1.2, 0.05);
                world.spawnParticle(Particle.CRIT, curLoc.clone().add(0, 0, 0), 10, 0.8, 0.8, 0.8, 0.1);

                if (currentY <= endY) {
                    cancel();
                    state = SphereState.FALLEN_MINING;
                    currentSphereHp = maxSphereHp;
                    explosionCount = 0;
                    currentExplosionChance = baseExplosionChance;
                    shieldUntil = 0L;

                    world.playSound(sphereFallenCenter, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.5f);
                    world.playSound(sphereFallenCenter, Sound.ENTITY_IRON_GOLEM_DAMAGE, 2.0f, 0.5f);
                    world.playSound(sphereFallenCenter, Sound.BLOCK_ANVIL_LAND, 2.5f, 0.4f);
                    world.spawnParticle(Particle.EXPLOSION_LARGE, sphereFallenCenter.clone().add(0, 1, 0), 8, 1.0, 0.5, 1.0);
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, sphereFallenCenter.clone().add(0, 0.5, 0), 60, 2.0, 0.3, 2.0, 0.05);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, sphereFallenCenter.clone().add(0, 1.0, 0), 30, 1.2, 0.8, 1.2, 0.05);

                    // Таинственный и зловещий саундтрек при падении Сердца Рая
                    playMysteriousSoundscape(world, sphereFallenCenter);

                    updateSphereHologram();
                }
            }
        }.runTaskTimer(plugin, 3L, 3L);
    }

    public boolean handleFallenSphereBreak(Player player, Location blockLoc) {
        if (state != SphereState.FALLEN_MINING) return false;

        String prefix = plugin.getConfigManager().getAdminPrefix();

        // 1. Проверка защитного щита после взрыва (3 секунды)
        long now = System.currentTimeMillis();
        if (now < shieldUntil) {
            long remainingMs = shieldUntil - now;
            double sec = Math.round(remainingMs / 100.0) / 10.0;
            ColorUtil.sendActionBar(player, "&cЗащитный барьер Сердца Рая! &7Подождите &#F8BEFB" + sec + " сек.");
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.2f);
            return true;
        }

        // 2. Задержка между ударами для каждого игрока (750 мс) чтобы предотвратить мгновенный спам
        Long lastHit = playerLastHitTime.get(player.getUniqueId());
        if (lastHit != null && (now - lastHit) < 750) {
            return true;
        }
        playerLastHitTime.put(player.getUniqueId(), now);

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || !hand.getType().name().endsWith("_PICKAXE")) {
            player.sendMessage(ColorUtil.colorize(prefix + "&cДля добычи Сердца Рая необходима кирка!"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return true;
        }

        World world = blockLoc.getWorld();
        Location center = sphereFallenCenter.clone().add(0.5, 1.0, 0.5);

        // Проверка шанса взрыва
        double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
        if (roll < currentExplosionChance) {
            triggerSphereExplosion(player);
            return true;
        }

        currentSphereHp = Math.max(0, currentSphereHp - 1);
        currentExplosionChance = Math.min(50.0, currentExplosionChance + explosionChanceStep);

        world.playSound(blockLoc, Sound.BLOCK_ANVIL_USE, 0.8f, 1.5f);
        world.playSound(blockLoc, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.8f);
        world.spawnParticle(Particle.TOTEM, blockLoc.clone().add(0.5, 0.8, 0.5), 15, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticle(Particle.FLASH, blockLoc.clone().add(0.5, 0.5, 0.5), 1);

        ExperienceOrb orb = (ExperienceOrb) world.spawn(blockLoc.clone().add(0.5, 1.0, 0.5), ExperienceOrb.class);
        orb.setExperience(ThreadLocalRandom.current().nextInt(3, 8));

        // Динамический лут в зависимости от числа игроков в радиусе 20 блоков
        int nearbyPlayers = 0;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(sphereFallenCenter) <= 400.0) {
                nearbyPlayers++;
            }
        }

        List<ItemStack> drops = plugin.getLootStorageManager().rollSphereDrops(nearbyPlayers);
        boolean dropped = false;
        for (ItemStack is : drops) {
            if (is != null && is.getType() != Material.AIR) {
                Location dropLoc = sphereFallenCenter.clone().add(0.5, 2.2, 0.5);
                org.bukkit.entity.Item droppedItem = world.dropItem(dropLoc, is);
                droppedItem.setVelocity(new Vector(
                        ThreadLocalRandom.current().nextDouble(-0.15, 0.15),
                        0.35,
                        ThreadLocalRandom.current().nextDouble(-0.15, 0.15)
                ));
                dropped = true;
            }
        }

        if (dropped) {
            world.playSound(blockLoc, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        }

        updateSphereHologram();

        if (currentSphereHp <= 0) {
            startAscensionSequence();
        }

        return true;
    }

    private void triggerSphereExplosion(Player triggerPlayer) {
        explosionCount++;
        currentExplosionChance = baseExplosionChance;
        shieldUntil = System.currentTimeMillis() + 3000L; // 3 секунды фазового барьера

        World world = sphereFallenCenter.getWorld();
        Location center = sphereFallenCenter.clone().add(0.5, 1.5, 0.5);

        world.spawnParticle(Particle.EXPLOSION_HUGE, center, 3);
        world.spawnParticle(Particle.FLASH, center, 2);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.9f);
        world.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 1.2f);
        world.playSound(center, Sound.ITEM_SHIELD_BLOCK, 1.5f, 0.8f);

        try {
            world.strikeLightningEffect(center);
        } catch (Throwable ignored) {}

        // Отталкивание и умеренный урон
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= 36.0) {
                p.damage(8.0);
                Vector dir = p.getLocation().toVector().subtract(center.toVector()).normalize().setY(0.45).multiply(0.85);
                p.setVelocity(dir);
            }
        }

        renderFallenSphereExplosionState();

        updateSphereHologram();

        if (explosionCount >= maxExplosions) {
            startAscensionSequence();
        }
    }

    public void startAscensionSequence() {
        if (state == SphereState.ASCENDING || state == SphereState.RESTORING_CHAINS) return;
        state = SphereState.ASCENDING;

        removeHologram("ep_sphere_main");

        // Красивое сообщение об окончании ивента для игроков
        List<String> endMessages = plugin.getConfigManager().getMessageList("event-end");
        if (endMessages != null && !endMessages.isEmpty()) {
            for (String line : endMessages) {
                ColorUtil.broadcastToPlayers(line);
            }
        } else {
            String prefix = plugin.getConfigManager().getAdminPrefix();
            ColorUtil.broadcastToPlayers(prefix + "&#F8BEFBИвент &f«Райское место» &#F8BEFBзавершен! Сердце Рая истощено и отправляется на перезарядку.");
        }

        final World world = sphereFallenCenter.getWorld();
        final int startY = sphereFallenCenter.getBlockY();
        final int targetY = sphereHighCenter.getBlockY();
        final int cx = sphereHighCenter.getBlockX();
        final int cz = sphereHighCenter.getBlockZ();

        clearSphereAt(sphereFallenCenter);

        if (animationTask != null) animationTask.cancel();

        animationTask = new BukkitRunnable() {
            private int currentY = startY;

            @Override
            public void run() {
                clearSphereAt(new Location(world, cx, currentY, cz));
                currentY++;

                Location curLoc = new Location(world, cx, currentY, cz);
                drawSphereAt(curLoc, Material.GRAY_GLAZED_TERRACOTTA);

                world.playSound(curLoc, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 1.5f);
                world.playSound(curLoc, Sound.ENTITY_SHULKER_BULLET_HIT, 1.0f, 1.2f);
                world.spawnParticle(Particle.PORTAL, curLoc.clone().add(0, 0, 0), 30, 1.2, 1.2, 1.2, 0.1);
                world.spawnParticle(Particle.TOTEM, curLoc.clone().add(0, -1, 0), 15, 0.8, 0.5, 0.8, 0.05);

                if (currentY >= targetY) {
                    cancel();
                    startChainRestorationSequence();
                }
            }
        }.runTaskTimer(plugin, 4L, 4L);
    }

    public void startChainRestorationSequence() {
        state = SphereState.RESTORING_CHAINS;
        final World world = sphereHighCenter.getWorld();
        world.playSound(sphereHighCenter, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.0f);

        if (animationTask != null) animationTask.cancel();

        animationTask = new BukkitRunnable() {
            private int chainIndex = 0;
            private int blockIndex = 0;

            @Override
            public void run() {
                if (chainIndex >= chains.size()) {
                    cancel();
                    enterCooldownPhase();
                    return;
                }

                ChainNode chain = chains.get(chainIndex);
                List<Location> bList = chain.getChainBlocks();

                if (blockIndex < bList.size()) {
                    Location bLoc = bList.get(blockIndex);
                    Block b = bLoc.getBlock();
                    BlockData origData = chain.getOriginalData().get(bLoc);
                    if (origData != null) {
                        b.setBlockData(origData, false);
                    } else {
                        b.setType(Material.CHAIN, false);
                    }
                    world.playSound(bLoc, Sound.ITEM_ARMOR_EQUIP_CHAIN, 1.2f, 1.2f);
                    world.spawnParticle(Particle.CRIT_MAGIC, bLoc.clone().add(0.5, 0.5, 0.5), 6, 0.2, 0.2, 0.2, 0.05);
                    blockIndex++;
                } else {
                    chain.reset();
                    chainIndex++;
                    blockIndex = 0;
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private void enterCooldownPhase() {
        state = SphereState.COOLDOWN;
        cooldownRemaining = cooldownSeconds; // 10800 сек = 3 часа
        drawSphereAt(sphereHighCenter, Material.GRAY_GLAZED_TERRACOTTA);

        for (ChainNode c : chains) {
            removeHologram("ep_chain_" + c.getId());
        }

        updateCooldownSphereHologram();
    }

    private void finishRestoration() {
        state = SphereState.INTACT;
        currentSphereHp = maxSphereHp;
        explosionCount = 0;
        currentExplosionChance = baseExplosionChance;
        shieldUntil = 0L;

        for (ChainNode c : chains) {
            c.reset();
            restoreChainBlocks(c);
        }
        drawSphereAt(sphereHighCenter, Material.BLUE_GLAZED_TERRACOTTA);
        updateAllHolograms();

        // Оповещение о начале Сердца Рая
        List<String> startMessages = plugin.getConfigManager().getMessageList("event-start");
        if (startMessages != null && !startMessages.isEmpty()) {
            for (String line : startMessages) {
                ColorUtil.broadcastToPlayers(line);
            }
        }

        if (sphereHighCenter.getWorld() != null) {
            sphereHighCenter.getWorld().playSound(sphereHighCenter, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
            sphereHighCenter.getWorld().playSound(sphereHighCenter, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.2f);
        }
    }

    private void playMysteriousSoundscape(World world, Location center) {
        if (world == null || center == null) return;

        double radiusSq = 150.0 * 150.0;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= radiusSq) {
                // Загадочный / зловещий саундтрек пластинки 13
                p.playSound(p.getLocation(), Sound.MUSIC_DISC_13, 2.5f, 1.0f);
                // Глубокий зловещий гул древнего стража
                p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.8f, 0.5f);
                // Мистический колокольный набат
                p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 2.0f, 0.4f);
                // Зловещее потустороннее эхо душ
                p.playSound(p.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 2.0f, 0.6f);
                // Далекий громоподобный рык
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.5f);
            }
        }

        // Дополнительные нагнетающие звуковые волны через 1.5 и 3.5 секунды
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distanceSquared(center) <= radiusSq) {
                        p.playSound(p.getLocation(), Sound.AMBIENT_WARPED_FOREST_MOOD, 2.0f, 0.7f);
                        p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.5f, 0.5f);
                        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 1.2f, 0.5f);
                    }
                }
            }
        }, 30L);

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distanceSquared(center) <= radiusSq) {
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);
                        p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.5f, 0.6f);
                    }
                }
            }
        }, 70L);
    }

    public void tick() {
        handleCosmicGravity();

        if (state == SphereState.COOLDOWN) {
            cooldownRemaining--;
            if (cooldownRemaining % 60 == 0 || cooldownRemaining <= 10) {
                updateCooldownSphereHologram();
            }
            if (cooldownRemaining <= 0) {
                finishRestoration();
            }
        }
    }

    private void handleCosmicGravity() {
        Location center = plugin.getConfigManager().getCenterLocation();
        if (center == null || center.getWorld() == null) return;
        World world = center.getWorld();

        boolean jumpBoostEnabled = plugin.getConfig().getBoolean("sphere.jump-boost.enabled", true);
        double roomRadius = plugin.getConfig().getDouble("sphere.jump-boost.room-radius", 15.0); // 15 блоков
        double roomRadiusSq = roomRadius * roomRadius;
        int jumpLevel = plugin.getConfig().getInt("sphere.jump-boost.level", 8); // Прыгучесть 8 уровня
        int jumpAmp = Math.max(0, jumpLevel - 1); // 7

        for (Player p : world.getPlayers()) {
            Location pl = p.getLocation();
            double dx = pl.getX() - center.getX();
            double dz = pl.getZ() - center.getZ();
            double distSq = dx * dx + dz * dz;

            if (distSq > 150.0 * 150.0) continue;

            double dy = pl.getY() - center.getY();
            boolean insideRoom = (distSq <= roomRadiusSq) && (dy >= -1 && dy <= 36);

            // Космическая гравитация работает ТОЛЬКО когда Сердце Рая активно под куполом (state == INTACT)
            if (insideRoom && state == SphereState.INTACT && jumpBoostEnabled) {
                if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 60, jumpAmp, true, false, true), true);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, true, false, true), true);
                }
            } else {
                PotionEffect j = p.getPotionEffect(PotionEffectType.JUMP);
                if (j != null && j.getAmplifier() >= 6 && !p.hasPermission("elytrixparadise.jump.keep")) {
                    p.removePotionEffect(PotionEffectType.JUMP);
                }
                PotionEffect sf = p.getPotionEffect(PotionEffectType.SLOW_FALLING);
                if (sf != null && !p.hasPermission("elytrixparadise.jump.keep")) {
                    p.removePotionEffect(PotionEffectType.SLOW_FALLING);
                }
            }
        }
    }

    private void removeJumpBoostFromPlayers() {
        Location center = plugin.getConfigManager().getCenterLocation();
        if (center == null || center.getWorld() == null) return;
        World world = center.getWorld();
        for (Player p : world.getPlayers()) {
            PotionEffect j = p.getPotionEffect(PotionEffectType.JUMP);
            if (j != null && j.getAmplifier() >= 6 && !p.hasPermission("elytrixparadise.jump.keep")) {
                p.removePotionEffect(PotionEffectType.JUMP);
            }
            PotionEffect sf = p.getPotionEffect(PotionEffectType.SLOW_FALLING);
            if (sf != null && !p.hasPermission("elytrixparadise.jump.keep")) {
                p.removePotionEffect(PotionEffectType.SLOW_FALLING);
            }
        }
    }

    public void updateAllHolograms() {
        if (state == SphereState.INTACT) {
            for (ChainNode c : chains) {
                updateChainHologram(c);
            }
            updateHangingSphereHologram();
        } else if (state == SphereState.FALLEN_MINING) {
            updateSphereHologram();
        } else if (state == SphereState.COOLDOWN) {
            updateCooldownSphereHologram();
        }
    }

    private void updateChainHologram(ChainNode chain) {
        if (chain.isBroken() || state != SphereState.INTACT) {
            removeHologram("ep_chain_" + chain.getId());
            return;
        }

        Location holoLoc = chain.getHologramLocation();
        if (holoLoc == null) return;

        List<String> lines = new ArrayList<>();
        lines.add(ColorUtil.colorize("&8[&c●&8] &#F8BEFB" + chain.getName() + " &8[&c●&8]"));
        lines.add(ColorUtil.colorize("&c● &fПрочность: &#F8BEFB" + chain.getCurrentHp() + "&7/&#F8BEFB" + chain.getMaxHp() + " HP"));
        lines.add(ColorUtil.colorize("&7● &fЛомайте киркой для обрыва цепи"));

        createOrUpdateHolo("ep_chain_" + chain.getId(), holoLoc, lines);
    }

    private void updateHangingSphereHologram() {
        if (sphereHighCenter == null) return;
        Location holoLoc = sphereHighCenter.clone().add(0.5, 3.5, 0.5);
        int aliveChains = chains.size() - getBrokenChainsCount();
        List<String> lines = new ArrayList<>();
        lines.add(ColorUtil.colorize("&#F8BEFB● &fСердце Рая &#F8BEFB●"));
        lines.add(ColorUtil.colorize("&e● &fЦепей цело: &#F8BEFB" + aliveChains + "&7/&#F8BEFB" + chains.size()));
        lines.add(ColorUtil.colorize("&7● &fРазрушьте все 6 цепей, чтобы сфера упала!"));

        createOrUpdateHolo("ep_sphere_hanging", holoLoc, lines);
    }

    private void updateSphereHologram() {
        if (sphereFallenCenter == null) return;
        Location holoLoc = sphereFallenCenter.clone().add(0.5, 3.5, 0.5);
        double risk = Math.round(currentExplosionChance * 10.0) / 10.0;
        List<String> lines = new ArrayList<>();
        lines.add(ColorUtil.colorize("&e★ &#F8BEFBСердце Рая (Упало) &e★"));
        lines.add(ColorUtil.colorize("&a● &fДобывайте киркой! &8| &cРиск: &#F8BEFB" + risk + "%"));
        lines.add(ColorUtil.colorize("&f● Взрывов до истощения: &#F8BEFB" + explosionCount + "&7/&#F8BEFB" + maxExplosions));

        createOrUpdateHolo("ep_sphere_main", holoLoc, lines);
    }

    private void updateCooldownSphereHologram() {
        if (sphereHighCenter == null) return;
        Location holoLoc = sphereHighCenter.clone().add(0.5, 3.5, 0.5);
        List<String> lines = new ArrayList<>();
        lines.add(ColorUtil.colorize("&8● &#F8BEFBСердце Рая &8●"));
        lines.add(ColorUtil.colorize("&c● &fСтатус: &cПерезарядка (3 ч)"));
        lines.add(ColorUtil.colorize("&7● &fВосстановление через: &#F8BEFB" + ColorUtil.formatTimeShort(cooldownRemaining)));

        createOrUpdateHolo("ep_sphere_hanging", holoLoc, lines);
    }

    private void createOrUpdateHolo(String id, Location loc, List<String> lines) {
        if (decentHologramsPresent) {
            try {
                Hologram holo = DHAPI.getHologram(id);
                if (holo == null) {
                    DHAPI.createHologram(id, loc, lines);
                } else {
                    DHAPI.moveHologram(holo, loc);
                    DHAPI.setHologramLines(holo, lines);
                }
                return;
            } catch (Throwable ignored) {}
        }

        List<ArmorStand> stands = fallbackHolograms.get(id);
        if (stands == null || stands.isEmpty() || stands.stream().anyMatch(Entity::isDead)) {
            removeArmorStandHolo(id);
            stands = new ArrayList<>();
            double yOffset = 0.0;
            for (int i = lines.size() - 1; i >= 0; i--) {
                Location lineLoc = loc.clone().add(0, yOffset, 0);
                ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(lineLoc, EntityType.ARMOR_STAND);
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setCustomNameVisible(true);
                stand.setCustomName(lines.get(i));
                stand.setMarker(true);
                stand.setSmall(true);
                stand.setInvulnerable(true);
                stands.add(0, stand);
                yOffset += 0.28;
            }
            fallbackHolograms.put(id, stands);
        } else {
            for (int i = 0; i < lines.size(); i++) {
                if (i < stands.size()) {
                    ArmorStand s = stands.get(i);
                    if (s.isValid()) {
                        s.setCustomName(lines.get(i));
                    }
                }
            }
        }
    }

    public void removeHologram(String id) {
        if (decentHologramsPresent) {
            try {
                Hologram holo = DHAPI.getHologram(id);
                if (holo != null) {
                    DHAPI.removeHologram(id);
                }
            } catch (Throwable ignored) {}
        }
        removeArmorStandHolo(id);
    }

    private void removeArmorStandHolo(String id) {
        List<ArmorStand> stands = fallbackHolograms.remove(id);
        if (stands != null) {
            for (ArmorStand s : stands) {
                if (s != null && s.isValid()) {
                    s.remove();
                }
            }
        }
    }

    public void clearAllHolograms() {
        for (ChainNode c : chains) {
            removeHologram("ep_chain_" + c.getId());
        }
        removeHologram("ep_sphere_hanging");
        removeHologram("ep_sphere_main");
    }

    public void clearAll() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
        removeJumpBoostFromPlayers();
        clearAllHolograms();
    }

    public SphereState getState() {
        return state;
    }

    public List<ChainNode> getChains() {
        return chains;
    }

    public int getAliveChainsCount() {
        return Math.max(0, chains.size() - getBrokenChainsCount());
    }

    public int getCurrentSphereHp() {
        return currentSphereHp;
    }

    public int getMaxSphereHp() {
        return maxSphereHp;
    }

    public int getExplosionCount() {
        return explosionCount;
    }

    public int getMaxExplosions() {
        return maxExplosions;
    }

    public int getCooldownRemaining() {
        return cooldownRemaining;
    }
}
