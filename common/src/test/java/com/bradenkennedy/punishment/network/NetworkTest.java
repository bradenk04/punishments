package com.bradenkennedy.punishment.network;
import com.bradenkennedy.punishment.api.model.*;
import com.bradenkennedy.punishment.storage.JdbcPunishmentRepository;
import com.bradenkennedy.punishment.storage.model.NetworkChange;
import com.j256.ormlite.dao.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NetworkTest {
    @Test void delayedCommitAndClockSkewDoNotLoseChangesOrReplayOldKicks() throws Exception {
        try(var repository=new JdbcPunishmentRepository("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","","")) {
            var dao=DaoManager.<Dao<NetworkChange,UUID>,NetworkChange>createDao(repository.connectionSource,NetworkChange.class);
            var old=new NetworkChange(UUID.randomUUID(),UUID.randomUUID(),"ISSUE");old.created=1;dao.create(old);
            var cache=new NetworkCache(repository);var received=new ArrayList<UUID>();cache.poll(c -> received.add(c.id));assertTrue(received.isEmpty());
            var delayed=new NetworkChange(UUID.randomUUID(),UUID.randomUUID(),"ISSUE");delayed.created=1;dao.create(delayed);
            cache.poll(c -> received.add(c.id));assertEquals(List.of(delayed.id),received);cache.poll(c -> received.add(c.id));assertEquals(1,received.size());
        }
    }
    @Test void reasonDoesNotExpandIntoAnotherTemplateToken() {
        var p=new Punishment(UUID.randomUUID(),UUID.randomUUID(),PunishmentType.BAN,new PunishmentIssuer(new UUID(0,0),Instant.EPOCH),"<id>",null,false);
        assertEquals("<id> Console",PunishmentMessages.render("<reason> <staff>",p));
    }
}
