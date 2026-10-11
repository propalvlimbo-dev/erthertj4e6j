package ru.rooyzee.elytrixairdrop.airdrops.types;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.airdrops.ActiveAirdrop;
import ru.rooyzee.elytrixairdrop.airdrops.Airdrop;
import ru.rooyzee.elytrixairdrop.managers.SchematicManager;
import ru.rooyzee.elytrixairdrop.services.LaunchService;
import ru.rooyzee.elytrixairdrop.utils.ColorUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActivePeacefulAirdrop extends ActiveAirdrop {

    private static final int INV_SIZE = 54;
    private static final int WAVE_SIZE = 6;
    private static final int MAX_WAVES = 8;
    private static final long LOOT_COOLDOWN_MS = 500L;
    private static final long ACTIVATION_TIMEOUT_MS = 10 * 60_000L;
    private static final long MAX_LIFETIME_MS = 15 * 60_000L;

private final long spawnTime = System.currentTimeMillis();
    private Location chestLoc;
    private volatile boolean activated;
    private long unlockTime;
    private volatile boolean unlocked;
    private volatile boolean looted;
    private boolean beforeLaunch;
    private final List<ItemStack> pendingLoot = new ArrayList<>();
    private int wavesSpawned;
    private Inventory lootInventory;
    private final Map<Integer, ItemStack> realLootBySlot = new HashMap<>();
    private final Map<UUID, Long> lootCooldowns = new ConcurrentHashMap<>();
    private final List<Location> knownBeacons;

    public ActivePeacefulAirdrop(Location center, String regionId, String hologramName,
                                 Map<Location, SchematicManager.BlockData> backup, long endTime) {
        this(center, regionId, hologramName, backup, endTime, Collections.emptyList());
    }

    public ActivePeacefulAirdrop(Location center, String regionId, String hologramName,
                                 Map<Location, SchematicManager.BlockData> backup, long endTime,
                                 List<Location> beacons) {
        super(Airdrop.PEACEFUL, center, regionId, hologramName, backup, endTime);
        this.knownBeacons = beacons != null ? beacons : Collections.emptyList();
    }

    @Override
    public void onStart(Main plugin, ConfigurationSection cfg) {
        chestLoc = findChestLocation();
        if (chestLoc == null) {
            plugin.getLogger().warning("Peaceful airdrop: chest location not found");
            return;
        }
        Block chestBlock = chestLoc.getBlock();
        getBackup().put(blockKey(chestLoc),
                new SchematicManager.BlockData(chestBlock.getType(), chestBlock.getBlockData().getAsString()));
        chestBlock.setType(Material.CHEST, false);

        List<ItemStack> pool = new ArrayList<>(plugin.getAirdropManager().getLootFileService().load("peaceful"));
        pendingLoot.clear();
        if (pool.isEmpty()) return;
        Random r = new Random();
        Collections.shuffle(pool, r);
        int total = WAVE_SIZE * MAX_WAVES;
        for (int i = 0; i < total; i++) {
            pendingLoot.add(pool.get(r.nextInt(pool.size())).clone());
        }
    }

    @Override
    public void onTick(Main plugin, ConfigurationSection cfg) {
        long now = System.currentTimeMillis();

        if (now - spawnTime >= MAX_LIFETIME_MS) {
            plugin.getAirdropManager().stop(false);
            return;
        }

        if (!activated && now - spawnTime >= ACTIVATION_TIMEOUT_MS) {
            plugin.getAirdropManager().stop(false);
            return;
        }

        if (activated && !unlocked) {
            long remaining = Math.max(0, (unlockTime - now) / 1000);
            if (!beforeLaunch && remaining <= 2 && chestLoc != null) {
                beforeLaunch = true;
                new LaunchService(plugin, this, chestLoc).launch();
            }
            if (now >= unlockTime) {
                unlocked = true;
                openLoot();
                plugin.getAirdropManager().getBroadcaster().broadcast("broadcast.unlocked", Collections.emptyMap());
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 1.4f);
                }
            }
        }
        plugin.getHologramManager().updateHologram(getHologramName(), buildHolo(cfg));
        cleanCooldowns();
    }

    @Override
    public void onStop(Main plugin, ConfigurationSection cfg) {
        if (lootInventory != null) {
            for (HumanEntity viewer : new ArrayList<>(lootInventory.getViewers())) viewer.closeInventory();
        }
        lootCooldowns.clear();
        realLootBySlot.clear();
        pendingLoot.clear();
    }

    @Override
    public boolean handleChestClick(Main plugin, Player p, Location blockLoc) {
        if (chestLoc == null || !sameBlock(blockLoc, chestLoc)) return false;
        if (p.getLocation().distance(chestLoc) > 6.0) {
            p.sendMessage(plugin.getConfigManager().getMessage("airdrop.too-far"));
            return true;
        }
        if (looted) {
            p.sendMessage(plugin.getConfigManager().getMessage("airdrop.already-looted"));
            return true;
        }
        if (!activated) {
            activated = true;
            long seconds = plugin.getConfigManager().getConfig()
                    .getInt("airdrops.peaceful.activation-minutes", 5) * 60L;
            unlockTime = System.currentTimeMillis() + seconds * 1000L;
            String time = plugin.getAirdropManager().formatTime(seconds);
            p.sendMessage(plugin.getConfigManager().getMessage("airdrop.activated").replace("{time}", time));
            String td = ColorUtil.colorize(plugin.getConfigManager().getConfig()
                    .getString("airdrops.peaceful.display-name", "peaceful"));
            Map<String, String> vars = new HashMap<>();
            vars.put("player", p.getName());
            vars.put("time", time);
            vars.put("type", td);
            plugin.getAirdropManager().getBroadcaster().broadcast("broadcast.activated", vars);
            for (Player pl : Bukkit.getOnlinePlayers()) {
                pl.playSound(pl.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.2f);
            }
            return true;
        }
        if (!unlocked) {
            long remaining = Math.max(0, (unlockTime - System.currentTimeMillis()) / 1000);
            p.sendMessage(plugin.getConfigManager().getMessage("airdrop.activating-by")
                    .replace("{time}", plugin.getAirdropManager().formatTime(remaining)));
            return true;
        }
        if (lootInventory == null) return true;
        p.openInventory(lootInventory);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        return true;
    }

    @Override
    public boolean handleLootClick(Main plugin, Player p, InventoryClickEvent e) {
        if (lootInventory == null || e.getInventory() != lootInventory) return false;
        long now = System.currentTimeMillis();
        Long last = lootCooldowns.get(p.getUniqueId());
        if (last != null && now - last < LOOT_COOLDOWN_MS) {
            e.setCancelled(true);
            return true;
        }
        if (e.getClickedInventory() != lootInventory) {
            if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) e.setCancelled(true);
            return true;
        }
        int slot = e.getSlot();
        ItemStack real = realLootBySlot.get(slot);
        if (real == null) { e.setCancelled(true); return true; }

        e.setCancelled(true);
        e.getInventory().setItem(slot, null);
        realLootBySlot.remove(slot);
        lootCooldowns.put(p.getUniqueId(), now);

        ItemStack toGive = real.clone();
        Map<Integer, ItemStack> left = p.getInventory().addItem(toGive);
        for (ItemStack drop : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);

        if (realLootBySlot.isEmpty()) {
            if (!pendingLoot.isEmpty() && wavesSpawned < MAX_WAVES) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (plugin.getAirdropManager().getActive() == this && lootInventory != null) spawnWave();
                }, 20L);
            } else {
                looted = true;
                for (HumanEntity viewer : new ArrayList<>(lootInventory.getViewers())) viewer.closeInventory();
                String td = ColorUtil.colorize(plugin.getConfigManager().getConfig()
                        .getString("airdrops.peaceful.display-name", "peaceful"));
                Map<String, String> vars = new HashMap<>();
                vars.put("player", p.getName());
                vars.put("type", td);
                plugin.getAirdropManager().getBroadcaster().broadcast("broadcast.looted", vars);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (plugin.getAirdropManager().getActive() == this) plugin.getAirdropManager().stop(true);
                }, 100L);
            }
        }
        return true;
    }

    private void cleanCooldowns() {
        long now = System.currentTimeMillis();
        lootCooldowns.entrySet().removeIf(e -> now - e.getValue() > 5000);
    }

    private void openLoot() {
        lootInventory = Bukkit.createInventory(null, INV_SIZE, ColorUtil.colorize("&#F8BEFBАирдроп"));
        spawnWave();
    }

    private void spawnWave() {
        if (lootInventory == null || wavesSpawned >= MAX_WAVES || pendingLoot.isEmpty()) return;
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < INV_SIZE; i++) {
            ItemStack it = lootInventory.getItem(i);
            if (it == null || it.getType() == Material.AIR) free.add(i);
        }
        Collections.shuffle(free);
        ItemStack mask = createMask();
        int placed = 0;
        for (int i = 0; i < WAVE_SIZE && !pendingLoot.isEmpty() && placed < free.size(); i++) {
            int slot = free.get(placed++);
            realLootBySlot.put(slot, pendingLoot.remove(0));
            lootInventory.setItem(slot, mask.clone());
        }
        wavesSpawned++;
    }

    private ItemStack createMask() {
        ItemStack mask = new ItemStack(Material.BLACK_DYE);
        ItemMeta meta = mask.getItemMeta();
        meta.setDisplayName(ColorUtil.colorize("&#F8BEFB???"));
        mask.setItemMeta(meta);
        return mask;
    }

    private List<String> buildHolo(ConfigurationSection cfg) {
        String status;
        if (!activated) {
            long remaining = Math.max(0, (ACTIVATION_TIMEOUT_MS - (System.currentTimeMillis() - spawnTime)) / 1000);
            status = "&7● &fИсчезнет через: &#F8BEFB" + (remaining / 60) + "м " + (remaining % 60) + "с";
        }
        else if (!unlocked) {
            long rem = Math.max(0, (unlockTime - System.currentTimeMillis()) / 1000);
            status = "&7● &fОткроется через: &#F8BEFB" + rem + "с";
        } else status = "&7● &aОткрыт, забирайте лут";
        List<String> out = new ArrayList<>();
        if (cfg == null) return out;
        for (String s : cfg.getStringList("hologram")) out.add(s.replace("%status%", status));
        return out;
    }

private Location findChestLocation() {
        World w = getCenter().getWorld();
        if (w == null) return null;

        // Prefer beacon positions collected during schematic paste (O(1), no world scan)
        if (knownBeacons != null && !knownBeacons.isEmpty()) {
            Location beacon = knownBeacons.get(0);
            return new Location(w, beacon.getBlockX(), beacon.getBlockY() + 3, beacon.getBlockZ());
        }

        // Fallback: tiny scan only around center (was 31x21x31 = ~20k blocks)
        int cx = getCenter().getBlockX();
        int cy = getCenter().getBlockY();
        int cz = getCenter().getBlockZ();
        int radius = 8;
        for (int dy = 0; dy <= 10; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (b.getType() == Material.BEACON) {
                        return new Location(w, b.getX(), b.getY() + 3, b.getZ());
                    }
                }
            }
        }
        int y = w.getHighestBlockYAt(cx, cz) + 1;
        return new Location(w, cx, y, cz);
    }
}