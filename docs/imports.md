# Importing punishment history

Run `punish import <source> --dry-run` from the server console. Check imported/skipped/failed counts, then repeat without `--dry-run`. The preview reports records that would be imported. Supported sources are `vanilla`, `essentials`, `advancedban`, `litebans`, and `libertybans`. Only one import runs at a time.

Imports use the existing local H2 punishment database and the existing single plugin build. They do not add shared SQL storage, platform modules, network polling, or a published developer API.

## Files and source databases

For vanilla and Essentials, `imports.<source>.directory` defaults to the server root (`.`). Vanilla reads `banned-players.json` and skips entries in `banned-ips.json`. Essentials reads those same ban files plus `plugins/Essentials/userdata/*.yml`; jails are not imported. For an offline backup, preserve this directory layout and set `directory` to its root.

Database sources use `imports.<source>.url`, `username`, `password`, and optionally `prefix`. Defaults are no prefix for AdvancedBan, `litebans_` for LiteBans, and `libertybans_` for LibertyBans. For example:

```yaml
imports:
  advancedban:
    url: jdbc:hsqldb:file:/absolute/path/to/copy/AdvancedBan;readonly=true;ifexists=true
    username: SA
    password: ''
    prefix: ''
```

Stop the source plugin and use a copy of embedded database files. Use a SELECT-only account for remote sources. AdvancedBan supports HSQLDB/MySQL source tables; LiteBans supports H2/MySQL; LibertyBans supports HSQLDB/MySQL-compatible MariaDB/PostgreSQL. H2 file URLs should include `;IFEXISTS=TRUE;ACCESS_MODE_DATA=r`. Drivers are bundled as in the original importer proposal; source credentials are never printed in command errors.

## Preservation and recovery

IDs are derived from the source and original record ID, including the table name where needed. A punishment and its provenance commit together. A failed record is counted as failed and rolled back; committed records are skipped on rerun. A dry run creates no destination tables or records and triggers no live enforcement.

Issuer UUIDs, issue time, expiry, reason, revoked state and available revocation metadata are preserved. Original issuer/player names and source IDs are retained in `punishment_imports`. Missing actor UUIDs use the zero UUID; unknown issue/removal times use the Unix epoch. These are explicit unknown-value sentinels, not reconstructed identities or dates. Essentials does not retain original mute issuer, reason or issue time. AdvancedBan and LibertyBans schemas may lack removal actor/time/reason. Expired records remain in history. IP and composite victims are skipped until IP punishments are supported.

Successful active bans are available to login checks immediately and schedule online-player removal; active mutes refresh the current plugin cache immediately. Imports do not fire new-punishment command events or generate network broadcasts.

Fixture tests cover all five sources, dry runs, reruns, active enforcement callbacks, revocation metadata and transactional rollback. They model source schemas; test a dry run against a backup from your source plugin version before a production migration.
