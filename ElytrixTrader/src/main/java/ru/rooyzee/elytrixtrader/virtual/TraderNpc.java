package ru.rooyzee.elytrixtrader.virtual;

import dev.by1337.virtualentity.api.entity.EntityAnimation;
import dev.by1337.virtualentity.api.entity.EquipmentSlot;
import dev.by1337.virtualentity.api.tracker.PlayerTracker;
import dev.by1337.virtualentity.api.virtual.VirtualEntity;
import dev.by1337.virtualentity.api.virtual.decoration.VirtualArmorStand;
import dev.by1337.virtualentity.api.virtual.player.VirtualPlayer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.by1337.blib.geom.Vec3d;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.TraderConfig;
import ru.rooyzee.elytrixtrader.util.Components;

import java.util.ArrayList;
import java.util.List;

/**
 * ЕДИНСТВЕННОЕ место плагина, где используется библиотека VirtualEntity.
 *
 * Владеет всеми виртуальными сущностями одного торговца:
 *  - {@link VirtualPlayer} — сам NPC;
 *  - {@link VirtualArmorStand} — строки голограммы;
 *  - один общий {@link PlayerTracker} — он один решает, кому из игроков
 *    показывать/прятать сущности (входит/выходит из радиуса видимости).
 *
 * Никаких VirtualExperienceOrb здесь нет и быть не должно: в 1.16.5 клиент
 * сам «тикает» XP-орбы (магнит к игрокам в радиусе 8 блоков — ванильный код
 * ExperienceOrbEntity.tick, флагом он не отключается), поэтому любые
 * виртуальные орбы дёргаются и улетают к игрокам. Декоративные эффекты
 * живут на частицах — см. {@link AuraEffect}.
 *
 * Жизненный цикл: {@code create -> spawn() -> tick() каждую итерацию ->
 * destroy()}. После destroy объект мёртв и пересоздаётся заново.
 */
public final class TraderNpc {

    private final Main plugin;
    private final TraderConfig config;
    private final Location location; // позиция НОГ npc (авторитетная, мутируется setPos)

    private VirtualPlayer player;
    private final List<VirtualArmorStand> holograms = new ArrayList<>();
    private PlayerTracker tracker;

    private boolean spawned;

    // ── Плавный поворот головы ─────────────────────────────────────────
    // Пакеты поворота отправляем только когда угол реально изменился
    // (dead-zone 0.05°) — в статике ноль пакетов.
    private float currentYaw;
    private float currentPitch;
    private float targetYaw;
    private float targetPitch;
    private float lastSentYaw = -9999.0f;
    private float lastSentPitch = -9999.0f;

    // Кеш текста голограммы: обновляем имена только когда текст изменился.
    private String lastHologramState;

    public TraderNpc(Main plugin, TraderConfig config, Location location) {
        this.plugin = plugin;
        this.config = config;
        this.location = location.clone();
        this.currentYaw = this.location.getYaw();
        this.currentPitch = 0.0f;
        this.targetYaw = currentYaw;
        this.targetPitch = currentPitch;
    }

    // ─────────────────────────────────────────────────────────────────
    // Жизненный цикл
    // ─────────────────────────────────────────────────────────────────

    public void spawn() {
        if (spawned) return;
        World world = location.getWorld();
        if (world == null) return;

        player = VirtualPlayer.create();
        player.setPos(new Vec3d(location));
        player.setYaw(location.getYaw());
        player.setPitch(0.0F);
        player.setNoGravity(true);   // NPC стоит ровно, его двигает только плагин
        player.setSilent(true);
        player.setCustomNameVisible(false);
        hideIdentity();

        if (config.holdItem() && config.icon().material() != null && config.icon().material().isItem()) {
            heldItem(new ItemStack(config.icon().material(), 1));
        }

        List<VirtualEntity> entities = new ArrayList<>();
        entities.add(player);
        if (plugin.config().hologramEnabled() && config.hologramEnabled()) {
            buildHolograms(entities);
        }

        tracker = new PlayerTracker(world, entities, new Vec3d(location), trackerRadius());
        spawned = true;

        // Скрыть из таба чуть позже — после того как клиент получит spawn-пакеты.
        Bukkit.getScheduler().runTaskLater(plugin, this::hideFromTabForAll, 15L);
    }

    /** Ни ника, ни строки в табе, ни лишней информации о «игроке». */
    private void hideIdentity() {
        try { player.setName(""); } catch (Throwable ignored) {}
        try { player.setDisplayName(Component.empty()); } catch (Throwable ignored) {}
        try { player.setListed(false); } catch (Throwable ignored) {}      // 1.19.4+
        try { player.hideFromTab(); } catch (Throwable ignored) {}
        try { player.setLatency(0); } catch (Throwable ignored) {}
        try { player.setGameMode(org.bukkit.GameMode.SURVIVAL); } catch (Throwable ignored) {}
        try { player.setPlayerModeCustomisation((byte) 0x00); } catch (Throwable ignored) {}
    }

    public void tick() {
        if (!spawned) return;
        if (tracker != null) {
            try { tracker.tick(); } catch (Throwable ignored) {}
        }
        updateSmoothRotation();
    }

    /** Принудительно прогнать трекер (используется анимацией исчезновения). */
    public void tickViewers() {
        if (tracker != null) {
            try { tracker.tick(); } catch (Throwable ignored) {}
        }
    }

    public void destroy() {
        if (!spawned) return;
        spawned = false;
        if (tracker != null) {
            try { tracker.removeAll(); } catch (Throwable ignored) {}
            tracker = null;
        }
        removeFromTabForAll();
        holograms.clear();
        player = null;
    }

    // ─────────────────────────────────────────────────────────────────
    // Внешний вид
    // ─────────────────────────────────────────────────────────────────

    /** Применяет скин. Вызывает respawn — entityId может измениться! */
    public void applySkin(String value, String signature) {
        if (!spawned || player == null) return;
        try {
            player.setTexture(value, signature);
            player.respawn();
            // respawn снова показывает строку в табе — прячем и пере-прячем
            Bukkit.getScheduler().runTaskLater(plugin, this::hideFromTabForAll, 2L);
        } catch (Throwable ignored) {}
    }

    public void heldItem(ItemStack item) {
        if (player == null || item == null) return;
        try { player.setEquipment(EquipmentSlot.MAINHAND, item); } catch (Throwable ignored) {}
    }

    public void setInvisible(boolean invisible) {
        if (player == null) return;
        try { player.setInvisible(invisible); } catch (Throwable ignored) {}
    }

    public void swing() {
        if (player == null) return;
        try { player.playAnimation(EntityAnimation.SWING_MAIN_ARM); } catch (Throwable ignored) {}
    }

    public void raiseHands() {
        if (player == null) return;
        try {
            player.playAnimation(EntityAnimation.SWING_MAIN_ARM);
            player.playAnimation(EntityAnimation.SWING_OFFHAND);
        } catch (Throwable ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────
    // Позиция и взгляд
    // ─────────────────────────────────────────────────────────────────

    public void setPos(Location newLoc) {
        if (player == null) return;
        try {
            player.setPos(new Vec3d(newLoc));
            location.setX(newLoc.getX());
            location.setY(newLoc.getY());
            location.setZ(newLoc.getZ());
            location.setYaw(newLoc.getYaw());
            location.setPitch(newLoc.getPitch());
        } catch (Throwable ignored) {}
    }

    public void lookAt(Player target) {
        if (player == null || target == null) return;
        Location from = location.clone().add(0, 1.5, 0);      // голова NPC
        Location to = target.getLocation().clone().add(0, 1.6, 0);
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, distXZ));
        setTargetLook(yaw, pitch);
    }

    public void setTargetLook(float yaw, float pitch) {
        this.targetYaw = yaw;
        this.targetPitch = Math.max(-60, Math.min(60, pitch));
    }

    private void updateSmoothRotation() {
        if (player == null) return;
        float yawDiff = targetYaw - currentYaw;
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;
        float yawStep = yawDiff * 0.15f;
        if (Math.abs(yawStep) > 8) yawStep = Math.signum(yawStep) * 8;
        if (Math.abs(yawDiff) < 0.5f) currentYaw = targetYaw;
        else currentYaw += yawStep;

        float pitchDiff = targetPitch - currentPitch;
        float pitchStep = pitchDiff * 0.15f;
        if (Math.abs(pitchStep) > 5) pitchStep = Math.signum(pitchStep) * 5;
        if (Math.abs(pitchDiff) < 0.5f) currentPitch = targetPitch;
        else currentPitch += pitchStep;

        while (currentYaw > 180) currentYaw -= 360;
        while (currentYaw < -180) currentYaw += 360;

        boolean changed = Math.abs(currentYaw - lastSentYaw) >= 0.05f
                || Math.abs(currentPitch - lastSentPitch) >= 0.05f;
        if (!changed) return; // стоим ровно — пакеты не шлём
        lastSentYaw = currentYaw;
        lastSentPitch = currentPitch;

        try {
            player.setYaw(currentYaw);
            player.setPitch(currentPitch);
        } catch (Throwable ignored) {
            // Запасной путь для версий библиотеки без setYaw/setPitch:
            // смотрим в точку «голова + направление взгляда * 10».
            try {
                double radYaw = Math.toRadians(currentYaw);
                double radPitch = Math.toRadians(currentPitch);
                double dirX = -Math.sin(radYaw) * Math.cos(radPitch);
                double dirY = -Math.sin(radPitch);
                double dirZ = Math.cos(radYaw) * Math.cos(radPitch);
                player.lookAt(new Vec3d(
                        location.getX() + dirX * 10,
                        location.getY() + 1.5 + dirY * 10,
                        location.getZ() + dirZ * 10));
            } catch (Throwable ignored2) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Голограмма
    // ─────────────────────────────────────────────────────────────────

    private void buildHolograms(List<VirtualEntity> entities) {
        List<String> lines = config.hologramLines();
        double baseY = location.getY() + config.hologramHeight();
        for (int index = 0; index < lines.size(); index++) {
            VirtualArmorStand stand = VirtualArmorStand.create();
            stand.setPos(new Vec3d(location.getX(), baseY - index * config.hologramLineHeight(), location.getZ()));
            stand.setMarker(true);
            stand.setNoBasePlate(true);
            stand.setSmall(true);
            stand.setNoGravity(true);
            stand.setSilent(true);
            stand.setInvisible(true);
            stand.setCustomNameVisible(true);
            holograms.add(stand);
            entities.add(stand);
        }
    }

    /**
     * Обновляет текст голограммы. Строки уже собраны владельцем (плейсхолдеры
     * подставлены); здесь только диф — если текст не менялся, пакетов нет.
     */
    public void updateHologramText(List<String> lines) {
        if (holograms.isEmpty() || lines == null) return;
        StringBuilder state = new StringBuilder();
        for (int i = 0; i < holograms.size() && i < lines.size(); i++) {
            state.append(lines.get(i)).append((char) 0);
        }
        if (state.toString().equals(lastHologramState)) return;
        lastHologramState = state.toString();
        for (int i = 0; i < holograms.size() && i < lines.size(); i++) {
            holograms.get(i).setCustomName(Components.colored(lines.get(i)));
        }
    }

    public void hideHologramNames() {
        for (VirtualArmorStand stand : holograms) {
            try { stand.setCustomNameVisible(false); } catch (Throwable ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Таб-лист
    // ─────────────────────────────────────────────────────────────────

    public void hideFromTabForAll() {
        if (player == null) return;
        removeFromTabForAll();
        try { player.setListed(false); } catch (Throwable ignored) {}
    }

    public void hideFromTabFor(Player target) {
        if (player == null || target == null) return;
        try { player.sendRemovePlayerPacket(target); } catch (Throwable ignored) {}
    }

    private void removeFromTabForAll() {
        if (player == null) return;
        try {
            for (Player online : Bukkit.getOnlinePlayers()) {
                try { player.sendRemovePlayerPacket(online); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────
    // Прочее
    // ─────────────────────────────────────────────────────────────────

    /** Радиус видимости виртуальных сущностей в блоках. */
    private int trackerRadius() {
        return Math.max(32, plugin.config().viewDistance() * 16);
    }

    public boolean spawned() { return spawned; }
    public int entityId() { return player == null ? -1 : player.getId(); }
    public VirtualPlayer player() { return player; }
    public Location location() { return location; }
}
