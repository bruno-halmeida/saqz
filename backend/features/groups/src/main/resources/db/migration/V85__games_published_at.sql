-- Hora em que o jogo abriu para confirmação. A janela das 24 h só abre para jogo publicado antes
-- do T-24h; o trigger cobre todo caminho de escrita (criação, edição e séries) sem tocar no Kotlin.
ALTER TABLE games ADD COLUMN published_at timestamptz;
UPDATE games SET published_at = created_at WHERE status = 'PUBLISHED';

CREATE FUNCTION games_stamp_published_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status = 'PUBLISHED' AND NEW.published_at IS NULL THEN
        NEW.published_at := now();
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER games_published_at BEFORE INSERT OR UPDATE OF status ON games
    FOR EACH ROW EXECUTE FUNCTION games_stamp_published_at();
