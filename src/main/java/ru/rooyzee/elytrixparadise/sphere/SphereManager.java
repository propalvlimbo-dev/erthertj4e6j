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
import ru.rooyzee.elytrixparadise.shards.LootItem;
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

    private int cooldownSeconds = 1800; // 30 min default
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
        this.cooldownSeconds = plugin.getConfig().getInt("sphere.cooldown-seconds", 1800);

        // 1. Поиск существующей сферы из синей глазурованной керамики (BLUE_GLAZED_TERRACOTTA)
        scanExistingSphereAndChains(center);
        updateAllHolograms();
    }

    /**
     * Сканирует существующие в мире блоки сферы (BLUE_GLAZED_TERRACOTTA) и 6 цепей (CHAIN)
     * из вставленной схематики, не создавая никаких лишних или искусственных блоков!
     */
    public void scanExistingSphereAndChains(Location center) {
        sphereBlocks.clear();
        chains.clear();

        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        int scanRadiusXZ = 25;
        int minY = Math.max(0, cy + 1);
        int maxY = Math.min(255, cy + 35);

        List<Location> foundSphereLocs = new ArrayList<>();
        List<Location> foundChainLocs = new ArrayList<>();

        for (int x = cx - scanRadiusXZ; x <= cx + scanRadiusXZ; x++) {
            for (int z = cz - scanRadiusXZ; z <= cz + scanRadiusXZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    Material type = b.getType();
                    if (type == Material.BLUE_GLAZED_TERRACOTTA) {
                        foundSphereLocs.add(b.getLocation());
                    } else if (type == Material.CHAIN) {
                        foundChainLocs.add(b.getLocation());
                    }
                }
            }
        }

        // Если сфера найдена в схематике, вычисляем её центр
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
            this.sphereFallenCenter = new Location(world, avgX, cy + 3, avgZ);

            for (Location loc : foundSphereLocs) {
                Block b = loc.getBlock();
                int rx = loc.getBlockX() - avgX;
                int ry = loc.getBlockY() - avgY;
                int rz = loc.getBlockZ() - avgZ;
                sphereBlocks.add(new SphereBlockInfo(loc, rx, ry, rz, b.getType(), b.getBlockData().clone()));
            }

            plugin.getLogger().info("Найдена Центральная Сфера (BLUE_GLAZED_TERRACOTTA): "
                    + sphereBlocks.size() + " блоков на высоте Y=" + avgY);
        } else {
            this.sphereHighCenter = new Location(world, cx, cy + 18, cz);
            this.sphereFallenCenter = new Location(world, cx, cy + 3, cz);
        }

        // 2. Группировка найденных существующих 6 цепей схематики: 4 горизонтальных + 2 вертикальных (сверху и снизу)
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

            // 1) Вертикальные цепи (к куполу сверху или к алтарю снизу)
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                if (dy > 1) {
                    top.addBlock(cLoc, b.getBlockData());
                    continue;
                } else if (dy < -1) {
                    bottom.addBlock(cLoc, b.getBlockData());
                    continue;
                }
            }

            // 2) Горизонтальные цепи к 4 стенам/колоннам
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

        plugin.getLogger().info("Найдено и сгруппировано существующих цепей схематики: "
                + chains.size() + " цепей (всего " + foundChainLocs.size() + " блоков цепей).");
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

    public void drawSphereAt(Location center) {
        if (center == null || center.getWorld() == null) return;
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (SphereBlockInfo info : sphereBlocks) {
            Block b = world.getBlockAt(cx + info.getRelX(), cy + info.getRelY(), cz + info.getRelZ());
            b.setBlockData(info.getBlockData(), false);
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

    /**
     * Нанесение урона цепи при ударе игрока
     */
    public boolean handleChainHit(Player player, Location blockLoc) {
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
            Bukkit.broadcastMessage(ColorUtil.colorize(prefix + "&#F8BEFB" + chain.getName() + " &cразорвана! &8(&c" + brokenCount + "&7/&c" + total + "&8)"));

            if (brokenCount >= total) {
                // Все цепи сломаны -> Запуск падения сферы!
                startFallingSequence();
            }
        } else {
            updateChainHologram(chain);
        }

        return true;
    }

    public int getBrokenChainsCount() {
        int count = 0;
        for (ChainNode c : chains) {
            if (c.isBroken()) count++;
        }
        return count;
    }

    /**
     * Анимация падения сферы вниз на алтарь
     */
    public void startFallingSequence() {
        if (state == SphereState.FALLING || state == SphereState.FALLEN_MINING) return;
        state = SphereState.FALLING;

        // Снимаем эффект прыгучести со всех игроков при падении сферы
        removeJumpBoostFromPlayers();

        String prefix = plugin.getConfigManager().getAdminPrefix();
        Bukkit.broadcastMessage(ColorUtil.colorize(prefix + "&c&lВсе 6 цепей разорваны! &#F8BEFBСердце Рая обрушивается вниз на алтарь!"));

        // Удаляем все голограммы цепей
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
                // Очистить сферу на предыдущей высоте
                clearSphereAt(new Location(world, cx, currentY, cz));

                currentY--;

                // Нарисовать сферу на новой высоте
                Location curLoc = new Location(world, cx, currentY, cz);
                drawSphereAt(curLoc);

                // Эффекты падения
                world.playSound(curLoc, Sound.ENTITY_PHANTOM_SWOOP, 1.5f, 0.7f);
                world.spawnParticle(Particle.CLOUD, curLoc.clone().add(0, -1.5, 0), 20, 1.5, 0.3, 1.5, 0.05);

                if (currentY <= endY) {
                    cancel();
                    // Приземление!
                    state = SphereState.FALLEN_MINING;
                    currentSphereHp = maxSphereHp;

                    world.playSound(sphereFallenCenter, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
                    world.playSound(sphereFallenCenter, Sound.ENTITY_IRON_GOLEM_DAMAGE, 2.0f, 0.5f);
                    world.spawnParticle(Particle.EXPLOSION_LARGE, sphereFallenCenter.clone().add(0, 1, 0), 5, 1.0, 0.5, 1.0);
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, sphereFallenCenter.clone().add(0, 0.5, 0), 40, 2.0, 0.3, 2.0, 0.05);

                    Bukkit.broadcastMessage(ColorUtil.colorize(prefix + "&#F8BEFBСердце Рая упало на алтарь! &aДобывайте его кирками для получения ценного лута!"));
                    updateSphereHologram();
                }
            }
        }.runTaskTimer(plugin, 3L, 3L);
    }

    /**
     * Обработка удара/добычи упавшей сферы игроком
     */
    public boolean handleFallenSphereHit(Player player, Location blockLoc) {
        if (state != SphereState.FALLEN_MINING) return false;

        String prefix = plugin.getConfigManager().getAdminPrefix();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || !hand.getType().name().endsWith("_PICKAXE")) {
            player.sendMessage(ColorUtil.colorize(prefix + "&cДля добычи Сердца Рая необходима кирка!"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return true;
        }

        currentSphereHp = Math.max(0, currentSphereHp - 1);
        World world = blockLoc.getWorld();

        // Эффекты удара
        world.playSound(blockLoc, Sound.BLOCK_ANVIL_USE, 0.8f, 1.5f);
        world.playSound(blockLoc, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.8f);
        world.spawnParticle(Particle.TOTEM, blockLoc.clone().add(0.5, 0.8, 0.5), 15, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticle(Particle.FLASH, blockLoc.clone().add(0.5, 0.5, 0.5), 1);

        // Опыт
        ExperienceOrb orb = (ExperienceOrb) world.spawn(blockLoc.clone().add(0.5, 1.0, 0.5), ExperienceOrb.class);
        orb.setExperience(ThreadLocalRandom.current().nextInt(3, 8));

        // Выпадение лута
        List<LootItem> lootTable = plugin.getConfigManager().getLootItems();
        boolean dropped = false;
        for (LootItem item : lootTable) {
            double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
            if (roll <= (item.getChance() * 1.2)) {
                ItemStack is = item.createItemStack();
                if (is != null && is.getType() != Material.AIR) {
                    Location dropLoc = sphereFallenCenter.clone().add(0.5, 2.5, 0.5);
                    org.bukkit.entity.Item droppedItem = world.dropItem(dropLoc, is);
                    droppedItem.setVelocity(new Vector(
                            ThreadLocalRandom.current().nextDouble(-0.15, 0.15),
                            0.35,
                            ThreadLocalRandom.current().nextDouble(-0.15, 0.15)
                    ));
                    dropped = true;
                }
            }
        }

        if (dropped) {
            world.playSound(blockLoc, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        }

        updateSphereHologram();

        if (currentSphereHp <= 0) {
            // Сфера полностью добыта -> Плавный подъём и восстановление!
            startAscensionSequence();
        }

        return true;
    }

    /**
     * Анимация плавного подъёма сферы обратно наверх и сборка цепей
     */
    public void startAscensionSequence() {
        if (state == SphereState.ASCENDING || state == SphereState.RESTORING_CHAINS) return;
        state = SphereState.ASCENDING;

        removeHologram("ep_sphere_main");

        String prefix = plugin.getConfigManager().getAdminPrefix();
        Bukkit.broadcastMessage(ColorUtil.colorize(prefix + "&#F8BEFBСердце Рая полностью истощено и начинает вознесение обратно к куполу!"));

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
                // Очистить сферу на предыдущей высоте
                clearSphereAt(new Location(world, cx, currentY, cz));

                currentY++;

                // Нарисовать сферу на новой высоте
                Location curLoc = new Location(world, cx, currentY, cz);
                drawSphereAt(curLoc);

                // Эффекты левитации и луча
                world.playSound(curLoc, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 1.5f);
                world.playSound(curLoc, Sound.ENTITY_SHULKER_BULLET_HIT, 1.0f, 1.2f);
                world.spawnParticle(Particle.PORTAL, curLoc.clone().add(0, 0, 0), 30, 1.2, 1.2, 1.2, 0.1);
                world.spawnParticle(Particle.TOTEM, curLoc.clone().add(0, -1, 0), 15, 0.8, 0.5, 0.8, 0.05);

                if (currentY >= targetY) {
                    cancel();
                    // Сфера достигла верха -> Восстановление цепей!
                    startChainRestorationSequence();
                }
            }
        }.runTaskTimer(plugin, 4L, 4L);
    }

    /**
     * Плавная сборка и соединение всех 6 цепей звено за звеном
     */
    public void startChainRestorationSequence() {
        state = SphereState.RESTORING_CHAINS;
        final World world = sphereHighCenter.getWorld();
        String prefix = plugin.getConfigManager().getAdminPrefix();
        Bukkit.broadcastMessage(ColorUtil.colorize(prefix + "&#F8BEFBДревние цепи Рая соединяются со сферой..."));

        world.playSound(sphereHighCenter, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.0f);

        if (animationTask != null) animationTask.cancel();

        animationTask = new BukkitRunnable() {
            private int chainIndex = 0;
            private int blockIndex = 0;

            @Override
            public void run() {
                if (chainIndex >= chains.size()) {
                    cancel();
                    finishRestoration();
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
                    updateChainHologram(chain);
                    chainIndex++;
                    blockIndex = 0;
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private void finishRestoration() {
        state = SphereState.INTACT;
        currentSphereHp = maxSphereHp;
        for (ChainNode c : chains) {
            c.reset();
            restoreChainBlocks(c);
        }
        drawSphereAt(sphereHighCenter);
        updateAllHolograms();

        String prefix = plugin.getConfigManager().getAdminPrefix();
        Bukkit.broadcastMessage(ColorUtil.colorize(prefix + "&#F8BEFBСердце Рая и все 6 цепей полностью восстановлены!"));
        if (sphereHighCenter.getWorld() != null) {
            sphereHighCenter.getWorld().playSound(sphereHighCenter, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
        }
    }

    public void tick() {
        // Управление гравитацией (высокой прыгучестью) в купольной комнате сферы
        handleRoomGravity();

        if (state == SphereState.COOLDOWN) {
            cooldownRemaining--;
            if (cooldownRemaining <= 0) {
                finishRestoration();
            }
        }
    }

    /**
     * Пока сфера висит на цепях (INTACT), игрокам в купольной комнате даётся высокая прыгучесть.
     * Как только все цепи срублены и сфера падает — прыгучесть/гравитация пропадает!
     */
    private void handleRoomGravity() {
        Location center = plugin.getConfigManager().getCenterLocation();
        if (center == null || center.getWorld() == null) return;
        World world = center.getWorld();

        boolean jumpBoostEnabled = plugin.getConfig().getBoolean("sphere.jump-boost.enabled", true);
        double roomRadius = plugin.getConfig().getDouble("sphere.jump-boost.room-radius", 22.0);
        double roomRadiusSq = roomRadius * roomRadius;
        int jumpLevel = plugin.getConfig().getInt("sphere.jump-boost.level", 5);
        int amplifier = Math.max(0, jumpLevel - 1);

        for (Player p : world.getPlayers()) {
            Location pl = p.getLocation();
            double dx = pl.getX() - center.getX();
            double dz = pl.getZ() - center.getZ();
            double dy = pl.getY() - center.getY();

            boolean insideRoom = (dx * dx + dz * dz <= roomRadiusSq) && (dy >= -2 && dy <= 38);

            if (insideRoom && state == SphereState.INTACT && jumpBoostEnabled) {
                if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 60, amplifier, true, false, true), true);
                }
            } else {
                if (p.hasPotionEffect(PotionEffectType.JUMP) && !p.hasPermission("elytrixparadise.jump.keep")) {
                    p.removePotionEffect(PotionEffectType.JUMP);
                }
            }
        }
    }

    private void removeJumpBoostFromPlayers() {
        Location center = plugin.getConfigManager().getCenterLocation();
        if (center == null || center.getWorld() == null) return;
        World world = center.getWorld();
        for (Player p : world.getPlayers()) {
            if (p.hasPotionEffect(PotionEffectType.JUMP) && !p.hasPermission("elytrixparadise.jump.keep")) {
                p.removePotionEffect(PotionEffectType.JUMP);
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
        }
    }

    private void updateChainHologram(ChainNode chain) {
        if (chain.isBroken()) {
            removeHologram("ep_chain_" + chain.getId());
            return;
        }

        Location holoLoc = chain.getHologramLocation();
        if (holoLoc == null) return;

        List<String> lines = new ArrayList<>();
        lines.add(ColorUtil.colorize("&f⛓ &#F8BEFB" + chain.getName() + " &f⛓"));
        lines.add(ColorUtil.colorize("&c● &fПрочность: &#F8BEFB" + chain.getCurrentHp() + "&7/&#F8BEFB" + chain.getMaxHp() + " HP"));
        lines.add(ColorUtil.colorize("&7● &fЛомайте киркой для обрыва цепи"));

        createOrUpdateHolo("ep_chain_" + chain.getId(), holoLoc, lines);
    }

    private void updateHangingSphereHologram() {
        if (sphereHighCenter == null) return;
        Location holoLoc = sphereHighCenter.clone().add(0.5, 3.5, 0.5);
        List<String> lines = new ArrayList<>();
        lines.add(ColorUtil.colorize("&f☁ &#F8BEFBСердце Рая &f☁"));
        lines.add(ColorUtil.colorize("&e● &fЦепей цело: &#F8BEFB" + (chains.size() - getBrokenChainsCount()) + "&7/&#F8BEFB" + chains.size()));
        lines.add(ColorUtil.colorize("&7● &fРазрушьте все 6 цепей, чтобы сфера упала!"));

        createOrUpdateHolo("ep_sphere_hanging", holoLoc, lines);
    }

    private void updateSphereHologram() {
        if (sphereFallenCenter == null) return;
        Location holoLoc = sphereFallenCenter.clone().add(0.5, 3.5, 0.5);
        List<String> lines = new ArrayList<>();
        lines.add(ColorUtil.colorize("&f⚡ &#F8BEFBСердце Рая (Упало) &f⚡"));
        lines.add(ColorUtil.colorize("&a● &fДобывайте киркой! Лут без взрыва"));
        lines.add(ColorUtil.colorize("&f● Оставшаяся прочность: &#F8BEFB" + currentSphereHp + "&7/&#F8BEFB" + maxSphereHp + " HP"));

        createOrUpdateHolo("ep_sphere_main", holoLoc, lines);
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

        // Fallback to ArmorStands
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
        clearAllHolograms();
    }

    public SphereState getState() {
        return state;
    }

    public List<ChainNode> getChains() {
        return chains;
    }
}
