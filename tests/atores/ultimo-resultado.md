# Resultado dos atores por API

Gerado por `node tests/atores/run.mjs --criticos` em 2026-10-18T22:22:26.928Z (31.6 s).

**20 passaram · 1 falharam · 0 bloqueados** de 21.

| Regra | Crítica | Resultado | Tempo | Passo que falhou | Resposta do servidor |
| --- | :---: | --- | ---: | --- | --- |
| ACE-001 · Entrar e criar conta | sim | passou | 0.2 s |  |  |
| ACE-002 · Confirmar e-mail e telefone | sim | **falhou** | 1.2 s | [achado DEB-082] divergências registradas durante o cenário: divergências: ATL1, conta por e-mail sem confirmar, toca em "Vou": o servidor barra e a presença não vale (DEB-082): o "Vou" de conta não confirmada deveria dar 403 (veio HTTP 200: {"attendance":{"memberId":"a1a2a581-6f29-49e2-ab3d-785654cc3fcb","status":"CONFIRMED","version":1,"waitlistPosition":nul) \| ATL1, conta por e-mail sem confirmar, toca em "Vou": o servidor barra e a presença não vale (DEB-082): o "Vou" de conta não confirmada não pode gravar presença (ficou CONFIRMED) | `PUT /api/session → HTTP 200 {"memberships":[],"planOwner":false,"user":{"accountVerified":false,"city":null,"displayName":"QA ATL6 mvee57x936f8","email":"qa-atl6+mvee57x936f8@saqz.local","emailVerified":false,"id":"c3534d48-74bd-4ce6-9324-6e14dbae7354","nickname":null,"phone":"+5511977776666","phone` |
| ASS-002 · O teste grátis | sim | passou | 0.8 s |  |  |
| ASS-003 · Sem plano ativo: o grupo fica congelado | sim | passou | 0.6 s |  |  |
| CONV-001 · O convite do grupo | sim | passou | 0.1 s |  |  |
| CONV-002 · Trocar o código | sim | passou | 0.2 s |  |  |
| CONV-007 · Aprovar o pedido de entrada | sim | passou | 0.6 s |  |  |
| FIN-001 · As cobranças: tipos, estados e quem vê | sim | passou | 0.5 s |  |  |
| FIN-002 · Dar baixa: Recebi, Dispensar e Cancelar | sim | passou | 0.4 s |  |  |
| FIN-004 · A diária do jogo | sim | passou | 0.8 s |  |  |
| JOGO-004 · Publicar | sim | passou | 2.9 s |  |  |
| JOGO-005 · Cancelar | sim | passou | 2.8 s |  |  |
| NOT-003 · Avisos do jogo | sim | passou | 4.7 s |  |  |
| PRES-002 · Início do jogo trava o atleta; o prazo nunca nega | sim | passou | 0.6 s |  |  |
| PRES-005 · "Vou" depois do prazo vai sempre para a espera | sim | passou | 4.0 s |  |  |
| PRES-006 · "Vou" antes do prazo, em ordem de chegada (prioridade do mensalista) | sim | passou | 1.0 s |  |  |
| PRES-009 · Repetir a mesma resposta não muda nada (toque duplo) | sim | passou | 1.1 s |  |  |
| PROM-001 · O que libera uma vaga | sim | passou | 1.7 s |  |  |
| PROM-002 · Quem sobe primeiro | sim | passou | 1.5 s |  |  |
| PROM-004 · Escolha manual: "Colocar no jogo" | sim | passou | 1.1 s |  |  |
| SAI-009 · Excluir a conta | sim | passou | 4.9 s |  |  |
