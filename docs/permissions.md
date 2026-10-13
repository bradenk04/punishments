---
title: Permissions
description: Permission nodes and groups provided by Warrant.
sidebar:
  order: 3
---

## Groups

Most servers only need these three groups.

| Permission              | Includes                                                                           |
|-------------------------|------------------------------------------------------------------------------------|
| `warrant.moderator` | mute, tempmute, unmute, warn, unwarn, kick, history and notify                     |
| `warrant.admin`     | everything in moderator, plus ban, tempban, unban and import                       |
| `warrant.*`         | everything in admin, plus `warrant.exempt`                                     |

## Individual nodes

| Permission              | Description                                       | Default |
|-------------------------|---------------------------------------------------|---------|
| `warrant.ban`       | Issue permanent bans                              | op      |
| `warrant.tempban`   | Issue temporary bans                              | op      |
| `warrant.unban`     | Revoke bans                                       | op      |
| `warrant.mute`      | Issue permanent mutes                             | op      |
| `warrant.tempmute`  | Issue temporary mutes                             | op      |
| `warrant.unmute`    | Revoke mutes                                      | op      |
| `warrant.warn`      | Issue warnings                                    | op      |
| `warrant.unwarn`    | Revoke warnings                                   | op      |
| `warrant.kick`      | Kick players                                      | op      |
| `warrant.history`   | View a player's history                           | op      |
| `warrant.notify`    | Receive silent punishment announcements           | op      |
| `warrant.import`    | Import history from the console                   | nobody  |
| `warrant.exempt`    | Can't be punished                                 | nobody  |

## Exemption

Players with `warrant.exempt` cannot be punished. Operators are not exempt automatically, so grant the permission explicitly to anyone who should be protected.

The check also works for offline players when LuckPerms or Vault is installed.
