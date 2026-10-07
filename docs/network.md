# Network setup and acceptance checks

Build with Java 21: `./gradlew clean test shadowJar :velocity:shadowJar`.
Install `paper/build/libs/*-all.jar` on every Paper backend and `velocity/build/libs/*-all.jar` on Velocity. Do not use the thin jars. Publish the platform-neutral model/repository API with `./gradlew :api:publishToMavenLocal`.

Configure the same MySQL or PostgreSQL JDBC URL and credentials in Paper's `database` section and Velocity's `plugins/punishments/config.properties`. Velocity intentionally requires a shared database. H2 remains the default for standalone Paper. Initialize schema with the plugin's JDBC repository. Existing punishment columns are preserved; new player-name, import-provenance and changes tables are additive. No Redis is required.

The default poll interval is one second. Each participant reads committed change IDs and refreshes connected players. Clock differences and late commits cannot discard events. On restart it snapshots historical change IDs so old kicks are not replayed, while active bans/mutes still refresh. The changes table and seen-ID set grow with moderation activity; retain them while instances are running. A coordinated maintenance restart permits archiving historical change rows. This first implementation prioritizes correct delivery over high-volume log compaction.

Modern signed chat (1.19.1+) is enforced by the Paper companion. Velocity cancels legacy chat and blocked proxy commands; it leaves modern chat signatures intact. Install the companion on every backend and preserve normal secure player forwarding and signature validation. No signed-chat setting must be weakened.

Proxy commands: ban/tempban/unban, mute/tempmute/unmute, warn/unwarn, kick, and punish history. Targets accept online network names or UUIDs. Temporary durations accept positive integer s/m/h/d units. Permission nodes match the Paper declarations; use a proxy permissions provider such as LuckPerms for staff permissions. Exemption is explicit. Database access executes asynchronously; chat/command enforcement uses the cache.

Staff updates appear on each Paper backend by default. For a proxy-owned notification stream set `notify-on-proxy=true` in config.properties; using both streams may produce duplicate staff updates. `punishment-screen` can override the plain-text proxy screen with `<id>`, `<staff>`, `<date>`, `<reason>`, `<type>` and `<expiry>`; Java properties newline escapes are supported.

Manual integration acceptance (requires two running Paper servers, Velocity, a shared database and two clients):

1. With staff on A and target on B, issue `/ban <target> reason`. B disconnects within the poll interval and proxy reconnect denies login with the punishment ID.
2. `/unban <UUID>` allows the next login. `/mute <target>` on A prevents B chat and listed messaging commands without relogging. `/unmute` restores them after refresh.
3. Repeat mute enforcement with a modern signed client and verify no signature-validation disconnect occurs.
4. Issue `/kick <target>` across the network; restart proxy/backend and verify that kick is not replayed.
5. Staff on both servers receive change notifications. Remove notify permission and verify notifications stop.
6. Repeat with MySQL and PostgreSQL and with no Redis installed. Test database outage: login is denied when storage cannot be checked; writes must not report success.

CI covers two-connection ban/mute/revoke refresh, delayed event visibility, clock skew, message placeholders, both real database dialects, compilation and shaded driver startup. Live two-server/client acceptance remains a manual check and must be completed before production rollout.
