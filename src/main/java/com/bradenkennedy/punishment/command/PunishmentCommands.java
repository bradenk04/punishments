package com.bradenkennedy.punishment.command;

import static com.bradenkennedy.punishment.command.CommandSupport.nameOf;
import static com.bradenkennedy.punishment.command.parser.PageParser.pageParser;
import static org.incendo.cloud.bukkit.parser.OfflinePlayerParser.offlinePlayerParser;
import static org.incendo.cloud.parser.standard.EnumParser.enumParser;

import com.bradenkennedy.punishment.PluginConfig;
import com.bradenkennedy.punishment.PunishmentDetails;
import com.bradenkennedy.punishment.api.model.HistoryPage;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import com.bradenkennedy.punishment.api.model.Revocation;
import com.bradenkennedy.punishment.command.parser.DurationParser;
import com.bradenkennedy.punishment.storage.PunishmentRepository;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

public final class PunishmentCommands {

    private final BukkitAudiences audiences;
    private final PunishmentRepository repository;
    private final PluginConfig config;

    public PunishmentCommands(BukkitAudiences audiences, PunishmentRepository repository, PluginConfig config) {
        this.audiences = audiences;
        this.repository = repository;
        this.config = config;
    }

    public void register(CommandManager<CommandSender> manager) {
        manager.command(manager.commandBuilder("punish")
                .literal("history")
                .required("player", offlinePlayerParser())
                .optional("page", pageParser())
                .flag(manager.flagBuilder("type").withAliases("t").withComponent(enumParser(PunishmentType.class)))
                .permission("punishments.history")
                .handler(this::history));
    }

    static int pageCount(int total, int pageSize) {
        return (total + pageSize - 1) / pageSize;
    }

    static int offset(int page, int pageSize) {
        return (int) Math.min((long) (page - 1) * pageSize, Integer.MAX_VALUE);
    }

    private void history(CommandContext<CommandSender> ctx) {
        Audience audience = audiences.sender(ctx.sender());
        historyMessages(
                        ctx.get("player"),
                        ctx.<Integer>optional("page").orElse(1),
                        ctx.flags().<PunishmentType>getValue("type"))
                .forEach(audience::sendMessage);
    }

    List<Component> historyMessages(OfflinePlayer target, int requestedPage, Optional<PunishmentType> type) {
        int pageSize = config.historyPageSize();
        HistoryPage result = findPage(target, requestedPage, pageSize, type);
        if (result.total() == 0) {
            return List.of(config.message("history.empty", Placeholder.unparsed("name", nameOf(target))));
        }
        int pages = pageCount(result.total(), pageSize);
        int page = Math.min(requestedPage, pages);
        if (page != requestedPage) {
            result = findPage(target, page, pageSize, type);
        }

        String name = nameOf(target);
        TagResolver pageInfo = TagResolver.resolver(
                Placeholder.unparsed("name", name),
                Placeholder.unparsed("page", String.valueOf(page)),
                Placeholder.unparsed("pages", String.valueOf(pages)));
        TagResolver footerInfo = TagResolver.resolver(
                pageInfo,
                Placeholder.component(
                        "previous",
                        page > 1 ? button("history.previous", name, page - 1, type) : disabled("history.previous")),
                Placeholder.component(
                        "next",
                        page < pages ? button("history.next", name, page + 1, type) : disabled("history.next")));
        List<Component> messages = new ArrayList<>();
        messages.add(config.message("history.header", pageInfo));
        result.entries().forEach(p -> messages.add(entry(p, name)));
        messages.add(config.message("history.footer", footerInfo));
        return List.copyOf(messages);
    }

    private HistoryPage findPage(OfflinePlayer target, int page, int pageSize, Optional<PunishmentType> type) {
        return repository.findHistory(target.getUniqueId(), type, offset(page, pageSize), pageSize);
    }

    private Component button(String key, String name, int page, Optional<PunishmentType> type) {
        String filter = type.map(t -> " --type " + t.name().toLowerCase()).orElse("");
        return config.message(key).clickEvent(ClickEvent.runCommand("/punish history " + name + " " + page + filter));
    }

    private Component disabled(String key) {
        return config.message(key + "-disabled");
    }

    private Component entry(Punishment punishment, String targetName) {
        Component entry =
                config.message("history.entry", entryResolvers(punishment)).hoverEvent(hover(punishment));
        return revokeSuggestion(punishment, targetName)
                .map(command -> entry.clickEvent(ClickEvent.suggestCommand(command)))
                .orElse(entry);
    }

    private Component hover(Punishment punishment) {
        return config.message(
                "history.hover",
                entryResolvers(punishment),
                Placeholder.component("revocation", revocation(punishment.revocation())));
    }

    private Component revocation(Revocation revocation) {
        if (revocation == null) return Component.empty();
        return config.message(
                "history.hover-revoked",
                Placeholder.unparsed("revoked-by", playerName(revocation.by())),
                config.reason(revocation.reason()),
                Placeholder.unparsed("revoked-at", DateTimeFormatter.ISO_INSTANT.format(revocation.at())));
    }

    private TagResolver entryResolvers(Punishment punishment) {
        return TagResolver.resolver(
                details(punishment),
                Placeholder.unparsed("type", punishment.type().name()),
                config.reason(punishment.reason()),
                Placeholder.unparsed("duration", duration(punishment)),
                remaining(punishment),
                Placeholder.unparsed("status", config.raw(statusKey(punishment))));
    }

    private static Optional<String> revokeSuggestion(Punishment punishment, String targetName) {
        if (punishment.revoked() || punishment.expired()) return Optional.empty();
        return switch (punishment.type()) {
            case BAN -> Optional.of("/unban " + targetName + " ");
            case MUTE -> Optional.of("/unmute " + targetName + " ");
            case WARN -> Optional.of("/unwarn " + targetName + " " + punishment.id() + " ");
            case KICK -> Optional.empty();
        };
    }

    private static String playerName(UUID id) {
        return Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse(id.toString());
    }

    private static TagResolver details(Punishment punishment) {
        String staff = Bukkit.getOfflinePlayer(punishment.issuer().issuer()).getName();
        return staff == null ? PunishmentDetails.resolvers(punishment) : PunishmentDetails.resolvers(punishment, staff);
    }

    private String duration(Punishment punishment) {
        return punishment.expiry() == null
                ? config.raw("permanent-duration")
                : DurationParser.format(Duration.between(punishment.issuer().issuedAt(), punishment.expiry()));
    }

    private TagResolver remaining(Punishment punishment) {
        return punishment.revoked() || punishment.expired()
                ? Placeholder.unparsed("remaining", config.raw("history.no-remaining"))
                : config.remaining(punishment.expiry());
    }

    private static String statusKey(Punishment punishment) {
        if (punishment.revoked()) return "history.status.revoked";
        if (punishment.expired()) return "history.status.expired";
        return "history.status.active";
    }
}
