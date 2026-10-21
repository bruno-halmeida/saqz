# Roteiro manual · Membros e papéis

Gerado das seções **QA** das regras do cofre (`Obsidian Vault/Saqz/Regras`). A regra manda: se o roteiro e o cofre divergirem, vale o cofre.

Etiquetas: **[CRÍTICO]** entra na rodada segura; **[MULTI]** depende de duas ou mais pessoas e tem roteiro em `tests/multi-aparelhos/roteiros/`; **[push: só produção]** o ambiente local não tem push.

Rode **em iOS e em Android**: nas colunas *iOS* e *Android* troque `☐` por `✔` (passou) ou `✘` (falhou, com anotação). Onde a regra depende de pessoas diferentes, vale o roteiro multi-aparelho, com os papéis invertidos entre as plataformas (organizador no iPhone e depois no Android).

## MEM-001 · Os três papéis e como se muda de papel [MULTI] [push: só produção]

| Ação | Atleta | Admin | Dono | iOS | Android |
| --- | --- | --- | --- | --- | --- |
| Ver o grupo | Sim | Sim | Sim | ☐ | ☐ |
| Configurar, convite, link, atletas, jogos, financeiro | Não | Sim | Sim | ☐ | ☐ |
| Mudar papéis | Não | Não | Sim | ☐ | ☐ |
| Não membro | 404 | 404 | 404 | ☐ | ☐ |

| Quem muda | Alvo | Resultado | iOS | Android |
| --- | --- | --- | --- | --- |
| Dono | Atleta para admin | Feito; a pessoa recebe push (alvo) | ☐ | ☐ |
| Dono | Admin para atleta | Feito, sem aviso; perde a delegação financeira | ☐ | ☐ |
| Dono | Ele mesmo | Recusado (dono imutável) | ☐ | ☐ |
| Admin | Qualquer | 403 | ☐ | ☐ |
| Dono | Quem já tem o papel | Sucesso, sem mudança | ☐ | ☐ |

## MEM-002 · A lista de membros [MULTI]

| Quem olha | Vê | iOS | Android |
| --- | --- | --- | --- |
| Atleta | Lista sem situação financeira (desconhecido) nem valor de mensalidade | ☐ | ☐ |
| Atleta filtra por situação financeira | 403 | ☐ | ☐ |
| Atleta pede inativos | Recusado ou ignorado (alvo) | ☐ | ☐ |
| Admin ou dono | Tudo, inclusive situação financeira e mensalidade | ☐ | ☐ |
| Quem saiu | Não aparece | ☐ | ☐ |
| Não membro | 404 | ☐ | ☐ |

## MEM-003 · Mensalista ou avulso [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Avulso vira mensalista com valor e dia | Passa a receber mensalidade | ☐ | ☐ |
| Vira mensalista sem valor, grupo sem padrão | Recusado | ☐ | ☐ |
| Vira mensalista sem valor, grupo com padrão | Usa o padrão | ☐ | ☐ |
| Mensalista vira avulso | Perde valor e dia; passa a pagar diária | ☐ | ☐ |
| Mensalista vira avulso, mensalidade pendente | Cobrança continua pendente | ☐ | ☐ |
| Atleta muda o próprio tipo | 403 | ☐ | ☐ |
| Entra pelo link de mensalistas, grupo com mensalidade padrão | Já nasce mensalista, com o valor e o dia do grupo | ☐ | ☐ |
| Entra pelo link de mensalistas, grupo sem mensalidade padrão | Entra como avulso; dono e admins avisados com o motivo | ☐ | ☐ |
| Avulso do grupo abre o link de mensalistas, grupo com padrão | Vira mensalista; o papel não muda | ☐ | ☐ |
| Mensalista abre o link de mensalistas | Nada muda | ☐ | ☐ |
| Promover avulso pela lista de membros | Vira mensalista; nenhuma mensagem de WhatsApp | ☐ | ☐ |
| Promover pelo botão "Promover a mensalista" no cadastro de convites | Vira mensalista; nenhuma mensagem de WhatsApp | ☐ | ☐ |
| Aceita pedido de entrada como mensalista, grupo com padrão | Entra mensalista, com o valor e o dia do grupo | ☐ | ☐ |
| Aceita pedido de entrada, grupo sem padrão | Entra como avulso; a opção de mensalista nem aparece | ☐ | ☐ |
| Isenta um mensalista (chave "Isenção da mensalidade", confirma "Dar isenção") | Continua mensalista, com prioridade; nenhuma mensalidade nova | ☐ | ☐ |
| Liga a chave e toca "Voltar" | Nada muda | ☐ | ☐ |
| Isenta com mensalidade pendente do mês atual | Ela vira dispensada, com a nota "Isenção da mensalidade" | ☐ | ☐ |
| Isenta com mensalidade pendente de mês anterior | A antiga continua pendente | ☐ | ☐ |
| Admin isenta a si mesmo | Permitido | ☐ | ☐ |
| Atleta tenta se isentar | 403 | ☐ | ☐ |
| Isento na lista de membros (dono ou admin) | "Mensalista · com isenção"; o cartão de valor some no editor | ☐ | ☐ |
| Isento vira avulso | Perde a isenção; passa a pagar diária | ☐ | ☐ |
| Avulso marcado como isento | Recusado; a chave nem aparece para avulso | ☐ | ☐ |
| App antigo (sem o campo) edita o membro isento | A isenção continua | ☐ | ☐ |
| Isento sai do grupo e volta | Volta sem isenção | ☐ | ☐ |

## MEM-004 · O perfil de atleta

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Quadra, posição líbero | Aceito | ☐ | ☐ |
| Praia, posição líbero | Recusado | ☐ | ☐ |
| Quadra, lado preferido | Recusado | ☐ | ☐ |
| Posição secundária igual à principal | Recusado | ☐ | ☐ |
| Altura 99 ou 251 | Recusado | ☐ | ☐ |
| Apelido com 1 caractere ou espaço na ponta | Recusado | ☐ | ☐ |
| Perfil vazio | Aceito | ☐ | ☐ |
| Atleta edita o de outro | 403 | ☐ | ☐ |
| Quadra para praia, depois volta | Posição e altura somem e voltam | ☐ | ☐ |

## MEM-005 · O telefone: quem vê [MULTI]

| Escolha | Atleta vê | Admin ou dono vê | A pessoa vê | iOS | Android |
| --- | --- | --- | --- | --- | --- |
| Todos | Sim | Sim | Sim | ☐ | ☐ |
| Só dono e admin | Não | Sim | Sim | ☐ | ☐ |
| Ninguém | Não | Não | Sim | ☐ | ☐ |
