package ru.rooyzee.elytrixparadise.event.custom;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import ru.rooyzee.elytrixparadise.event.ParadiseEvent;
import ru.rooyzee.elytrixparadise.event.ParadisePhase;

public class ParadisePhaseChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final ParadiseEvent event;
    private final ParadisePhase oldPhase;
    private final ParadisePhase newPhase;

    public ParadisePhaseChangeEvent(ParadiseEvent event, ParadisePhase oldPhase, ParadisePhase newPhase) {
        this.event = event;
        this.oldPhase = oldPhase;
        this.newPhase = newPhase;
    }

    public ParadiseEvent getParadiseEvent() {
        return event;
    }

    public ParadisePhase getOldPhase() {
        return oldPhase;
    }

    public ParadisePhase getNewPhase() {
        return newPhase;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
