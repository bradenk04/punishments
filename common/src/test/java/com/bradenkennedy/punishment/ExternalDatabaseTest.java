package com.bradenkennedy.punishment;
import com.bradenkennedy.punishment.api.model.*;
import com.bradenkennedy.punishment.storage.JdbcPunishmentRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
class ExternalDatabaseTest {
    @Test void mysqlSchemaAndOperations() throws Exception { exercise("MYSQL_JDBC"); }
    @Test void postgresSchemaAndOperations() throws Exception { exercise("POSTGRES_JDBC"); }
    private void exercise(String key) throws Exception {
        String url=System.getenv(key);assumeTrue(url!=null&&!url.isBlank(),"Set "+key+" for integration test");
        try(var repository=new JdbcPunishmentRepository(url,"punishments","punishments")) {
            UUID target=UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            UUID id=UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
            if(repository.findById(id).isEmpty()) repository.create(new Punishment(id,target,PunishmentType.BAN,
                new PunishmentIssuer(new UUID(0,0),Instant.ofEpochMilli(1700000000000L)),"Integration test",null,false));
            repository.rememberName(target,"IntegrationPlayer");
            assertEquals(id,repository.findActive(target,PunishmentType.BAN).orElseThrow().id());
            UUID other=UUID.randomUUID();repository.create(new Punishment(other,UUID.randomUUID(),PunishmentType.MUTE,
                new PunishmentIssuer(new UUID(0,0),Instant.now()),"temporary",null,false));
            repository.revoke(other,new UUID(0,0),"test",Instant.now());assertTrue(repository.findById(other).orElseThrow().revoked());
        }
    }
}
