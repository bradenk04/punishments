package com.bradenkennedy.punishment.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bradenkennedy.punishment.api.model.PunishmentType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.component.CommandComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CommandRegistrationTest {

    private final TestCommandManager manager = new TestCommandManager();

    private Map<String, String> permissionsByPath() {
        return manager.commands().stream()
                .collect(Collectors.toMap(
                        command -> command.components().stream()
                                .filter(component -> component.type() == CommandComponent.ComponentType.LITERAL)
                                .map(CommandComponent::name)
                                .collect(Collectors.joining(" ")),
                        command -> command.commandPermission().permissionString()));
    }

    private Command<CommandSender> command(String root) {
        return manager.commands().stream()
                .filter(command -> command.rootComponent().name().equals(root))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void kickRegistersWithPermission() {
        KickCommands.register(manager);

        assertEquals(Map.of("kick", "punishments.kick"), permissionsByPath());
    }

    @Test
    void historyRegistersWithPermission() {
        PunishmentCommands.register(manager);

        assertEquals(Map.of("punish history", "punishments.history"), permissionsByPath());
    }

    @Test
    void warnRegistersWarnAndUnwarn() {
        WarnCommands.register(manager);

        assertEquals(Map.of("warn", "punishments.warn", "unwarn", "punishments.unwarn"), permissionsByPath());
    }

    @ParameterizedTest
    @EnumSource(
            value = PunishmentType.class,
            names = {"BAN", "MUTE"})
    void timedPunishmentRegistersIssueTempAndRevoke(PunishmentType type) {
        String name = type.name().toLowerCase();

        TimedPunishmentCommands.register(manager, type, punishment -> {});

        assertEquals(
                Map.of(
                        name,
                        "punishments." + name,
                        "temp" + name,
                        "punishments.temp" + name,
                        "un" + name,
                        "punishments.un" + name),
                permissionsByPath());
    }

    @Test
    void everyIssuingCommandHasSilentFlagWithAlias() {
        KickCommands.register(manager);
        WarnCommands.register(manager);
        TimedPunishmentCommands.register(manager, PunishmentType.BAN, punishment -> {});

        List<String> roots = List.of("kick", "warn", "unwarn", "ban", "tempban", "unban");
        for (String root : roots) {
            var flags = command(root).flagParser().flags();
            assertTrue(
                    flags.stream()
                            .anyMatch(flag -> flag.name().equals("silent")
                                    && flag.aliases().contains("s")),
                    root + " is missing the silent flag");
        }
    }
}
