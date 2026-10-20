# Roteiro manual · Financeiro

Gerado das seções **QA** das regras do cofre (`Obsidian Vault/Saqz/Regras`). A regra manda: se o roteiro e o cofre divergirem, vale o cofre.

Etiquetas: **[CRÍTICO]** entra na rodada segura; **[MULTI]** depende de duas ou mais pessoas e tem roteiro em `tests/multi-aparelhos/roteiros/`; **[push: só produção]** o ambiente local não tem push.

Rode **em iOS e em Android**: nas colunas *iOS* e *Android* troque `☐` por `✔` (passou) ou `✘` (falhou, com anotação). Onde a regra depende de pessoas diferentes, vale o roteiro multi-aparelho, com os papéis invertidos entre as plataformas (organizador no iPhone e depois no Android).

## FIN-001 · As cobranças: tipos, estados e quem vê [CRÍTICO] [MULTI]

| Quem | Vê | iOS | Android |
| --- | --- | --- | --- |
| Dono ou admin | Todas as cobranças do grupo, com totais | ☐ | ☐ |
| Atleta | Só as próprias, sem totais | ☐ | ☐ |
| Quem não é membro | Nada (404) | ☐ | ☐ |

| De | Para | Resultado | iOS | Android |
| --- | --- | --- | --- | --- |
| Pendente | Recebida, dispensada ou cancelada | Permitido | ☐ | ☐ |
| Recebida ou dispensada | Pendente | Permitido (desfazer, FIN-002) | ☐ | ☐ |
| Cancelada | Qualquer | Recusado | ☐ | ☐ |

## FIN-002 · Dar baixa: Recebi, Dispensar e Cancelar [CRÍTICO] [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| "Recebi" sem dizer o jeito | Recebida por Pix | ☐ | ☐ |
| "Recebi" com Dinheiro | Recebida por dinheiro | ☐ | ☐ |
| Nota com 1 caractere | Recusada | ☐ | ☐ |
| Baixa em cobrança já recebida | Recusada; desfazer primeiro | ☐ | ☐ |
| Atleta tenta dar baixa | 403 | ☐ | ☐ |
| Dois organizadores, mesma cobrança | Um passa, o outro recebe conflito | ☐ | ☐ |
| Desfazer recebida | Volta a pendente e sai do caixa | ☐ | ☐ |
| Desfazer cancelada | Recusado | ☐ | ☐ |

## FIN-003 · A mensalidade automática [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Mensalista sem cobrança do mês, job roda | Mensalidade pendente | ☐ | ☐ |
| Job roda de novo no dia | Nada novo | ☐ | ☐ |
| Entra no dia 20, regra "mês seguinte" | Nenhuma cobrança neste mês | ☐ | ☐ |
| Avulso | Não ganha mensalidade | ☐ | ☐ |
| Mensalista em atraso | Mantém a prioridade | ☐ | ☐ |
| Entra pelo link de mensalistas no dia 20, regra "mês seguinte" | Nenhuma cobrança neste mês | ☐ | ☐ |
| Entra pelo link de mensalistas, regra "data de entrada" | Paga já, a partir do dia em que entrou | ☐ | ☐ |
| Entra pelo link de mensalistas, regra "vencimento do grupo" | Mensalidade inteira, no vencimento | ☐ | ☐ |
| Entra pelo link de mensalistas, grupo sem mensalidade padrão | Entra como avulso; nenhuma mensalidade | ☐ | ☐ |
| Avulso vira mensalista pelo link | O dia vira o início de mensalista; segue a regra de entrada tardia | ☐ | ☐ |

## FIN-004 · A diária do jogo [CRÍTICO] [MULTI]

| Situação | Cobrança | iOS | Android |
| --- | --- | --- | --- |
| Avulso confirma em jogo com diária | Pendente, vence na data do jogo | ☐ | ☐ |
| Avulso na espera | Nenhuma | ☐ | ☐ |
| Avulso promovido | Nasce na promoção | ☐ | ☐ |
| Mensalista confirma | Nenhuma | ☐ | ☐ |
| Mensalista leva 1 convidado | 1 diária no nome dele | ☐ | ☐ |
| Jogo sem valor | Nenhuma | ☐ | ☐ |
| Convidado removido, pendente | Cancelada | ☐ | ☐ |
| Jogo cancelado, pendente | Cancelada | ☐ | ☐ |
| Jogo cancelado, recebida | Continua recebida, com "revisar" | ☐ | ☐ |
| Valor do jogo muda depois | Antigas mantêm o valor | ☐ | ☐ |

## FIN-005 · Cobrar": o lembrete ao devedor [MULTI] [push: só produção]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Cobrar em pendente | Push ao devedor | ☐ | ☐ |
| Cobrar de novo 1 h depois | Recusado, com o tempo restante | ☐ | ☐ |
| Cobrar 13 h depois | Push | ☐ | ☐ |
| Cobrança recebida | Recusado | ☐ | ☐ |
| Atleta tenta cobrar | 403 | ☐ | ☐ |

## FIN-006 · Caixa e extrato

| Situação | Saldo | iOS | Android |
| --- | --- | --- | --- |
| Cobrança de R$ 20 recebida | + R$ 20 | ☐ | ☐ |
| Saída de R$ 100 (quadra) | - R$ 100 | ☐ | ☐ |
| Cobrança dispensada ou cancelada | Não muda | ☐ | ☐ |
| Lançamento anulado | Sai da conta, continua visível | ☐ | ☐ |
| Lançamento corrigido | Saldo usa o valor novo; histórico guarda a edição | ☐ | ☐ |
| Atleta abre o caixa | 403 | ☐ | ☐ |

## FIN-007 · O acerto do jogo

| Situação | Mostra | iOS | Android |
| --- | --- | --- | --- |
| 3 diárias de R$ 20, 2 recebidas | Recebido R$ 40, falta R$ 20 | ☐ | ☐ |
| Uma dispensada | Sai do "falta" e do recebido | ☐ | ☐ |
| Atleta abre | Recusado | ☐ | ☐ |
| Dois jogos no mesmo dia, uma saída de quadra | Nenhum dos acertos mostra custo | ☐ | ☐ |

## FIN-008 · O que o atleta vê [MULTI]

| Situação | O atleta vê | iOS | Android |
| --- | --- | --- | --- |
| 2 pendentes | Aviso na Início com o total e a lista | ☐ | ☐ |
| Uma vencida | Aviso destaca o atraso | ☐ | ☐ |
| Tudo pago | Sem aviso; histórico na lista | ☐ | ☐ |
| Abre o caixa do grupo | 403 | ☐ | ☐ |
| Convidado dele | Cobrança no nome dele, com o nome do convidado | ☐ | ☐ |
| Grupo sem chave Pix, cobrança pendente | Organizador vê o aviso para cadastrar | ☐ | ☐ |
