package com.bradenkennedy.punishment.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PunishmentHistoryInteractionTest extends CommandTestBase {

    private PunishmentCommands commands;
    private PlayerMock staff;
    private PlayerMock target;

    @BeforeEach
    void registerCommands() {
        commands = new PunishmentCommands(audiences, repository, config);
        commands.register(manager);
        staff = server.addPlayer("Staff");
        target = server.addPlayer("Target");
    }

    private Punishment store(PunishmentType type, Instant expiry, String reason, Instant issuedAt) {
        var punishment = new Punishment(
                UUID.randomUUID(),
                target.getUniqueId(),
                type,
                new PunishmentIssuer(staff.getUniqueId(), issuedAt),
                reason,
                expiry,
                null);
        repository.create(punishment);
        return punishment;
    }

    private Punishment store(PunishmentType type) {
        return store(type, null, "reason", Instant.now());
    }

    private List<Component> messages(int page, Optional<PunishmentType> type) {
        return commands.historyMessages(target, page, type);
    }

    private Component onlyEntry() {
        List<Component> messages = messages(1, Optional.empty());
        assertEquals(3, messages.size());
        return messages.get(1);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static Stream<Component> flatten(Component component) {
        return Stream.concat(
                component.children().stream().flatMap(PunishmentHistoryInteractionTest::flatten), Stream.of(component));
    }

    private static List<String> clickValues(Component component) {
        return flatten(component)
                .map(c -> c.clickEvent())
                .filter(e -> e != null)
                .map(PunishmentHistoryInteractionTest::value)
                .toList();
    }

    private static String value(ClickEvent<?> event) {
        return ((ClickEvent.Payload.Text) event.payload()).value();
    }

    private String hoverText(Component entry) {
        return plain((Component) entry.hoverEvent().value());
    }

    private List<String> footerClicks(int page, Optional<PunishmentType> type) {
        List<Component> messages = messages(page, type);
        return clickValues(messages.getLast());
    }

    @Test
    void activeBanSuggestsUnban() {
        store(PunishmentType.BAN);

        ClickEvent<?> click = onlyEntry().clickEvent();

        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, click.action());
        assertEquals("/unban Target ", value(click));
    }

    @Test
    void activeMuteSuggestsUnmute() {
        store(PunishmentType.MUTE);

        assertEquals("/unmute Target ", value(onlyEntry().clickEvent()));
    }

    @Test
    void warningSuggestsUnwarnWithItsId() {
        Punishment warning = store(PunishmentType.WARN);

        assertEquals("/unwarn Target " + warning.id() + " ", value(onlyEntry().clickEvent()));
    }

    @Test
    void kickHasNoClickAction() {
        store(PunishmentType.KICK);

        assertNull(onlyEntry().clickEvent());
    }

    @Test
    void revokedEntryHasNoClickAction() {
        Punishment ban = store(PunishmentType.BAN);
        repository.revoke(ban.id(), staff.getUniqueId(), "appeal", Instant.now());

        assertNull(onlyEntry().clickEvent());
    }

    @Test
    void expiredEntryHasNoClickAction() {
        store(
                PunishmentType.MUTE,
                Instant.now().minus(Duration.ofHours(1)),
                "reason",
                Instant.now().minusSeconds(7200));

        assertNull(onlyEntry().clickEvent());
    }

    @Test
    void hoverShowsFullDetails() {
        Punishment ban = store(PunishmentType.BAN, Instant.now().plus(Duration.ofHours(2)), "griefing", Instant.now());

        String hover = hoverText(onlyEntry());

        assertTrue(hover.contains("BAN"));
        assertTrue(hover.contains("Staff"));
        assertTrue(hover.contains("griefing"));
        assertTrue(hover.contains("2h"));
        assertTrue(hover.contains(ban.id().toString()));
        assertFalse(hover.contains("Revoked by"));
    }

    @Test
    void hoverOfRevokedEntryShowsRevocation() {
        Punishment ban = store(PunishmentType.BAN);
        repository.revoke(ban.id(), staff.getUniqueId(), "appeal accepted", Instant.now());

        String hover = hoverText(onlyEntry());

        assertTrue(hover.contains("Revoked by: Staff"));
        assertTrue(hover.contains("Revoke reason: appeal accepted"));
        assertTrue(hover.contains("Revoked at:"));
    }

    @Test
    void reasonMarkupIsRenderedAsLiteralTextWithoutEvents() {
        store(PunishmentType.KICK, null, "<click:run_command:/op Target>click me</click>", Instant.now());

        Component entry = onlyEntry();

        assertTrue(plain(entry).contains("<click:run_command:/op Target>"));
        assertEquals(List.of(), clickValues(entry));
        assertFalse(hoverText(entry).isEmpty());
    }

    @Test
    void firstPageOfManyHasOnlyNextButton() {
        for (int i = 0; i < 25; i++) store(PunishmentType.KICK);

        assertEquals(List.of("/punish history Target 2"), footerClicks(1, Optional.empty()));
    }

    @Test
    void middlePageHasBothButtons() {
        for (int i = 0; i < 25; i++) store(PunishmentType.KICK);

        assertEquals(
                List.of("/punish history Target 1", "/punish history Target 3"), footerClicks(2, Optional.empty()));
    }

    @Test
    void lastPageHasOnlyPreviousButton() {
        for (int i = 0; i < 25; i++) store(PunishmentType.KICK);

        assertEquals(List.of("/punish history Target 2"), footerClicks(3, Optional.empty()));
    }

    @Test
    void singlePageHasNoButtons() {
        store(PunishmentType.KICK);

        assertEquals(List.of(), footerClicks(1, Optional.empty()));
    }

    @Test
    void typeFilterKeepsOnlyMatchingEntriesAndCountsThem() {
        for (int i = 0; i < 12; i++) store(PunishmentType.KICK);
        store(PunishmentType.WARN);

        List<Component> messages = messages(1, Optional.of(PunishmentType.KICK));

        assertEquals(10 + 2, messages.size());
        assertTrue(plain(messages.getFirst()).contains("page 1/2"));
        assertTrue(messages.subList(1, 11).stream().allMatch(m -> plain(m).startsWith("KICK")));
    }

    @Test
    void typeFilterIsCarriedIntoPageButtons() {
        for (int i = 0; i < 12; i++) store(PunishmentType.KICK);

        assertEquals(
                List.of("/punish history Target 2 --type kick"), footerClicks(1, Optional.of(PunishmentType.KICK)));
    }

    @Test
    void typeFilterWithNoMatchesShowsEmptyMessage() {
        store(PunishmentType.WARN);

        List<Component> messages = messages(1, Optional.of(PunishmentType.BAN));

        assertEquals(1, messages.size());
        assertTrue(plain(messages.getFirst()).contains("No punishments found"));
    }

    @Test
    void plainTextHasNoCommandsSoConsoleOutputStaysReadable() {
        for (int i = 0; i < 12; i++)
            store(PunishmentType.BAN, null, "r", Instant.now().plusSeconds(i));

        String text = messages(1, Optional.empty()).stream()
                .map(PunishmentHistoryInteractionTest::plain)
                .reduce("", String::concat);

        assertFalse(text.contains("/unban"));
        assertFalse(text.contains("/punish history"));
    }

    @Test
    void typeFlagAcceptsLongAndShortFormsCaseInsensitively() {
        store(PunishmentType.BAN);

        assertDoesNotThrow(() -> run(staff, "punish history Target --type ban"));
        assertDoesNotThrow(() -> run(staff, "punish history Target 1 -t BAN"));
        assertDoesNotThrow(() -> run(staff, "punish history Target --type ban"));
        assertDoesNotThrow(() -> run(staff, "punish history Target -t ban"));
        assertDoesNotThrow(() -> run(staff, "punish history Target 1 --type ban"));
    }

    @Test
    void unknownTypeIsRejected() {
        assertThrows(CompletionException.class, () -> run(staff, "punish history Target --type nonsense"));
    }
}
