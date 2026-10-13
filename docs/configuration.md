---
title: Configuration
description: Options in config.yml.
sidebar:
  order: 4
---

The configuration lives in `plugins/Warrant/config.yml`.

```yaml
language: en_US

mute:
  blocked-commands:
    - msg
    - tell
    - w
    - whisper
    - r
    - reply
    - me
    - teammsg

history:
  page-size: 10
```

## Options

| Option                  | Default      | Description                                                         |
|-------------------------|--------------|---------------------------------------------------------------------|
| `language`              | `en_US`      | Language file to load from `plugins/Warrant/lang/`               |
| `mute.blocked-commands` | see above    | Commands muted players aren't allowed to use, without the slash     |
| `history.page-size`     | `10`         | Entries per page in `/punish history`; values below 1 become 1      |

Restart the server after changing the configuration.
