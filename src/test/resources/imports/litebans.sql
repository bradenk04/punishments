CREATE TABLE litebans_bans(id INT,uuid VARCHAR,banned_by_uuid VARCHAR,banned_by_name VARCHAR,time BIGINT,until BIGINT,reason VARCHAR,active BOOLEAN,ipban BOOLEAN,removed_by_uuid VARCHAR,removed_by_date BIGINT,removed_by_reason VARCHAR);
CREATE TABLE litebans_mutes AS SELECT * FROM litebans_bans;
CREATE TABLE litebans_warnings AS SELECT * FROM litebans_bans;
CREATE TABLE litebans_kicks AS SELECT * FROM litebans_bans;
INSERT INTO litebans_bans VALUES(1,'12345678-1234-1234-1234-123456789abc','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','Staff',1700000000000,1700000200000,'Test',false,false,'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',1700000100000,'Appeal');
INSERT INTO litebans_mutes SELECT * FROM litebans_bans;
INSERT INTO litebans_warnings SELECT * FROM litebans_bans;
INSERT INTO litebans_kicks SELECT * FROM litebans_bans;
