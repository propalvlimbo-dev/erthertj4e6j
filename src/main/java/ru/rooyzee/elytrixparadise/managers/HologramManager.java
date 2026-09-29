package ru.rooyzee.elytrixparadise.managers;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.event.ParadiseEvent;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.util.ArrayList;
import java.util.List;

public class HologramManager {

    private static final String HOLOGRAM_NAME = "elytrix_paradise_holo";
    private final Main plugin;

    public HologramManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isDecentHologramsEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
    }

    public void createOrUpdateHologram(ParadiseEvent event) {
        if (!plugin.getConfigManager().isHologramEnabled()) return;
        if (!isDecentHologramsEnabled()) return;

        Location center = plugin.getConfigManager().getCenterLocation();
        double heightOffset = plugin.getConfigManager().getHologramHeightOffset();
        Location holoLoc = center.clone().add(0.5, heightOffset, 0.5);

        List<String> rawLines = plugin.getConfigManager().getHologramLines();
        List<String> formattedLines = new ArrayList<>();

        String phaseName = event != null ? event.getPhaseDisplayName() : "&#208BFBАктивен";
        String timeStr = event != null ? event.getRemainingFormatted() : "00:00";
        String playersCount = event != null ? String.valueOf(event.getPlayerCount()) : "0";

        for (String line : rawLines) {
            String f = line
                    .replace("{phase}", phaseName)
                    .replace("{time}", timeStr)
                    .replace("{players}", playersCount)
                    .replace("{x}", String.valueOf(center.getBlockX()))
                    .replace("{y}", String.valueOf(center.getBlockY()))
                    .replace("{z}", String.valueOf(center.getBlockZ()));
            formattedLines.add(ColorUtil.colorize(f));
        }

        try {
            Hologram holo = DHAPI.getHologram(HOLOGRAM_NAME);
            if (holo == null) {
                DHAPI.createHologram(HOLOGRAM_NAME, holoLoc, formattedLines);
            } else {
                DHAPI.setHologramLines(holo, formattedLines);
                if (!holo.getLocation().equals(holoLoc)) {
                    DHAPI.moveHologram(holo, holoLoc);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при обновлении голограммы DecentHolograms: " + e.getMessage());
        }
    }

    public void removeHologram() {
        if (!isDecentHologramsEnabled()) return;
        try {
            Hologram holo = DHAPI.getHologram(HOLOGRAM_NAME);
            if (holo != null) {
                DHAPI.removeHologram(HOLOGRAM_NAME);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при удалении голограммы DecentHolograms: " + e.getMessage());
        }
    }
}
