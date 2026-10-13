---
title: Commands
description: Every Warrant command, its arguments and permission.
sidebar:
  order: 2
---

`<player>` accepts online and offline players, except `/kick`, which needs an online player. `[reason]` is optional unless noted.

| Command                                  | Description                                     | Permission             |
|------------------------------------------|-------------------------------------------------|------------------------|
| `/warn <player> <reason>`                | Warn a player (reason required)                 | `warrant.warn`     |
| `/unwarn <player> [id] [reason]`         | Remove a warning (the latest if no ID is given) | `warrant.unwarn`   |
| `/kick <player> [reason]`                | Kick an online player                           | `warrant.kick`     |
| `/mute <player> [reason]`                | Permanently mute a player                       | `warrant.mute`     |
| `/tempmute <player> <duration> [reason]` | Mute a player for a set time                    | `warrant.tempmute` |
| `/unmute <player> [reason]`              | Lift an active mute                             | `warrant.unmute`   |
| `/ban <player> [reason]`                 | Permanently ban a player                        | `warrant.ban`      |
| `/tempban <player> <duration> [reason]`  | Ban a player for a set time                     | `warrant.tempban`  |
| `/unban <player> [reason]`               | Lift an active ban                              | `warrant.unban`    |
| `/punish history <player> [page]`        | View a player's punishment history              | `warrant.history`  |
| `/punish import <source>`                | Import history from another plugin              | `warrant.import`   |

## Silent mode

Add `-s` or `--silent` to any punishment command. The announcement is then only shown to staff with `warrant.notify`.

```
/ban Herobrine -s Ban evasion
```

## Durations

A duration is a number followed by a unit. Units can be chained.

| Unit | Meaning |
|------|---------|
| `s`  | seconds |
| `m`  | minutes |
| `h`  | hours   |
| `d`  | days    |
| `w`  | weeks   |

```
/tempban Steve 7d Griefing spawn
/tempmute Alex 1h30m Spamming chat
```

## History

`/punish history <player> [page]` lists every punishment for a player, including expired and revoked ones. Hover an entry for the issuer, duration and revocation details.

Use `--type` or `-t` to filter by `warn`, `kick`, `mute` or `ban`:

```
/punish history Steve 2 --type ban
```

The page size is set by `history.page-size` in the [configuration](../configuration/).

## Importing

`/punish import <source> [--dry-run]` can only be run from the console. See [Importing](../importing/).
