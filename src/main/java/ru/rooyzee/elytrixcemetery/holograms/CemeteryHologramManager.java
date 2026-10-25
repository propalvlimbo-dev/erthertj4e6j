package ru.rooyzee.elytrixcemetery.holograms;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;

import java.util.ArrayList;
import java.util.List;

public class CemeteryHologramManager {

    private static final String MAIN_HOLO_NAME = "cemetery_main_info";

    private final Main plugin;

    public CemeteryHologramManager(Main plugin) {
        this.plugin = plugin;
    }

    public void createOrUpdateMainHologram() {
        Location center = plugin.getConfigManager().getCenterLocation();
        if (center == null || center.getWorld() == null) return;

        // Располагаем над главными воротами входа (Z = -20)
        Location holoLoc = new Location(center.getWorld(),
                center.getBlockX() + 0.5,
                center.getBlockY() + 6.2,
                center.getBlockZ() - 20.0);

        List<String> lines = buildMainHoloLines();

        try {
            Hologram holo = DHAPI.getHologram(MAIN_HOLO_NAME);
            if (holo == null) {
                DHAPI.createHologram(MAIN_HOLO_NAME, holoLoc, lines);
            } else {
                DHAPI.setHologramLines(holo, lines);
            }
        } catch (Throwable ignored) {}
    }

    public void removeMainHologram() {
        try {
            if (DHAPI.getHologram(MAIN_HOLO_NAME) != null) {
                DHAPI.removeHologram(MAIN_HOLO_NAME);
            }
        } catch (Throwable ignored) {}
    }

    public List<String> buildMainHoloLines() {
        List<String> lines = new ArrayList<>();

        int dug = plugin.getGraveManager().getDugCount();
        int total = plugin.getGraveManager().getTotalCount();
        int ghosts = plugin.getGhostController().getAliveCount();
        String time = plugin.getGraveManager().formatTime(plugin.getGraveManager().getRemainingRefillSeconds());

        // Стиль строго по скриншоту 1 (без фона, аккуратные заголовки и буллеты •)
        lines.add(ColorUtil.colorize("&#F8BEFB&lКЛАДБИЩЕ"));
        lines.add(ColorUtil.colorize("Режим: &#F8BEFBГриферский"));
        lines.add("");
        lines.add(ColorUtil.colorize("• &#F8BEFBРаскопано могил: &f" + dug + "&7/&#F8BEFB" + total));
        lines.add(ColorUtil.colorize("• &#F8BEFBПризраков вокруг: &f" + ghosts));
        lines.add(ColorUtil.colorize("• &#F8BEFBОбновление могил: &f" + time));
        lines.add("");
        lines.add(ColorUtil.colorize("• &#F8BEFBНаш сайт: &felytrix.pw"));
        lines.add(ColorUtil.colorize("• &#F8BEFBDiscord: &#F8BEFBdsc.gg/elytrix"));

        return lines;
    }
}
