# Roteiro manual · Assinatura e teste grátis

Gerado das seções **QA** das regras do cofre (`Obsidian Vault/Saqz/Regras`). A regra manda: se o roteiro e o cofre divergirem, vale o cofre.

Etiquetas: **[CRÍTICO]** entra na rodada segura; **[MULTI]** depende de duas ou mais pessoas e tem roteiro em `tests/multi-aparelhos/roteiros/`; **[push: só produção]** o ambiente local não tem push.

Rode **em iOS e em Android**: nas colunas *iOS* e *Android* troque `☐` por `✔` (passou) ou `✘` (falhou, com anotação). Onde a regra depende de pessoas diferentes, vale o roteiro multi-aparelho, com os papéis invertidos entre as plataformas (organizador no iPhone e depois no Android).

## ASS-001 · Planos e limites

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Titular com 1 grupo cria outro | Recusado; app oferece plano maior | ☐ | ☐ |
| Titular, grupo com 25 atletas, convite novo | Recusado ("limite de atletas do plano") | ☐ | ☐ |
| Atleta que já é membro, no limite | Segue normal | ☐ | ☐ |
| Organizador, grupo com 40 atletas | Aceito | ☐ | ☐ |
| Relatórios no Titular ou Organizador | Fechado | ☐ | ☐ |
| Troca agendada para plano menor | Já vale o limite menor | ☐ | ☐ |
| Lista de benefícios do plano | Sem "vários admins" nem "WhatsApp prioritário" (alvo) | ☐ | ☐ |

## ASS-002 · O teste grátis [CRÍTICO] [push: só produção]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Dono novo cria o primeiro grupo | Teste de 14 dias começa | ☐ | ☐ |
| Dono que já teve grupo | Sem teste | ☐ | ☐ |
| Dono que já pagou | Sem teste | ☐ | ☐ |
| Oferta "só com cupom", sem cupom | Sem teste | ☐ | ☐ |
| Cupom de 30 dias | Teste de 30 dias | ☐ | ☐ |
| Faltam 3, 1 e 0 dias, sem assinatura | Um aviso em cada marco | ☐ | ☐ |
| Assinou no meio do teste | Avisos param | ☐ | ☐ |
| Criar jogo depois do fim do teste | Recusado (alvo) | ☐ | ☐ |

## ASS-003 · Sem plano ativo: o grupo fica congelado [CRÍTICO] [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Teste acabou, dono cria jogo | Recusado ("assine um plano") | ☐ | ☐ |
| Teste acabou, atleta toca "Vou" | Recusado, com aviso de plano congelado | ☐ | ☐ |
| Teste acabou, atleta toca "Não vou" | Funciona | ☐ | ☐ |
| Congelado, atleta sai do grupo | Funciona | ☐ | ☐ |
| Congelado, abrir grupo e financeiro | Mostra tudo, sem ações | ☐ | ☐ |
| Congelado, convite novo | Recusado | ☐ | ☐ |
| Congelado, hora do lembrete | Nenhum lembrete (alvo) | ☐ | ☐ |
| Dono assina | Tudo volta | ☐ | ☐ |

## ASS-004 · Assinar e pagar

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Compra no iPhone aprovada | Acesso liberado | ☐ | ☐ |
| Compra no iPhone pendente | Aviso de pendente; acesso depois | ☐ | ☐ |
| Apple confirmou, Saqz não | "Confirmar de novo" | ☐ | ☐ |
| Assinatura de loja de outra conta | Recusado | ☐ | ☐ |
| Site, Pix | Código e QR; acesso ao confirmar | ☐ | ☐ |
| Site, cartão recusado | Mensagem do Asaas; nada gravado | ☐ | ☐ |
| Cupom válido no site | Preço com desconto | ☐ | ☐ |
| Cupom vencido ou já usado | Recusado | ☐ | ☐ |
| Já tem assinatura ativa | Recusado | ☐ | ☐ |
| Plano menor que o uso | Recusado | ☐ | ☐ |

## ASS-005 · Trocar de plano, cancelar e reativar

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Titular sobe para Organizador | Cobrança só da diferença; vale ao confirmar | ☐ | ☐ |
| Mesmo preço | Vale na hora | ☐ | ☐ |
| Organizador desce para Titular com 2 grupos | Recusado | ☐ | ☐ |
| Desce com 1 grupo | Agendado; limite de 25 atletas já vale | ☐ | ☐ |
| Descer com subida pendente | Recusado | ☐ | ☐ |
| Cancelar | Acesso até o fim do período | ☐ | ☐ |
| Reativar antes do fim | Cobrança só no vencimento | ☐ | ☐ |
| Reativar depois do fim | Recusado | ☐ | ☐ |
| Reativar com assinatura de loja | Recusado | ☐ | ☐ |

## ASS-006 · Pagamento atrasado e a carência [push: só produção]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Venceu ontem, não pagou | "Pagamento pendente"; acesso normal; aviso no dia | ☐ | ☐ |
| Pagou no 5º dia | Ativo de novo | ☐ | ☐ |
| Passou 7 dias | Grupos congelados | ☐ | ☐ |
| Pagou no dia 10 | Volta na hora | ☐ | ☐ |
| Nunca pagou nada | Sem plano, sem carência | ☐ | ☐ |
| Aviso no 3º dia e no 7º | Um em cada marco; param ao pagar | ☐ | ☐ |
