package ru.rooyzee.elytrixtrader.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import dev.by1337.virtualentity.api.virtual.player.VirtualPlayer;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.region.TraderRegion;
import ru.rooyzee.elytrixtrader.schematic.SchematicSnapshot;
import ru.rooyzee.elytrixtrader.skin.SkinProfile;
import ru.rooyzee.elytrixtrader.util.ColorUtil;
import ru.rooyzee.elytrixtrader.util.Numbers;
import ru.rooyzee.elytrixtrader.util.Text;
import ru.rooyzee.elytrixtrader.util.Worlds;
import ru.rooyzee.elytrixtrader.virtual.AuraEffect;
import ru.rooyzee.elytrixtrader.virtual.TraderNpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Живой торговец в мире.
 *
 * Здесь только «доменная» логика: сток, предлагаемые сделки, плейсхолдеры,
 * время жизни, кулдауны. ВСЁ, что касается библиотеки VirtualEntity, вынесено
 * в {@link ru.rooyzee.elytrixtrader.virtual.TraderNpc} (NPC + голограмма +
 * трекер видимости) — TraderInstance его лишь координирует. Декоративный
 * эффект (зелёная аура) — частицы: см. AuraEffect.
 *
 * VirtualExperienceOrb больше не используется нигде: в 1.16.5 клиент сам
 * тикает XP-орбы (магнит к игрокам ближе 8 блоков — ванильный код, флагами
 * не отключается), из-за чего виртуальные сферы опыта дёргались и улетали
 * к игрокам. Это не починить на стороне сервера, поэтому эффекты переведены
 * на частицы, у которых нет клиентской физики.
 */
public class TraderInstance {

    private static final java.util.concurrent.atomic.AtomicInteger IDENTIFIER = new java.util.concurrent.atomic.AtomicInteger(1);

    private final Main plugin;
    private final int uid = IDENTIFIER.getAndIncrement();
    private final TraderConfig config;
    private final Location location;
    private final Map<String, Integer> stock;
    private final long createdAt = System.currentTimeMillis();
    private final List<TradeConfig> offeredTrades = new ArrayList<>();
    private final Random random = new Random();

    private final TraderNpc npc;
    private AuraEffect aura;
    private BukkitTask task;
    private long expiresAt;
    private int tickCounter;
    private boolean alive;
    private TraderRegion region;
    private SchematicSnapshot schematicSnapshot;
    private boolean despawning = false;

    public TraderInstance(Main plugin, TraderConfig config, Location location) {
        this.plugin = plugin;
        this.config = config;
        this.location = location.clone();
        this.npc = new TraderNpc(plugin, config, this.location);
        this.stock = new HashMap<>();
        for (String id : config.tradeIds()) {
            TradeConfig trade = plugin.trades().get(id);
            if (trade == null) continue;
            int stockValue = trade.randomStock(random);
            if (stockValue >= 0) {
                stock.put(id, stockValue);
            }
        }
        this.expiresAt = config.lifetimeSeconds() > 0 ? createdAt + config.lifetimeSeconds() * 1000L : Long.MAX_VALUE;
        resolveOfferedTrades();
        // Сток катаем из реально предлагаемых трейдов (редакторские в том числе)
        for (TradeConfig trade : offeredTrades) {
            int stockValue = trade.randomStock(random);
            if (stockValue >= 0) {
                stock.put(trade.id(), stockValue);
            }
        }
    }

    private void resolveOfferedTrades() {
        List<TradeConfig> full = plugin.resolveTrades(config);
        int limit = config.randomTradesCount();
        if (limit > 0 && limit < full.size()) {
            Collections.shuffle(full, random);
            full = new ArrayList<>(full.subList(0, limit));
        }
        offeredTrades.addAll(full);
    }

    // ─────────────────────────────────────────────────────────────────
    // Жизненный цикл
    // ─────────────────────────────────────────────────────────────────

    public void spawn() {
        if (alive) return;
        World world = location.getWorld();
        if (world == null) return;

        npc.spawn();
        if (!npc.spawned()) return;
        alive = true;

        plugin.skins().request(config.skin(), profile -> {
            if (profile != null && alive) {
                applySkin(profile);
            }
        });

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);

        // Аура стартует чуть позже появления NPC
        if (plugin.config().auraEnabled()) {
            aura = new AuraEffect(plugin, this::location);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (alive && aura != null) aura.start();
            }, 10L);
        }
    }

    private void tick() {
        if (!alive) return;
        tickCounter++;
        if (expiresAt > 0L && System.currentTimeMillis() >= expiresAt) {
            plugin.traders().expire(this);
            return;
        }

        npc.tick();

        // Взгляд на ближайшего игрока — раз в 10 тиков (0.5 сек)
        if (tickCounter % 10 == 0) {
            Player nearest = nearest();
            if (nearest != null) npc.lookAt(nearest);
        }

        // Голограммы — раз в 40 тиков; пакет только если текст изменился
        if (tickCounter % 40 == 0) {
            npc.updateHologramText(hologramLines());
        }
    }

    private Player nearest() {
        World world = location.getWorld();
        if (world == null) return null;
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player online : world.getPlayers()) {
            if (!online.isOnline()) continue;
            double distance = online.getLocation().distanceSquared(location);
            if (distance < best && distance < 4096.0D) {
                best = distance;
                nearest = online;
            }
        }
        return nearest;
    }

    public void despawn() { despawn(false); }

    public void despawn(boolean withAnimation) {
        if (!alive || despawning) return;
        if (withAnimation) despawning = true;
        cleanupEffects();
        alive = false;
        npc.destroy();
        plugin.traders().forget(this);
    }

    /** Подготовка к анимации исчезновения: логика выключена, NPC ещё нужен. */
    public void despawnAnimated() {
        if (!alive || despawning) return;
        despawning = true;
        if (task != null) { task.cancel(); task = null; }
    }

    /** Полное исчезновение (вызывается по завершении анимации или сразу). */
    public void finalizeDespawn() {
        cleanupEffects();
        alive = false;
        despawning = false;
        if (task != null) { task.cancel(); task = null; }
        npc.destroy();
        plugin.traders().forget(this);
    }

    private void cleanupEffects() {
        if (task != null) { task.cancel(); task = null; }
        if (aura != null) {
            aura.stop();
            aura = null;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Делегаты к NPC (используются анимациями и менеджером)
    // ─────────────────────────────────────────────────────────────────

    public void stopAura() {
        if (aura != null) {
            aura.stop();
            aura = null;
        }
    }

    public void hideHolograms() {
        npc.hideHologramNames();
    }

    public void tickViewers() {
        npc.tickViewers();
    }

    public void swing() {
        npc.swing();
    }

    public void raiseHands() {
        npc.raiseHands();
    }

    /** NPC бьёт игрока рукой: замах + полсердца урона + лёгкий отброс. */
    public void punch(Player target) {
        npc.swing();
        if (target == null) return;
        try {
            target.damage(1.0D);
            org.bukkit.util.Vector direction = target.getLocation().toVector()
                    .subtract(location.toVector()).setY(0);
            if (direction.lengthSquared() > 0.0001D) {
                direction.normalize().multiply(0.45D).setY(0.18D);
                target.setVelocity(direction);
            }
        } catch (Throwable ignored) {}
    }

    public void setPos(Location newLoc) {
        npc.setPos(newLoc);
        // Держим собственную копию позиции в актуальном состоянии —
        // от неё зависят плейсхолдеры, дистанции и отброс при ударе.
        location.setX(newLoc.getX());
        location.setY(newLoc.getY());
        location.setZ(newLoc.getZ());
        location.setYaw(newLoc.getYaw());
        location.setPitch(newLoc.getPitch());
    }

    public void applySkin(SkinProfile profile) {
        if (profile == null || !alive || !profile.isValid()) return;
        npc.applySkin(profile.value(), profile.signature());
        // respawn у VirtualPlayer может сменить entityId — перерегистрируем
        plugin.traders().reRegisterByEntity(this);
    }

    public void hideFromTabForAll() {
        npc.hideFromTabForAll();
    }

    public void hideFromTabFor(Player target) {
        npc.hideFromTabFor(target);
    }

    public int entityId() { return npc.entityId(); }

    public VirtualPlayer player() {
        return npc.player();
    }

    // ─────────────────────────────────────────────────────────────────
    // Голограмма: строки с плейсхолдерами
    // ─────────────────────────────────────────────────────────────────

    private List<String> hologramLines() {
        List<String> raw = config.hologramLines();
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(Text.apply(line, null, placeholders()));
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────
    // Плейсхолдеры
    // ─────────────────────────────────────────────────────────────────

    public Map<String, String> placeholders() {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("trader", ColorUtil.translate(config.displayName()));
        placeholders.put("trader_colored", ColorUtil.translate(config.displayName()));
        placeholders.put("color", accentColor(config.displayName()));
        placeholders.put("left", Numbers.duration(remainingSeconds()));
        placeholders.put("seconds", String.valueOf(remainingSeconds()));
        placeholders.put("lifetime", Numbers.duration(config.lifetimeSeconds()));
        placeholders.put("world", location.getWorld() == null ? "" : Worlds.display(location.getWorld().getName()));
        placeholders.put("x", String.valueOf(location.getBlockX()));
        placeholders.put("y", String.valueOf(location.getBlockY()));
        placeholders.put("z", String.valueOf(location.getBlockZ()));
        placeholders.put("count", String.valueOf(plugin.traders().count(config.id())));
        placeholders.put("max", String.valueOf(config.maxSimultaneous()));
        placeholders.put("uid", String.valueOf(uid));
        if (region != null) {
            placeholders.put("region", region.id());
        } else {
            placeholders.put("region", "none");
        }
        return placeholders;
    }

    private String accentColor(String text) {
        if (text == null) return "&#F8BEFB";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("&#([0-9A-Fa-f]{6})").matcher(text);
        return matcher.find() ? "&#" + matcher.group(1) : "&#F8BEFB";
    }

    // ─────────────────────────────────────────────────────────────────
    // Сток и состояние
    // ─────────────────────────────────────────────────────────────────

    public boolean alive() { return alive; }
    public boolean isDespawning() { return despawning; }
    public int uid() { return uid; }
    public long createdAt() { return createdAt; }
    public TraderConfig config() { return config; }
    public Location location() { return location; }
    public TraderRegion region() { return region; }
    public void setRegion(TraderRegion region) { this.region = region; }
    public SchematicSnapshot schematicSnapshot() { return schematicSnapshot; }
    public void setSchematicSnapshot(SchematicSnapshot snapshot) { this.schematicSnapshot = snapshot; }
    public List<TradeConfig> offeredTrades() { return offeredTrades; }

    public long remainingSeconds() {
        if (expiresAt <= 0L) return -1L;
        return Math.max(0L, (expiresAt - System.currentTimeMillis()) / 1000L);
    }

    public boolean hasStock(String tradeId, int amount) {
        Integer value = stock.get(tradeId);
        return value == null || value >= amount;
    }

    public void takeStock(String tradeId, int amount) {
        Integer value = stock.get(tradeId);
        if (value != null) stock.put(tradeId, Math.max(0, value - amount));
    }

    public void setStock(String tradeId, int value) {
        if (value < 0) stock.remove(tradeId);
        else stock.put(tradeId, value);
    }

    public int stock(String tradeId) {
        Integer value = stock.get(tradeId);
        return value == null ? -1 : value;
    }

    public String stockText(String tradeId) {
        int value = stock(tradeId);
        return value < 0 ? "∞" : String.valueOf(value);
    }

    public boolean inRange(Player player) {
        double radius = plugin.config().interactionRadius();
        return player != null && player.getWorld() == location.getWorld()
                && player.getLocation().distanceSquared(location) <= radius * radius;
    }

    public double distance(Player player) {
        if (player == null || player.getWorld() != location.getWorld()) return Double.MAX_VALUE;
        return Math.sqrt(player.getLocation().distanceSquared(location));
    }
}
