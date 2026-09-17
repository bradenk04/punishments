package com.bradenkennedy.punishment.api.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.bradenkennedy.punishment.api.model.Punishment;

public class PlayerPunishedEvent extends Event implements Cancellable {
    private boolean punishmentCancelled = false;
    private static final HandlerList handlers = new HandlerList();
    private Punishment punishment;

    public PlayerPunishedEvent(Punishment punishment) {
        this.punishment = punishment;
    }

    public Punishment getPunishment() {
        return this.punishment;
    }

    public void setCancelled(boolean cancelled) {
        this.punishmentCancelled = cancelled;
    }

    public boolean isCancelled() {
        return this.punishmentCancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
