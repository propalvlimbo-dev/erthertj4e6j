package ru.rooyzee.elytrixcemetery.graves;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;
import ru.rooyzee.elytrixcemetery.utils.ParticleUtil;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Grave {

    private final int id;
    private final Location digLocation;
    private final Location chestLocation;
    private final Location hologramLocation;

    private boolean dug = false;
    private long dugAt = 0L;
    private final String hologramName;

    public Grave(int id, Location digLocation, Location chestLocation, Location hologramLocation) {
        this.id = id;
        this.digLocation = digLocation;
        this.chestLocation = chestLocation;
        this.hologramLocation = hologramLocation;
        this.hologramName = "cemetery_grave_" + id;
    }

    public int getId() { return id; }
    public Location getDigLocation() { return digLocation; }
    public Location getChestLocation() { return chestLocation; }
    public Location getHologramLocation() { return hologramLocation; }
    public boolean isDug() { return dug; }
    public long getDugAt() { return dugAt; }
    public String getHologramName() { return hologramName; }

    public void setDug(boolean dug) { this.dug = dug; }

    public boolean dig(Main plugin, Player player) {
        if (dug) return false;

        World world = digLocation.getWorld();
        if (world == null) return false;

        dug = true;
        dugAt = System.currentTimeMillis();

        // 1. Убираем землю (открываем яму к сундуку)
        Block block = digLocation.getBlock();
        block.setType(Material.AIR, false);

        // 2. Наполняем сундук лутом с учетом текущего онлайна
        Block cb = chestLocation.getBlock();
        if (cb.getType() != Material.CHEST) {
            cb.setType(Material.CHEST, false);
        }

        if (cb.getState() instanceof Chest) {
            Chest chest = (Chest) cb.getState();
            Inventory inv = chest.getInventory();
            inv.clear();

            int online = Bukkit.getOnlinePlayers().size();
            List<ItemStack> loot = plugin.getLootStorageManager().rollGraveDrops(online);

            ThreadLocalRandom rng = ThreadLocalRandom.current();
            int size = inv.getSize();
            for (ItemStack item : loot) {
                if (item != null && item.getType() != Material.AIR) {
                    for (int tries = 0; tries < 10; tries++) {
                        int slot = rng.nextInt(size);
                        if (inv.getItem(slot) == null || inv.getItem(slot).getType() == Material.AIR) {
                            inv.setItem(slot, item.clone());
                            break;
                        }
                    }
                }
            }
        }

        // 3. Шанс на появление мощного визер-скелета (Осквернённого Стража)
        double guardianChance = 25.0;
        if (ThreadLocalRandom.current().nextDouble(0.0, 100.0) <= guardianChance) {
            plugin.getGhostController().spawnGuardian(digLocation, player, this);
        }

        // 4. Звуки и эффекты раскопки
        world.playSound(digLocation, Sound.ITEM_SHOVEL_FLATTEN, 1.2f, 0.8f);
        world.playSound(digLocation, Sound.BLOCK_GRAVEL_BREAK, 1.0f, 1.2f);
        world.playSound(digLocation, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.6f, 0.6f);

        ParticleUtil.spawnBlock(world, digLocation.clone().add(0.5, 0.5, 0.5), 25, 0.3, 0.3, 0.3, Material.COARSE_DIRT);
        ParticleUtil.spawn(world, ParticleUtil.SOUL, digLocation.clone().add(0.5, 0.5, 0.5), 10, 0.2, 0.4, 0.2, 0.02);

        updateHologram(plugin);
        return true;
    }

    public void openChestFor(Player player) {
        Block cb = chestLocation.getBlock();
        if (cb.getState() instanceof Chest) {
            Chest chest = (Chest) cb.getState();
            player.openInventory(chest.getInventory());
            player.playSound(chestLocation, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        }
    }

    public void bury(Main plugin) {
        dug = false;
        dugAt = 0L;

        World world = digLocation.getWorld();
        if (world == null) return;

        Block block = digLocation.getBlock();
        block.setType(Material.COARSE_DIRT, false);

        // Очищаем сундук под землей
        Block cb = chestLocation.getBlock();
        if (cb.getState() instanceof Chest) {
            Chest chest = (Chest) cb.getState();
            chest.getInventory().clear();
        }

        ParticleUtil.spawnBlock(world, digLocation.clone().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, Material.COARSE_DIRT);
        updateHologram(plugin);
    }

    public void updateHologram(Main plugin) {
        if (!plugin.getConfigManager().isGraveHologramsEnabled()) {
            removeHologram();
            return;
        }

        try {
            if (eu.decentsoftware.holograms.api.DHAPI.getHologram(hologramName) == null) {
                eu.decentsoftware.holograms.api.DHAPI.createHologram(hologramName, hologramLocation, buildHoloLines(plugin));
            } else {
                eu.decentsoftware.holograms.api.DHAPI.setHologramLines(eu.decentsoftware.holograms.api.DHAPI.getHologram(hologramName), buildHoloLines(plugin));
            }
        } catch (Throwable ignored) {}
    }

    public void removeHologram() {
        try {
            if (eu.decentsoftware.holograms.api.DHAPI.getHologram(hologramName) != null) {
                eu.decentsoftware.holograms.api.DHAPI.removeHologram(hologramName);
            }
        } catch (Throwable ignored) {}
    }

    private List<String> buildHoloLines(Main plugin) {
        if (!dug) {
            return java.util.Arrays.asList(
                    ColorUtil.colorize("&#F8BEFB&lДревняя могила"),
                    ColorUtil.colorize("&7Вскопайте лопатой")
            );
        } else {
            return java.util.Arrays.asList(
                    ColorUtil.colorize("&#96FF91&lМогила раскопана"),
                    ColorUtil.colorize("&7Сундук открыт")
            );
        }
    }
}
