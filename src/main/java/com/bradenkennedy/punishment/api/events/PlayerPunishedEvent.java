package com.bradenkennedy.punishment.api.events;

import com.bradenkennedy.punishment.api.model.Punishment;
import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerPunishedEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Punishment punishment;
    private boolean cancelled;

    public PlayerPunishedEvent(Punishment punishment) {
        super(!Bukkit.isPrimaryThread());
        this.punishment = punishment;
    }

    public Punishment getPunishment() {
        return punishment;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
