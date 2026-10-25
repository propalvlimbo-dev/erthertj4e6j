package ru.rooyzee.elytrixcemetery.holograms;

import eu.decentsoftware.holograms.api.DHAPI;
import ru.rooyzee.elytrixcemetery.Main;

public class CemeteryHologramManager {

    private static final String MAIN_HOLO_NAME = "cemetery_main_info";

    private final Main plugin;

    public CemeteryHologramManager(Main plugin) {
        this.plugin = plugin;
    }

    public void createOrUpdateMainHologram() {
        // Убрана главная голограмма КЛАДБИЩЕ по запросу пользователя
        removeMainHologram();
    }

    public void removeMainHologram() {
        try {
            if (DHAPI.getHologram(MAIN_HOLO_NAME) != null) {
                DHAPI.removeHologram(MAIN_HOLO_NAME);
            }
        } catch (Throwable ignored) {}
    }
}
