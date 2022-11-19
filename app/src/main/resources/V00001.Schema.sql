CREATE TABLE thought_leaders(
  thought_leader_id uuid PRIMARY KEY,
  nickname character varying(50) not null,
  salted_hash character(81) not null, -- 64 for the hash + 16 for the salt + ':',
  UNIQUE(nickname)
);
-----
CREATE UNIQUE INDEX thought_leaders_nickname_idx ON thought_leaders (LOWER(nickname));
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
