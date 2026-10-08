package com.bradenkennedy.punishment;

import com.bradenkennedy.punishment.command.parser.DurationParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class PluginConfig {

    private static final String DEFAULT_LANGUAGE_PATH = "lang/en_US.yml";

    private final JavaPlugin plugin;
    private final YamlConfiguration messages;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.messages = load("lang/" + plugin.getConfig().getString("language") + ".yml");
        this.messages.setDefaults(YamlConfiguration.loadConfiguration(
                new InputStreamReader(plugin.getResource(DEFAULT_LANGUAGE_PATH), StandardCharsets.UTF_8)));
    }

    private YamlConfiguration load(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists() && plugin.getResource(path) != null) {
            plugin.saveResource(path, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public List<String> blockedMuteCommands() {
        return plugin.getConfig().getStringList("mute.blocked-commands");
    }

    public String raw(String key) {
        return messages.getString(key, key);
    }

    public Component message(String key, TagResolver... resolvers) {
        return PunishmentPlugin.getMiniMessage().deserialize(raw(key), resolvers);
    }

    public TagResolver reason(@Nullable String reason) {
        return Placeholder.unparsed("reason", reason == null ? raw("no-reason") : reason);
    }

    public TagResolver remaining(@Nullable Instant expiry) {
        return Placeholder.unparsed("remaining", expiry == null ? raw("permanent-duration")
                : DurationParser.format(Duration.between(Instant.now(), expiry)));
    }
}
