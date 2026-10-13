package ru.rooyzee.elytrixairdrop.airdrops.types;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
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
    private static final int METEOR_HEIGHT = 45;
    private static final int SPHERE_RADIUS = 2;
    private static final int DAMAGE_RADIUS = 10;
    private static final int CRATER_RADIUS = 4;
    private static final double MAX_DAMAGE = 10.0;
    private static final long NO_PLAYER_TIMEOUT = 600_000L;
    private static final long LOOT_COOLDOWN_MS = 500L;

    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<Location, Meteor> meteors = new LinkedHashMap<>();
    private final Set<Location> breakable = new HashSet<>();
    private final Map<Location, Inventory> chests = new HashMap<>();
    private final Map<Location, Map<Integer, ItemStack>> realLootBySlot = new HashMap<>();
    private final Map<UUID, Long> lootCooldowns = new ConcurrentHashMap<>();
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
        boolean cooled;
        boolean opened;
        long landTime;
        String holoName;
        final Set<Location> blocks = new HashSet<>();
        final Set<Location> craterBlocks = new HashSet<>();
        Location chestLoc;
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

        for (Inventory inv : chests.values()) {
            for (HumanEntity v : new ArrayList<>(inv.getViewers())) v.closeInventory();
        }
        for (Meteor m : meteors.values()) {
            if (m.holoName != null) plugin.getHologramManager().removeHologram(m.holoName);
        }

        for (Location loc : chests.keySet()) {
            if (loc.getBlock().getType() == Material.ENDER_CHEST) {
                loc.getBlock().setType(Material.AIR, false);
            }
        }

        meteors.clear();
        chests.clear();
        breakable.clear();
        realLootBySlot.clear();
        lootCooldowns.clear();
    }

    private void backupOriginalBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        Location norm = normalize(loc);
        Map<Location, SchematicManager.BlockData> backup = getBackup();
        if (backup != null && !backup.containsKey(norm)) {
            Block b = norm.getBlock();
            backup.put(norm, new SchematicManager.BlockData(b.getType(), b.getBlockData().getAsString()));
        }
    }

    @Override
    public boolean handleChestClick(Main plugin, Player p, Location blockLoc) {
        Location normalized = normalize(blockLoc);
        Inventory inv = chests.get(normalized);
        if (inv == null) return false;

        if (!isInZone(p)) {
            p.sendMessage(plugin.getConfigManager().getMessage("airdrop.too-far"));
            return true;
        }

        Meteor meteor = findMeteorByChest(normalized);
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
        Location invKey = null;
        for (Map.Entry<Location, Inventory> en : chests.entrySet()) {
            if (en.getValue() == e.getInventory()) { invKey = en.getKey(); break; }
        }
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
        Location norm = normalize(loc);
        if (breakable.contains(norm)) return false;
        if (getBackup() != null && getBackup().containsKey(norm)) return true;
        if (chests.containsKey(norm)) return true;
        return super.isProtectedBlock(loc);
    }

    public boolean isBreakable(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Location norm = normalize(loc);
        return breakable.contains(norm) && norm.getBlock().getType() == Material.OBSIDIAN;
    }

    public void onBreakableBroken(Main plugin, Location loc) {
        Location norm = normalize(loc);
        breakable.remove(norm);
        for (Meteor m : meteors.values()) m.blocks.remove(norm);
        loc.getWorld().spawnParticle(Particle.BLOCK_CRACK, loc.clone().add(0.5, 0.5, 0.5),
                15, 0.3, 0.3, 0.3, 0.1, Bukkit.createBlockData(Material.OBSIDIAN));
    }

    public boolean isMeteorArea(Location loc) {
        Location norm = normalize(loc);
        if (getBackup() != null && getBackup().containsKey(norm)) return true;
        if (chests.containsKey(norm)) return true;
        return false;
    }

    private Meteor findMeteorByChest(Location chestLoc) {
        for (Meteor m : meteors.values()) {
            if (m.chestLoc != null && m.chestLoc.equals(chestLoc)) return m;
        }
        return null;
    }

    private void cleanupMeteor(Main plugin, Meteor meteor) {
        Map<Location, SchematicManager.BlockData> backup = getBackup();
        if (meteor.chestLoc != null) {
            Inventory inv = chests.remove(meteor.chestLoc);
            realLootBySlot.remove(meteor.chestLoc);
            if (inv != null) for (HumanEntity v : new ArrayList<>(inv.getViewers())) v.closeInventory();
            if (backup != null) {
                SchematicManager.BlockData d = backup.get(meteor.chestLoc);
                if (d != null) restoreBlock(meteor.chestLoc, d);
                else if (meteor.chestLoc.getBlock().getType() == Material.ENDER_CHEST) {
                    meteor.chestLoc.getBlock().setType(Material.AIR, false);
                }
            }
        }
        if (meteor.holoName != null) {
            plugin.getHologramManager().removeHologram(meteor.holoName);
            meteor.holoName = null;
        }
        for (Location k : new HashSet<>(meteor.blocks)) {
            breakable.remove(k);
            if (backup != null) {
                SchematicManager.BlockData d = backup.get(k);
                if (d != null) restoreBlock(k, d);
                else k.getBlock().setType(Material.AIR, false);
            }
        }
        for (Location k : new HashSet<>(meteor.craterBlocks)) {
            if (backup != null) {
                SchematicManager.BlockData d = backup.get(k);
                if (d != null) restoreBlock(k, d);
            }
        }
        meteors.remove(meteor.center);
    }

    private void restoreBlock(Location loc, SchematicManager.BlockData data) {
        if (loc == null || loc.getWorld() == null || data == null) return;
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
        Location start = target.clone().add(0.5, METEOR_HEIGHT, 0.5);
        Location core = target.clone().add(0.5, SPHERE_RADIUS, 0.5);

        final double[] currentY = {start.getY()};
        final double[] velocity = {0.6};
        final double gravity = 0.08;
        final double maxSpeed = 1.8;
        final BukkitTask[] holder = new BukkitTask[1];

        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.getAirdropManager().getActive() != this) {
                if (holder[0] != null) holder[0].cancel();
                return;
            }

            velocity[0] += gravity;
            if (velocity[0] > maxSpeed) velocity[0] = maxSpeed;
            double step = velocity[0];
            currentY[0] -= step;

            if (currentY[0] <= core.getY()) {
                currentY[0] = core.getY();
                if (holder[0] != null) holder[0].cancel();
                onImpact(plugin, target);
                return;
            }

            Location head = new Location(w, core.getX(), currentY[0], core.getZ());
            broadcastParticle(w, Particle.FLAME, head, 30, 0.8, 0.8, 0.8, 0.05, null);
            broadcastParticle(w, Particle.LAVA, head, 10, 0.6, 0.6, 0.6, 0.02, null);
            broadcastParticle(w, Particle.CAMPFIRE_COSY_SMOKE, head, 15, 0.5, 0.5, 0.5, 0.02, null);
            broadcastParticle(w, Particle.SMOKE_LARGE, head, 12, 0.7, 0.7, 0.7, 0.03, null);

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(w) && p.getLocation().distanceSquared(head) <= 60 * 60) {
                    p.playSound(head, Sound.ENTITY_BLAZE_BURN, 0.4f, 0.6f);
                }
            }
        }, 1L, 1L);
        tasks.add(holder[0]);
    }

    private void onImpact(Main plugin, Location target) {
        World w = target.getWorld();
        Location core = target.clone().add(0, SPHERE_RADIUS, 0);
        Location visualCenter = core.clone().add(0.5, 0, 0.5);

        broadcastParticle(w, Particle.EXPLOSION_HUGE, visualCenter, 3, 1.5, 0.8, 1.5, 0, null);
        broadcastParticle(w, Particle.LAVA, visualCenter, 50, 3, 1.5, 3, 0, null);
        broadcastParticle(w, Particle.FLAME, visualCenter, 80, 4, 1.5, 4, 0.2, null);
        broadcastParticle(w, Particle.SMOKE_LARGE, visualCenter, 40, 4, 1.5, 4, 0.1, null);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(w)) continue;
            if (p.getLocation().distance(core) > 80) continue;
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1f, 1f);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);
        }

        Location normCore = normalize(core);
        Meteor m = new Meteor();
        m.center = normCore;
        m.landTime = System.currentTimeMillis();
        meteors.put(normCore, m);

        buildSphere(normCore, Material.MAGMA_BLOCK, m);
        applyDamage(core);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getAirdropManager().getActive() != this) return;
            createCrater(plugin, w, target, m);
        }, 2L);

        phase = "cooling";

        m.holoName = plugin.getHologramManager().createHologram(
                normCore.clone().add(0.5, SPHERE_RADIUS + 1.5, 0.5),
                Collections.singletonList(meteorHoloLine(m)));
    }

    private void createCrater(Main plugin, World w, Location target, Meteor meteor) {
        int cx = target.getBlockX(), cy = target.getBlockY() - 1, cz = target.getBlockZ();

        for (int dx = -CRATER_RADIUS; dx <= CRATER_RADIUS; dx++) {
            for (int dz = -CRATER_RADIUS; dz <= CRATER_RADIUS; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq > CRATER_RADIUS * CRATER_RADIUS) continue;
                if (Math.abs(dx) <= SPHERE_RADIUS && Math.abs(dz) <= SPHERE_RADIUS) continue;
                int depth = (int) Math.round(1.5 - Math.sqrt(distSq) * 0.3);
                if (depth < 0) depth = 0;
                for (int dy = 0; dy <= depth; dy++) {
                    Location l = normalize(new Location(w, cx + dx, cy - dy, cz + dz));
                    Block b = l.getBlock();
                    if (b.getType() == Material.AIR) continue;
                    if (b.getType() == Material.BEDROCK) continue;

                    backupOriginalBlock(l);
                    meteor.craterBlocks.add(l);

                    if (dy == 0) {
                        w.spawnParticle(Particle.BLOCK_CRACK, l.clone().add(0.5, 0.5, 0.5),
                                12, 0.4, 0.4, 0.4, 0.1, b.getBlockData());
                    }
                    b.setType(Material.AIR, false);
                }
            }
        }
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

            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 0, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 60, 0, false, false, false));

            if (dealDamage) {
                p.damage(ZONE_DAMAGE);
                p.playSound(p.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.4f, 1.2f);
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
                    Location loc = normalize(new Location(w, cx + dx, cy + dy, cz + dz));
                    Block b = loc.getBlock();
                    backupOriginalBlock(loc);
                    b.setType(mat, false);
                    meteor.blocks.add(loc);
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
        List<Location> sortedBlocks = new ArrayList<>(m.blocks);
        Location chestLoc = normalize(m.center);
        sortedBlocks.sort((a, b) -> Double.compare(b.distanceSquared(m.center), a.distanceSquared(m.center)));

        m.chestLoc = chestLoc;
        backupOriginalBlock(chestLoc);

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
                    Location loc = sortedBlocks.get(index[0]);
                    if (loc.equals(chestLoc)) continue;
                    Block b = loc.getBlock();
                    b.setType(Material.OBSIDIAN, false);
                    breakable.add(loc);

                    broadcastParticle(w, Particle.SMOKE_NORMAL, loc.clone().add(0.5, 0.5, 0.5),
                            4, 0.15, 0.05, 0.15, 0.01, null);

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

        Inventory inv = createMaskedLootInventory(plugin, m.chestLoc,
                plugin.getConfigManager().getConfig().getConfigurationSection("airdrops.fire"));
        chests.put(m.chestLoc, inv);

        broadcastParticle(w, Particle.PORTAL, m.center.clone().add(0.5, 0.5, 0.5),
                30, 0.6, 0.6, 0.6, 0.4, null);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(w) && p.getLocation().distance(m.center) <= 40) {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.7f);
            }
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

    private Inventory createMaskedLootInventory(Main plugin, Location chestLoc, ConfigurationSection cfg) {
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
        realLootBySlot.put(chestLoc, realMap);
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

        for (int dx = -14; dx <= 14; dx += 2) {
            outer:
            for (int dz = -14; dz <= 14; dz += 2) {
                int x = cx + dx, z = cz + dz;
                int topY = w.getHighestBlockYAt(x, z);
                Block ground = w.getBlockAt(x, topY, z);
                if (ground.getType() != Material.POLISHED_BLACKSTONE) continue;
                if (!isFullCube(ground)) continue;

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

    private Location normalize(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
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
        double maxDistSq = 80 * 80;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(w)) continue;
            if (p.getLocation().distanceSquared(loc) > maxDistSq) continue;
            if (data != null) p.spawnParticle(particle, loc, count, dx, dy, dz, extra, data);
            else p.spawnParticle(particle, loc, count, dx, dy, dz, extra);
        }
    }
}
