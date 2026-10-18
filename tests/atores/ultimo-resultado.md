# Resultado dos atores por API

Gerado por `node tests/atores/run.mjs --regra ACE-002,SAI-009` em 2026-10-18T22:21:32.605Z (10.3 s).

**1 passaram · 1 falharam · 0 bloqueados** de 2.

| Regra | Crítica | Resultado | Tempo | Passo que falhou | Resposta do servidor |
| --- | :---: | --- | ---: | --- | --- |
| ACE-002 · Confirmar e-mail e telefone | sim | **falhou** | 1.3 s | [achado DEB-082] trocar o telefone zera a confirmação do telefone: divergências: ATL1, conta por e-mail sem confirmar, toca em "Vou": o servidor barra e a presença não vale (DEB-082): o "Vou" de conta não confirmada deveria dar 403 (veio HTTP 200: {"attendance":{"memberId":"286731f0-c825-44e2-8cd0-8760ddf9425b","status":"CONFIRMED","version":1,"waitlistPosition":nul) \| ATL1, conta por e-mail sem confirmar, toca em "Vou": o servidor barra e a presença não vale (DEB-082): o "Vou" de conta não confirmada não pode gravar presença (ficou CONFIRMED) | `PUT /api/session → HTTP 200 {"memberships":[],"planOwner":false,"user":{"accountVerified":false,"city":null,"displayName":"QA ATL6 mvee41tp3f66","email":"qa-atl6+mvee41tp3f66@saqz.local","emailVerified":false,"id":"465f11c9-c1a9-415f-9c27-3d1d44fa21b8","nickname":null,"phone":"+5511977776666","phone` |
| SAI-009 · Excluir a conta | sim | passou | 9.1 s |  |  |
