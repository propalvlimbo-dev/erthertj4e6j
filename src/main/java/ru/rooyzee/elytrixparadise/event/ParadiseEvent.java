package ru.rooyzee.elytrixparadise.event;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.event.custom.ParadisePhaseChangeEvent;
import ru.rooyzee.elytrixparadise.event.custom.ParadisePlayerEnterEvent;
import ru.rooyzee.elytrixparadise.event.custom.ParadisePlayerLeaveEvent;
import ru.rooyzee.elytrixparadise.event.custom.ParadiseStartEvent;
import ru.rooyzee.elytrixparadise.event.custom.ParadiseStopEvent;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ParadiseEvent {

    private final Main plugin;
    private Location center;
    private Location safeSpawn;
    private ParadisePhase currentPhase = ParadisePhase.WAITING;
    private int remainingSeconds;
    private int phaseDurationSeconds;
    private int totalCycles = 0;
    private boolean running = false;

    private final Set<UUID> playersInside = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, Object> eventData = new ConcurrentHashMap<>();

    public ParadiseEvent(Main plugin, Location center, Location safeSpawn) {
        this.plugin = plugin;
        this.center = center;
        this.safeSpawn = safeSpawn;
        this.currentPhase = ParadisePhase.WAITING;
        initPhaseDuration(this.currentPhase);
    }

    private void initPhaseDuration(ParadisePhase phase) {
        String key = "event.phases." + phase.getId() + ".duration-seconds";
        this.phaseDurationSeconds = plugin.getConfigManager().getConfig().getInt(key, phase.getDefaultDurationSeconds());
        this.remainingSeconds = this.phaseDurationSeconds;
    }

    public void start() {
        if (running) return;
        this.running = true;
        initPhaseDuration(currentPhase);
        Bukkit.getPluginManager().callEvent(new ParadiseStartEvent(this));
    }

    public void stop() {
        if (!running) return;
        this.running = false;
        playersInside.clear();
        Bukkit.getPluginManager().callEvent(new ParadiseStopEvent(this));
    }

    public void tick() {
        if (!running) return;

        if (remainingSeconds > 0) {
            remainingSeconds--;
        }

        // Broadcast notifications at specific intervals
        checkBroadcastTimings();

        if (remainingSeconds <= 0) {
            nextPhase();
        }
    }

    private void checkBroadcastTimings() {
        if (remainingSeconds == 60 || remainingSeconds == 30 || remainingSeconds == 10 || remainingSeconds == 5 || remainingSeconds == 3 || remainingSeconds == 2 || remainingSeconds == 1) {
            // Optional periodic reminders if configured
        }
    }

    public void nextPhase() {
        ParadisePhase oldPhase = this.currentPhase;
        ParadisePhase newPhase = oldPhase.next();
        if (newPhase == ParadisePhase.WAITING) {
            totalCycles++;
        }
        setPhase(newPhase);
    }

    public void setPhase(ParadisePhase newPhase) {
        setPhase(newPhase, -1);
    }

    public void setPhase(ParadisePhase newPhase, int customSeconds) {
        ParadisePhase oldPhase = this.currentPhase;
        this.currentPhase = newPhase;

        if (customSeconds > 0) {
            this.phaseDurationSeconds = customSeconds;
            this.remainingSeconds = customSeconds;
        } else {
            initPhaseDuration(newPhase);
        }

        // Update region PvP flag if dynamic PvP is enabled
        plugin.getRegionManager().updatePvpFlag(newPhase);

        // Announce phase change
        broadcastPhaseChange(oldPhase, newPhase);

        // Fire custom Bukkit event
        Bukkit.getPluginManager().callEvent(new ParadisePhaseChangeEvent(this, oldPhase, newPhase));
    }

    private void broadcastPhaseChange(ParadisePhase oldPhase, ParadisePhase newPhase) {
        String msgKey = "broadcast.phase-" + newPhase.getId();
        List<String> messages = plugin.getConfigManager().getMessageList(msgKey);
        if (messages != null && !messages.isEmpty()) {
            for (String s : messages) {
                String formatted = s
                        .replace("{phase}", getPhaseDisplayName())
                        .replace("{old_phase}", getPhaseDisplayName(oldPhase))
                        .replace("{time}", getRemainingFormatted())
                        .replace("{duration}", ColorUtil.formatTimeRussian(phaseDurationSeconds))
                        .replace("{x}", center != null ? String.valueOf(center.getBlockX()) : "0")
                        .replace("{y}", center != null ? String.valueOf(center.getBlockY()) : "0")
                        .replace("{z}", center != null ? String.valueOf(center.getBlockZ()) : "0");
                Bukkit.broadcastMessage(formatted);
            }
        }
    }

    public boolean addPlayer(Player player) {
        if (player == null) return false;
        boolean added = playersInside.add(player.getUniqueId());
        if (added) {
            Bukkit.getPluginManager().callEvent(new ParadisePlayerEnterEvent(player, this));
            String enterMsg = plugin.getConfigManager().getMessage("event.enter");
            if (enterMsg != null && !enterMsg.isEmpty()) {
                player.sendMessage(enterMsg
                        .replace("{phase}", getPhaseDisplayName())
                        .replace("{time}", getRemainingFormatted()));
            }
        }
        return added;
    }

    public boolean removePlayer(Player player) {
        if (player == null) return false;
        boolean removed = playersInside.remove(player.getUniqueId());
        if (removed) {
            Bukkit.getPluginManager().callEvent(new ParadisePlayerLeaveEvent(player, this));
            String leaveMsg = plugin.getConfigManager().getMessage("event.leave");
            if (leaveMsg != null && !leaveMsg.isEmpty()) {
                player.sendMessage(leaveMsg);
            }
        }
        return removed;
    }

    public boolean isPlayerInside(Player player) {
        return player != null && playersInside.contains(player.getUniqueId());
    }

    public List<Player> getOnlinePlayersInside() {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : playersInside) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                list.add(p);
            }
        }
        return list;
    }

    public int getPlayerCount() {
        return getOnlinePlayersInside().size();
    }

    public String getPhaseDisplayName() {
        return getPhaseDisplayName(this.currentPhase);
    }

    public String getPhaseDisplayName(ParadisePhase phase) {
        if (phase == null) return "";
        String configName = plugin.getConfigManager().getConfig()
                .getString("event.phases." + phase.getId() + ".display-name");
        if (configName != null) {
            return ColorUtil.colorize(configName);
        }
        return ColorUtil.colorize(phase.getDefaultDisplayName());
    }

    public double getProgress() {
        if (phaseDurationSeconds <= 0) return 1.0;
        double progress = (double) remainingSeconds / (double) phaseDurationSeconds;
        return Math.max(0.0, Math.min(1.0, progress));
    }

    public String getRemainingFormatted() {
        return ColorUtil.formatTimeShort(remainingSeconds);
    }

    public String getRemainingRussian() {
        return ColorUtil.formatTimeRussian(remainingSeconds);
    }

    // Getters and Setters
    public Location getCenter() {
        return center;
    }

    public void setCenter(Location center) {
        this.center = center;
    }

    public Location getSafeSpawn() {
        return safeSpawn != null ? safeSpawn : center.clone().add(0, 2, 0);
    }

    public void setSafeSpawn(Location safeSpawn) {
        this.safeSpawn = safeSpawn;
    }

    public ParadisePhase getCurrentPhase() {
        return currentPhase;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public int getPhaseDurationSeconds() {
        return phaseDurationSeconds;
    }

    public int getTotalCycles() {
        return totalCycles;
    }

    public boolean isRunning() {
        return running;
    }

    public Map<String, Object> getEventData() {
        return eventData;
    }

    public Object getData(String key) {
        return eventData.get(key);
    }

    public void setData(String key, Object value) {
        if (value == null) eventData.remove(key);
        else eventData.put(key, value);
    }
}
