package ru.rooyzee.elytrixparadise.holograms;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.shards.ParadiseShard;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ShardHologramHandler {

    private final Main plugin;
    private final boolean decentHologramsPresent;
    private final Map<String, List<ArmorStand>> fallbackHolograms = new ConcurrentHashMap<>();

    public ShardHologramHandler(Main plugin) {
        this.plugin = plugin;
        this.decentHologramsPresent = Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
    }

    public void createOrUpdateHologram(ParadiseShard shard) {
        Location holoLoc = shard.getLocation().clone().add(0.5, 1.8, 0.5);
        List<String> lines = getHologramLines(shard);

        if (decentHologramsPresent) {
            try {
                Hologram holo = DHAPI.getHologram(shard.getId());
                if (holo == null) {
                    DHAPI.createHologram(shard.getId(), holoLoc, lines);
                } else {
                    DHAPI.setHologramLines(holo, lines);
                }
                return;
            } catch (Throwable t) {
                // fallback to armor stands
            }
        }

        updateArmorStandHologram(shard.getId(), holoLoc, lines);
    }

    private List<String> getHologramLines(ParadiseShard shard) {
        List<String> lines = new ArrayList<>();
        if (shard.getState() == ParadiseShard.ShardState.ACTIVE) {
            lines.add(ColorUtil.colorize("&#FFFFA0✦ &#F8BEFBОсколок Рая &#FFFFA0✦"));
            lines.add(ColorUtil.colorize("&a● Активен &7(Добывай киркой)"));
            lines.add(ColorUtil.colorize("&eДобывай для получения лута и опыта!"));
            double risk = Math.round(shard.getCurrentExplosionChance() * 10.0) / 10.0;
            if (shard.getHitCount() > 0) {
                lines.add(ColorUtil.colorize("&7Риск взрыва: &#FF5555" + risk + "%"));
            }
        } else {
            lines.add(ColorUtil.colorize("&8✦ &#AAAAAAОсколок Рая &8✦"));
            String timeStr = ColorUtil.formatTimeShort(shard.getCooldownRemaining());
            lines.add(ColorUtil.colorize("&c● Перезарядка: &#FFFFA0" + timeStr));
            lines.add(ColorUtil.colorize("&7Восстанавливает небесную энергию..."));
        }
        return lines;
    }

    private void updateArmorStandHologram(String id, Location baseLoc, List<String> lines) {
        List<ArmorStand> stands = fallbackHolograms.get(id);
        if (stands == null || stands.isEmpty() || stands.stream().anyMatch(Entity::isDead)) {
            removeArmorStandHologram(id);
            stands = new ArrayList<>();
            double yOffset = 0.0;
            for (int i = lines.size() - 1; i >= 0; i--) {
                Location lineLoc = baseLoc.clone().add(0, yOffset, 0);
                ArmorStand stand = (ArmorStand) baseLoc.getWorld().spawnEntity(lineLoc, EntityType.ARMOR_STAND);
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
                    ArmorStand stand = stands.get(i);
                    if (stand.isValid()) {
                        stand.setCustomName(lines.get(i));
                    }
                }
            }
        }
    }

    public void removeHologram(ParadiseShard shard) {
        if (shard == null) return;
        removeHologramById(shard.getId());
    }

    public void removeHologramById(String id) {
        if (decentHologramsPresent) {
            try {
                DHAPI.removeHologram(id);
            } catch (Throwable ignored) {}
        }
        removeArmorStandHologram(id);
    }

    private void removeArmorStandHologram(String id) {
        List<ArmorStand> stands = fallbackHolograms.remove(id);
        if (stands != null) {
            for (ArmorStand stand : stands) {
                if (stand != null && stand.isValid()) {
                    stand.remove();
                }
            }
        }
    }

    public void removeAllHolograms() {
        for (String id : fallbackHolograms.keySet()) {
            removeArmorStandHologram(id);
        }
        fallbackHolograms.clear();
    }
}
