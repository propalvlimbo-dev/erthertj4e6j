package ru.rooyzee.elytrixtrader.util;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class Sounds {

    private Sounds() {
    }

    public static void play(Player player, String name, float volume, float pitch) {
        if (player == null) {
            return;
        }
        play(player.getLocation(), name, volume, pitch);
    }

    public static void play(Location location, String name, float volume, float pitch) {
        if (location == null || name == null || name.trim().isEmpty() || name.trim().equalsIgnoreCase("none")) {
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        String key = name.trim().toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_').replace(' ', '_');
        try {
            world.playSound(location, Sound.valueOf(key), SoundCategory.PLAYERS, volume, pitch);
        } catch (IllegalArgumentException exception) {
            try {
                world.playSound(location, name.trim(), volume, pitch);
            } catch (Throwable ignored) {
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    public static void fromSection(Player player, ConfigurationSection section, String path, String def, float defVolume, float defPitch) {
        if (section == null) {
            play(player, def, defVolume, defPitch);
            return;
        }
        ConfigurationSection target = section.getConfigurationSection(path);
        if (target != null) {
            play(player, Cfg.str(target, "sound", def), (float) Cfg.dbl(target, "volume", defVolume), (float) Cfg.dbl(target, "pitch", defPitch));
            return;
        }
        play(player, Cfg.str(section, path, def), defVolume, defPitch);
    }
}
