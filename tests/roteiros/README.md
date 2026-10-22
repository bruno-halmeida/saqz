# Roteiros manuais por regra

Um arquivo por feature, gerado das tabelas de QA do cofre. Complementa `tests/producao` (jornadas em produção) e `tests/multi-aparelhos` (várias pessoas ao mesmo tempo, com agentes).

| Feature | Regras | Críticas | Multi-aparelho |
| --- | ---: | ---: | ---: |
| [Acesso e conta](acesso-e-conta.md) | 6 | 2 | 2 |
| [Assinatura e teste grátis](assinatura-e-teste-gratis.md) | 6 | 2 | 1 |
| [Avisos e notificações](avisos-e-notificacoes.md) | 8 | 1 | 8 |
| [Convites e entrada no grupo](convites-e-entrada-no-grupo.md) | 11 | 5 | 11 |
| [Financeiro](financeiro.md) | 8 | 3 | 6 |
| [Geral](geral.md) | 2 | 0 | 0 |
| [Jogos](jogos.md) | 12 | 2 | 8 |
| [Link de presença do WhatsApp](link-de-presenca-do-whatsapp.md) | 6 | 0 | 5 |
| [Membros e papéis](membros-e-papeis.md) | 5 | 0 | 4 |
| [Presença e lista de espera](presenca-e-lista-de-espera.md) | 23 | 7 | 21 |
| [Saída do grupo e exclusão de conta](saida-do-grupo-e-exclusao-de-conta.md) | 9 | 1 | 9 |

## Como rodar

- **Rodada segura:** só as regras com **[CRÍTICO]**.
- **Rodada completa:** todas.
- Regras **[MULTI]** têm roteiro com agentes em `tests/multi-aparelhos/roteiros/` (ORG, ATL, ATL2, ATL3).
- **[push: só produção]**: o stack local não tem FCM nem APNs; conferir o push em produção (`tests/producao`).
- **[WhatsApp: só produção]**: o stack local não manda WhatsApp (só o link para copiar) ou usa o Uazapi falso do roteiro multi-aparelho, que só grava; a mensagem chegar ao aparelho se confere em produção, com números de teste (`tests/producao`).
- **Regras ainda não implementadas** (CONV-010 a CONV-012, DEB-106) trazem a nota "rodar só depois da entrega". O mesmo vale para as linhas de "aceitar o pedido como mensalista ou avulso" (DEB-112) em CONV-007, MEM-003, NOT-004 e FIN-003.
- **iOS e Android:** toda regra é executada nas duas plataformas (colunas *iOS* e *Android*). Nos roteiros multi-aparelho, rodar duas vezes: ORG no iPhone com ATL no Android, e ORG no Android com ATL no iPhone.
- Registrar o resultado nas colunas *iOS* e *Android* e levar as falhas ao cofre (`Débitos.md`).