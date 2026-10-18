# Resultado dos atores por API

Gerado por `node tests/atores/run.mjs --regra NOT-001,NOT-004` em 2026-10-18T22:38:15.369Z (12.1 s).

**0 passaram · 2 falharam · 0 bloqueados** de 2.

| Regra | Crítica | Resultado | Tempo | Passo que falhou | Resposta do servidor |
| --- | :---: | --- | ---: | --- | --- |
| NOT-001 · Como um aviso chega: central, push e WhatsApp |  | **falhou** | 4.8 s | [achado DEB-064] divergências registradas durante o cenário: divergências: preferências desligadas só controlam push e WhatsApp: o aviso continua na central (DEB-064 — hoje desligar a chave tira o aviso): com a chave "Avisos dos grupos" desligada o aviso deveria continuar na central (tinha 3, tem 3) — DEB-064 |  |
| NOT-004 · Avisos de pessoas |  | **falhou** | 7.2 s | [achado FIRST_MEMBER-NA-APROVAÇÃO] divergências registradas durante o cenário: divergências: grupo com aprovação: o primeiro atleta aprovado também deveria gerar o aviso de primeiro atleta para o dono: aprovar o primeiro atleta de um grupo com aprovação deveria avisar o dono ("A galera começou a chegar"); recebeu 0 aviso(s) — o aviso só é disparado na entrada direta (RedeemInvite) |  |
