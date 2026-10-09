package com.bradenkennedy.punishment.command;

import com.bradenkennedy.punishment.ServerTestBase;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;

abstract class CommandTestBase extends ServerTestBase {

    protected final TestCommandManager manager = new TestCommandManager();
    protected CommandSupport support;

    @BeforeEach
    void setUpSupport() {
        support = new CommandSupport(repository, cache, config, audiences);
    }

    protected void run(CommandSender sender, String input) {
        manager.commandExecutor().executeCommand(sender, input).join();
    }
}
