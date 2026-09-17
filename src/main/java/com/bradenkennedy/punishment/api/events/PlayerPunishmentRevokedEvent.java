package com.bradenkennedy.punishment.api.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.bradenkennedy.punishment.api.model.Punishment;

public class PlayerPunishmentRevokedEvent extends Event implements Cancellable {
    private boolean cancelled = false;
    private Punishment punishment;
    public static final HandlerList handlers = new HandlerList();

    public PlayerPunishmentRevokedEvent(Punishment punishment) {
        this.punishment = punishment;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }
}
