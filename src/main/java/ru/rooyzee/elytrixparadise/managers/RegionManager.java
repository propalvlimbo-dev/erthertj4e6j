package ru.rooyzee.elytrixparadise.managers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import ru.rooyzee.elytrixparadise.Main;

public class RegionManager {

    private final Main plugin;
    public static final String REGION_ID = "elytrix_paradise";
    private final boolean worldGuardPresent;

    public RegionManager(Main plugin) {
        this.plugin = plugin;
        this.worldGuardPresent = Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
    }

    public boolean createParadiseRegion(Location center, int radiusXZ, int minY, int maxY) {
        if (!worldGuardPresent || center == null || center.getWorld() == null) return false;

        try {
            World world = center.getWorld();
            int cx = center.getBlockX();
            int cz = center.getBlockZ();

            // Расширенный регион: 150 блоков в стороны, от самого низа (-64) до самого верха (320)
            int effectiveMinY = Math.min(-64, world.getMinHeight());
            int effectiveMaxY = Math.max(320, world.getMaxHeight());

            BlockVector3 min = BlockVector3.at(cx - radiusXZ, effectiveMinY, cz - radiusXZ);
            BlockVector3 max = BlockVector3.at(cx + radiusXZ, effectiveMaxY, cz + radiusXZ);

            ProtectedCuboidRegion region = new ProtectedCuboidRegion(REGION_ID, min, max);
            region.setPriority(200);

            // Flags:
            // 1. Отключить спавн мобов
            region.setFlag(Flags.MOB_SPAWNING, StateFlag.State.DENY);
            // 2. Урон от падения ВКЛЮЧЕН (ALLOW)
            region.setFlag(Flags.FALL_DAMAGE, StateFlag.State.ALLOW);
            // 3. Запрет разрушения и постройки (кроме осколков через плагин)
            region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
            region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);
            // 4. Запрет взрывов
            region.setFlag(Flags.CREEPER_EXPLOSION, StateFlag.State.DENY);
            region.setFlag(Flags.OTHER_EXPLOSION, StateFlag.State.DENY);
            region.setFlag(Flags.TNT, StateFlag.State.DENY);
            // 5. Запрет огня и лавы
            region.setFlag(Flags.FIRE_SPREAD, StateFlag.State.DENY);
            region.setFlag(Flags.LAVA_FIRE, StateFlag.State.DENY);
            region.setFlag(Flags.LAVA_FLOW, StateFlag.State.DENY);
            region.setFlag(Flags.WATER_FLOW, StateFlag.State.DENY);
            // 6. PvP и эндер-пёрлы ВКЛЮЧЕНЫ
            region.setFlag(Flags.PVP, StateFlag.State.ALLOW);
            region.setFlag(Flags.ENDERPEARL, StateFlag.State.ALLOW);
            region.setFlag(Flags.CHORUS_TELEPORT, StateFlag.State.ALLOW);

            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
            if (rm == null) return false;

            rm.addRegion(region);
            try {
                rm.save();
            } catch (Exception ignored) {}

            plugin.getLogger().info("Регион защиты '" + REGION_ID + "' успешно создан в WorldGuard (150 блоков, полный диапазон высот).");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось создать регион WorldGuard: " + t.getMessage());
            return false;
        }
    }

    public void removeParadiseRegion(World world) {
        if (!worldGuardPresent || world == null) return;
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(world));
            if (rm == null) return;

            rm.removeRegion(REGION_ID);
            try {
                rm.save();
            } catch (Exception ignored) {}
            plugin.getLogger().info("Регион защиты '" + REGION_ID + "' удалён из WorldGuard.");
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось удалить регион WorldGuard: " + t.getMessage());
        }
    }

    public boolean isInParadiseRegion(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (worldGuardPresent) {
            try {
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                com.sk89q.worldguard.protection.managers.RegionManager rm = container.get(BukkitAdapter.adapt(loc.getWorld()));
                if (rm != null) {
                    ProtectedRegion region = rm.getRegion(REGION_ID);
                    if (region != null) {
                        return region.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    }
                }
            } catch (Throwable ignored) {}
        }

        Location center = plugin.getConfigManager().getCenterLocation();
        if (center == null || !loc.getWorld().equals(center.getWorld())) return false;
        double radiusXZ = plugin.getConfigManager().getActionbarRadiusXZ();
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();

        return (dx * dx + dz * dz <= radiusXZ * radiusXZ);
    }
}
