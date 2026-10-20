# Roteiro manual · Convites e entrada no grupo

Gerado das seções **QA** das regras do cofre (`Obsidian Vault/Saqz/Regras`). A regra manda: se o roteiro e o cofre divergirem, vale o cofre.

Etiquetas: **[CRÍTICO]** entra na rodada segura; **[MULTI]** depende de duas ou mais pessoas e tem roteiro em `tests/multi-aparelhos/roteiros/`; **[push: só produção]** o ambiente local não tem push; **[WhatsApp: só produção]** o ambiente local não manda WhatsApp (só o link para copiar, ou o Uazapi falso do roteiro multi-aparelho); a mensagem chegar ao aparelho se confere em produção, com números de teste.

Rode **em iOS e em Android**: nas colunas *iOS* e *Android* troque `☐` por `✔` (passou) ou `✘` (falhou, com anotação). Onde a regra depende de pessoas diferentes, vale o roteiro multi-aparelho, com os papéis invertidos entre as plataformas (organizador no iPhone e depois no Android).

## CONV-001 · O convite do grupo [CRÍTICO] [MULTI]

| Quem abre | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Dono ou admin, primeira vez | Convite criado e mostrado | ☐ | ☐ |
| Admin em outro aparelho | Vê o mesmo código e link | ☐ | ☐ |
| Atleta | 403 | ☐ | ☐ |
| Convite legado vencido | Nasce um novo, sem prazo | ☐ | ☐ |
| Código digitado sem hífen e em minúscula | Aceito | ☐ | ☐ |
| Dono ou admin envia convites de mensalistas | O convite público não muda: mesmo código, sem prazo, mesma tela | ☐ | ☐ |
| Dono ou admin abre a tela do convite com o link de mensalistas vivo | Vê só o convite público; o link de mensalistas não aparece nessa tela | ☐ | ☐ |

## CONV-002 · Trocar o código [CRÍTICO] [MULTI]

| Passo | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Admin troca o código | Novo código e link; o antigo é inválido | ☐ | ☐ |
| Quem abre o link antigo | "Este link foi substituído" | ☐ | ☐ |
| Outro admin em outro aparelho, depois | Vê o código novo | ☐ | ☐ |
| Atleta | 403 | ☐ | ☐ |
| Admin troca o código do convite público | O link de mensalistas segue valendo | ☐ | ☐ |
| Admin invalida o link de mensalistas | O código do convite público segue valendo | ☐ | ☐ |

## CONV-003 · Desativar o convite [MULTI]

| Passo | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Admin desativa | Convite apagado; tela mostra "sem convite ativo" | ☐ | ☐ |
| Alguém abre o link depois | "Este link foi substituído" | ☐ | ☐ |
| Admin reabre a tela | Continua desativado | ☐ | ☐ |
| Admin toca "Gerar código" | Novo código e link ativos | ☐ | ☐ |
| Pedido de entrada pendente | Continua na lista | ☐ | ☐ |
| Atleta | 403 | ☐ | ☐ |
| Admin desativa o convite público, com o link de mensalistas vivo | O link de mensalistas segue valendo | ☐ | ☐ |

## CONV-004 · A prévia do convite [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Link válido, deslogado | Cartão do grupo | ☐ | ☐ |
| Link válido, grupo com aprovação | Cartão sem o local do próximo jogo | ☐ | ☐ |
| Link de grupo excluído | "Convite inválido" | ☐ | ☐ |
| 11 códigos inválidos (logado) em 10 min | Bloqueio temporário | ☐ | ☐ |
| 31 códigos inválidos (anônimo, mesmo IP) | Bloqueio temporário | ☐ | ☐ |
| Link antigo, o dono gerou outro | "Este link foi substituído" | ☐ | ☐ |
| Link de mensalistas válido | Cartão do grupo dizendo que entra como mensalista | ☐ | ☐ |
| Link de mensalistas, grupo com aprovação | Sem "precisa de aprovação"; local do próximo jogo escondido | ☐ | ☐ |
| Link de mensalistas vencido | "Este convite venceu", com a data | ☐ | ☐ |
| Link de mensalistas invalidado | "Este link foi substituído. Peça um novo ao organizador." | ☐ | ☐ |
| Link de mensalistas, grupo excluído | "Convite inválido" | ☐ | ☐ |
| Convite público | Prévia como sempre foi, sem a frase de mensalista | ☐ | ☐ |

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
| Convite público, depois que o dono cadastrou mensalistas | Entra como atleta avulso (ou pedido, se exige aprovação), como sempre | ☐ | ☐ |
| Link de mensalistas, grupo com aprovação | Entra na hora como mensalista; sem pedido | ☐ | ☐ |
| Link de mensalistas, quem saiu antes | Entra na hora; sem pedido | ☐ | ☐ |
| Link de mensalistas, grupo sem mensalidade padrão | Entra como avulso; dono e admins avisados com o motivo | ☐ | ☐ |
| Link de mensalistas, limite do plano cheio | Bloqueado, texto do limite | ☐ | ☐ |
| Link de mensalistas, plano do organizador vencido | Bloqueado, mensagem neutra | ☐ | ☐ |
| Link de mensalistas vencido ou invalidado | "Este convite venceu" ou "Este link foi substituído" | ☐ | ☐ |
| Link de mensalistas, quem já é mensalista | Abre o grupo; nada muda; sem aviso | ☐ | ☐ |

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

## CONV-010 · Convidar mensalistas [CRÍTICO] [MULTI] [WhatsApp: só produção]

_Regra decidida em 20/10 e ainda não implementada (DEB-106); rodar só depois da entrega._

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Dono cadastra 3 pessoas com telefone válido | 3 linhas na fila, "Na fila" em Convidados | ☐ | ☐ |
| Admin cadastra | Mesmo resultado do dono | ☐ | ☐ |
| Atleta | Sem o botão; 403 na API | ☐ | ☐ |
| Telefone com menos de 11 dígitos, ou repetido no lote que está sendo montado | Erro na linha; nada é enviado | ☐ | ☐ |
| Nome vazio ou com 1 letra | "Digite o nome da pessoa."; nada é adicionado | ☐ | ☐ |
| Telefone vazio | "Digite o WhatsApp da pessoa."; nada é adicionado | ☐ | ☐ |
| Telefone com DDD inexistente ou sem o 9 inicial | "Esse número não parece um celular do Brasil"; nada é adicionado | ☐ | ☐ |
| Telefone que já está em Convidados (lote anterior) | "Esse número já foi convidado"; fora da fila; "Reenviar" na lista | ☐ | ☐ |
| Toque duplo em "Enviar" | Um lote só; nenhuma linha nem mensagem repetida | ☐ | ☐ |
| Telefone de quem já é mensalista | "Já é mensalista"; fora da fila | ☐ | ☐ |
| Telefone de avulso do grupo | "Já está no grupo como avulso"; "Promover" não manda WhatsApp | ☐ | ☐ |
| Promover pela lista de membros | Vira mensalista; nenhuma mensagem | ☐ | ☐ |
| Lote maior que as vagas livres do plano | Nada enviado; aviso de limite com "Ver planos" | ☐ | ☐ |
| Grupo sem valor e dia padrão da mensalidade | Tela bloqueada, com "Configurar mensalidade" | ☐ | ☐ |
| Dono salva valor e dia no cartão "Mensalidade" de Editar grupo | A tela de convidar libera | ☐ | ☐ |
| Plano do dono vencido | Bloqueado como qualquer escrita | ☐ | ☐ |
| WhatsApp indisponível | Link para copiar, sem fila | ☐ | ☐ |
| Membro com o mesmo telefone aparece | A linha some de Convidados e os dados do convidado são apagados | ☐ | ☐ |
| Dois admins abrem Membros, em aparelhos diferentes | Veem a mesma lista e os mesmos estados | ☐ | ☐ |
| Dono abre Membros com convidados | Chip "Convidados · N" ao lado de "Pendentes"; tocar mostra só a seção | ☐ | ☐ |
| Dono abre Membros sem convidados | Sem o chip; cartão de entrada no lugar da seção | ☐ | ☐ |
| Atleta abre Membros | Não vê a seção nem o chip | ☐ | ☐ |
| Convidado aguardando | Não conta em "Todos · N" nem no limite do plano | ☐ | ☐ |
| Reenviar uma linha Aguardando, Falhou, Pausado ou Venceu | Volta ao fim da fila; o link é renovado; sem linha repetida | ☐ | ☐ |
| "Reenviar para todos" com linhas Aguardando, Falhou, Pausado e Venceu | As quatro voltam à fila, uma por vez | ☐ | ☐ |
| "Reenviar para todos" com linhas Na fila e Sem WhatsApp | As duas ficam de fora; nada é enviado em dobro | ☐ | ☐ |
| Só há linhas Na fila ou Sem WhatsApp | Sem o botão "Reenviar para todos" | ☐ | ☐ |
| "Reenviar para todos" com o link vencido ou invalidado | Código novo; as linhas elegíveis voltam à fila com o link novo | ☐ | ☐ |
| Remover linha já enviada | Some da lista; nome e telefone apagados na hora; o link segue valendo | ☐ | ☐ |
| 30 dias depois de o link vencer | As linhas de quem não entrou são apagadas | ☐ | ☐ |
| 30 dias depois de o link ser invalidado | As linhas de quem não entrou são apagadas | ☐ | ☐ |
| Link vencido há menos de 30 dias | As linhas seguem como "Venceu", com "Reenviar" | ☐ | ☐ |
| Dono exclui o grupo | Os convidados são apagados junto | ☐ | ☐ |

## CONV-011 · O link de mensalistas [CRÍTICO] [MULTI]

_Regra decidida em 20/10 e ainda não implementada (DEB-106); rodar só depois da entrega._

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Logado, link válido, grupo sem aprovação | Entra como atleta mensalista | ☐ | ☐ |
| Logado, link válido, grupo com aprovação | Entra como mensalista, sem pedido | ☐ | ☐ |
| Avulso do grupo abre o link | Vira mensalista | ☐ | ☐ |
| Mensalista abre o link | Nada muda | ☐ | ☐ |
| Quem saiu antes abre o link | Entra, sem pedido | ☐ | ☐ |
| Grupo no limite do plano | Bloqueado, texto do limite | ☐ | ☐ |
| Plano do organizador vencido | Bloqueado, mensagem neutra | ☐ | ☐ |
| Link com mais de 7 dias do último envio | "Este convite venceu" | ☐ | ☐ |
| Novo lote com o código ainda vivo | Mesmo código; validade renovada | ☐ | ☐ |
| Novo lote com o código vencido | Código novo; o velho continua morto | ☐ | ☐ |
| Dono invalida o link | Link velho: "Este link foi substituído" | ☐ | ☐ |
| Remover um nome da lista | O link segue valendo | ☐ | ☐ |
| Link repassado a um desconhecido | Entra como mensalista (proteção: privado, 7 dias, invalidar) | ☐ | ☐ |
| App 1.0.8 abre o link | Entra como mensalista, sem a prévia nova | ☐ | ☐ |
| 11 códigos inválidos em 10 min | Espera | ☐ | ☐ |
| Pessoa sem conta abre o link | Cria a conta (ou entra) e volta ao convite; entra como mensalista | ☐ | ☐ |
| Dono tirou a mensalidade padrão depois do envio; pessoa nova abre o link | Entra como **avulso**; dono e admins avisados com o motivo | ☐ | ☐ |
| Mesmo caso, quem já é avulso do grupo abre o link | Segue avulso; sem aviso | ☐ | ☐ |
| Pessoa nova entra pelo link | Dono e admins recebem aviso na central, no push e no WhatsApp; quem entrou, não | ☐ | ☐ |
| Avulso do grupo abre o link, grupo com mensalidade padrão | Vira mensalista; aviso "virou mensalista" | ☐ | ☐ |
| Quem entra é um admin que era avulso | Os outros organizadores recebem; ele, não | ☐ | ☐ |
| Dono com telefone só cadastrado (não confirmado), alguém entra pelo link | Aviso na central, no push e no WhatsApp | ☐ | ☐ |
| Dono sem telefone cadastrado, alguém entra pelo link | Aviso na central e no push; sem WhatsApp | ☐ | ☐ |
| Dono com "Avisos dos grupos" desligado, alguém entra pelo link | Sem push e sem WhatsApp; o aviso fica na central | ☐ | ☐ |
| Entra pelo link com o telefone cadastrado | Entra; a linha de Convidados some e os dados são apagados | ☐ | ☐ |
| Entra pelo link com outro telefone | Entra; a linha segue "Aguardando" até o dono remover | ☐ | ☐ |
| Dono troca o código do convite público | O link de mensalistas segue valendo | ☐ | ☐ |
| Dono desativa o convite público | O link de mensalistas segue valendo | ☐ | ☐ |
| Dono invalida o link de mensalistas | O convite público segue valendo | ☐ | ☐ |

## CONV-012 · A fila de envio no WhatsApp [MULTI] [WhatsApp: só produção]

_Regra decidida em 20/10 e ainda não implementada (DEB-106); rodar só depois da entrega._

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Lote de 3 | Saem um a um, de 5 a 15 s entre eles | ☐ | ☐ |
| Dois grupos enviam ao mesmo tempo | Uma fila só, sem paralelo | ☐ | ☐ |
| Backend reinicia no meio do lote | Os "Na fila" voltam sozinhos | ☐ | ☐ |
| Número sem WhatsApp | "Sem WhatsApp"; link para copiar | ☐ | ☐ |
| Checagem do número sem resposta (falha ou demora) | O envio segue; a linha não vira "Sem WhatsApp" | ☐ | ☐ |
| Envio falha uma vez | Tenta de novo; até 3 vezes; depois "Falhou" | ☐ | ☐ |
| 3 falhas seguidas | Fila pausa; linhas "Pausado"; tenta de novo após 10 min | ☐ | ☐ |
| WhatsApp indisponível (dev) | Só o link para copiar | ☐ | ☐ |
| Reenviar uma linha | Vai ao fim da fila; sem linha repetida | ☐ | ☐ |
| Mensagem recebida | Texto fixo com os quatro nomes e a frase final | ☐ | ☐ |
| "Reenviar para todos" com Aguardando, Falhou, Pausado e Venceu | Entram na fila, na ordem da lista, de 5 a 15 s entre elas | ☐ | ☐ |
| "Reenviar para todos" com Na fila e Sem WhatsApp | Não entram | ☐ | ☐ |
| "Reenviar para todos" com o link vencido ou invalidado | Código novo; o link da mensagem é o novo | ☐ | ☐ |
| "Reenviar para todos" com a fila pausada | Voltam ao fim da fila; seguem "Pausado" até a fila retomar | ☐ | ☐ |
| Retomada após 10 min com o envio de teste ok | A fila segue e as linhas "Pausado" saem | ☐ | ☐ |
| Retomada após 10 min com o envio de teste falhando | Pausa de novo, por mais 10 min | ☐ | ☐ |
| Backend reinicia com linhas "Aguardando" | Seguem "Aguardando"; só quem estava "Na fila" volta à fila | ☐ | ☐ |
| Alguém entra pelo link | O aviso ao dono não usa esta fila nem espera os 5 a 15 s | ☐ | ☐ |
