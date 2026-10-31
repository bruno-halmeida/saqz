# Roteiro manual · Avisos e notificações

Gerado das seções **QA** das regras do cofre (`Obsidian Vault/Saqz/Regras`). A regra manda: se o roteiro e o cofre divergirem, vale o cofre.

Etiquetas: **[CRÍTICO]** entra na rodada segura; **[MULTI]** depende de duas ou mais pessoas e tem roteiro em `tests/multi-aparelhos/roteiros/`; **[push: só produção]** o ambiente local não tem push; **[WhatsApp: só produção]** o ambiente local não manda WhatsApp (só o link para copiar, ou o Uazapi falso do roteiro multi-aparelho); a mensagem chegar ao aparelho se confere em produção, com números de teste.

Rode **em iOS e em Android**: nas colunas *iOS* e *Android* troque `☐` por `✔` (passou) ou `✘` (falhou, com anotação). Onde a regra depende de pessoas diferentes, vale o roteiro multi-aparelho, com os papéis invertidos entre as plataformas (organizador no iPhone e depois no Android).

## NOT-001 · Como um aviso chega: central, push e WhatsApp [MULTI] [push: só produção] [WhatsApp: só produção]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Organizador publica no mural | Membros ativos recebem; push genérico; o autor não recebe | ☐ | ☐ |
| Jogo cancelado | Push com o texto | ☐ | ☐ |
| Pessoa saiu do grupo | Não recebe mais | ☐ | ☐ |
| Falha do serviço de push | Nova tentativa; ação original salva | ☐ | ☐ |
| Toque duplo | Um aviso só | ☐ | ☐ |
| Token recusado | Aparelho removido | ☐ | ☐ |
| Preferência desligada (alvo) | Aviso continua na central | ☐ | ☐ |
| Preferência desligada | Aviso continua na central | ☐ | ☐ |
| Alguém entra pelo link de mensalistas | Dono e admins recebem na central, no push e no WhatsApp; quem entrou, não | ☐ | ☐ |
| Falha do WhatsApp nesse aviso | Nova tentativa; a entrada da pessoa segue valendo | ☐ | ☐ |

## NOT-002 · As preferências de cada pessoa [MULTI] [push: só produção] [WhatsApp: só produção]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Pessoa nova | Push todo ligado; WhatsApp de cobrança ligado | ☐ | ☐ |
| Desliga "Avisos dos grupos" | Sem push de mural etc.; **jogo cancelado e mudança de horário ainda chegam** (alvo) | ☐ | ☐ |
| Desliga "Mensagens" | Sem push de conversa | ☐ | ☐ |
| Desliga "Lembretes de cobrança" | Sem push de cobrança; WhatsApp segue a chave dele | ☐ | ☐ |
| Desliga o WhatsApp de cobrança | Sem WhatsApp; push segue a chave de push | ☐ | ☐ |
| Dono com "Avisos dos grupos" desligado; alguém entra pelo link de mensalistas | Sem push e sem WhatsApp desse aviso; ele fica na central | ☐ | ☐ |
| Dono com número confirmado; alguém entra pelo link de mensalistas | Central, push e WhatsApp | ☐ | ☐ |
| Dono com número só cadastrado (não confirmado); alguém entra pelo link de mensalistas | Central, push e WhatsApp (exceção à confirmação do número) | ☐ | ☐ |
| Dono sem telefone cadastrado; alguém entra pelo link de mensalistas | Central e push; sem WhatsApp | ☐ | ☐ |
| Cobrança para quem não confirmou o número | Segue sem WhatsApp (a regra de confirmação não mudou) | ☐ | ☐ |
| Mural, lembrete ou pedido de entrada comum | Continuam sem WhatsApp particular | ☐ | ☐ |

## NOT-003 · Avisos do jogo [CRÍTICO] [MULTI] [push: só produção]

_As linhas da janela de presença (a partir de "Janela ligada") foram decididas em 20/10 e valem só depois da entrega do DEB-113; com a flag desligada, nenhum cartão abre._

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Jogo publicado, sem resposta, 9h | Recebe o lembrete | ☐ | ☐ |
| Responde "Vou" | Não recebe mais | ☐ | ☐ |
| Jogo cancelado | Confirmados e espera recebem push com o texto | ☐ | ☐ |
| Mudou o horário | Todos, menos "Não vou", recebem push com o novo horário | ☐ | ☐ |
| Rascunho | Nenhum aviso | ☐ | ☐ |
| Jogo já começou | Nenhum lembrete | ☐ | ☐ |
| Lembretes por dia | No máximo 3 (9h, 12h30 e 18h) | ☐ | ☐ |
| Janela ligada, jogo publicado, sem resposta | 8 h antes do prazo de confirmação abre o cartão ao vivo com a contagem de vagas, "Confirmar" e "Não vou" | ☐ | ☐ |
| Jogo publicado já dentro das 8 h antes do prazo | O cartão abre na primeira passada do job | ☐ | ☐ |
| Já respondeu ("Vou", confirmado ou na espera, ou "Não vou") antes da janela | Não recebe o cartão nem o push da janela | ☐ | ☐ |
| Responde depois de o cartão abrir | O cartão encerra | ☐ | ☐ |
| Chega o prazo de confirmação, Android | O cartão some | ☐ | ☐ |
| Chega o prazo de confirmação, iPhone | O cartão troca para "Janela encerrada. Confirme pelo app."; sai da Dynamic Island em 8 h e fica na tela bloqueada por até 4 h, ou até arrastar | ☐ | ☐ |
| Jogo hoje, amanhã ou em outro dia (iPhone 1.0.10) | Título "Hoje às 19:30", "Amanhã às 19:30" ou "<dia da semana> às 19:30" | ☐ | ☐ |
| Vagas mudam depois de o cartão abrir | A contagem do cartão continua a da abertura | ☐ | ☐ |
| iPhone com o app até a 1.0.9 | Mostra "Amanhã às" mesmo quando o jogo é hoje | ☐ | ☐ |
| Jogo 5-1, toca "Confirmar" no cartão | Abre o app na tela de escolher posição; nada é confirmado por baixo | ☐ | ☐ |
| Jogo cancelado ou já iniciado | O cartão não abre (ou encerra) | ☐ | ☐ |
| Flag da janela desligada (produção até o OK do dono) | Nenhum cartão e nenhum push da janela | ☐ | ☐ |

## NOT-004 · Avisos de pessoas [MULTI] [push: só produção] [WhatsApp: só produção]

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
| Alguém entra pelo link de mensalistas | Dono e admins recebem na central, no push e no WhatsApp; quem entrou, não | ☐ | ☐ |
| Entra pelo link, grupo sem mensalidade padrão | Aviso "…como avulso, porque o grupo está sem mensalidade padrão." | ☐ | ☐ |
| Avulso do grupo abre o link | Aviso "virou mensalista" | ☐ | ☐ |
| Mensalista abre o link de novo | Ninguém recebe | ☐ | ☐ |
| Avulso abre o link com o grupo sem mensalidade padrão | Ninguém recebe | ☐ | ☐ |
| Primeiro atleta do grupo entra pelo link | Um aviso só (o de entrada pelo link); "Primeiro atleta" não repete | ☐ | ☐ |
| Dono ou admin com telefone cadastrado e não confirmado | Recebe central, push e WhatsApp (exceção) | ☐ | ☐ |
| Dono ou admin sem telefone cadastrado | Recebe central e push; sem WhatsApp | ☐ | ☐ |
| 3 convidados entram em sequência | 3 avisos separados a cada organizador | ☐ | ☐ |
| Admin aceita como mensalista | A pessoa recebe "Você entrou no <Grupo> como mensalista."; o admin, não | ☐ | ☐ |

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

## NOT-008 · A lista no grupo do WhatsApp: por horário ou a cada confirmação [MULTI]

_Decidida em 22/10/2026; vale depois da entrega do DEB-127 (app 1.0.12 onde houver tela)._

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Grupo sem mudar nada | 3 listas por dia no grupo do WhatsApp, como antes | ☐ | ☐ |
| Dono troca para "A cada confirmação" (configurações do grupo) | Admin não vê o seletor; atleta não vê | ☐ | ☐ |
| A cada confirmação, alguém confirma | Lista no grupo (se passou 15 min da última) | ☐ | ☐ |
| Segunda mudança 5 min depois | Não sai; sai na próxima passada depois dos 15 min | ☐ | ☐ |
| 13ª mudança no dia | Não sai; o contador zera no dia seguinte | ☐ | ☐ |
| Mudança às 23h | Não sai; vai às 8h se a lista ainda mudou | ☐ | ☐ |
| A cada confirmação, 9h/12h30/18h | Push e central como sempre; nada no grupo do WhatsApp por horário | ☐ | ☐ |
| A cada confirmação, chega 9h sem lista no dia, faltando até 4 dias para o jogo | Sai 1 lista no grupo mesmo sem mudança | ☐ | ☐ |
| A cada confirmação, já saiu lista no dia antes das 9h | Nada a mais às 9h; mudanças seguem saindo | ☐ | ☐ |
| A cada confirmação, jogo a mais de 4 dias, sem mudança | Nada no grupo | ☐ | ☐ |
| "Avisar" do organizador | Vai ao grupo nos dois modos | ☐ | ☐ |
| Jogo 6-0 com 6 de 12 | Linhas 1 a 6 com nomes, 7 a 12 só com o número | ☐ | ☐ |
| Jogo 5-1 | Uma seção por posição, vagas numeradas dentro dela | ☐ | ☐ |
| Espera em FIFO sem prioridade de mensalista | Numerada | ☐ | ☐ |
| Espera com prioridade de mensalista ou escolha manual | Sem número | ☐ | ☐ |
| Jogo sem sistema de jogo (legado) | Formato de antes, sem numeração | ☐ | ☐ |
