package com.bradenkennedy.punishment;

import com.bradenkennedy.punishment.api.model.Punishment;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class PunishmentDetails {
    private static final UUID CONSOLE = new UUID(0, 0);

    private PunishmentDetails() {}

    public static TagResolver resolvers(Punishment punishment) {
        UUID issuer = punishment.issuer().issuer();
        return resolvers(punishment, CONSOLE.equals(issuer) ? "Console" : issuer.toString());
    }

    public static TagResolver resolvers(Punishment punishment, String staff) {
        return TagResolver.resolver(
                Placeholder.unparsed("id", punishment.id().toString()),
                Placeholder.unparsed("staff", staff),
                Placeholder.unparsed(
                        "date",
                        DateTimeFormatter.ISO_INSTANT.format(punishment.issuer().issuedAt())));
    }
}
