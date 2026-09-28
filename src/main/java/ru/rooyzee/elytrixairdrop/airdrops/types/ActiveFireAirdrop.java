package ru.rooyzee.elytrixairdrop.airdrops.types;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.airdrops.ActiveAirdrop;
import ru.rooyzee.elytrixairdrop.airdrops.Airdrop;
import ru.rooyzee.elytrixairdrop.managers.SchematicManager;
import ru.rooyzee.elytrixairdrop.utils.ColorUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveFireAirdrop extends ActiveAirdrop {

    private static final int MAX_METEORS = 5;
    private static final int FIRST_METEOR_DELAY = 10;
    private static final int NEXT_METEOR_DELAY = 30;
    private static final int END_DELAY_SEC = 60;
    private static final int COOLING_SEC = 30;
    private static final int METEOR_HEIGHT = 50;
    private static final int SPHERE_RADIUS = 2;
    private static final int DAMAGE_RADIUS = 10;
    private static final int CRATER_RADIUS = 4;
    private static final double MAX_DAMAGE = 10.0;
    private static final long NO_PLAYER_TIMEOUT = 600_000L;
    private static final long LOOT_COOLDOWN_MS = 500L;

    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<Long, Meteor> meteors = new LinkedHashMap<>();
    private final Set<Long> breakable = new HashSet<>();
    private final Map<Long, Inventory> chests = new HashMap<>();
    private final Map<Long, Map<Integer, ItemStack>> realLootBySlot = new HashMap<>();
    private final Map<Long, SchematicManager.BlockData> meteorBackup = new HashMap<>();
    private final Map<Long, SchematicManager.BlockData> craterBackup = new HashMap<>();
    private final Map<UUID, Long> lootCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> trackedDebris = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Random random = new Random();

    private int meteorsSpawned = 0;
    private boolean firstPlayer = false;
    private long lastPlayerTime;
    private long lastOpenTime = 0;
    private boolean ended = false;
    private BukkitTask waitingTask = null;
    private BukkitTask spawnTask = null;
    private long nextMeteorTime = 0;
    private String phase = "waiting";

    private static class Meteor {
        Location center;
        long key;
        boolean cooled;
        boolean opened;
        long landTime;
        String holoName;
        final Set<Long> blocks = new HashSet<>();
        final Set<Long> craterBlocks = new HashSet<>();
        Long chestKey;
    }

    public ActiveFireAirdrop(Location center, String regionId, String hologramName,
                             Map<Location, SchematicManager.BlockData> backup, long endTime) {
        super(Airdrop.FIRE, center, regionId, hologramName, backup, endTime);
        this.lastPlayerTime = System.currentTimeMillis();
    }

    @Override
    public void onStart(Main plugin, ConfigurationSection cfg) {
        updateMainHologram(plugin, cfg);
    }

    @Override
    public void onTick(Main plugin, ConfigurationSection cfg) {
        boolean anyPlayer = hasPlayers();
        if (anyPlayer) lastPlayerTime = System.currentTimeMillis();

        if (!firstPlayer) {
            if (anyPlayer) {
                firstPlayer = true;
                phase = "next";
                scheduleMeteor(plugin, cfg, FIRST_METEOR_DELAY);
            }
            if (System.currentTimeMillis() - lastPlayerTime > NO_PLAYER_TIMEOUT) {
                plugin.getAirdropManager().stop(false);
                return;
            }
            updateMainHologram(plugin, cfg);
            return;
        }

        if (System.currentTimeMillis() - lastPlayerTime > NO_PLAYER_TIMEOUT) {
            plugin.getAirdropManager().stop(false);
            return;
        }

        tickMeteors(plugin);
        applyZoneEffects();
        checkCompletion(plugin);
        updateMainHologram(plugin, cfg);
        cleanCooldowns();
    }

    @Override
    public void onStop(Main plugin, ConfigurationSection cfg) {
        for (BukkitTask t : tasks) { try { t.cancel(); } catch (Exception ignored) {} }
        tasks.clear();
        if (waitingTask != null) { try { waitingTask.cancel(); } catch (Exception ignored) {} }
        if (spawnTask != null) { try { spawnTask.cancel(); } catch (Exception ignored) {} }

        for (UUID id : trackedDebris) {
            org.bukkit.entity.Entity ent = Bukkit.getEntity(id);
            if (ent != null && ent.isValid()) ent.remove();
        }
        trackedDebris.clear();

        for (Inventory inv : chests.values())
            for (HumanEntity v : new ArrayList<>(inv.getViewers())) v.closeInventory();
        for (Meteor m : meteors.values())
            if (m.holoName != null) plugin.getHologramManager().removeHologram(m.holoName);

        for (Map.Entry<Long, SchematicManager.BlockData> e : meteorBackup.entrySet())
            restoreBlock(e.getKey(), e.getValue());
        for (Map.Entry<Long, SchematicManager.BlockData> e : craterBackup.entrySet())
            restoreBlock(e.getKey(), e.getValue());

        meteors.clear();
        chests.clear();
        breakable.clear();
        meteorBackup.clear();
        craterBackup.clear();
        realLootBySlot.clear();
        lootCooldowns.clear();
    }

    @Override
    public boolean handleChestClick(Main plugin, Player p, Location blockLoc) {
        long key = meteorKey(blockLoc);
        Inventory inv = chests.get(key);
        if (inv == null) return false;

        if (!isInZone(p)) {
            p.sendMessage(plugin.getConfigManager().getMessage("airdrop.too-far"));
            return true;
        }

        Meteor meteor = findMeteorByChest(key);
        if (meteor != null && !meteor.opened) {
            meteor.opened = true;
            lastOpenTime = System.currentTimeMillis();
            Meteor fm = meteor;
            tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> cleanupMeteor(plugin, fm), 20L * NEXT_METEOR_DELAY));
            if (meteorsSpawned < MAX_METEORS) {
                phase = "next";
                scheduleMeteor(plugin, plugin.getConfigManager().getConfig()
                        .getConfigurationSection("airdrops.fire"), NEXT_METEOR_DELAY);
            } else {
                phase = "ending";
            }
        }

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1f);
        return true;
    }

    @Override
    public boolean handleLootClick(Main plugin, Player p, InventoryClickEvent e) {
        Long invKey = null;
        for (Map.Entry<Long, Inventory> en : chests.entrySet())
            if (en.getValue() == e.getInventory()) { invKey = en.getKey(); break; }
        if (invKey == null) return false;

        long now = System.currentTimeMillis();
        Long last = lootCooldowns.get(p.getUniqueId());
        if (last != null && now - last < LOOT_COOLDOWN_MS) {
            e.setCancelled(true);
            return true;
        }

        if (e.getClickedInventory() != e.getInventory()) {
            if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) e.setCancelled(true);
            return true;
        }

        Map<Integer, ItemStack> realMap = realLootBySlot.get(invKey);
        if (realMap == null) { e.setCancelled(true); return true; }

        int slot = e.getSlot();
        ItemStack real = realMap.get(slot);
        if (real == null) { e.setCancelled(true); return true; }

        e.setCancelled(true);
        e.getInventory().setItem(slot, null);
        realMap.remove(slot);
        lootCooldowns.put(p.getUniqueId(), now);

        Map<Integer, ItemStack> left = p.getInventory().addItem(real.clone());
        for (ItemStack drop : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.5f);
        return true;
    }

    @Override
    public boolean isProtectedBlock(Location loc) {
        if (loc == null) return false;
        long key = meteorKey(loc);
        if (breakable.contains(key)) return false;
        if (meteorBackup.containsKey(key)) return true;
        if (chests.containsKey(key)) return true;
        return super.isProtectedBlock(loc);
    }

    public boolean isBreakable(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        long key = meteorKey(loc);
        return breakable.contains(key) && loc.getBlock().getType() == Material.OBSIDIAN;
    }

    public boolean isTrackedDebris(UUID id) {
        return trackedDebris.contains(id);
    }

    public void removeTrackedDebris(UUID id) {
        trackedDebris.remove(id);
    }

    public void onBreakableBroken(Main plugin, Location loc) {
        long key = meteorKey(loc);
        breakable.remove(key);
        meteorBackup.remove(key);
        for (Meteor m : meteors.values()) m.blocks.remove(key);
        loc.getWorld().spawnParticle(Particle.BLOCK_CRACK, loc.clone().add(0.5, 0.5, 0.5),
                15, 0.3, 0.3, 0.3, 0.1, Bukkit.createBlockData(Material.OBSIDIAN));
    }

    public boolean isMeteorArea(Location loc) {
        long key = meteorKey(loc);
        if (meteorBackup.containsKey(key)) return true;
        if (chests.containsKey(key)) return true;
        return false;
    }

    private Meteor findMeteorByChest(long chestKey) {
        for (Meteor m : meteors.values())
            if (m.chestKey != null && m.chestKey == chestKey) return m;
        return null;
    }

    private void cleanupMeteor(Main plugin, Meteor meteor) {
        if (meteor.chestKey != null) {
            Inventory inv = chests.remove(meteor.chestKey);
            realLootBySlot.remove(meteor.chestKey);
            if (inv != null) for (HumanEntity v : new ArrayList<>(inv.getViewers())) v.closeInventory();
        }
        if (meteor.holoName != null) {
            plugin.getHologramManager().removeHologram(meteor.holoName);
            meteor.holoName = null;
        }
        for (Long k : new HashSet<>(meteor.blocks)) {
            SchematicManager.BlockData d = meteorBackup.remove(k);
            breakable.remove(k);
            if (d != null) restoreBlock(k, d);
        }
        if (meteor.chestKey != null) {
            SchematicManager.BlockData d = meteorBackup.remove(meteor.chestKey);
            if (d != null) restoreBlock(meteor.chestKey, d);
        }
        for (Long k : new HashSet<>(meteor.craterBlocks)) {
            SchematicManager.BlockData d = craterBackup.remove(k);
            if (d != null) restoreBlock(k, d);
        }
        meteors.remove(meteor.key);
    }

    private void restoreBlock(long key, SchematicManager.BlockData data) {
        Location loc = keyToLoc(key);
        if (loc == null || data == null) return;
        Block b = loc.getBlock();
        b.setType(data.material, false);
        try { b.setBlockData(Bukkit.createBlockData(data.data), false); } catch (Exception ignored) {}
    }

    private void scheduleMeteor(Main plugin, ConfigurationSection cfg, int delaySec) {
        if (meteorsSpawned >= MAX_METEORS) return;
        if (spawnTask != null) { try { spawnTask.cancel(); } catch (Exception ignored) {} }
        nextMeteorTime = System.currentTimeMillis() + delaySec * 1000L;

        spawnTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnTask = null;
            if (plugin.getAirdropManager().getActive() != this) return;
            if (meteorsSpawned >= MAX_METEORS) return;
            if (!hasPlayers()) { waitForPlayers(plugin, cfg); return; }
            launchMeteor(plugin);
        }, delaySec * 20L);
        tasks.add(spawnTask);
    }

    private void waitForPlayers(Main plugin, ConfigurationSection cfg) {
        if (waitingTask != null) return;
        nextMeteorTime = System.currentTimeMillis() + 999_999_999L;
        waitingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.getAirdropManager().getActive() != this) {
                if (waitingTask != null) { waitingTask.cancel(); waitingTask = null; }
                return;
            }
            if (hasPlayers()) {
                if (waitingTask != null) { waitingTask.cancel(); waitingTask = null; }
                launchMeteor(plugin);
            }
        }, 40L, 40L);
        tasks.add(waitingTask);
    }

    private void launchMeteor(Main plugin) {
        Location target = findTargetLocation();
        if (target == null) {
            plugin.getLogger().warning("Fire: no valid meteor target, retry in 5s");
            scheduleMeteor(plugin, plugin.getConfigManager().getConfig()
                    .getConfigurationSection("airdrops.fire"), 5);
            return;
        }

        meteorsSpawned++;
        phase = "flying";
        World w = target.getWorld();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(w)) continue;
            if (p.getLocation().distance(target) <= 80) {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.6f);
                p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.4f);
            }
        }

        broadcastParticle(w, Particle.REDSTONE, target.clone().add(0.5, 0.5, 0.5), 100, 3, 0.2, 3, 0,
                new Particle.DustOptions(Color.fromRGB(248, 190, 251), 3f));

        animateMeteor(plugin, w, target);
    }

    private void animateMeteor(Main plugin, World w, Location target) {
        List<Vector> offsets = sphereOffsets(SPHERE_RADIUS);
        Location core = target.clone().add(0.5, SPHERE_RADIUS - 2.5, 0.5);
        Location start = core.clone().add(0, METEOR_HEIGHT, 0);

        List<FallingBlock> blocks = new ArrayList<>();
        for (Vector off : offsets) {
            Location spawn = start.clone().add(off.getX(), off.getY(), off.getZ());
            FallingBlock fb = w.spawnFallingBlock(spawn, Bukkit.createBlockData(Material.MAGMA_BLOCK));
            fb.setDropItem(false);
            fb.setHurtEntities(false);
            fb.setGravity(false);
            fb.setInvulnerable(true);
            fb.setVelocity(new Vector(0, 0, 0));
            trackedDebris.add(fb.getUniqueId());
            blocks.add(fb);
        }

        final double[] currentY = {start.getY()};
        final double[] velocity = {0.3};
        final double gravity = 0.05;
        final double maxSpeed = 1.0;
        final BukkitTask[] holder = new BukkitTask[1];

        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            velocity[0] += gravity;
            if (velocity[0] > maxSpeed) velocity[0] = maxSpeed;
            double step = velocity[0];
            currentY[0] -= step;

            if (currentY[0] <= core.getY()) {
                currentY[0] = core.getY();
                if (holder[0] != null) holder[0].cancel();
                for (FallingBlock fb : blocks) {
                    if (fb.isValid()) {
                        trackedDebris.remove(fb.getUniqueId());
                        fb.remove();
                    }
                }
                onImpact(plugin, target);
                return;
            }

            Vector vel = new Vector(0, -step, 0);
            for (int i = 0; i < blocks.size(); i++) {
                FallingBlock fb = blocks.get(i);
                if (!fb.isValid()) continue;
                Vector off = offsets.get(i);
                Location tp = new Location(w, core.getX() + off.getX() - 0.5,
                        currentY[0] + off.getY(), core.getZ() + off.getZ() - 0.5);
                fb.teleport(tp);
                fb.setVelocity(vel);
            }

Location trail = new Location(w, core.getX(), currentY[0] + 1, core.getZ());
            // Reduced particle counts + sound every other tick to cut network spam
            broadcastParticle(w, Particle.FLAME, trail, 12, 0.8, 0.4, 0.8, 0.04, null);
            broadcastParticle(w, Particle.SMOKE_LARGE, trail, 6, 0.8, 0.4, 0.8, 0.02, null);
            if (((int) currentY[0] & 1) == 0) {
                broadcastParticle(w, Particle.LAVA, trail, 3, 0.5, 0.3, 0.5, 0, null);
                double trailDistSq = 60 * 60;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().equals(w) && p.getLocation().distanceSquared(trail) < trailDistSq) {
                        p.playSound(trail, Sound.ENTITY_BLAZE_BURN, 0.35f, 0.5f);
                    }
                }
            }
        }, 1L, 1L);
        tasks.add(holder[0]);
    }

    private List<Vector> sphereOffsets(int r) {
        List<Vector> out = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++)
                    if (dx * dx + dy * dy + dz * dz <= r * r + 1)
                        out.add(new Vector(dx, dy, dz));
        return out;
    }

    private void onImpact(Main plugin, Location target) {
        World w = target.getWorld();
        // The target is the first air block above the platform. Keep the whole
        // meteor above it; the old center put two blocks of the sphere into the ground.
        Location core = target.clone().add(0, SPHERE_RADIUS, 0);

Location visualCenter = core.clone().add(0.5, 0, 0.5);
        // Lower particle counts — still looks punchy, far less packet spam on impact
        broadcastParticle(w, Particle.EXPLOSION_HUGE, visualCenter, 3, 1.5, 0.8, 1.5, 0, null);
        broadcastParticle(w, Particle.LAVA, visualCenter, 40, 3, 1.5, 3, 0, null);
        broadcastParticle(w, Particle.FLAME, visualCenter, 60, 4, 1.5, 4, 0.2, null);
        broadcastParticle(w, Particle.SMOKE_LARGE, visualCenter, 30, 4, 1.5, 4, 0.1, null);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(w)) continue;
            if (p.getLocation().distance(core) > 80) continue;
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1f, 1f);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);
        }

        long key = meteorKey(core);
        Meteor m = new Meteor();
        m.center = core;
        m.key = key;
        m.landTime = System.currentTimeMillis();
        meteors.put(key, m);

        buildSphere(core, Material.MAGMA_BLOCK, m);
        applyDamage(core);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getAirdropManager().getActive() != this) return;
            createCrater(plugin, w, target, m);
        }, 3L);

        phase = "cooling";

        m.holoName = plugin.getHologramManager().createHologram(
                core.clone().add(0.5, SPHERE_RADIUS + 1.5, 0.5),
                Collections.singletonList(meteorHoloLine(m)));
    }

    private void createCrater(Main plugin, World w, Location target, Meteor meteor) {
        int cx = target.getBlockX(), cy = target.getBlockY() - 1, cz = target.getBlockZ();
        List<FallingBlock> debris = new ArrayList<>();

        for (int dx = -CRATER_RADIUS; dx <= CRATER_RADIUS; dx++) {
            for (int dz = -CRATER_RADIUS; dz <= CRATER_RADIUS; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq > CRATER_RADIUS * CRATER_RADIUS) continue;
                if (Math.abs(dx) <= SPHERE_RADIUS && Math.abs(dz) <= SPHERE_RADIUS) continue;
                int depth = (int) Math.round(1.5 - Math.sqrt(distSq) * 0.3);
                if (depth < 0) depth = 0;
                for (int dy = 0; dy <= depth; dy++) {
                    Location l = new Location(w, cx + dx, cy - dy, cz + dz);
                    Block b = l.getBlock();
                    if (b.getType() == Material.AIR) continue;
                    if (b.getType() == Material.BEDROCK) continue;
                    long k = meteorKey(l);
                    if (meteorBackup.containsKey(k) || craterBackup.containsKey(k)) continue;

                    craterBackup.put(k, new SchematicManager.BlockData(b.getType(), b.getBlockData().getAsString()));
                    meteor.craterBlocks.add(k);

                    if (dy == 0 && random.nextInt(100) < 85) {
                        FallingBlock fb = w.spawnFallingBlock(l.clone().add(0.5, 0.5, 0.5), b.getBlockData());
                        fb.setDropItem(false);
                        fb.setHurtEntities(false);
                        double vx = (random.nextDouble() - 0.5) * 1.4;
                        double vz = (random.nextDouble() - 0.5) * 1.4;
                        double vy = 0.6 + random.nextDouble() * 0.9;
                        fb.setVelocity(new Vector(vx, vy, vz));
                        trackedDebris.add(fb.getUniqueId());
                        debris.add(fb);
                    }
                    b.setType(Material.AIR, false);
                }
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (FallingBlock fb : debris) {
                if (fb.isValid()) {
                    trackedDebris.remove(fb.getUniqueId());
                    fb.remove();
                }
            }
        }, 50L);
    }

    private void applyDamage(Location center) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(center.getWorld())) continue;
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.hasPermission("elytrixairdrop.admin")) continue;

            double dist = p.getLocation().distance(center);
            if (dist > DAMAGE_RADIUS) continue;

            double factor = 1.0 - (dist / DAMAGE_RADIUS);
            double dmg = Math.max(1.0, MAX_DAMAGE * factor);
            p.damage(dmg);

            ejectFromSphere(p, center);
            p.setFireTicks(60);
        }
    }

    private void ejectFromSphere(Player p, Location center) {
        Vector diff = p.getLocation().toVector().subtract(center.toVector());
        double sphereRadius = SPHERE_RADIUS + 1.5;

        if (diff.lengthSquared() < 0.5) {
            double angle = Math.random() * Math.PI * 2;
            diff = new Vector(Math.cos(angle), 0, Math.sin(angle));
        }

        Vector dir = diff.clone().normalize();
        if (diff.length() < sphereRadius) {
            Location safe = center.clone().add(dir.clone().multiply(sphereRadius + 0.5));
            safe.setY(Math.max(safe.getY(), p.getWorld().getHighestBlockYAt(safe.getBlockX(), safe.getBlockZ()) + 1));
            safe.setYaw(p.getLocation().getYaw());
            safe.setPitch(p.getLocation().getPitch());
            p.teleport(safe);
        }

        double distFactor = Math.max(0.3, 1.0 - (p.getLocation().distance(center) / DAMAGE_RADIUS));
        Vector kb = dir.multiply(0.8 * distFactor + 0.3);
        kb.setY(0.5);
        p.setVelocity(kb);
    }

    private long lastZoneDamage = 0;
    private static final long ZONE_DAMAGE_INTERVAL_MS = 3000L;
    private static final double ZONE_DAMAGE = 2.0;

    private void applyZoneEffects() {
        boolean dealDamage = System.currentTimeMillis() - lastZoneDamage >= ZONE_DAMAGE_INTERVAL_MS;
        if (dealDamage) lastZoneDamage = System.currentTimeMillis();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isInZone(p)) continue;
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.hasPermission("elytrixairdrop.admin")) continue;

            p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1, false, true));

            if (dealDamage) {
                p.damage(ZONE_DAMAGE);
            }

            Block under = p.getLocation().subtract(0, 0.1, 0).getBlock();
            if (under.getType() == Material.LAVA || under.getType() == Material.MAGMA_BLOCK) {
                p.setFireTicks(60);
            }
        }
    }

    private void buildSphere(Location center, Material mat, Meteor meteor) {
        int r = SPHERE_RADIUS;
        World w = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r + 1) continue;
                    Location loc = new Location(w, cx + dx, cy + dy, cz + dz);
                    long k = meteorKey(loc);
                    Block b = loc.getBlock();
                    meteorBackup.putIfAbsent(k, new SchematicManager.BlockData(b.getType(), b.getBlockData().getAsString()));
                    b.setType(mat, false);
                    meteor.blocks.add(k);
                }
    }

    private void tickMeteors(Main plugin) {
        for (Meteor m : new ArrayList<>(meteors.values())) {
            if (m.cooled) {
                if (m.holoName != null)
                    plugin.getHologramManager().updateHologram(m.holoName,
                            Collections.singletonList(meteorHoloLine(m)));
                continue;
            }
            long elapsed = (System.currentTimeMillis() - m.landTime) / 1000L;
            if (elapsed >= COOLING_SEC) {
                m.cooled = true;
                convertToObsidian(plugin, m);
            }
            if (m.holoName != null)
                plugin.getHologramManager().updateHologram(m.holoName,
                        Collections.singletonList(meteorHoloLine(m)));
        }
    }

    private void convertToObsidian(Main plugin, Meteor m) {
        World w = m.center.getWorld();

        List<Long> sortedBlocks = new ArrayList<>(m.blocks);
        long chestKey = meteorKey(m.center);
        sortedBlocks.sort((a, b) -> {
            Location la = keyToLoc(a);
            Location lb = keyToLoc(b);
            if (la == null || lb == null) return 0;
            double da = la.distanceSquared(m.center);
            double db = lb.distanceSquared(m.center);
            return Double.compare(db, da);
        });

        m.chestKey = chestKey;
        m.blocks.remove(chestKey);
        sortedBlocks.remove((Long) chestKey);

        final int[] index = {0};
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (plugin.getAirdropManager().getActive() != this) {
                    if (holder[0] != null) holder[0].cancel();
                    return;
                }
                if (index[0] >= sortedBlocks.size()) {
                    if (holder[0] != null) holder[0].cancel();
                    finishCooling(plugin, m, w);
                    return;
                }

                int perTick = 2;
                for (int i = 0; i < perTick && index[0] < sortedBlocks.size(); i++, index[0]++) {
                    Long k = sortedBlocks.get(index[0]);
                    Location loc = keyToLoc(k);
                    if (loc == null) continue;
                    Block b = loc.getBlock();
                    if (b.getType() != Material.MAGMA_BLOCK) continue;

                    b.setType(Material.OBSIDIAN, false);
                    breakable.add(k);

                    Location center = loc.clone().add(0.5, 1.05, 0.5);
                    broadcastParticle(w, Particle.SMOKE_NORMAL, center, 2, 0.15, 0.05, 0.15, 0.01, null);

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getWorld().equals(w) && p.getLocation().distance(loc) <= 25) {
                            p.playSound(loc, Sound.BLOCK_LAVA_EXTINGUISH, 0.25f, 1.6f);
                        }
                    }
                }
            } catch (Exception e) {
                if (holder[0] != null) holder[0].cancel();
                plugin.getLogger().warning("Cooling error: " + e.getMessage());
            }
        }, 2L, 2L);
        tasks.add(holder[0]);
    }

    private void finishCooling(Main plugin, Meteor m, World w) {
        m.center.getBlock().setType(Material.ENDER_CHEST, false);

        Inventory inv = createMaskedLootInventory(plugin, m.chestKey,
                plugin.getConfigManager().getConfig().getConfigurationSection("airdrops.fire"));
        chests.put(m.chestKey, inv);

        broadcastParticle(w, Particle.PORTAL, m.center.clone().add(0.5, 0.5, 0.5),
                30, 0.6, 0.6, 0.6, 0.4, null);

        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getWorld().equals(w) && p.getLocation().distance(m.center) <= 40) {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.7f);
            }

        phase = "open";
    }

    private String meteorHoloLine(Meteor m) {
        if (m.opened) return "&7— &fЗабран";
        if (m.cooled) return "&#F8BEFB● &fЛомайте Обсидиан";
        long ms = COOLING_SEC * 1000L - (System.currentTimeMillis() - m.landTime);
        long rem = Math.max(0, (ms + 999) / 1000);
        if (rem <= 0) return "&#F8BEFB● &fЛомайте Обсидиан";
        return "&#F8BEFB● &fОстывает &8— &#F8BEFB" + rem + "с";
    }

    private Inventory createMaskedLootInventory(Main plugin, long chestKey, ConfigurationSection cfg) {
        List<ItemStack> pool = plugin.getAirdropManager().getLootFileService().load("fire");
        int min = 10, max = 20;
        if (cfg != null) {
            min = Math.max(0, cfg.getInt("loot-min-items", 10));
            max = Math.max(min, cfg.getInt("loot-max-items", 20));
        }
        Inventory inv = Bukkit.createInventory(null, 54,
                ColorUtil.colorize("&f☁ &#F8BEFBᴀɪʀᴅʀᴏᴘ &7» &cОгненный сундук"));
        if (pool.isEmpty()) return inv;

        int count = min + random.nextInt(max - min + 1);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 54; i++) slots.add(i);
        Collections.shuffle(slots, random);

        Map<Integer, ItemStack> realMap = new HashMap<>();
        ItemStack mask = createMask();
        for (int i = 0; i < Math.min(count, 54); i++) {
            int slot = slots.get(i);
            realMap.put(slot, pool.get(random.nextInt(pool.size())).clone());
            inv.setItem(slot, mask.clone());
        }
        realLootBySlot.put(chestKey, realMap);
        return inv;
    }

    private ItemStack createMask() {
        ItemStack mask = new ItemStack(Material.BLACK_DYE);
        ItemMeta meta = mask.getItemMeta();
        meta.setDisplayName(ColorUtil.colorize("&#F8BEFB???"));
        meta.setLore(Collections.singletonList(ColorUtil.colorize("&7● &fСекретный лут")));
        mask.setItemMeta(meta);
        return mask;
    }

private Location findTargetLocation() {
        World w = getCenter().getWorld();
        int cx = getCenter().getBlockX(), cz = getCenter().getBlockZ();
        List<Location> valid = new ArrayList<>();

        // Step by 2 — half the samples, still dense enough for platform targets
        for (int dx = -14; dx <= 14; dx += 2) {
            outer:
            for (int dz = -14; dz <= 14; dz += 2) {
                int x = cx + dx, z = cz + dz;
                int topY = w.getHighestBlockYAt(x, z);
                Block ground = w.getBlockAt(x, topY, z);
                if (ground.getType() != Material.POLISHED_BLACKSTONE) continue;
                if (!isFullCube(ground)) continue;

                // Check only 4 cardinal neighbours + center instead of full 3x3
                int[][] neigh = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] n : neigh) {
                    int ax = x + n[0], az = z + n[1];
                    int ay = w.getHighestBlockYAt(ax, az);
                    if (ay != topY) continue outer;
                    Block ab = w.getBlockAt(ax, ay, az);
                    if (!isFullCube(ab)) continue outer;
                }

                for (int uy = 1; uy <= 4; uy++) {
                    Material am = w.getBlockAt(x, topY + uy, z).getType();
                    if (am != Material.AIR && am != Material.CAVE_AIR) continue outer;
                }

                Location c = new Location(w, x, topY + 1, z);
                boolean bad = false;
                for (Meteor m : meteors.values()) {
                    if (m.center.distanceSquared(c) < 49) { bad = true; break; }
                }
                if (bad) continue;
                valid.add(c);
            }
        }
        if (valid.isEmpty()) return null;
        return valid.get(random.nextInt(valid.size()));
    }

    private boolean isFullCube(Block b) {
        org.bukkit.block.data.BlockData bd = b.getBlockData();
        if (bd instanceof org.bukkit.block.data.type.Slab) return false;
        if (bd instanceof org.bukkit.block.data.type.Stairs) return false;
        if (bd instanceof org.bukkit.block.data.type.Fence) return false;
        if (bd instanceof org.bukkit.block.data.type.Wall) return false;
        Material m = b.getType();
        if (m == Material.LANTERN || m == Material.SOUL_LANTERN) return false;
        if (m == Material.CHAIN || m == Material.END_ROD) return false;
        return true;
    }

    private long meteorKey(Location loc) {
        long x = loc.getBlockX() & 0x3FFFFFFL;
        long y = loc.getBlockY() & 0xFFFL;
        long z = loc.getBlockZ() & 0x3FFFFFFL;
        return (x << 38) | (z << 12) | y;
    }

    private Location keyToLoc(long key) {
        World w = getCenter().getWorld();
        if (w == null) return null;
        int y = (int) (key & 0xFFFL);
        int z = (int) ((key >> 12) & 0x3FFFFFFL);
        int x = (int) ((key >> 38) & 0x3FFFFFFL);
        if ((x & 0x2000000) != 0) x |= 0xFC000000;
        if ((z & 0x2000000) != 0) z |= 0xFC000000;
        return new Location(w, x, y, z);
    }

    private boolean isInZone(Player p) {
        if (p == null || p.getWorld() == null || !p.getWorld().equals(getCenter().getWorld())) return false;
        int dx = p.getLocation().getBlockX() - getCenter().getBlockX();
        int dz = p.getLocation().getBlockZ() - getCenter().getBlockZ();
        return Math.abs(dx) <= 35 && Math.abs(dz) <= 35;
    }

    private boolean hasPlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (isInZone(p)) return true;
        }
        return false;
    }

    private void cleanCooldowns() {
        long now = System.currentTimeMillis();
        lootCooldowns.entrySet().removeIf(e -> now - e.getValue() > 5000);
    }

    private void checkCompletion(Main plugin) {
        if (ended || meteorsSpawned < MAX_METEORS || lastOpenTime == 0) return;
        for (Meteor m : meteors.values()) if (!m.opened) return;
        long since = (System.currentTimeMillis() - lastOpenTime) / 1000L;
        if (since >= END_DELAY_SEC) {
            ended = true;
            plugin.getAirdropManager().stop(true);
        }
    }

    private void updateMainHologram(Main plugin, ConfigurationSection cfg) {
        plugin.getHologramManager().updateHologram(getHologramName(), buildMainHolo(cfg));
    }

    private List<String> buildMainHolo(ConfigurationSection cfg) {
        List<String> out = new ArrayList<>();
        if (cfg == null) return out;
        for (String s : cfg.getStringList("hologram")) {
            if (s.contains("%status%")) {
                out.add("");
                out.add("&7Метеорит &#F8BEFB" + meteorsSpawned + " &7из &#F8BEFB" + MAX_METEORS);
                out.add(statusLine());
                out.add("");
            } else out.add(s);
        }
        return out;
    }

    private String statusLine() {
        switch (phase) {
            case "waiting": return "&#F8BEFB● &fОжидание игроков";
            case "next": {
                long ms = nextMeteorTime - System.currentTimeMillis();
                long rem = Math.max(0, (ms + 999) / 1000);
                return "&#F8BEFB● &fПадение через &#F8BEFB" + rem + "с";
            }
            case "flying": return "&#F8BEFB● &cПадение метеорита";
            case "cooling": return "&#F8BEFB● &fМетеорит остывает";
            case "open": return "&#F8BEFB● &aСундук доступен";
            case "ending": {
                long ms = END_DELAY_SEC * 1000L - (System.currentTimeMillis() - lastOpenTime);
                long rem = Math.max(0, (ms + 999) / 1000);
                return "&#F8BEFB● &fЗавершение через &#F8BEFB" + rem + "с";
            }
        }
        return "&7—";
    }

private void broadcastParticle(World w, Particle particle, Location loc, int count,
                                   double dx, double dy, double dz, double extra, Object data) {
        double maxDistSq = 80 * 80; // was 100 — tighter radius, uses distanceSquared
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(w)) continue;
            if (p.getLocation().distanceSquared(loc) > maxDistSq) continue;
            if (data != null) p.spawnParticle(particle, loc, count, dx, dy, dz, extra, data);
            else p.spawnParticle(particle, loc, count, dx, dy, dz, extra);
        }
    }
}