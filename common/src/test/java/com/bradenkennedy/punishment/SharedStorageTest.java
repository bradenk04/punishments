package com.bradenkennedy.punishment;
import com.bradenkennedy.punishment.api.model.*;
import com.bradenkennedy.punishment.storage.*;
import com.bradenkennedy.punishment.network.NetworkCache;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SharedStorageTest {
    @Test void anotherConnectionSeesBanMuteRevocationAndPolling() throws Exception {
        String url="jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        try(var a=new JdbcPunishmentRepository(url,"",""); var b=new JdbcPunishmentRepository(url,"","")) {
            UUID target=UUID.randomUUID(); var cache=new NetworkCache(b); cache.load(target);
            var ban=new Punishment(UUID.randomUUID(),target,PunishmentType.BAN,new PunishmentIssuer(new UUID(0,0),Instant.now()),"test",null,false);
            a.create(ban); assertTrue(b.findActive(target,PunishmentType.BAN).isPresent());
            cache.poll(change -> {}); assertEquals(ban.id(),cache.active(target,PunishmentType.BAN).orElseThrow().id());
            a.revoke(ban.id(),new UUID(0,0),"appeal",Instant.now()); cache.poll(change -> {});
            assertTrue(cache.active(target,PunishmentType.BAN).isEmpty());
            var mute=new Punishment(UUID.randomUUID(),target,PunishmentType.MUTE,ban.issuer(),"test",Instant.now().plusSeconds(60),false);
            a.create(mute); cache.poll(change -> {}); assertTrue(cache.active(target,PunishmentType.MUTE).isPresent());
            a.revoke(mute.id(),new UUID(0,0),"done",Instant.now()); cache.poll(change -> {});
            assertTrue(cache.active(target,PunishmentType.MUTE).isEmpty());
        }
    }
    @Test void failureIsNotReportedAsSuccessfulWrite() throws Exception {
        var repo=new JdbcPunishmentRepository("jdbc:h2:mem:"+UUID.randomUUID(),"",""); repo.close();
        assertThrows(IllegalStateException.class,()->repo.create(new Punishment(UUID.randomUUID(),UUID.randomUUID(),PunishmentType.BAN,
            new PunishmentIssuer(new UUID(0,0),Instant.now()),"test",null,false)));
    }
}
