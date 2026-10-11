<div align="center">

![Punishments banner](https://placehold.co/1200x300/1e1e2e/f38ba8/png?text=Punishments&font=montserrat)

**Simple and effective punishment plugin for Spigot / Paper servers.**

Warn, kick, mute and ban players, permanently or for a set time, with a full history stored locally in H2.

[![Discord](https://img.shields.io/badge/Discord-Join%20us-5865F2?logo=discord&logoColor=white)](https://discord.gg/qkr2jrX9jF)

</div>

---

## Features

- **Warnings, kicks, mutes and bans.** Mutes and bans can be permanent or temporary (`/tempmute`, `/tempban`).
- **Punishment history.** Every punishment is recorded, including revoked and expired ones.
- **Silent mode.** Add `-s` / `--silent` to any command so only staff with `punishments.notify` see the announcement.
- **Offline targets.** Ban, mute or warn players who aren't online. Warnings are delivered the next time they join.
- **Mute command blocking.** Muted players can't use private-message commands like `/msg`, `/tell` or `/r`.
- **MiniMessage formatting.** Every message lives in a language file and can be restyled.
- **Developer API.** Cancellable events fire whenever a punishment is issued or revoked.

![Ban screen preview](https://placehold.co/800x450/1e1e2e/cdd6f4/png?text=Ban+screen+screenshot)

## Requirements

| Component | Version             |
|-----------|---------------------|
| Server    | Spigot / Paper 26.2 |
| Java      | 25 or newer         |

## Installation

1. Download the latest jar from the [releases page](https://github.com/bradenk04/punishments/releases), or [build it yourself](#building-from-source).
2. Drop it into your server's `plugins/` folder.
3. Start the server. `config.yml`, `lang/en_US.yml` and the H2 database (`punishments.mv.db`) are created in `plugins/Punishment/`.

## Commands

`<player>` accepts online and offline players (except `/kick`, which needs an online player). `[reason]` is optional unless noted. Every command accepts `-s` / `--silent`.

| Command                                  | Description                                    | Permission                |
|------------------------------------------|------------------------------------------------|---------------------------|
| `/warn <player> <reason>`                | Warn a player (reason required)                | `punishments.warn`        |
| `/unwarn <player> [id] [reason]`         | Remove a warning (latest one if no ID is given) | `punishments.unwarn`      |
| `/kick <player> [reason]`                | Kick an online player                          | `punishments.kick`        |
| `/mute <player> [reason]`                | Permanently mute a player                      | `punishments.mute`        |
| `/tempmute <player> <duration> [reason]` | Mute a player for a set time                   | `punishments.tempmute`    |
| `/unmute <player> [reason]`              | Lift an active mute                            | `punishments.unmute`      |
| `/ban <player> [reason]`                 | Permanently ban a player                       | `punishments.ban`         |
| `/tempban <player> <duration> [reason]`  | Ban a player for a set time                    | `punishments.tempban`     |
| `/unban <player> [reason]`               | Lift an active ban                             | `punishments.unban`       |
| `/punish history <player>`               | View a player's punishment history             | `punishments.history`     |

### Durations

Durations combine a number with a unit: `s` (seconds), `m` (minutes), `h` (hours), `d` (days) and `w` (weeks). Units can be chained.

```
/tempban Steve 7d Griefing spawn
/tempmute Alex 1h30m Spamming chat
/ban Herobrine -s Ban evasion
```

![Punishment history preview](https://placehold.co/800x300/1e1e2e/cdd6f4/png?text=/punish+history+screenshot)

### Other permissions

| Permission           | Description                                                    |
|----------------------|----------------------------------------------------------------|
| `punishments.notify` | See announcements for silent punishments                       |
| `punishments.exempt` | Can't be punished (server operators are always exempt)         |

## Configuration

### `config.yml`

```yaml
# Language file to load from plugins/Punishment/lang/
language: en_US

mute:
  # Commands muted players are not allowed to use
  blocked-commands:
    - msg
    - tell
    - w
    - whisper
    - r
    - reply
    - me
    - teammsg
```

### Messages

All messages live in `lang/<language>.yml` and use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting. Keys missing from a custom language file fall back to the bundled `en_US.yml`.

```yaml
ban:
  announce: "<red><name> was banned by <staff> for <duration>: <reason>"
  screen: "<red>You are banned for <remaining><newline><gray><reason>"
```

| Placeholder   | Meaning                                         |
|---------------|-------------------------------------------------|
| `<name>`      | The punished player                             |
| `<staff>`     | The staff member (or console) issuing the action |
| `<reason>`    | The reason, or the `no-reason` message          |
| `<duration>`  | The full punishment length                      |
| `<remaining>` | Time left on an active mute or ban              |
| `<id>`        | The punishment's unique ID                      |

![Chat announcement preview](https://placehold.co/800x200/1e1e2e/f38ba8/png?text=Chat+announcement+screenshot)

## Developer API

Listen for `PlayerPunishedEvent` and `PlayerPunishmentRevokedEvent` to react to punishments, or cancel them before they're saved.

```java
import com.bradenkennedy.punishment.api.events.PlayerPunishedEvent;
import com.bradenkennedy.punishment.api.model.PunishmentType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class PunishmentLogger implements Listener {

    @EventHandler
    public void onPunish(PlayerPunishedEvent event) {
        var punishment = event.getPunishment();
        if (punishment.type() == PunishmentType.BAN && punishment.reason() == null) {
            event.setCancelled(true); // require a reason for bans
        }
    }
}
```

> [!NOTE]
> Commands run asynchronously, so these events usually fire off the main thread. Don't touch the Bukkit world directly from your listener.

## Building from source

```sh
git clone https://github.com/bradenk04/punishments.git
cd punishments
./gradlew shadowJar
```

The plugin jar is written to `build/libs/`.

| Task                 | Description                                      |
|----------------------|--------------------------------------------------|
| `./gradlew test`      | Run the JUnit test suite                         |
| `./gradlew runServer` | Start a local 26.2 test server with the plugin (needs Java 25) |

## Community

Questions, feedback or just want to chat? Join our [Discord server](https://discord.gg/qkr2jrX9jF).

## Contributing

Bug reports and feature requests are welcome. Please use the [issue templates](.github/ISSUE_TEMPLATE) and follow the [pull request template](.github/PULL_REQUEST_TEMPLATE.md) when opening a PR.


