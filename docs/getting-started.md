---
title: Getting started
description: Requirements and installation for Punishments.
sidebar:
  order: 1
---

## Requirements

| Component | Version             |
|-----------|---------------------|
| Server    | Spigot / Paper 26.2 |
| Java      | 25 or newer         |

LuckPerms and Vault are optional. When present they are used to check the [exempt permission](../permissions/#exemption) for players who are offline.

## Installation

1. Download the latest jar from the [releases page](https://github.com/bradenk04/punishments/releases).
2. Drop it into your server's `plugins/` folder.
3. Start the server.

On first start the plugin creates the following in `plugins/Punishment/`:

| File                | Purpose                                  |
|---------------------|------------------------------------------|
| `config.yml`        | [Configuration](../configuration/)       |
| `lang/en_US.yml`    | [Messages](../messages/)                 |
| `punishments.mv.db` | The H2 database holding all punishments  |

## First steps

Give your staff a permission group, then try a command:

```
/lp group moderator permission set punishments.moderator true
/warn Steve Please keep chat friendly
/punish history Steve
```

See [Permissions](../permissions/) for the full list.
