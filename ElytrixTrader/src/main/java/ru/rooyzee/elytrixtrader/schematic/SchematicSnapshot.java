package ru.rooyzee.elytrixtrader.schematic;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Снимок вставленной схематики: исходные блоки (для отката при исчезновении
 * торговца), поставленные блоки (для анимации исчезновения) и МАРКЕР ЦЕНТРА.
 *
 * Маркер центра — специальный блок в схематике (по умолчанию УЛЕЙ / BEEHIVE),
 * которым автор схемы размечает середину постройки. NPC торговца ставится
 * ровно на этот блок: ноги на его верхней грани, по центру блока. Если маркер
 * в схеме не найден — используется прежняя логика «на самый верх постройки».
 */
public class SchematicSnapshot {

    private final Location origin;
    private final List<BlockSnapshot> originalBlocks;
    private final List<Location> placedBlocks;      // блоки, поставленные схематикой
    private final Location centerMarker;            // блок-маркер центра (может быть null)

    public SchematicSnapshot(Location origin, List<BlockSnapshot> originalBlocks, List<Location> placedBlocks) {
        this(origin, originalBlocks, placedBlocks, null);
    }

    public SchematicSnapshot(Location origin, List<BlockSnapshot> originalBlocks,
                             List<Location> placedBlocks, Location centerMarker) {
        this.origin = origin.clone();
        this.originalBlocks = originalBlocks;
        this.placedBlocks = placedBlocks;
        this.centerMarker = centerMarker == null ? null : centerMarker.clone();
    }

    public Location origin() {
        return origin;
    }

    public List<BlockSnapshot> originalBlocks() {
        return originalBlocks;
    }

    public List<Location> placedBlocks() {
        return placedBlocks;
    }

    /** Блок-маркер центра схематики или null, если маркера нет. */
    public Location centerMarker() {
        return centerMarker;
    }

    /**
     * Точка, где должен СТОЯТЬ торговец: верхняя грань блока-маркера,
     * по центру блока. null — маркер не найден, позицию выбирает вызывающий.
     */
    public Location standingSpot() {
        if (centerMarker == null) return null;
        World world = centerMarker.getWorld();
        if (world == null) return null;
        return new Location(world,
                centerMarker.getBlockX() + 0.5,
                centerMarker.getBlockY() + 1.0,
                centerMarker.getBlockZ() + 0.5);
    }

    public void restore() {
        World world = origin.getWorld();
        if (world == null) return;
        if (originalBlocks.isEmpty() && placedBlocks.isEmpty()) return;

        // Позиции, где ДО вставки стоял не-воздух (их восстанавливаем как есть).
        java.util.Set<Long> originalPositions = new java.util.HashSet<>();
        for (BlockSnapshot snap : originalBlocks) {
            originalPositions.add(BlockSnapshot.key(snap.x(), snap.y(), snap.z()));
        }

        // 1) Возвращаем исходные не-воздушные блоки.
        for (BlockSnapshot snap : originalBlocks) {
            snap.restore(world);
        }

        // 2) Раньше в снимке хранился и воздух — восстановление само «сдувало»
        //    блоки схемы, поставленные на месте воздуха. Воздух больше не
        //    храним (основная экономия на спавне), поэтому такие блоки убираем
        //    отдельным проходом по списку поставленных блоков.
        for (Location loc : placedBlocks) {
            if (loc == null || loc.getWorld() == null) continue;
            long key = BlockSnapshot.key(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            if (originalPositions.contains(key)) continue;
            org.bukkit.block.Block block = world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            org.bukkit.Material type = block.getType();
            if (type == org.bukkit.Material.AIR || type == org.bukkit.Material.CAVE_AIR
                    || type == org.bukkit.Material.VOID_AIR) {
                continue;
            }
            try {
                block.setType(org.bukkit.Material.AIR, false);
            } catch (Throwable ignored) {
            }
        }
    }

    public List<BlockSnapshot> copy() {
        return new ArrayList<>(originalBlocks);
    }
}
