CREATE TABLE thought_leaders(
  thought_leader_id uuid PRIMARY KEY,
  nickname character varying(50) not null,
  salted_hash character(81) not null, -- 64 for the hash + 16 for the salt + ':',
  UNIQUE(nickname)
);
-----
CREATE INDEX thought_leaders_nickname_idx ON thought_leaders(nickname);
-----
CREATE TABLE twots(
  twot_id uuid primary key,
  author_id uuid not null,
  content character varying(128),
  added timestamptz not null,
  CONSTRAINT fk_author FOREIGN KEY(author_Id) REFERENCES thought_leaders(thought_leader_id)
);
-----
CREATE TABLE followers(
  leader_id uuid not null,
  follower uuid not null,
  UNIQUE (leader_id, follower),
  CONSTRAINT fk_leader FOREIGN KEY(leader_id) REFERENCES thought_leaders(thought_leader_id),
  CONSTRAINT fk_follower FOREIGN KEY(follower) REFERENCES thought_leaders(thought_leader_id)
);
-----
CREATE INDEX followers_leader_idx ON followers(leader_id);
-----
CREATE INDEX followers_follower_idx ON followers(follower);
-----
CREATE TABLE uwotm8s(
  author_id uuid not null,
  twot_id uuid not null,
  UNIQUE (author_id, twot_id),
  CONSTRAINT fk_author FOREIGN KEY(author_id) REFERENCES thought_leaders(thought_leader_id),
  CONSTRAINT fk_twot FOREIGN KEY(twot_id) REFERENCES twots(twot_id)
);
-----
CREATE INDEX twot_uwotm8s_idx ON uwotm8s(twot_id);
-----
CREATE VIEW uwotm8_counts AS (SELECT twot_id, count(*)::int4 AS uwotm8Count FROM uwotm8s GROUP BY twot_id);
-----
INSERT INTO thought_leaders values ('65C86878-0D78-457B-AAF0-3BC6C24D100F', 'techbro', 'Z34S9AoCAJyHcxYL:B71C5BC8B8357288817DA8505303A95D8725DA8E2C3E1E04183047EDBA56F659');
-----
INSERT INTO thought_leaders values ('69305B20-FF2D-4CCC-8866-17195F75DB9E', 'techdude', 'salt:6AB8814730EFC90559D7D20BFBC47DAB4CF252B46E05373854757237B8284743');
-----
INSERT INTO followers values ('65C86878-0D78-457B-AAF0-3BC6C24D100F', '69305B20-FF2D-4CCC-8866-17195F75DB9E');
-----
INSERT INTO twots values ('70C7244F-3AD6-456D-A74E-656FA988A7F8', '65C86878-0D78-457B-AAF0-3BC6C24D100F', 'WE MUST STOP WRITING BUGS', NOW());
-----
INSERT INTO twots values ('CB7A7392-0696-4EF4-A7F1-AD82B5D52068', '69305B20-FF2D-4CCC-8866-17195F75DB9E', 'YO STOP BEING DICKS LOL', NOW());
-----
INSERT INTO followers values ('69305b20-ff2d-4ccc-8866-17195f75db9e', '65c86878-0d78-457b-aaf0-3bc6c24d100f');
