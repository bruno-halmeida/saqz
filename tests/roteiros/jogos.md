# Roteiro manual · Jogos

Gerado das seções **QA** das regras do cofre (`Obsidian Vault/Saqz/Regras`). A regra manda: se o roteiro e o cofre divergirem, vale o cofre.

Etiquetas: **[CRÍTICO]** entra na rodada segura; **[MULTI]** depende de duas ou mais pessoas e tem roteiro em `tests/multi-aparelhos/roteiros/`; **[push: só produção]** o ambiente local não tem push.

Rode **em iOS e em Android**: nas colunas *iOS* e *Android* troque `☐` por `✔` (passou) ou `✘` (falhou, com anotação). Onde a regra depende de pessoas diferentes, vale o roteiro multi-aparelho, com os papéis invertidos entre as plataformas (organizador no iPhone e depois no Android).

## JOGO-001 · Estados do jogo e quem os vê

| De | Para | Quem pode | Resultado | iOS | Android |
| --- | --- | --- | --- | --- | --- |
| Rascunho | Publicado | Dono, admin | Permitido | ☐ | ☐ |
| Rascunho ou publicado | Cancelado | Dono, admin | Permitido | ☐ | ☐ |
| Publicado | Concluído | Sistema | Automático ao fim do jogo | ☐ | ☐ |
| Cancelado ou concluído | Qualquer | Qualquer | Recusado | ☐ | ☐ |
| Atleta | Qualquer mudança | | 403 | ☐ | ☐ |
| Atleta vendo rascunho | | | Não vê | ☐ | ☐ |

## JOGO-002 · Criar o jogo

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Tudo preenchido, início futuro | Criado | ☐ | ☐ |
| Início no passado | Recusado | ☐ | ☐ |
| Prazo depois do início | Recusado | ☐ | ☐ |
| Vagas = 3 | Recusado | ☐ | ☐ |
| Título impróprio | Recusado | ☐ | ☐ |
| Jogo em cima da hora, padrão de 24 h | Prazo = início; sem prioridade de mensalista | ☐ | ☐ |
| Atleta cria | 403 | ☐ | ☐ |

## JOGO-003 · Editar o jogo [MULTI] [push: só produção]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Edita antes do início | Salvo | ☐ | ☐ |
| Edita depois do início | Recusado | ☐ | ☐ |
| Início movido para o passado | Recusado | ☐ | ☐ |
| Salva com a versão antiga | Conflito | ☐ | ☐ |
| Muda dia, hora ou local de jogo publicado | Presenças mantidas; membros (menos "Não vou") recebem aviso | ☐ | ☐ |
| Aumenta vagas pelo editor, fila com 3, ordem de chegada | Sobem os da fila | ☐ | ☐ |

## JOGO-004 · Publicar [CRÍTICO] [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Dono publica rascunho, jogo futuro | Publicado; presença aberta; aviso "liberado" se for o próximo | ☐ | ☐ |
| Atleta tenta publicar | 403 | ☐ | ☐ |
| Rascunho com início passado | Recusado | ☐ | ☐ |
| Publicar jogo já publicado | Recusado (transição inválida) | ☐ | ☐ |
| Publicar um jogo que não é o próximo do grupo | Publicado; sem aviso agora | ☐ | ☐ |

## JOGO-005 · Cancelar [CRÍTICO] [MULTI] [push: só produção]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Cancela publicado antes do início | Cancelado; presença congelada; pendentes canceladas; aviso a confirmados e espera | ☐ | ☐ |
| Cobrança já paga | Fica, marcada para revisão | ☐ | ☐ |
| Cancela rascunho | Cancelado, sem aviso | ☐ | ☐ |
| Cancela jogo já cancelado ou concluído | Recusado | ☐ | ☐ |
| Atleta | 403 | ☐ | ☐ |

## JOGO-006 · Encerramento automático

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Jogo das 19h com 2 h de duração | Encerrado a partir das 21h | ☐ | ☐ |
| Jogo cancelado | Continua cancelado | ☐ | ☐ |
| Atleta tenta responder depois | Presença congelada | ☐ | ☐ |
| Ana confirmada, Bruno na espera, Carla "Não vou" | Só a Ana ganha +1 em "Jogos" | ☐ | ☐ |

## JOGO-007 · Local e quadra [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Nome da quadra em branco | "Informe o nome da quadra" | ☐ | ☐ |
| Endereço em branco | Aceito | ☐ | ☐ |
| Endereço com 3 caracteres | "Precisa de pelo menos 5 caracteres" | ☐ | ☐ |
| Grupo sem quadra, jogo com quadra nova | Vira a quadra do grupo | ☐ | ☐ |
| Muda a quadra do grupo depois | Jogos antigos ficam com a quadra antiga | ☐ | ☐ |

## JOGO-008 · Valor do jogo (e recado removido) [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Valor vazio | Jogo sem cobrança | ☐ | ☐ |
| Valor R$ 0,00 | Recusado | ☐ | ☐ |
| Recado enviado por app antigo | Aceito e ignorado | ☐ | ☐ |

## JOGO-009 · Um jogo por vez no grupo [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Criar às 20h com jogo das 19h às 21h | Conflito | ☐ | ☐ |
| Criar às 21h com jogo das 19h às 21h | Permitido | ☐ | ☐ |
| Criar no horário de um jogo cancelado | Permitido | ☐ | ☐ |
| Dois organizadores criando ao mesmo tempo | Um passa, o outro recebe conflito | ☐ | ☐ |

## JOGO-010 · Horários fixos (série semanal)

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Organizador cria o horário "terça 19h30" | Próximo jogo da terça publicado na hora; os seguintes nascem em rascunho invisível | ☐ | ☐ |
| 5 dias antes do jogo seguinte | A rotina publica | ☐ | ☐ |
| Organizador edita o horário para 20h | Vale daqui para frente | ☐ | ☐ |
| Avulso publicado na terça 19h30 antes do horário | O avulso vira o jogo da série | ☐ | ☐ |
| Horários pausados | Nenhum jogo novo; os existentes continuam | ☐ | ☐ |
| Grupo sem quadra padrão | Nada é gerado | ☐ | ☐ |
| Teste grátis vencido sem assinatura | O futuro da série é cancelado | ☐ | ☐ |
