| Column        | Type          | Notes                                        |
|---------------|---------------|----------------------------------------------|
| ID            | VARCHAR (48)  | UUIDv4                                       |
| TARGET        | VARCHAR (48)  | UUIDv4                                       |
| TYPE          | VARCHAR (100) | PunishmentType enum                          |
| ISSUERID      | VARCHAR (48)  | UUIDv4, the player who issued the punishment |
| ISSUEDAT      | BIGINT        | Instant timestamp                            |
| REASON        | VARCHAR (256) |                                              |
| EXPIRY        | BIGINT        | Instant timestamp                            |
| REVOKED       | BOOLEAN       |                                              |
| REVOKEDREASON | VARCHAR(256)  |                                              |
| REVOKEDBY     | VARCHAR(48)   | UUIDv4                                       |
| REVOKEDAT     | BIGINT        | Instant timestamp                            |