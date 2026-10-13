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

import org.bukkit.scheduler.BukkitTask;

import ru.rooyzee.elytrixairdrop.Main;

import ru.rooyzee.elytrixairdrop.airdrops.ActiveAirdrop;

import ru.rooyzee.elytrixairdrop.airdrops.Airdrop;

import ru.rooyzee.elytrixairdrop.managers.SchematicManager;

import ru.rooyzee.elytrixairdrop.utils.ColorUtil;



import java.util.*;

import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicInteger;



public class ActiveSkyAirdrop extends ActiveAirdrop {



    private static final int MAX_SHULKERS = 5;

    private static final long NO_ACTIVATION_TIMEOUT_MS = 10 * 60_000L;

    private static final long AFTER_ACTIVATION_TIMEOUT_MS = 15 * 60_000L;

    private static final long AFTER_LOOTED_MS = 60_000L;

    private static final long ROLL_DURATION_MS = 10_000L;

    private static final long UNLOCK_DELAY_MS = 3 * 60_000L;

    private static final long LOOT_COOLDOWN_MS = 500L;



    private static final Material[] ROLL_MATERIALS = new Material[]{

            Material.GRAY_SHULKER_BOX,

            Material.BLUE_SHULKER_BOX,

            Material.RED_SHULKER_BOX,

            Material.YELLOW_SHULKER_BOX,

            Material.LIME_SHULKER_BOX,

            Material.PINK_SHULKER_BOX,

            Material.CYAN_SHULKER_BOX,

            Material.ORANGE_SHULKER_BOX

    };



    public enum Rarity {

        COMMON("common", Material.GRAY_SHULKER_BOX, "&7Обычный", 60),

        RARE("rare", Material.BLUE_SHULKER_BOX, "&9Редкий", 35),

        MYTHIC("mythic", Material.RED_SHULKER_BOX, "&cМифический", 15),

        LEGENDARY("legendary", Material.YELLOW_SHULKER_BOX, "&eЛегендарный", 5);



        public final String id;

        public final Material material;

        public final String display;

        public final int weight;



        Rarity(String id, Material material, String display, int weight) {

            this.id = id;

            this.material = material;

            this.display = display;

            this.weight = weight;

        }



        public static Rarity roll(Random r) {

            int total = 0;

            for (Rarity v : values()) total += v.weight;

            int roll = r.nextInt(total);

            int acc = 0;

            for (Rarity v : values()) {

                acc += v.weight;

                if (roll < acc) return v;

            }

            return COMMON;

        }

    }



    private static class ShulkerData {

        Location loc;

        long key;

        boolean activated;

        boolean rolling;

        boolean unlocked;

        boolean looted;

        Rarity rarity;

        long rollEndTime;

        long unlockTime;

        Inventory inventory;

        Map<Integer, ItemStack> realLoot = new HashMap<>();

        String holoName;

        BukkitTask rollTask;

    }



private final long spawnTime = System.currentTimeMillis();
    private final Map<Long, ShulkerData> shulkers = new LinkedHashMap<>();
    private final Map<UUID, Long> lootCooldowns = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private long lastLootedTime = 0;
    private boolean anyActivated = false;
    private long firstActivationTime = 0;
    private BukkitTask fastTickTask = null;
    private static final AtomicInteger HOLO_COUNTER = new AtomicInteger(0);
    private final List<Location> knownBeacons;

    public ActiveSkyAirdrop(Location center, String regionId, String hologramName,
                            Map<Location, SchematicManager.BlockData> backup, long endTime) {
        this(center, regionId, hologramName, backup, endTime, Collections.emptyList());
    }

    public ActiveSkyAirdrop(Location center, String regionId, String hologramName,
                            Map<Location, SchematicManager.BlockData> backup, long endTime,
                            List<Location> beacons) {
        super(Airdrop.SKY, center, regionId, hologramName, backup, endTime);
        this.knownBeacons = beacons != null ? beacons : Collections.emptyList();
    }

    @Override
    public void onStart(Main plugin, ConfigurationSection cfg) {
        findShulkerLocations(plugin);
        updateMainHologram(plugin, cfg);
        startFastTick(plugin);
    }


    private void startFastTick(Main plugin) {

        fastTickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            if (plugin.getAirdropManager().getActive() != this) {

                if (fastTickTask != null) { fastTickTask.cancel(); fastTickTask = null; }

                return;

            }

            long now = System.currentTimeMillis();

            for (ShulkerData sh : shulkers.values()) {

                if (sh.rolling && now >= sh.rollEndTime) {

                    sh.rolling = false;

                    finalizeRoll(plugin, sh);

                }

                if (sh.activated && !sh.unlocked && !sh.rolling && now >= sh.unlockTime) {

                    sh.unlocked = true;

                    openShulker(plugin, sh);

                }

                if (sh.holoName != null) {

                    plugin.getHologramManager().updateHologram(sh.holoName,

                            Collections.singletonList(buildShulkerHolo(sh)));

                }

            }

        }, 4L, 4L);

    }



    @Override

    public void onTick(Main plugin, ConfigurationSection cfg) {

        long now = System.currentTimeMillis();



        if (!anyActivated && now - spawnTime >= NO_ACTIVATION_TIMEOUT_MS) {

            plugin.getAirdropManager().stop(false);

            return;

        }



        if (anyActivated && now - firstActivationTime >= AFTER_ACTIVATION_TIMEOUT_MS) {

            plugin.getAirdropManager().stop(false);

            return;

        }



        if (allLooted() && lastLootedTime > 0 && now - lastLootedTime >= AFTER_LOOTED_MS) {

            plugin.getAirdropManager().stop(true);

            return;

        }



        updateMainHologram(plugin, cfg);

        cleanCooldowns();

    }



    @Override

    public void onStop(Main plugin, ConfigurationSection cfg) {

        if (fastTickTask != null) { try { fastTickTask.cancel(); } catch (Exception ignored) {} fastTickTask = null; }

        for (ShulkerData sh : shulkers.values()) {

            if (sh.rollTask != null) { try { sh.rollTask.cancel(); } catch (Exception ignored) {} }

            if (sh.inventory != null)

                for (HumanEntity v : new ArrayList<>(sh.inventory.getViewers())) v.closeInventory();

            if (sh.holoName != null) plugin.getHologramManager().removeHologram(sh.holoName);

        }

        shulkers.clear();

        lootCooldowns.clear();

    }



    @Override

    public boolean handleChestClick(Main plugin, Player p, Location blockLoc) {

        long key = blockKeyLong(blockLoc);

        ShulkerData sh = shulkers.get(key);

        if (sh == null) return false;



        if (p.getLocation().distance(sh.loc) > 8.0) {

            p.sendMessage(plugin.getConfigManager().getMessage("airdrop.too-far"));

            return true;

        }



        if (sh.looted) {

            p.sendMessage(plugin.getConfigManager().getMessage("airdrop.already-looted"));

            return true;

        }



        if (!sh.activated) {

            sh.activated = true;

            sh.rolling = true;

            sh.rollEndTime = System.currentTimeMillis() + ROLL_DURATION_MS;

            if (!anyActivated) {

                anyActivated = true;

                firstActivationTime = System.currentTimeMillis();

            }

            startRolling(plugin, sh);

            p.sendMessage(plugin.getConfigManager().getMessage("airdrop.activated")

                    .replace("{time}", "3м 10с"));

            p.playSound(sh.loc, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.5f);

            return true;

        }



        if (sh.rolling) {

            p.sendMessage(plugin.getConfigManager().getMessage("airdrop.revealing"));

            return true;

        }



        if (!sh.unlocked) {

            long rem = Math.max(0, (sh.unlockTime - System.currentTimeMillis()) / 1000);

            p.sendMessage(plugin.getConfigManager().getMessage("airdrop.activating-by")

                    .replace("{time}", plugin.getAirdropManager().formatTime(rem)));

            return true;

        }



        if (sh.inventory != null) {

            p.openInventory(sh.inventory);

            p.playSound(p.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 1f, 1f);

        }

        return true;

    }



    @Override

    public boolean handleLootClick(Main plugin, Player p, InventoryClickEvent e) {

        ShulkerData found = null;

        for (ShulkerData sh : shulkers.values()) {

            if (sh.inventory == e.getInventory()) { found = sh; break; }

        }

        if (found == null) return false;

        final ShulkerData target = found;



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



        int slot = e.getSlot();

        ItemStack real = target.realLoot.get(slot);

        if (real == null) { e.setCancelled(true); return true; }



        e.setCancelled(true);

        e.getInventory().setItem(slot, null);

        target.realLoot.remove(slot);

        lootCooldowns.put(p.getUniqueId(), now);



        Map<Integer, ItemStack> left = p.getInventory().addItem(real.clone());

        for (ItemStack drop : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);

        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);



        if (target.realLoot.isEmpty() && !target.looted) {

            target.looted = true;

            lastLootedTime = System.currentTimeMillis();

            for (HumanEntity v : new ArrayList<>(target.inventory.getViewers())) v.closeInventory();

            Bukkit.getScheduler().runTaskLater(plugin, () -> {

                if (plugin.getAirdropManager().getActive() != this) return;

                Block b = target.loc.getBlock();

                b.setType(Material.AIR, false);

                if (target.holoName != null) {

                    plugin.getHologramManager().removeHologram(target.holoName);

                    target.holoName = null;

                }

            }, 5L);

        }

        return true;

    }



    @Override

    public boolean isProtectedBlock(Location loc) {

        if (loc == null) return false;

        long k = blockKeyLong(loc);

        if (shulkers.containsKey(k)) return true;

        return super.isProtectedBlock(loc);

    }



    private void startRolling(Main plugin, ShulkerData sh) {

        sh.rollTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            if (plugin.getAirdropManager().getActive() != this) {

                if (sh.rollTask != null) sh.rollTask.cancel();

                return;

            }

            if (!sh.rolling) {

                if (sh.rollTask != null) sh.rollTask.cancel();

                return;

            }

            Block b = sh.loc.getBlock();

            Material m = ROLL_MATERIALS[random.nextInt(ROLL_MATERIALS.length)];

            b.setType(m, false);

sh.loc.getWorld().spawnParticle(Particle.END_ROD, sh.loc.clone().add(0.5, 0.6, 0.5),
                    3, 0.25, 0.25, 0.25, 0.015);
            // Sound only every other roll tick to reduce audio spam
            if (random.nextBoolean()) {
                sh.loc.getWorld().playSound(sh.loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.5f + random.nextFloat() * 0.5f);
            }
        }, 0L, 5L);
    }



    private void finalizeRoll(Main plugin, ShulkerData sh) {

        if (sh.rollTask != null) { sh.rollTask.cancel(); sh.rollTask = null; }

        sh.rarity = Rarity.roll(random);

        Block b = sh.loc.getBlock();

        b.setType(sh.rarity.material, false);

        sh.unlockTime = System.currentTimeMillis() + UNLOCK_DELAY_MS;



        sh.loc.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, sh.loc.clone().add(0.5, 1, 0.5),

                50, 0.5, 0.5, 0.5, 0.15);

        sh.loc.getWorld().playSound(sh.loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);




    }



    private void openShulker(Main plugin, ShulkerData sh) {

        List<ItemStack> pool = plugin.getAirdropManager().getLootFileService().load("sky_" + sh.rarity.id);

        String title = ColorUtil.colorize("&f☁ &#F8BEFBᴀɪʀᴅʀᴏᴘ &7» " + sh.rarity.display);

        sh.inventory = Bukkit.createInventory(null, 27, title);



        if (pool.isEmpty()) {

            plugin.getLogger().warning("Sky airdrop: empty loot pool for " + sh.rarity.id);

            return;

        }



        int count = 5 + random.nextInt(10);

        List<Integer> slots = new ArrayList<>();

        for (int i = 0; i < 27; i++) slots.add(i);

        Collections.shuffle(slots, random);



        ItemStack mask = createMask();

        for (int i = 0; i < Math.min(count, 27); i++) {

            int slot = slots.get(i);

            sh.realLoot.put(slot, pool.get(random.nextInt(pool.size())).clone());

            sh.inventory.setItem(slot, mask.clone());

        }



        sh.loc.getWorld().playSound(sh.loc, Sound.BLOCK_SHULKER_BOX_OPEN, 1f, 1f);

        sh.loc.getWorld().spawnParticle(Particle.PORTAL, sh.loc.clone().add(0.5, 0.5, 0.5),

                30, 0.5, 0.5, 0.5, 0.3);

    }



    private ItemStack createMask() {

        ItemStack mask = new ItemStack(Material.BLACK_DYE);

        ItemMeta meta = mask.getItemMeta();

        meta.setDisplayName(ColorUtil.colorize("&#F8BEFB???"));

        mask.setItemMeta(meta);

        return mask;

    }



private void findShulkerLocations(Main plugin) {
        World w = getCenter().getWorld();
        if (w == null) return;

        // Use beacon positions from schematic paste — avoids 61x31x61 (~116k) block scan
        List<Location> beacons = new ArrayList<>();
        if (knownBeacons != null && !knownBeacons.isEmpty()) {
            beacons.addAll(knownBeacons);
        } else {
            // Fallback: reduced scan only if paste didn't report beacons
            int cx = getCenter().getBlockX();
            int cy = getCenter().getBlockY();
            int cz = getCenter().getBlockZ();
            int r = 20;
            for (int dy = -10; dy <= 10; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                        if (b.getType() == Material.BEACON) {
                            beacons.add(b.getLocation());
                            if (beacons.size() >= MAX_SHULKERS) break;
                        }
                    }
                    if (beacons.size() >= MAX_SHULKERS) break;
                }
                if (beacons.size() >= MAX_SHULKERS) break;
            }
        }

        int idx = 0;
        for (Location beacon : beacons) {
            if (idx >= MAX_SHULKERS) break;
            Location shulkerLoc = new Location(w, beacon.getBlockX(), beacon.getBlockY() + 3, beacon.getBlockZ());
            Block b = shulkerLoc.getBlock();
            getBackup().put(new Location(w, shulkerLoc.getBlockX(), shulkerLoc.getBlockY(), shulkerLoc.getBlockZ()),
                    new SchematicManager.BlockData(b.getType(), b.getBlockData().getAsString()));
            b.setType(Material.GRAY_SHULKER_BOX, false);

            ShulkerData sh = new ShulkerData();
            sh.loc = shulkerLoc;
            sh.key = blockKeyLong(shulkerLoc);
            String uniqueName = "airdrop_sky_" + System.currentTimeMillis() + "_" + HOLO_COUNTER.incrementAndGet() + "_" + idx;
            try {
                eu.decentsoftware.holograms.api.DHAPI.createHologram(uniqueName,
                        shulkerLoc.clone().add(0.5, 1.5, 0.5),
                        Collections.singletonList(ColorUtil.colorize("&7Не активирован")));
                sh.holoName = uniqueName;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to create shulker hologram: " + e.getMessage());
            }
            shulkers.put(sh.key, sh);
            idx++;
        }
        plugin.getLogger().info("Sky airdrop: found " + shulkers.size() + " shulkers");
    }


    private String buildShulkerHolo(ShulkerData sh) {

        if (sh.looted) return "&7— &fЗабран";

        if (sh.unlocked) return "&#F8BEFB● &aОткрыт";

        if (sh.rolling) return "&#F8BEFB● &fРедкость определяется...";

        if (sh.activated && sh.rarity != null) {

            long rem = Math.max(0, (sh.unlockTime - System.currentTimeMillis()) / 1000);

            return sh.rarity.display + " &7— &#F8BEFB" + (rem / 60) + "м " + (rem % 60) + "с";

        }

        return "&7Не активирован";

    }



    private void updateMainHologram(Main plugin, ConfigurationSection cfg) {

        if (cfg == null) return;

        List<String> lines = new ArrayList<>();

        int act = 0, loot = 0;

        for (ShulkerData sh : shulkers.values()) {

            if (sh.activated) act++;

            if (sh.looted) loot++;

        }

        String status = "&7Активировано: &#F8BEFB" + act + "&7/&#F8BEFB" + shulkers.size()

                + " &8│ &7Забрано: &#F8BEFB" + loot;

        for (String s : cfg.getStringList("hologram")) {

            lines.add(s.replace("%status%", status));

        }

        plugin.getHologramManager().updateHologram(getHologramName(), lines);

    }



    private boolean allLooted() {

        if (shulkers.isEmpty()) return false;

        for (ShulkerData sh : shulkers.values()) if (!sh.looted) return false;

        return true;

    }



    private void cleanCooldowns() {

        long now = System.currentTimeMillis();

        lootCooldowns.entrySet().removeIf(e -> now - e.getValue() > 5000);

    }



    private long blockKeyLong(Location loc) {

        long x = loc.getBlockX() & 0x3FFFFFFL;

        long y = loc.getBlockY() & 0xFFFL;

        long z = loc.getBlockZ() & 0x3FFFFFFL;

        return (x << 38) | (z << 12) | y;

    }

}