---
title: Importing
description: Migrate punishment history from other plugins.
sidebar:
  order: 6
---

Punishments can import existing history from other plugins.

```
/punish import <source> [--dry-run]
```

Imports can only be run from the console and need `punishments.import`.

## Sources

| Source        | Reads from                                          |
|---------------|-----------------------------------------------------|
| `vanilla`     | `banned-players.json` and `banned-ips.json`         |
| `litebans`    | `plugins/LiteBans`                                  |
| `advancedban` | `plugins/AdvancedBan`                               |
| `libertybans` | `plugins/LibertyBans`                               |
| `essentials`  | Essentials mutes in `plugins/Essentials/userdata`, plus the vanilla bans |

## Steps

1. Stop using the old plugin, or at least stop it issuing new punishments.
2. Run a dry run and check the counts:

   ```
   punish import litebans --dry-run
   ```

3. Run the import without `--dry-run`.

The report shows how many records were imported, skipped and failed.
