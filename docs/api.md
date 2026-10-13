---
title: Developer API
description: Events and model types for plugins that integrate with Warrant.
sidebar:
  order: 7
---

Warrant fires two cancellable events.

| Event                          | Fired when                    |
|--------------------------------|-------------------------------|
| `PlayerPunishedEvent`          | A punishment is about to be saved  |
| `PlayerPunishmentRevokedEvent` | A punishment is about to be revoked |

Both live in `com.lotusplugins.warrant.api.events` and expose `getPunishment()`. Cancelling the event stops the action.

:::caution
Commands run asynchronously, so these events usually fire off the main thread. Don't touch the Bukkit world directly from your listener.
:::

## Example

```java
import com.lotusplugins.warrant.api.events.PlayerPunishedEvent;
import com.lotusplugins.warrant.api.model.PunishmentType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class PunishmentLogger implements Listener {

    @EventHandler
    public void onPunish(PlayerPunishedEvent event) {
        var punishment = event.getPunishment();
        if (punishment.type() == PunishmentType.BAN && punishment.reason() == null) {
            event.setCancelled(true);
        }
    }
}
```

## Model

`Punishment` is a record in `com.lotusplugins.warrant.api.model`.

| Field        | Type               | Notes                               |
|--------------|--------------------|-------------------------------------|
| `id`         | `UUID`             | Unique punishment ID                |
| `target`     | `UUID`             | The punished player                 |
| `type`       | `PunishmentType`   | `WARN`, `KICK`, `MUTE` or `BAN`     |
| `issuer`     | `PunishmentIssuer` | Issuer UUID and `issuedAt` instant  |
| `reason`     | `String`           | May be `null`                       |
| `expiry`     | `Instant`          | `null` means permanent              |
| `revocation` | `Revocation`       | `null` unless revoked               |

`expired()` and `revoked()` are provided for convenience.
