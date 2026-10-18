# Roteiro manual · Geral

Gerado das seções **QA** das regras do cofre (`Obsidian Vault/Saqz/Regras`). A regra manda: se o roteiro e o cofre divergirem, vale o cofre.

Etiquetas: **[CRÍTICO]** entra na rodada segura; **[MULTI]** depende de duas ou mais pessoas e tem roteiro em `tests/multi-aparelhos/roteiros/`; **[push: só produção]** o ambiente local não tem push.

Rode **em iOS e em Android**: nas colunas *iOS* e *Android* troque `☐` por `✔` (passou) ou `✘` (falhou, com anotação). Onde a regra depende de pessoas diferentes, vale o roteiro multi-aparelho, com os papéis invertidos entre as plataformas (organizador no iPhone e depois no Android).

## GER-001 · Toda ação com efeito ignora toque duplo

Para cada ação: tocar duas e três vezes rápido e conferir que acontece um só efeito (uma linha, um aviso, uma cobrança). Repetir
com rede lenta. Casos já conhecidos que a regra reabre: P1-15 ("Cobrar" manda dois avisos), WA-4c ("Avisar" sem trava) e BE-4b
(`VERSION_CONFLICT` ao cobrar cobrança paga).
