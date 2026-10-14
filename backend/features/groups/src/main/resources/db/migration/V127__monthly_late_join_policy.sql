-- O que cobrar de quem vira mensalista depois do dia de vencimento do mês. O padrão é o comportamento de
-- sempre: a mensalidade do mês nasce vencida no dia de vencimento do grupo.
--   GROUP_DUE_DATE: cobra o mês atual com o vencimento do grupo (já entra em atraso)
--   JOIN_DATE:      cobra o mês atual com vencimento no dia em que a cobrança é gerada (o mais tarde entre o dia em
--                   que a pessoa virou mensalista e o da geração, que roda de madrugada): nunca nasce atrasada
--   NEXT_MONTH:     a primeira mensalidade é a do mês seguinte
ALTER TABLE access_groups
    ADD COLUMN monthly_late_join_policy varchar(32) NOT NULL DEFAULT 'GROUP_DUE_DATE',
    ADD CONSTRAINT ck_access_groups_monthly_late_join_policy
        CHECK (monthly_late_join_policy IN ('GROUP_DUE_DATE', 'JOIN_DATE', 'NEXT_MONTH'));

-- Desde quando a pessoa é mensalista (no dia do fuso do grupo). Fica nula para quem não é mensalista.
ALTER TABLE group_memberships ADD COLUMN mensalista_since date;

UPDATE group_memberships SET mensalista_since = created_at::date WHERE membership_type = 'MENSALISTA';

CREATE FUNCTION set_mensalista_since() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v_zone varchar;
BEGIN
    IF NEW.membership_type IS DISTINCT FROM 'MENSALISTA' THEN
        NEW.mensalista_since := NULL;
        RETURN NEW;
    END IF;
    -- Começa a contar ao entrar como mensalista, ao virar mensalista e ao voltar ao grupo.
    IF TG_OP = 'INSERT' THEN
        IF NEW.mensalista_since IS NOT NULL THEN RETURN NEW; END IF;
    ELSIF OLD.membership_type IS NOT DISTINCT FROM 'MENSALISTA' AND NOT (NEW.active AND NOT OLD.active) THEN
        RETURN NEW;
    END IF;
    SELECT g.time_zone INTO v_zone FROM access_groups g WHERE g.id = NEW.group_id;
    NEW.mensalista_since := (now() AT TIME ZONE COALESCE(v_zone, 'UTC'))::date;
    RETURN NEW;
END;
$$;
CREATE TRIGGER group_memberships_mensalista_since BEFORE INSERT OR UPDATE OF membership_type, active ON group_memberships
    FOR EACH ROW EXECUTE FUNCTION set_mensalista_since();
