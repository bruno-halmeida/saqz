# Roteiro manual · Avisos e notificações

Gerado das seções **QA** das regras do cofre (`Obsidian Vault/Saqz/Regras`). A regra manda: se o roteiro e o cofre divergirem, vale o cofre.

Etiquetas: **[CRÍTICO]** entra na rodada segura; **[MULTI]** depende de duas ou mais pessoas e tem roteiro em `tests/multi-aparelhos/roteiros/`; **[push: só produção]** o ambiente local não tem push.

Rode **em iOS e em Android**: nas colunas *iOS* e *Android* troque `☐` por `✔` (passou) ou `✘` (falhou, com anotação). Onde a regra depende de pessoas diferentes, vale o roteiro multi-aparelho, com os papéis invertidos entre as plataformas (organizador no iPhone e depois no Android).

## NOT-001 · Como um aviso chega: central, push e WhatsApp [MULTI] [push: só produção]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Organizador publica no mural | Membros ativos recebem; push genérico; o autor não recebe | ☐ | ☐ |
| Jogo cancelado | Push com o texto | ☐ | ☐ |
| Pessoa saiu do grupo | Não recebe mais | ☐ | ☐ |
| Falha do serviço de push | Nova tentativa; ação original salva | ☐ | ☐ |
| Toque duplo | Um aviso só | ☐ | ☐ |
| Token recusado | Aparelho removido | ☐ | ☐ |
| Preferência desligada (alvo) | Aviso continua na central | ☐ | ☐ |

## NOT-002 · As preferências de cada pessoa [MULTI] [push: só produção]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Pessoa nova | Push todo ligado; WhatsApp de cobrança ligado | ☐ | ☐ |
| Desliga "Avisos dos grupos" | Sem push de mural etc.; **jogo cancelado e mudança de horário ainda chegam** (alvo) | ☐ | ☐ |
| Desliga "Mensagens" | Sem push de conversa | ☐ | ☐ |
| Desliga "Lembretes de cobrança" | Sem push de cobrança; WhatsApp segue a chave dele | ☐ | ☐ |
| Desliga o WhatsApp de cobrança | Sem WhatsApp; push segue a chave de push | ☐ | ☐ |

## NOT-003 · Avisos do jogo [CRÍTICO] [MULTI] [push: só produção]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Jogo publicado, sem resposta, 9h | Recebe o lembrete | ☐ | ☐ |
| Responde "Vou" | Não recebe mais | ☐ | ☐ |
| Jogo cancelado | Confirmados e espera recebem push com o texto | ☐ | ☐ |
| Mudou o horário | Todos, menos "Não vou", recebem push com o novo horário | ☐ | ☐ |
| Rascunho | Nenhum aviso | ☐ | ☐ |
| Jogo já começou | Nenhum lembrete | ☐ | ☐ |
| Lembretes por dia | No máximo 2 | ☐ | ☐ |

## NOT-004 · Avisos de pessoas [MULTI] [push: só produção]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Atleta pede para entrar | Dono e admins recebem; quem pediu, não | ☐ | ☐ |
| Pedido sem resposta por 2 dias | Segundo aviso, uma vez | ☐ | ☐ |
| Admin aprova | A pessoa recebe; o admin, não | ☐ | ☐ |
| Pedido recusado | Ninguém recebe | ☐ | ☐ |
| Dono promove atleta | A pessoa recebe; rebaixar, ninguém | ☐ | ☐ |
| Primeiro atleta entra | Dono e admins, uma vez só | ☐ | ☐ |
| Organizador tira alguém do jogo | A pessoa (ou anfitrião) recebe | ☐ | ☐ |
| Dono apaga o grupo | Todos os membros recebem | ☐ | ☐ |

## NOT-005 · Avisos de dinheiro [MULTI] [push: só produção]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Cobrar 3 pendentes | 3 avisos (push e WhatsApp) | ☐ | ☐ |
| Uma cobrada há 2 h | Pulada; app informa | ☐ | ☐ |
| Paga antes da entrega | Não sai | ☐ | ☐ |
| Devedor desligou push e WhatsApp | Só na central | ☐ | ☐ |
| Mesmo pedido duas vezes | Um aviso por cobrança | ☐ | ☐ |
| Mais de 200 selecionadas | Recusado | ☐ | ☐ |
| Atleta cobra | 403 | ☐ | ☐ |

## NOT-006 · Mural e conversas [MULTI] [push: só produção]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Atleta publica no mural | 403 | ☐ | ☐ |
| Atleta escreve na conversa | Publicado | ☐ | ☐ |
| Texto vazio ou com 2.001 caracteres | Recusado | ☐ | ☐ |
| Palavra proibida | Recusado com a mensagem do app | ☐ | ☐ |
| Mesma mensagem duas vezes | Uma só | ☐ | ☐ |
| Mesmo `requestId`, texto diferente | Conflito | ☐ | ☐ |
| A bloqueia B | A não vê mais B nem recebe push dele; B não percebe | ☐ | ☐ |
| A denuncia mensagem de B | E-mail à equipe; a mensagem some para A | ☐ | ☐ |
| Denunciar o próprio texto | Recusado | ☐ | ☐ |
| Quem saiu tenta denunciar | 404 | ☐ | ☐ |

## NOT-007 · O grupo do WhatsApp [MULTI] [push: só produção]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Convite válido | Vinculado e ativo | ☐ | ☐ |
| Nenhum admin do WhatsApp bate com membro do Saqz | Recusado | ☐ | ☐ |
| Mesmo grupo de WhatsApp em outro grupo Saqz | Recusado | ☐ | ☐ |
| Entrada pendente de aprovação no WhatsApp | "Aguardando aprovação" | ☐ | ☐ |
| Atleta tenta vincular | 403 | ☐ | ☐ |
| Aviso de mural | Aparece no WhatsApp | ☐ | ☐ |
| Lembrete | Aparece com botões | ☐ | ☐ |
| Mudança de horário | Aparece no WhatsApp (alvo) | ☐ | ☐ |
| Número removido do grupo de WhatsApp | Vínculo quebrado; pendentes canceladas | ☐ | ☐ |
| Provedor fora do ar | Tenta de novo | ☐ | ☐ |
