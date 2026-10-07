package com.bradenkennedy.punishment;
import com.bradenkennedy.punishment.api.model.*;
import com.bradenkennedy.punishment.migration.ImportService;
import com.bradenkennedy.punishment.storage.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ImportServiceTest {
    @TempDir Path directory;
    private final UUID target=UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private ImportService.Progress run(JdbcPunishmentRepository repo,String source,String url,boolean dry) throws Exception {
        return new ImportService(repo,dry,p -> {}).run(source,new ImportService.Options(directory,url,"","",null));
    }
    private JdbcPunishmentRepository destination() throws Exception {
        return new JdbcPunishmentRepository("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","","");
    }
    @Test void vanillaFixtureDryRunRerunAndBanEnforcement() throws Exception {
        Files.writeString(directory.resolve("banned-players.json"),"[{\"uuid\":\""+target+"\",\"name\":\"Player\",\"created\":\"2026-01-02 03:04:05 +0000\",\"expires\":\"forever\",\"reason\":\"Test\",\"source\":\"Console\"}]");
        Files.writeString(directory.resolve("banned-ips.json"),"[{\"ip\":\"192.0.2.1\"}]");
        try(var repo=destination()) {
            assertEquals(1,run(repo,"vanilla",null,true).imported()); assertTrue(repo.findHistory(target).isEmpty());
            var first=run(repo,"vanilla",null,false); assertEquals(1,first.imported()); assertEquals(1,first.skipped());
            assertTrue(repo.findActive(target,PunishmentType.BAN).isPresent());
            assertEquals(0,run(repo,"vanilla",null,false).imported()); assertEquals(1,repo.findHistory(target).size());
        }
    }
    @Test void essentialsFixtureImportsOnlyMutes() throws Exception {
        Files.createDirectories(directory.resolve("userdata"));
        Files.writeString(directory.resolve("userdata/"+target+".yml"),"last-account-name: Player\nmuted: true\ntimestamps:\n  mute: 0\njailed: true\n");
        try(var repo=destination()) { assertEquals(1,run(repo,"essentials",null,false).imported());
            assertTrue(repo.findActive(target,PunishmentType.MUTE).isPresent()); assertEquals(0,run(repo,"essentials",null,false).imported()); }
    }
    @Test void advancedBanFixtureUsesHistoryAndActiveMembership() throws Exception {
        String url="jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;NON_KEYWORDS=END,START,OPERATOR";
        try(var connection=DriverManager.getConnection(url); var sql=connection.createStatement(); var repo=destination()) {
            sql.execute("CREATE TABLE Punishments(id INT,uuid VARCHAR,name VARCHAR,operator VARCHAR,punishmentType VARCHAR,start BIGINT,end BIGINT,reason VARCHAR)");
            sql.execute("CREATE TABLE PunishmentHistory AS SELECT * FROM Punishments");
            sql.execute("INSERT INTO Punishments VALUES(3,'"+target+"','Player','Staff','BAN',1700000000000,-1,'Test')");
            sql.execute("INSERT INTO PunishmentHistory SELECT * FROM Punishments");
            sql.execute("INSERT INTO PunishmentHistory VALUES(4,'"+target+"','Player','Staff','MUTE',1700000000000,-1,'Removed')");
            assertEquals(2,run(repo,"advancedban",url,false).imported()); assertTrue(repo.findActive(target,PunishmentType.BAN).isPresent());
            assertTrue(repo.findActive(target,PunishmentType.MUTE).isEmpty()); assertEquals(0,run(repo,"advancedban",url,false).imported());
        }
    }
    @Test void liteBansFixtureKeepsRevocationAndDistinctTableIds() throws Exception {
        String url="jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        try(var connection=DriverManager.getConnection(url); var sql=connection.createStatement(); var repo=destination()) {
            for(String table:List.of("bans","mutes","warnings","kicks")) {
                sql.execute("CREATE TABLE litebans_"+table+"(id INT,uuid VARCHAR,banned_by_uuid VARCHAR,banned_by_name VARCHAR,time BIGINT,until BIGINT,reason VARCHAR,active BOOLEAN,ipban BOOLEAN,removed_by_uuid VARCHAR,removed_by_date BIGINT,removed_by_reason VARCHAR)");
                sql.execute("INSERT INTO litebans_"+table+" VALUES(1,'"+target+"','00000000-0000-0000-0000-000000000000','Console',1700000000000,-1,'Test',false,false,NULL,NULL,'Appeal')");
            }
            assertEquals(4,run(repo,"litebans",url,false).imported()); assertTrue(repo.findActive(target,PunishmentType.BAN).isEmpty());
            assertEquals(0,run(repo,"litebans",url,false).imported()); assertEquals(4,repo.findHistory(target).size());
        }
    }
    @Test void libertyBansFixtureUsesBinaryUuidAndSeconds() throws Exception {
        String url="jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;NON_KEYWORDS=END,START,OPERATOR";
        try(var connection=DriverManager.getConnection(url); var sql=connection.createStatement(); var repo=destination()) {
            sql.execute("CREATE TABLE libertybans_punishments(id INT,type INT,start BIGINT,end BIGINT,operator BINARY(16),reason VARCHAR)");
            sql.execute("CREATE TABLE libertybans_victims(id INT,type INT,data BINARY(16))");
            sql.execute("CREATE TABLE libertybans_history(id INT,victim INT)");
            for(String table:List.of("bans","mutes","warns")) sql.execute("CREATE TABLE libertybans_"+table+"(id INT,victim INT)");
            sql.execute("INSERT INTO libertybans_victims VALUES(1,0,X'12345678123412341234123456789abc')");
            sql.execute("INSERT INTO libertybans_punishments VALUES(2,0,1700000000,0,X'00000000000000000000000000000000','Test')");
            sql.execute("INSERT INTO libertybans_history VALUES(2,1)"); sql.execute("INSERT INTO libertybans_bans VALUES(2,1)");
            assertEquals(1,run(repo,"libertybans",url,false).imported());
            assertEquals(1700000000000L,repo.findActive(target,PunishmentType.BAN).orElseThrow().issuer().issuedAt().toEpochMilli());
            assertEquals(0,run(repo,"libertybans",url,false).imported());
        }
    }
}
