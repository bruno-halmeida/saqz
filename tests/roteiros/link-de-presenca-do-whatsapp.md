# Roteiro manual · Link de presença do WhatsApp

Gerado das seções **QA** das regras do cofre (`Obsidian Vault/Saqz/Regras`). A regra manda: se o roteiro e o cofre divergirem, vale o cofre.

Etiquetas: **[CRÍTICO]** entra na rodada segura; **[MULTI]** depende de duas ou mais pessoas e tem roteiro em `tests/multi-aparelhos/roteiros/`; **[push: só produção]** o ambiente local não tem push.

Rode **em iOS e em Android**: nas colunas *iOS* e *Android* troque `☐` por `✔` (passou) ou `✘` (falhou, com anotação). Onde a regra depende de pessoas diferentes, vale o roteiro multi-aparelho, com os papéis invertidos entre as plataformas (organizador no iPhone e depois no Android).

## LINK-001 · Os links de presença [MULTI]

| Link | Quem usa | Para quê | iOS | Android |
| --- | --- | --- | --- | --- |
| Lembrete, "Vou" | Qualquer pessoa do grupo do WhatsApp | Responder "Vou" (e, se não for membro, entrar no grupo) | ☐ | ☐ |
| Lembrete, "Não vou" | Idem | Responder "Não vou" | ☐ | ☐ |

## LINK-002 · Abrir o link sendo membro [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Membro logado abre o link "Vou", jogo futuro | Resposta registrada ao abrir | ☐ | ☐ |
| Membro abre o link "Não vou" | Aviso; só grava após "Sim, não vou" | ☐ | ☐ |
| Jogo já começou | "O jogo já começou" | ☐ | ☐ |
| Jogo cancelado | "Esse jogo foi cancelado" | ☐ | ☐ |
| Código inventado | Mensagem genérica | ☐ | ☐ |
| Passou o prazo, antes do início | Membro responde; "Vou" vai para a espera | ☐ | ☐ |
| 11 códigos inválidos em 10 min | Bloqueio temporário | ☐ | ☐ |

## LINK-003 · Abrir o link sem ser membro [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Não membro, grupo sem aprovação, qualquer momento | Entra, faz o cadastro; presença registrada se o jogo não começou | ☐ | ☐ |
| Mesmo caso, jogo cheio | Entra e fica na lista de espera | ☐ | ☐ |
| Não membro, grupo com aprovação | Vira pedido; presença não registrada; tela "Pedido enviado" | ☐ | ☐ |
| Pedido aprovado, cadastro adiantado, volta à tela | Presença registrada sem outro passo | ☐ | ☐ |
| Pedido aprovado, sem cadastro, volta à tela | Abre o cadastro, depois a presença | ☐ | ☐ |
| Pedido recusado ou cancelado, volta à tela | "Seu pedido não está mais ativo"; nenhum pedido novo até tocar em "Pedir de novo" | ☐ | ☐ |
| Organizador trocou o código depois do lembrete | Lembrete antigo não vale como convite | ☐ | ☐ |
| Organizador desativou o convite | Lembretes anteriores não valem; os novos valem | ☐ | ☐ |
| Removido antes | Pedido de entrada | ☐ | ☐ |

## LINK-004 · O cadastro de atleta antes da resposta [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Membro novo, cadastro incompleto | Cadastro, depois a presença | ☐ | ☐ |
| Membro com cadastro completo | Resposta direta | ☐ | ☐ |
| Grupo com aprovação, pedido pendente | Sem resposta agora; dá para adiantar o cadastro, que fica no pedido | ☐ | ☐ |
| Pedido aprovado, cadastro adiantado | Presença registrada ao voltar à tela, sem cadastro de novo | ☐ | ☐ |
| Pedido aprovado, sem cadastro | O app abre o cadastro, depois a presença | ☐ | ☐ |

## LINK-005 · O que a tela do link diz [MULTI]

| Situação | Texto | iOS | Android |
| --- | --- | --- | --- |
| "Vou", confirmado | "Sua vaga está confirmada" / "Seu nome já está na lista.", vagas preenchidas e "Não vou mais" | ☐ | ☐ |
| "Vou", espera com jogo lotado | "Você está na lista de espera" / "As vagas acabaram. Se abrir uma, você recebe um aviso." e a vez na fila | ☐ | ☐ |
| "Vou", espera com vaga sobrando | "Se você ganhar uma vaga, recebe um aviso." (nunca "as vagas acabaram") | ☐ | ☐ |
| Folha de "Não vou", antes do toque | "Não vai poder ir?" / "Seu nome sai da lista do jogo de …" (sem prometer aviso ao grupo) | ☐ | ☐ |
| "Não vou", depois do toque | "Você não vai neste jogo" / "Seu nome saiu da lista. Valeu por avisar." | ☐ | ☐ |
| Todos os casos | Resultado em cima, cartão do jogo embaixo | ☐ | ☐ |

## LINK-006 · Link de compartilhar a lista (removido)

Depois da remoção: as rotas de gerar o link e de ler a lista deixam de existir; o link do lembrete e a resolução do link continuam como estão (LINK-002).
