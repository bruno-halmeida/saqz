# Resultado dos atores por API

Gerado por `node tests/atores/run.mjs --regra DEB-102-A,DEB-102-B,DEB-102-C,DEB-102-D,DEB-102-E` em 2026-10-20T12:56:29.537Z (4.7 s).

**5 passaram · 0 falharam · 0 bloqueados** de 5.

| Regra | Crítica | Resultado | Tempo | Passo que falhou | Resposta do servidor |
| --- | :---: | --- | ---: | --- | --- |
| DEB-102-A · Configuração por posição: sugestão pelo total, 5-1 exige posições com a soma certa, só dono/admin grava, o total do jogo é a soma e jogo antigo não muda | sim | passou | 1.5 s |  |  |
| DEB-102-B · Posição cheia = espera mesmo com vaga no jogo; a vaga que abre sobe só quem espera na mesma posição | sim | passou | 1.1 s |  |  |
| DEB-102-C · App antigo (sem position) em grupo com posições: principal do perfil, depois a mais livre; sem posições nada muda | sim | passou | 1.0 s |  |  |
| DEB-102-D · Organizador força a entrada em posição cheia (fica acima das vagas, sinalizado) e quem convida escolhe a posição do convidado |  | passou | 0.6 s |  |  |
| DEB-102-E · Disputa pela última vaga de uma posição: em paralelo, só um confirma e os outros esperam |  | passou | 0.5 s |  |  |
