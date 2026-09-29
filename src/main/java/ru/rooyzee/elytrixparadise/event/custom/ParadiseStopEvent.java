package ru.rooyzee.elytrixparadise.event.custom;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import ru.rooyzee.elytrixparadise.event.ParadiseEvent;

public class ParadiseStopEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final ParadiseEvent event;

    public ParadiseStopEvent(ParadiseEvent event) {
        this.event = event;
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
