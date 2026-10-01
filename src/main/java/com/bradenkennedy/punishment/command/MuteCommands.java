package com.bradenkennedy.punishment.command;

import com.bradenkennedy.punishment.PunishmentPlugin;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentIssuer;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.command.parser.DurationParser;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.bradenkennedy.punishment.command.parser.DurationParser.durationParser;
import static org.incendo.cloud.bukkit.parser.OfflinePlayerParser.offlinePlayerParser;
import static org.incendo.cloud.parser.standard.StringParser.greedyFlagYieldingStringParser;

public final class MuteCommands {

    private static final UUID CONSOLE_ID = new UUID(0, 0);
    private static final String NOTIFY_PERMISSION = "punishments.notify";

    private MuteCommands() {
    }

    public static void register(CommandManager<CommandSender> manager) {
        var silent = manager.flagBuilder("silent").withAliases("s");
        manager.command(manager.commandBuilder("mute")
                .required("player", offlinePlayerParser())
                .optional("reason", greedyFlagYieldingStringParser())
                .flag(silent)
                .permission("punishments.mute")
                .handler(ctx -> mute(ctx, null)));
        manager.command(manager.commandBuilder("tempmute")
                .required("player", offlinePlayerParser())
                .required("duration", durationParser())
                .optional("reason", greedyFlagYieldingStringParser())
                .flag(silent)
                .permission("punishments.tempmute")
                .handler(ctx -> mute(ctx, ctx.get("duration"))));
        manager.command(manager.commandBuilder("unmute")
                .required("player", offlinePlayerParser())
                .flag(silent)
                .permission("punishments.unmute")
                .handler(MuteCommands::unmute));
    }

    private static void mute(CommandContext<CommandSender> ctx, @Nullable Duration duration) {
        OfflinePlayer target = ctx.get("player");
        var repository = PunishmentPlugin.getDataRepository();
        if (repository.findActive(target.getUniqueId(), PunishmentType.MUTE).isPresent()) {
            reply(ctx, "<red><name> is already muted", target);
            return;
        }
        Instant now = Instant.now();
        String reason = ctx.<String>optional("reason").orElse(null);
        repository.create(new Punishment(UUID.randomUUID(), target.getUniqueId(), PunishmentType.MUTE,
                new PunishmentIssuer(issuerId(ctx.sender()), now), reason,
                duration == null ? null : now.plus(duration), false));
        announce(ctx, "<red><name> was muted by <staff> for <duration>: <reason>", target,
                Placeholder.unparsed("duration", duration == null ? "ever" : DurationParser.format(duration)),
                Placeholder.unparsed("reason", Objects.requireNonNullElse(reason, "No reason")));
    }

    private static void unmute(CommandContext<CommandSender> ctx) {
        OfflinePlayer target = ctx.get("player");
        var repository = PunishmentPlugin.getDataRepository();
        repository.findActive(target.getUniqueId(), PunishmentType.MUTE).ifPresentOrElse(
                mute -> {
                    repository.revoke(mute.id(), issuerId(ctx.sender()), null, Instant.now());
                    announce(ctx, "<green><name> was unmuted by <staff>", target);
                },
                () -> reply(ctx, "<red><name> is not muted", target));
    }

    private static void reply(CommandContext<CommandSender> ctx, String message, OfflinePlayer target) {
        PunishmentPlugin.getAdventure().sender(ctx.sender())
                .sendMessage(PunishmentPlugin.getMiniMessage().deserialize(message, Placeholder.unparsed("name", nameOf(target))));
    }

    private static void announce(CommandContext<CommandSender> ctx, String message, OfflinePlayer target, TagResolver... extra) {
        boolean silent = ctx.flags().isPresent("silent");
        var resolver = TagResolver.builder()
                .resolver(Placeholder.unparsed("name", nameOf(target)))
                .resolver(Placeholder.unparsed("staff", ctx.sender().getName()))
                .resolvers(extra)
                .build();
        PunishmentPlugin.getAdventure()
                .filter(s -> !silent || s == ctx.sender() || s.hasPermission(NOTIFY_PERMISSION))
                .sendMessage(PunishmentPlugin.getMiniMessage().deserialize((silent ? "<gray>[S]</gray> " : "") + message, resolver));
    }

    private static String nameOf(OfflinePlayer player) {
        return Objects.requireNonNullElse(player.getName(), player.getUniqueId().toString());
    }

    private static UUID issuerId(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE_ID;
    }
}
