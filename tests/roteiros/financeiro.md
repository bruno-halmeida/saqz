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
| Pedido aceito como mensalista no dia 20, regra "mês seguinte" | Nenhuma cobrança neste mês | ☐ | ☐ |
| Mensalista isento, job roda | Nada | ☐ | ☐ |
| Geração manual do mês | O isento não aparece e não recebe | ☐ | ☐ |
| Isenção sai no dia 20, regra "mês seguinte" | Nenhuma cobrança neste mês; volta no próximo | ☐ | ☐ |
| Isenção sai no dia 3, vencimento dia 10 | Mensalidade inteira do mês, no dia 10 | ☐ | ☐ |
| Isenta (pendente do mês dispensada) e tira no mesmo mês | A dispensada fica; nada novo no mês | ☐ | ☐ |

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

_Linhas novas decididas em 28/10/2026 (DEB-146 e DEB-150): rodar só depois da entrega._

| Situação | Cobrança | iOS | Android |
| --- | --- | --- | --- |
| Jogo Sem diária, avulso confirma | Nenhuma | ☐ | ☐ |
| Avulso na espera, grupo com pagamento antecipado (PROM-010) | Pendente desde o "Vou", vence na data do jogo | ☐ | ☐ |

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

_Linhas novas decididas em 28 e 29/10/2026 (DEB-148, caixa novo): rodar só depois da entrega._

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Caixa zerado, primeira vez | Pergunta "Quanto o grupo já tem em caixa?" com Salvar saldo inicial e Começar do zero | ☐ | ☐ |
| Salva saldo inicial de R$ 250 | Saldo + R$ 250; fixo no topo do extrato, com Editar | ☐ | ☐ |
| Edita o saldo inicial de R$ 250 para R$ 300 (extrato ou ⋯ do caixa) | Saldo sobe R$ 50 | ☐ | ☐ |
| Saldo inicial negativo | Recusado | ☐ | ☐ |
| "Começar do zero" | Pergunta some; saldo inicial R$ 0 | ☐ | ☐ |
| Caixa: botões | Entrada e Saída no lugar do "Registrar" | ☐ | ☐ |
| Placar Entrou · Saiu · Falta receber | Cada número troca a lista de baixo; Saiu em vermelho | ☐ | ☐ |
| Devedor na lista do caixa | Recebi e o ⋮ com Cobrar, Dispensar e Cancelar | ☐ | ☐ |
| Menu ⋯ do caixa | Saldo inicial e Criar cobranças do mês | ☐ | ☐ |
| Caixa novo | Sem o cartão do Pix (o Pix está na página do grupo) | ☐ | ☐ |
| Nova entrada | Patrocínio, Venda e Outros; sem atalhos de valor | ☐ | ☐ |
| Nova saída | Quadra, Material, Árbitro e Outros; atalhos R$ 80, 120 e 160 | ☐ | ☐ |
| Data da saída | Vem com hoje; o toque abre o calendário | ☐ | ☐ |
| Saída com outro dia escolhido | Entra no extrato nesse dia | ☐ | ☐ |
| Saída antiga de Racha | Continua "Racha" no extrato | ☐ | ☐ |
| Extrato | Filtro Tudo · Entradas · Saídas; valores de saída em vermelho | ☐ | ☐ |
| Atleta mexe no saldo inicial | 403 | ☐ | ☐ |

## FIN-007 · O acerto do jogo

| Situação | Mostra | iOS | Android |
| --- | --- | --- | --- |
| 3 diárias de R$ 20, 2 recebidas | Recebido R$ 40, falta R$ 20 | ☐ | ☐ |
| Uma dispensada | Sai do "falta" e do recebido | ☐ | ☐ |
| Atleta abre | Recusado | ☐ | ☐ |
| Dois jogos no mesmo dia, uma saída de quadra | Nenhum dos acertos mostra custo | ☐ | ☐ |

_Tela nova aprovada em 29/10/2026 (DEB-147 e DEB-150): rodar só depois da entrega._

| Situação | Mostra | iOS | Android |
| --- | --- | --- | --- |
| 12 jogaram, 2 de 4 diárias pagas | Cabeçalho azul; placar 12 · 2/4 · falta R$ 40; mensalistas numa linha | ☐ | ☐ |
| Avulso na lista | "Avulso", nunca "Diarista"; Recebi e o ⋮ | ☐ | ☐ |
| Jogo com gente sem cobrança | Faixa "N jogaram sem cobrança" com Revisar e a seção Sem cobrança | ☐ | ☐ |
| Avulso com diária antecipada que ficou na espera | Em "Não jogaram · você decide", com "Não liberado" | ☐ | ☐ |
| Avulso com diária que disse "Não vou" | Em "Não jogaram", com "Desistiu" | ☐ | ☐ |
| Lançar da quadra | Saída com Quadra e a data do jogo, sem valor | ☐ | ☐ |
| Cobrar os N que faltam | No rodapé | ☐ | ☐ |

## FIN-008 · O que o atleta vê [MULTI]

| Situação | O atleta vê | iOS | Android |
| --- | --- | --- | --- |
| 2 pendentes | Aviso na Início com o total e a lista | ☐ | ☐ |
| Uma vencida | Aviso destaca o atraso | ☐ | ☐ |
| Tudo pago | Sem aviso; histórico na lista | ☐ | ☐ |
| Abre o caixa do grupo | 403 | ☐ | ☐ |
| Convidado dele | Cobrança no nome dele, com o nome do convidado | ☐ | ☐ |
| Grupo sem chave Pix, cobrança pendente | Organizador vê o aviso para cadastrar | ☐ | ☐ |

_Pix na página do grupo, decidido em 29/10/2026 (DEB-148): rodar só depois da entrega._

| Situação | O que aparece | iOS | Android |
| --- | --- | --- | --- |
| Membro sem cobrança, grupo com chave | Seção "Pix do grupo" depois de Minhas cobranças, com a chave, o tipo e Copiar | ☐ | ☐ |
| Copiar na seção | Copia a chave guardada (CPF e telefone só com dígitos) | ☐ | ☐ |
| Membro com cobrança em aberto | Chave só no cartão da cobrança; sem a seção | ☐ | ☐ |
| Dono ou admin sem cobrança | Seção "Pix do grupo", depois da Galera | ☐ | ☐ |
| Grupo com recebimentos ativos | Sem a seção | ☐ | ☐ |
| Grupo sem chave | Sem a seção para o membro; gestor com o aviso e a checklist de hoje | ☐ | ☐ |

## FIN-016 · Quem jogou sem cobrança: a diária retroativa [CRÍTICO]

_Decidida em 28/10/2026 (DEB-147, diária retroativa): rodar só depois da entrega._

Preparação para ter gente sem cobrança num jogo novo: marcar o jogo como Sem diária, deixar avulsos confirmarem e depois trocar para Cobrar diária. Grupo antigo com jogo sem valor serve do mesmo jeito.

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Jogo realizado sem valor, 6 avulsos confirmados | Faixa "6 jogaram sem cobrança" no acerto; revisão com os 6 em Cobrar e o valor do grupo | ☐ | ☐ |
| Confirma com os 6 em Cobrar, R$ 10 | 6 diárias pendentes de R$ 10, vencendo hoje; faixa some | ☐ | ☐ |
| 1 em Já pagou, meio trocado para Dinheiro | Diária recebida com a data do jogo e meio Dinheiro; entra no caixa nesse dia | ☐ | ☐ |
| 2 em Não cobrar | Nenhuma cobrança; atleta não vê nada; ficam em "Sem cobrança" no acerto | ☐ | ☐ |
| Reabre um Não cobrar e escolhe Cobrar | Diária pendente vencendo no dia | ☐ | ☐ |
| Resumo antes de confirmar | "2 cobranças · 1 já pagou · 2 sem cobrança" | ☐ | ☐ |
| Mensalista confirmado | Não aparece | ☐ | ☐ |
| Mensalista com 1 convidado | Convidado em Cobrar, no nome do mensalista | ☐ | ☐ |
| Avulso marcado Faltou ou que saiu do grupo | Em Não cobrar, com a etiqueta | ☐ | ☐ |
| Valor entrou depois de 10 confirmados (jogo aberto) | Faixa com os avulsos entre os 10; quem confirmar depois já nasce cobrado | ☐ | ☐ |
| Dois jogos com gente sem cobrança | Faixa no caixa soma os dois e leva à lista dos jogos | ☐ | ☐ |
| Jogo Sem diária ou cancelado | Sem faixa | ☐ | ☐ |
| Toque duplo em Confirmar, ou dois gestores juntos | Uma diária por pessoa | ☐ | ☐ |
| Atleta tenta | 403 | ☐ | ☐ |
| Confirmar | Nenhum push ou WhatsApp ao atleta | ☐ | ☐ |
