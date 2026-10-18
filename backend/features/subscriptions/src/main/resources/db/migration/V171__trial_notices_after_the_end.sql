-- DEB-078 (ASS-002): dois avisos depois do fim do teste, 3 e 14 dias depois, na mesma trava por marco do fim do teste.
-- O marco é o número de dias que faltam para o fim: -3 e -14 são "3 e 14 dias depois". Os marcos 3, 1 e 0 seguem iguais.
ALTER TABLE organizer_trial_notices DROP CONSTRAINT organizer_trial_notices_milestone_check;
ALTER TABLE organizer_trial_notices ADD CONSTRAINT organizer_trial_notices_milestone_check
    CHECK (milestone IN (-14, -3, 0, 1, 3));
