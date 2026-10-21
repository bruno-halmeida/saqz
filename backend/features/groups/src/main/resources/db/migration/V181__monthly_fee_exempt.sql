-- Isenção da mensalidade (DEB-115): o mensalista isento continua mensalista (prioridade, sem diária) e só deixa de
-- receber mensalidade. Só mensalista pode ser isento.
ALTER TABLE group_memberships
    ADD COLUMN monthly_fee_exempt boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT ck_group_memberships_monthly_fee_exempt
        CHECK (NOT monthly_fee_exempt OR membership_type = 'MENSALISTA');

-- Mesma função da V127, agora também cuidando da isenção: quem deixa de ser mensalista ou volta ao grupo fica sem
-- isenção, e quem perde a isenção conta como mensalista a partir de hoje (política de entrada tardia).
CREATE OR REPLACE FUNCTION set_mensalista_since() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v_zone varchar;
BEGIN
    IF NEW.membership_type IS DISTINCT FROM 'MENSALISTA' THEN
        NEW.mensalista_since := NULL;
        NEW.monthly_fee_exempt := false;
        RETURN NEW;
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.active AND NOT OLD.active THEN
        NEW.monthly_fee_exempt := false;
    END IF;
    -- Começa a contar ao entrar como mensalista, ao virar mensalista, ao voltar ao grupo e ao perder a isenção.
    IF TG_OP = 'INSERT' THEN
        IF NEW.mensalista_since IS NOT NULL THEN RETURN NEW; END IF;
    ELSIF OLD.membership_type IS NOT DISTINCT FROM 'MENSALISTA' AND NOT (NEW.active AND NOT OLD.active)
        AND NOT (OLD.monthly_fee_exempt AND NOT NEW.monthly_fee_exempt) THEN
        RETURN NEW;
    END IF;
    SELECT g.time_zone INTO v_zone FROM access_groups g WHERE g.id = NEW.group_id;
    NEW.mensalista_since := (now() AT TIME ZONE COALESCE(v_zone, 'UTC'))::date;
    RETURN NEW;
END;
$$;
DROP TRIGGER group_memberships_mensalista_since ON group_memberships;
CREATE TRIGGER group_memberships_mensalista_since
    BEFORE INSERT OR UPDATE OF membership_type, active, monthly_fee_exempt ON group_memberships
    FOR EACH ROW EXECUTE FUNCTION set_mensalista_since();
