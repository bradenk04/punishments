package com.bradenkennedy.punishment.command;

import com.bradenkennedy.punishment.ServerTestBase;
import com.bradenkennedy.punishment.exemption.ExemptionCheck;
import com.bradenkennedy.punishment.metrics.PunishmentCounter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;

abstract class CommandTestBase extends ServerTestBase {

    protected final TestCommandManager manager = new TestCommandManager();
    protected final Set<UUID> offlineExempt = new HashSet<>();
    protected final PunishmentCounter counter = new PunishmentCounter();
    protected CommandSupport support;

    @BeforeEach
    void setUpSupport() {
        support = new CommandSupport(
                repository,
                cache,
                config,
                audiences,
                new ExemptionCheck(
                        (id, permission) -> CompletableFuture.completedFuture(offlineExempt.contains(id)),
                        Runnable::run),
                counter);
    }

    protected void run(CommandSender sender, String input) {
        manager.commandExecutor().executeCommand(sender, input).join();
    }
}
