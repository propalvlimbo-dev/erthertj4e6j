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
        // Hologram moved 1 block higher (3.2 blocks above the block)
        Location holoLoc = shard.getLocation().clone().add(0.5, 3.2, 0.5);
        List<String> lines = getHologramLines(shard);

        if (decentHologramsPresent) {
            try {
                Hologram holo = DHAPI.getHologram(shard.getId());
                if (holo == null) {
                    DHAPI.createHologram(shard.getId(), holoLoc, lines);
                } else {
                    DHAPI.moveHologram(holo, holoLoc);
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
            lines.add("#ICON:RED_GLAZED_TERRACOTTA");
            lines.add(ColorUtil.colorize("&f☁ &#F8BEFBОсколок Рая &f☁"));
            lines.add(ColorUtil.colorize("&a● &fСтатус: &aАктивен"));
            lines.add("");
            lines.add(ColorUtil.colorize("&7● &fДобывай киркой для получения лута"));
            double risk = Math.round(shard.getCurrentExplosionChance() * 10.0) / 10.0;
            if (shard.getHitCount() > 0) {
                lines.add(ColorUtil.colorize("&7● &fРиск взрыва: &#F8BEFB" + risk + "%"));
            }
        } else {
            lines.add("#ICON:GRAY_GLAZED_TERRACOTTA");
            lines.add(ColorUtil.colorize("&8☁ &#F8BEFBОсколок Рая &8☁"));
            lines.add(ColorUtil.colorize("&c● &fСтатус: &cПерезарядка"));
            lines.add("");
            String timeStr = ColorUtil.formatTimeShort(shard.getCooldownRemaining());
            lines.add(ColorUtil.colorize("&7● &fВосстановление через: &#F8BEFB" + timeStr));
            lines.add(ColorUtil.colorize("&7● &fОсколок накапливает энергию"));
        }
        return lines;
    }

    private void updateArmorStandHologram(String id, Location baseLoc, List<String> lines) {
        // Strip #ICON directives for armor stand text display
        List<String> textLines = new ArrayList<>();
        for (String l : lines) {
            if (!l.startsWith("#ICON:")) {
                textLines.add(l);
            }
        }

        List<ArmorStand> stands = fallbackHolograms.get(id);
        if (stands == null || stands.isEmpty() || stands.stream().anyMatch(Entity::isDead)) {
            removeArmorStandHologram(id);
            stands = new ArrayList<>();
            double yOffset = 0.0;
            for (int i = textLines.size() - 1; i >= 0; i--) {
                Location lineLoc = baseLoc.clone().add(0, yOffset, 0);
                ArmorStand stand = (ArmorStand) baseLoc.getWorld().spawnEntity(lineLoc, EntityType.ARMOR_STAND);
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setCustomNameVisible(true);
                stand.setCustomName(textLines.get(i));
                stand.setMarker(true);
                stand.setSmall(true);
                stand.setInvulnerable(true);
                stands.add(0, stand);
                yOffset += 0.28;
            }
            fallbackHolograms.put(id, stands);
        } else {
            for (int i = 0; i < textLines.size(); i++) {
                if (i < stands.size()) {
                    ArmorStand stand = stands.get(i);
                    if (stand.isValid()) {
                        stand.setCustomName(textLines.get(i));
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
