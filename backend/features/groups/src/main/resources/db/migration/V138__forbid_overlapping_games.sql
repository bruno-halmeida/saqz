-- DEB-048 (JOGO-009): dois jogos ativos do mesmo grupo não podem se sobrepor no tempo.
-- Antes só o início idêntico bloqueava (games_schedule_start_unique, V34/V40). "Encostados"
-- (um termina quando o outro começa) seguem permitidos: o intervalo é fechado à esquerda e
-- aberto à direita. Cancelado e encerrado ficam fora do predicado e liberam o horário.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- timestamptz + interval é STABLE; com minutos inteiros o resultado não depende do fuso,
-- então o wrapper é IMMUTABLE de verdade e pode entrar na expressão da restrição.
CREATE FUNCTION game_time_range(starts_at timestamptz, duration_minutes integer)
RETURNS tstzrange
LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$ SELECT tstzrange(starts_at, starts_at + duration_minutes * interval '1 minute', '[)') $$;

ALTER TABLE games DROP CONSTRAINT games_schedule_start_unique;

-- DEFERRABLE como a anterior: a regeneração da série troca horários de jogos existentes na
-- mesma transação (SET CONSTRAINTS ... DEFERRED em JdbcSeriesBoundaryRepository).
ALTER TABLE games
    ADD CONSTRAINT games_schedule_no_overlap
    EXCLUDE USING gist (group_id WITH =, game_time_range(starts_at, duration_minutes) WITH &&)
    WHERE (status IN ('DRAFT', 'PUBLISHED'))
    DEFERRABLE INITIALLY IMMEDIATE;
