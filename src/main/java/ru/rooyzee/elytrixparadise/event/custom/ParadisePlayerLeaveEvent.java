package ru.rooyzee.elytrixparadise.event.custom;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import ru.rooyzee.elytrixparadise.event.ParadiseEvent;

public class ParadisePlayerLeaveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final ParadiseEvent event;

    public ParadisePlayerLeaveEvent(Player player, ParadiseEvent event) {
        this.player = player;
        this.event = event;
    }

    public Player getPlayer() {
        return player;
    }

    public ParadiseEvent getParadiseEvent() {
        return event;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
