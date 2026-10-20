# Roteiro manual · Saída do grupo e exclusão de conta

Gerado das seções **QA** das regras do cofre (`Obsidian Vault/Saqz/Regras`). A regra manda: se o roteiro e o cofre divergirem, vale o cofre.

Etiquetas: **[CRÍTICO]** entra na rodada segura; **[MULTI]** depende de duas ou mais pessoas e tem roteiro em `tests/multi-aparelhos/roteiros/`; **[push: só produção]** o ambiente local não tem push.

Rode **em iOS e em Android**: nas colunas *iOS* e *Android* troque `☐` por `✔` (passou) ou `✘` (falhou, com anotação). Onde a regra depende de pessoas diferentes, vale o roteiro multi-aparelho, com os papéis invertidos entre as plataformas (organizador no iPhone e depois no Android).

## SAI-001 · Sair do grupo [MULTI]

| Quem | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Atleta | Sai; deixa de ver o grupo | ☐ | ☐ |
| Admin | Sai; perde a permissão de admin (SAI-004) | ☐ | ☐ |
| Dono | Recusado ("dono imutável") | ☐ | ☐ |
| Quem já não é membro | Sucesso, sem efeito | ☐ | ☐ |

## SAI-002 · O que acontece com as presenças de quem sai [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Confirmado sai, jogo futuro, ordem de chegada | Vira "Não vou"; sobe o próximo da fila | ☐ | ☐ |
| Mesmo caso, escolha manual | Vira "Não vou"; vaga fica aberta | ☐ | ☐ |
| Na espera, sai do grupo | Sai da fila | ☐ | ☐ |
| Anfitrião com 2 convidados sai | 3 vagas liberadas | ☐ | ☐ |
| Jogo já iniciado ou concluído | Nada muda | ☐ | ☐ |

## SAI-003 · Remover membro [MULTI]

| Quem remove | Quem é removido | Resultado | iOS | Android |
| --- | --- | --- | --- | --- |
| Dono | Atleta | Removido | ☐ | ☐ |
| Dono | Admin | Removido; perde a delegação financeira | ☐ | ☐ |
| Admin | Atleta | Removido | ☐ | ☐ |
| Admin | **Outro admin** | **Deve ser recusado** (hoje é permitido) | ☐ | ☐ |
| Qualquer | Dono | Recusado ("dono imutável") | ☐ | ☐ |
| Atleta | Qualquer | 403 | ☐ | ☐ |
| Qualquer | Quem já saiu | Sucesso, sem efeito | ☐ | ☐ |

## SAI-004 · Admin que sai, é removido ou rebaixado [MULTI]

| Evento | Delegação financeira | iOS | Android |
| --- | --- | --- | --- |
| Admin sai do grupo | Revogada | ☐ | ☐ |
| Dono remove o admin | Revogada | ☐ | ☐ |
| Dono rebaixa o admin a atleta | Revogada | ☐ | ☐ |
| Volta a ser admin depois | Não volta; o dono delega de novo | ☐ | ☐ |
| Dono sem conta de recebimentos | Nada a revogar | ☐ | ☐ |

## SAI-005 · Limite de atletas do plano: por grupo, só quem é membro [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Limite 10, 9 membros | Entrada permitida | ☐ | ☐ |
| Limite 10, 10 membros | Entrada bloqueada | ☐ | ☐ |
| Um membro sai | A vaga volta na hora | ☐ | ☐ |
| Mesmo atleta em dois grupos do dono | Conta em cada grupo | ☐ | ☐ |
| Plano ilimitado | Sem checagem | ☐ | ☐ |
| Cadastro de mensalistas com lote maior que as vagas livres | Nada enviado; aviso de limite com "Ver planos" | ☐ | ☐ |
| Grupo cheio; convidado abre o link de mensalistas | Bloqueado, texto do limite; nada é gravado para o dono | ☐ | ☐ |
| Convidados aguardando | Não ocupam vaga; a vaga é consumida quando a pessoa entra | ☐ | ☐ |
| Mais convidados do que vagas, todos abrem o link | Entram até o limite; quem chega com o grupo cheio é bloqueado | ☐ | ☐ |
| Mensalista entra pelo link com vaga livre | Conta no limite como qualquer atleta | ☐ | ☐ |

## SAI-006 · Pendências financeiras de quem sai [MULTI]

| Situação | Cobrança pendente | iOS | Android |
| --- | --- | --- | --- |
| Atleta sai com mensalidade pendente | Continua pendente | ☐ | ☐ |
| Atleta removido com diária pendente | Continua pendente | ☐ | ☐ |
| Conta excluída com cobrança pendente | Continua pendente, nome "Conta excluída" | ☐ | ☐ |
| Convidado confirmado do anfitrião que sai | Cancelada | ☐ | ☐ |
| Mês seguinte à saída | Nenhuma mensalidade nova | ☐ | ☐ |
| Volta ao grupo depois | Vê as cobranças antigas | ☐ | ☐ |

## SAI-007 · Voltar ao grupo depois de sair ou ser removido [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Removido usa o convite, convite sem aprovação | **Fica pendente** para o organizador | ☐ | ☐ |
| Removido usa o convite, convite com aprovação | Fica pendente | ☐ | ☐ |
| Saiu por conta própria, convite sem aprovação | **Fica pendente** para o organizador | ☐ | ☐ |
| Pedido de volta aprovado | Entra como atleta; marca limpa | ☐ | ☐ |
| Pedido de volta recusado | Não entra | ☐ | ☐ |
| Ex-admin volta | Entra como atleta | ☐ | ☐ |
| Grupo excluído | "Grupo excluído" | ☐ | ☐ |
| Limite do plano cheio | Bloqueado na entrada | ☐ | ☐ |
| Quem saiu abre o link de mensalistas | Entra direto como mensalista, sem pedido | ☐ | ☐ |
| Removido pelo organizador abre o link de mensalistas | Entra direto como mensalista, sem pedido | ☐ | ☐ |
| Quem saiu abre o link de mensalistas, grupo cheio | Bloqueado, texto do limite | ☐ | ☐ |

## SAI-008 · Excluir o grupo [MULTI] [push: só produção]

| Quem | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Dono | Grupo excluído; membros recebem push | ☐ | ☐ |
| Admin ou atleta | 403 | ☐ | ☐ |
| Grupo já excluído ou inexistente | 404 | ☐ | ☐ |
| Membro, depois | Não vê o grupo | ☐ | ☐ |
| Convite do grupo excluído | "Grupo excluído" | ☐ | ☐ |
| Dono com 3 grupos exclui 1 | Pode criar outro | ☐ | ☐ |
| Dono exclui o grupo com convidados de mensalista | Nome e telefone dos convidados apagados | ☐ | ☐ |
| Alguém abre o link de mensalistas do grupo excluído | "Grupo excluído" | ☐ | ☐ |

## SAI-009 · Excluir a conta [CRÍTICO] [MULTI]

| Passo | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Excluir sem confirmar a identidade | Recusado | ☐ | ☐ |
| Atleta excluindo a conta | Sai de todos os grupos; presenças futuras viram "Não vou" | ☐ | ☐ |
| Dono excluindo a conta | Grupos excluídos; membros perdem acesso e recebem push | ☐ | ☐ |
| Conta suspensa | Recusado, nada é apagado | ☐ | ☐ |
| Presenças e cobranças depois | "Conta excluída" | ☐ | ☐ |
| Sessão do app | Termina | ☐ | ☐ |
| Entrar de novo com o mesmo e-mail ou Google | Cria conta nova, sem histórico | ☐ | ☐ |
| Assinatura Asaas | Cancelada em segundo plano | ☐ | ☐ |
| Assinatura da Apple ou do Google | A investigar (DEB-030) | ☐ | ☐ |
