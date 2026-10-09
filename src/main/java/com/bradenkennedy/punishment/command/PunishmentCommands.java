package com.bradenkennedy.punishment.command;

import static com.bradenkennedy.punishment.command.CommandSupport.nameOf;
import static org.incendo.cloud.bukkit.parser.OfflinePlayerParser.offlinePlayerParser;
import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;

import com.bradenkennedy.punishment.PluginConfig;
import com.bradenkennedy.punishment.PunishmentDetails;
import com.bradenkennedy.punishment.api.model.HistoryPage;
import com.bradenkennedy.punishment.api.model.Punishment;
import com.bradenkennedy.punishment.command.parser.DurationParser;
import com.bradenkennedy.punishment.storage.PunishmentRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
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
                .optional("page", integerParser(1))
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
        historyMessages(ctx.get("player"), ctx.<Integer>optional("page").orElse(1))
                .forEach(audience::sendMessage);
    }

    List<Component> historyMessages(OfflinePlayer target, int requestedPage) {
        int pageSize = config.historyPageSize();
        HistoryPage result = findPage(target, requestedPage, pageSize);
        if (result.total() == 0) {
            return List.of(config.message("history.empty", Placeholder.unparsed("name", nameOf(target))));
        }
        int pages = pageCount(result.total(), pageSize);
        int page = Math.min(requestedPage, pages);
        if (page != requestedPage) {
            result = findPage(target, page, pageSize);
        }

        TagResolver pageInfo = TagResolver.resolver(
                Placeholder.unparsed("name", nameOf(target)),
                Placeholder.unparsed("page", String.valueOf(page)),
                Placeholder.unparsed("pages", String.valueOf(pages)));
        List<Component> messages = new ArrayList<>();
        messages.add(config.message("history.header", pageInfo));
        result.entries().forEach(p -> messages.add(entry(p)));
        messages.add(config.message("history.footer", pageInfo));
        return List.copyOf(messages);
    }

    private HistoryPage findPage(OfflinePlayer target, int page, int pageSize) {
        return repository.findHistory(target.getUniqueId(), Optional.empty(), offset(page, pageSize), pageSize);
    }

    private Component entry(Punishment punishment) {
        return config.message(
                "history.entry",
                details(punishment),
                Placeholder.unparsed("type", punishment.type().name()),
                config.reason(punishment.reason()),
                Placeholder.unparsed("duration", duration(punishment)),
                remaining(punishment),
                Placeholder.unparsed("status", config.raw(statusKey(punishment))));
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
