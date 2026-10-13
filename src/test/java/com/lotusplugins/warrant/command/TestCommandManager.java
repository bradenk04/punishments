package com.bradenkennedy.punishment.command;

import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.incendo.cloud.meta.SimpleCommandMeta;

final class TestCommandManager extends CommandManager<CommandSender> {

    TestCommandManager() {
        super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());
    }

    @Override
    public boolean hasPermission(CommandSender sender, String permission) {
        return true;
    }

    @Override
    public CommandMeta createDefaultCommandMeta() {
        return SimpleCommandMeta.empty();
    }
}
