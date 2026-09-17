package com.bradenkennedy.punishment;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

public class PunishmentPlugin extends JavaPlugin {

    private static PunishmentPlugin instance;

    private BukkitAudiences adventure;
    private MiniMessage miniMessage;

    public static PunishmentPlugin getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Punishments is not initialized yet!");
        }
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        this.adventure = BukkitAudiences.create(this);
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public void onDisable() {
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
        this.miniMessage = null;
        instance = null;
    }

    public BukkitAudiences adventure() {
        if (this.adventure == null) {
            throw new IllegalStateException("Tried to access BukkitAudiences when Punishments was disabled!");
        }
        return this.adventure;
    }

    public MiniMessage miniMessage() {
        if (this.miniMessage == null) {
            this.miniMessage = MiniMessage.miniMessage();
        }
        return this.miniMessage;
    }

    public static BukkitAudiences getAdventure() {
        return getInstance().adventure();
    }

    public static MiniMessage getMiniMessage() {
        return getInstance().miniMessage();
    }
}
