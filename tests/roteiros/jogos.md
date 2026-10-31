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

_Linhas novas decididas em 28/10/2026 (DEB-146, valor por jogo obrigatório): rodar só depois da entrega._

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Editor de jogo e horário regular | Bloco "Valor por jogo" com Cobrar diária e Sem diária; jogo novo vem com a escolha do grupo | ☐ | ☐ |
| Editor de jogo, nem Cobrar diária nem Sem diária | Não salva; erro no topo e no campo ("Escolha o valor ou Sem diária.") | ☐ | ☐ |
| Criar ou Editar grupo sem escolha (decidido em 29/10) | Salva; grupo sem valor; cartão azul para dono e admins | ☐ | ☐ |
| Primeiro jogo num grupo sem valor, com Sem diária ou com valor | O grupo passa a ter a mesma escolha; cartão some | ☐ | ☐ |
| Cobrar diária com R$ 0,00 ou vazio | Não salva | ☐ | ☐ |
| Sem diária | Jogo sem cobrança, sem linha de diária e sem faixa de retroativa | ☐ | ☐ |
| Cobrar diária R$ 20 | Avulso confirmado ganha diária de R$ 20 | ☐ | ☐ |
| Editor com mensalidade R$ 80 no grupo | Apoio "Sugestão: mensalidade ÷ 4 (R$ 80 ÷ 4)." | ☐ | ☐ |
| App antigo manda valor vazio, grupo com R$ 20 | Aceito; o jogo leva R$ 20 | ☐ | ☐ |
| App antigo manda valor vazio, grupo Sem diária | Aceito; o jogo fica Sem diária | ☐ | ☐ |
| App antigo abre jogo Sem diária | Mostra o jogo como sem valor, como hoje | ☐ | ☐ |
| Grupo existente sem valor | Nada muda; cartão azul no grupo só para dono e admins | ☐ | ☐ |
| Aviso uma vez a dono e admins [push: só produção] | Push e central "<Grupo>: defina o valor por jogo"; o toque abre o Valor por jogo | ☐ | ☐ |
| Dono escolhe R$ 10 pelo aviso | Horário regular e próximos jogos sem valor passam a R$ 10; cartão some | ☐ | ☐ |
| Atleta comum | Não vê o cartão nem recebe o aviso | ☐ | ☐ |
| Avulso abre o jogo e o link de presença | "Diária de R$ 20, cobrada ao confirmar" antes do Vou | ☐ | ☐ |
| Jogo Sem diária | Sem a linha da diária | ☐ | ☐ |

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
| 4 dias antes do jogo seguinte | A rotina publica | ☐ | ☐ |
| Jogo do horário começa e o seguinte está a 7 dias | O seguinte continua em rascunho até faltarem 4 dias | ☐ | ☐ |
| Organizador edita o horário para 20h | Vale daqui para frente | ☐ | ☐ |
| Avulso publicado na terça 19h30 antes do horário | O avulso vira o jogo da série | ☐ | ☐ |
| Horários pausados | Nenhum jogo novo; os existentes continuam | ☐ | ☐ |
| Grupo sem quadra padrão | Nada é gerado | ☐ | ☐ |
| Teste grátis vencido sem assinatura | O futuro da série é cancelado | ☐ | ☐ |

## JOGO-011 · Jogos anteriores [MULTI]

_Decidida em 22/10/2026; vale depois da entrega do DEB-128 (app 1.0.12 onde houver tela)._

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Gestor com 3 próximos e anteriores | Rodapé de "Próximos jogos" com "Ver mais N jogos" e "Jogos anteriores" | ☐ | ☐ |
| Gestor sem próximo jogo, com anteriores | Seção só com o botão "Jogos anteriores" | ☐ | ☐ |
| Grupo que nunca teve jogo anterior | Sem o botão | ☐ | ☐ |
| Atleta | Lista como hoje, sem botão; a rota recusa (403) | ☐ | ☐ |
| Abre a lista | Do mais recente para trás, separada por mês | ☐ | ☐ |
| Jogo encerrado | "N de M foram", selo Encerrado | ☐ | ☐ |
| Encerrado com diária aberta | Linha âmbar "Acerto pendente · N pessoas, R$ X" | ☐ | ☐ |
| Cancelado depois de publicado | Na lista, apagado, "Cancelado" | ☐ | ☐ |
| Rascunho de série cancelado | Fora da lista | ☐ | ☐ |
| 45 jogos anteriores | 20, depois mais 20 ao rolar, depois 5, sem repetir | ☐ | ☐ |
| Toque num jogo | Abre o detalhe do jogo | ☐ | ☐ |
| Abre o 45º jogo e volta | Lista continua com 45, na mesma posição; a linha do jogo aberto atualiza | ☐ | ☐ |
| Puxar a lista | Bola gira e a lista atualiza sem encolher | ☐ | ☐ |
| Detalhe de realizado já acertado (gestor) | Botão "Ver acerto" | ☐ | ☐ |
| Detalhe de realizado com pendência (gestor) | Botão "Encerrar jogo e acertar" | ☐ | ☐ |
| Detalhe de cancelado | Abas "Confirmados | Não iam" | ☐ | ☐ |

## JOGO-012 · Histórico de respostas do jogo [MULTI]

_Decidida em 22/10/2026; vale depois da entrega do DEB-135 (app 1.0.12)._

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Gestor abre ⋯ do jogo | Item "Histórico de respostas" entre "Ajustar vagas" e "Cancelar jogo" | ☐ | ☐ |
| Atleta abre o jogo | Sem o item; rota do histórico responde 403 | ☐ | ☐ |
| Atleta confirma | "Fulano confirmou", hora, Vai | ☐ | ☐ |
| Confirmado desiste e o 1º da espera sobe | "desistiu · estava confirmado(a)" e "subiu da espera · automático, a vaga de Fulano abriu" | ☐ | ☐ |
| Organizador tira alguém com motivo | "Bruno tirou Fulano do jogo · organizador" e a linha do motivo | ☐ | ☐ |
| Anfitrião leva convidado | "Fulano levou um convidado" | ☐ | ☐ |
| Jogo realizado e cancelado | Item no ⋯ e histórico completo | ☐ | ☐ |
| Jogo sem resposta | "Ninguém respondeu ainda." | ☐ | ☐ |
