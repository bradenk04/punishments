---
title: Permissions
description: Permission nodes and groups provided by Punishments.
sidebar:
  order: 3
---

## Groups

Most servers only need these three groups.

| Permission              | Includes                                                                           |
|-------------------------|------------------------------------------------------------------------------------|
| `punishments.moderator` | mute, tempmute, unmute, warn, unwarn, kick, history and notify                     |
| `punishments.admin`     | everything in moderator, plus ban, tempban, unban and import                       |
| `punishments.*`         | everything in admin, plus `punishments.exempt`                                     |

## Individual nodes

| Permission              | Description                                       | Default |
|-------------------------|---------------------------------------------------|---------|
| `punishments.ban`       | Issue permanent bans                              | op      |
| `punishments.tempban`   | Issue temporary bans                              | op      |
| `punishments.unban`     | Revoke bans                                       | op      |
| `punishments.mute`      | Issue permanent mutes                             | op      |
| `punishments.tempmute`  | Issue temporary mutes                             | op      |
| `punishments.unmute`    | Revoke mutes                                      | op      |
| `punishments.warn`      | Issue warnings                                    | op      |
| `punishments.unwarn`    | Revoke warnings                                   | op      |
| `punishments.kick`      | Kick players                                      | op      |
| `punishments.history`   | View a player's history                           | op      |
| `punishments.notify`    | Receive silent punishment announcements           | op      |
| `punishments.import`    | Import history from the console                   | nobody  |
| `punishments.exempt`    | Can't be punished                                 | nobody  |

## Exemption

Players with `punishments.exempt` cannot be punished. Operators are not exempt automatically, so grant the permission explicitly to anyone who should be protected.

The check also works for offline players when LuckPerms or Vault is installed.
