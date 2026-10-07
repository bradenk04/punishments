package com.bradenkennedy.punishment.network;
import com.bradenkennedy.punishment.api.model.Punishment;
import java.util.Objects;
import java.util.regex.*;
/** Plain-text proxy templates; values are replaced once and never parsed as formatting. */
public final class PunishmentMessages {
    private static final Pattern TOKEN = Pattern.compile("<(id|staff|date|reason|type|expiry)>");
    private PunishmentMessages() {}
    public static String render(String template, Punishment p) {
        Matcher matcher = TOKEN.matcher(template);
        return matcher.replaceAll(match -> Matcher.quoteReplacement(switch (match.group(1)) {
            case "id" -> p.id().toString();
            case "staff" -> p.issuer().issuer().getMostSignificantBits() == 0 && p.issuer().issuer().getLeastSignificantBits() == 0
                ? "Console" : p.issuer().issuer().toString();
            case "date" -> p.issuer().issuedAt().toString();
            case "reason" -> Objects.toString(p.reason(), "No reason");
            case "type" -> p.type().name();
            default -> p.expiry() == null ? "Permanent" : p.expiry().toString();
        }));
    }
}
