package com.lotusplugins.warrant;

import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class Scheduling {

    private final Plugin plugin;
    private final boolean folia = foliaPresent();

    public Scheduling(Plugin plugin) {
        this.plugin = plugin;
    }

    public void global(Runnable task) {
        if (!folia) {
            plugin.getServer().getScheduler().runTask(plugin, task);
            return;
        }
        invoke(
                plugin.getServer(),
                "getGlobalRegionScheduler",
                "execute",
                new Class<?>[] {Plugin.class, Runnable.class},
                plugin,
                task);
    }

    public void onPlayer(Player player, Runnable task) {
        if (!folia) {
            plugin.getServer().getScheduler().runTask(plugin, task);
            return;
        }
        Consumer<Object> scheduled = ignored -> task.run();
        invoke(
                player,
                "getScheduler",
                "run",
                new Class<?>[] {Plugin.class, Consumer.class, Runnable.class},
                plugin,
                scheduled,
                null);
    }

    private static void invoke(Object owner, String getter, String method, Class<?>[] parameters, Object... arguments) {
        try {
            var getterMethod = owner.getClass().getMethod(getter);
            var scheduler = getterMethod.invoke(owner);
            getterMethod.getReturnType().getMethod(method, parameters).invoke(scheduler, arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Folia scheduler call failed: " + getter + "." + method, e);
        }
    }

    private static boolean foliaPresent() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
