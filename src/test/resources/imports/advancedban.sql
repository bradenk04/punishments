CREATE TABLE Punishments(id INT,uuid VARCHAR,name VARCHAR,operator VARCHAR,punishmentType VARCHAR,start BIGINT,end BIGINT,reason VARCHAR);
CREATE TABLE PunishmentHistory AS SELECT * FROM Punishments;
INSERT INTO Punishments VALUES(3,'12345678123412341234123456789abc','Player','Staff','BAN',1700000000000,-1,'Test');
INSERT INTO PunishmentHistory SELECT * FROM Punishments;
INSERT INTO PunishmentHistory VALUES(4,'12345678123412341234123456789abc','Player','Staff','MUTE',1700000000000,-1,'Removed');
INSERT INTO PunishmentHistory VALUES(5,'192.0.2.1','Player','Staff','IPBAN',1700000000000,-1,'Skipped');
