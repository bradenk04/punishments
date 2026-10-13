package com.bradenkennedy.punishment.metrics;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginMetrics {

    private static final int SERVICE_ID = 34608;

    private PluginMetrics() {}

    public static void start(JavaPlugin plugin, PunishmentCounter counter, String storageBackend, String proxyMode) {
        Metrics metrics = new Metrics(plugin, SERVICE_ID);
        metrics.addCustomChart(new SimplePie("storage_backend", () -> storageBackend));
        metrics.addCustomChart(new SimplePie("proxy_mode", () -> proxyMode));
        metrics.addCustomChart(new SingleLineChart("punishments_issued", counter::drain));
    }
}
