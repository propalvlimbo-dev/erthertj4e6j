package ru.rooyzee.elytrixschalkerpvp.manager;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;
import ru.rooyzee.elytrixschalkerpvp.Main;
import ru.rooyzee.elytrixschalkerpvp.model.Rarity;
import ru.rooyzee.elytrixschalkerpvp.model.SchalkerData;
import ru.rooyzee.elytrixschalkerpvp.model.SchalkerState;
import ru.rooyzee.elytrixschalkerpvp.util.ColorUtil;
import ru.rooyzee.elytrixschalkerpvp.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HologramManager {

    private final Main plugin;
    private final Map<String, Hologram> holograms = new ConcurrentHashMap<>();

    public HologramManager(Main plugin) {
        this.plugin = plugin;
    }

    public void updateHologram(SchalkerData data, Rarity overrideRarity, long remainingSeconds) {
        if (!data.tryResolveWorld()) return;
        if (data.getLocation() == null || data.getLocation().getWorld() == null) return;
        String holoId = "escp_" + data.getId().replace("-", "_").replace(".", "_");
        Location holoLoc;
        try {
            holoLoc = data.getLocation().clone().add(0.5, 3.4, 0.5);
        } catch (Exception e) {
            return;
        }
        if (holoLoc.getWorld() == null) return;

        ConfigManager cm = plugin.getConfigManager();
        Rarity rarity = overrideRarity != null ? overrideRarity : data.getCurrentRarity();

        String state;
        switch (data.getState()) {
            case SLEEPING:
                state = "sleeping";
                break;
            case READY:
                state = "ready";
                break;
            case ACTIVE:
                state = "active";
                break;
            case EXPLODING:
                state = "exploding";
                break;
            default:
                state = "sleeping";
        }

        List<String> templateLines = cm.getHologramLines(state);
        List<String> lines = new ArrayList<>();

        String rarityName = rarity != null ? cm.getRarityName(rarity) : "—";
        String rarityColor = rarity != null ? ColorUtil.colorize(cm.getRarityHexColor(rarity)) : "§7";
        String timeStr = TimeUtil.formatSeconds(remainingSeconds);

        for (String line : templateLines) {
            line = line.replace("{rarity_name}", rarityName)
                    .replace("{rarity_color}", rarityColor)
                    .replace("{time}", timeStr);
            lines.add(line);
        }

        try {
            Hologram existing = holograms.get(holoId);
            if (existing != null) {
                DHAPI.setHologramLines(existing, lines);
            } else {
                Hologram hologram = DHAPI.createHologram(holoId, holoLoc, false, lines);
                holograms.put(holoId, hologram);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeHologram(SchalkerData data) {
        String holoId = "escp_" + data.getId().replace("-", "_").replace(".", "_");
        try {
            Hologram hologram = holograms.remove(holoId);
            if (hologram != null) {
                hologram.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeAll() {
        for (Map.Entry<String, Hologram> entry : holograms.entrySet()) {
            try {
                entry.getValue().delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        holograms.clear();
    }
}