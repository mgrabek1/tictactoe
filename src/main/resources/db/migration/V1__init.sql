-- V1__Initial_schema.sql

-- 1) definiujemy natywne enum-y PostgreSQL
CREATE TYPE symbol AS ENUM ('X', 'O');
CREATE TYPE game_status AS ENUM ('WAITING', 'IN_PROGRESS', 'FINISHED');

-- 2) tworzymy tabelę game z wszystkimi kolumnami (w tym winner)
CREATE TABLE game (
  game_id    UUID         PRIMARY KEY,
  status     game_status  NOT NULL,
  next_turn  symbol,
  winner     symbol,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  version    BIGINT       NOT NULL DEFAULT 0
);

-- 3) tabela player
CREATE TABLE player (
  player_id UUID       PRIMARY KEY,
  game_id   UUID       REFERENCES game(game_id) ON DELETE CASCADE,
  name      VARCHAR(100),
  symbol    symbol,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 4) tabela move
CREATE TABLE move (
  move_id   UUID       PRIMARY KEY,
  game_id   UUID       REFERENCES game(game_id) ON DELETE CASCADE,
  player_id UUID       REFERENCES player(player_id),
  row       SMALLINT   NOT NULL,
  col       SMALLINT   NOT NULL,
  moved_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT unique_move UNIQUE(game_id, row, col)
);
