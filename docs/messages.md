---
title: Messages
description: Customising messages with language files and MiniMessage.
sidebar:
  order: 5
---

Every message is in `plugins/Punishment/lang/<language>.yml` and uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting.

```yaml
ban:
  announce: "<red><name> was banned by <staff> for <duration>: <reason>"
  screen: "<red>You are banned for <remaining><newline><gray><reason>"
```

## Custom languages

1. Copy `lang/en_US.yml` to `lang/<your-language>.yml`.
2. Set `language: <your-language>` in `config.yml`.
3. Restart the server.

Keys missing from your file fall back to the bundled `en_US.yml`, so you only need to include what you change.

## Placeholders

Which placeholders work depends on the message. The common ones are:

| Placeholder   | Meaning                                           |
|---------------|---------------------------------------------------|
| `<name>`      | The punished player                               |
| `<staff>`     | The staff member (or console) issuing the action  |
| `<reason>`    | The reason, or the `no-reason` message            |
| `<duration>`  | The full punishment length                        |
| `<remaining>` | Time left on an active mute or ban                |
| `<id>`        | The punishment's unique ID                        |
| `<date>`      | When the punishment was issued                    |

The `history.*` messages also provide `<type>`, `<status>`, `<page>`, `<pages>`, `<previous>`, `<next>`, `<revoked-by>` and `<revoked-at>`. The `import.*` messages provide `<processed>`, `<total>`, `<imported>`, `<skipped>` and `<failed>`.

Check the default `en_US.yml` to see which placeholders each message uses.
