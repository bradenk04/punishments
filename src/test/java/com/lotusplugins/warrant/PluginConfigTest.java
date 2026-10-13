package com.bradenkennedy.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class PluginConfigTest {

    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PluginConfig newConfig() {
        return new PluginConfig(plugin, MiniMessage.miniMessage());
    }

    private static String render(String template, TagResolver resolver) {
        return PlainTextComponentSerializer.plainText()
                .serialize(MiniMessage.miniMessage().deserialize(template, resolver));
    }

    @Test
    void savesDefaultFilesToDataFolder() {
        newConfig();

        assertTrue(new File(plugin.getDataFolder(), "config.yml").exists());
        assertTrue(new File(plugin.getDataFolder(), "lang/en_US.yml").exists());
    }

    @Test
    void blockedMuteCommandsComeFromConfig() {
        assertTrue(newConfig().blockedMuteCommands().containsAll(List.of("msg", "tell", "me")));
    }

    @Test
    void historyPageSizeDefaultsToTen() {
        assertEquals(10, newConfig().historyPageSize());
    }

    @Test
    void historyPageSizeIsAtLeastOne() {
        plugin.getConfig().set("history.page-size", 0);

        assertEquals(1, newConfig().historyPageSize());
    }

    @Test
    void rawReturnsMessageForKnownKey() {
        assertEquals("No reason", newConfig().raw("no-reason"));
    }

    @Test
    void rawFallsBackToKeyForUnknownKey() {
        assertEquals("missing.key", newConfig().raw("missing.key"));
    }

    @Test
    void reasonFallsBackToDefaultWhenAbsent() {
        PluginConfig config = newConfig();

        assertEquals("No reason", render("<reason>", config.reason(null)));
    }

    @Test
    void reasonIsInsertedUnparsed() {
        assertEquals("<red>griefing", render("<reason>", newConfig().reason("<red>griefing")));
    }

    @Test
    void remainingShowsPermanentTextWhenNoExpiry() {
        PluginConfig config = newConfig();

        assertEquals(config.raw("permanent-duration"), render("<remaining>", config.remaining(null)));
    }

    @Test
    void remainingShowsFormattedDurationForFutureExpiry() {
        PluginConfig config = newConfig();

        String rendered = render("<remaining>", config.remaining(Instant.now().plus(Duration.ofHours(1))));

        assertFalse(rendered.isBlank());
        assertNotEquals(config.raw("permanent-duration"), rendered);
    }
}
