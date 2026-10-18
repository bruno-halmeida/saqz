# Roteiro manual · Convites e entrada no grupo

Gerado das seções **QA** das regras do cofre (`Obsidian Vault/Saqz/Regras`). A regra manda: se o roteiro e o cofre divergirem, vale o cofre.

Etiquetas: **[CRÍTICO]** entra na rodada segura; **[MULTI]** depende de duas ou mais pessoas e tem roteiro em `tests/multi-aparelhos/roteiros/`; **[push: só produção]** o ambiente local não tem push.

Rode **em iOS e em Android**: nas colunas *iOS* e *Android* troque `☐` por `✔` (passou) ou `✘` (falhou, com anotação). Onde a regra depende de pessoas diferentes, vale o roteiro multi-aparelho, com os papéis invertidos entre as plataformas (organizador no iPhone e depois no Android).

## CONV-001 · O convite do grupo [CRÍTICO] [MULTI]

| Quem abre | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Dono ou admin, primeira vez | Convite criado e mostrado | ☐ | ☐ |
| Admin em outro aparelho | Vê o mesmo código e link | ☐ | ☐ |
| Atleta | 403 | ☐ | ☐ |
| Convite legado vencido | Nasce um novo, sem prazo | ☐ | ☐ |
| Código digitado sem hífen e em minúscula | Aceito | ☐ | ☐ |

## CONV-002 · Trocar o código [CRÍTICO] [MULTI]

| Passo | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Admin troca o código | Novo código e link; o antigo é inválido | ☐ | ☐ |
| Quem abre o link antigo | "Convite inválido" | ☐ | ☐ |
| Outro admin em outro aparelho, depois | Vê o código novo | ☐ | ☐ |
| Atleta | 403 | ☐ | ☐ |

## CONV-003 · Desativar o convite [MULTI]

| Passo | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Admin desativa | Convite apagado; tela mostra "sem convite ativo" | ☐ | ☐ |
| Alguém abre o link depois | "Convite inválido" | ☐ | ☐ |
| Admin reabre a tela | Continua desativado | ☐ | ☐ |
| Admin toca "Gerar código" | Novo código e link ativos | ☐ | ☐ |
| Pedido de entrada pendente | Continua na lista | ☐ | ☐ |
| Atleta | 403 | ☐ | ☐ |

## CONV-004 · A prévia do convite

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Link válido, deslogado | Cartão do grupo | ☐ | ☐ |
| Link válido, grupo com aprovação | Cartão sem o local do próximo jogo | ☐ | ☐ |
| Link de grupo excluído | "Convite inválido" | ☐ | ☐ |
| 11 códigos inválidos (logado) em 10 min | Bloqueio temporário | ☐ | ☐ |
| 31 códigos inválidos (anônimo, mesmo IP) | Bloqueio temporário | ☐ | ☐ |

## CONV-005 · Entrar com o convite [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Logado, convite válido, sem aprovação | Entra como atleta | ☐ | ☐ |
| Logado, convite válido, com aprovação | Pedido pendente | ☐ | ☐ |
| Já é membro | Abre o grupo, sem efeito | ☐ | ☐ |
| Limite do plano cheio | Bloqueado, texto do limite | ☐ | ☐ |
| Plano do organizador vencido | Bloqueado, mensagem neutra | ☐ | ☐ |
| Grupo excluído | "Grupo excluído" | ☐ | ☐ |
| 11 códigos inválidos em 10 min | Espera | ☐ | ☐ |

## CONV-006 · O pedido de entrada [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Usa o convite, grupo com aprovação | Pedido criado; cadastro de atleta aberto | ☐ | ☐ |
| Pede de novo, ainda pendente | Mesmo pedido; nenhum aviso novo | ☐ | ☐ |
| Pedido criado | Dono e admins recebem aviso | ☐ | ☐ |
| Lista de pedidos do organizador | Nome e hora no fuso do grupo | ☐ | ☐ |
| Pessoa que pediu | Vê "aguardando aprovação" | ☐ | ☐ |
| Pessoa cancela o pedido | Pedido e cadastro guardado apagados | ☐ | ☐ |

## CONV-007 · Aprovar o pedido [CRÍTICO] [MULTI] [push: só produção]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Dono ou admin aprova | Vira atleta avulso; cadastro aplicado; sai da lista; pessoa recebe push | ☐ | ☐ |
| Atleta tenta aprovar | 403 | ☐ | ☐ |
| Aprovar quem já é membro | Sucesso, sem efeito | ☐ | ☐ |
| Aprovar pedido que sumiu | "Pedido não encontrado" | ☐ | ☐ |
| Limite do plano cheio | Bloqueado | ☐ | ☐ |
| Plano do organizador vencido | Bloqueado | ☐ | ☐ |

## CONV-008 · Recusar o pedido [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Dono ou admin recusa | Pedido apagado | ☐ | ☐ |
| Atleta tenta recusar | 403 | ☐ | ☐ |
| Recusar pedido que sumiu | Sucesso, sem efeito | ☐ | ☐ |
| Pessoa recusada, na Início | O cartão do pedido some | ☐ | ☐ |
| Pessoa recusada pede de novo | Novo pedido, novo aviso ao organizador | ☐ | ☐ |
