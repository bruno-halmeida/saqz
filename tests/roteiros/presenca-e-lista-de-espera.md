# Roteiro manual · Presença e lista de espera

Gerado das seções **QA** das regras do cofre (`Obsidian Vault/Saqz/Regras`). A regra manda: se o roteiro e o cofre divergirem, vale o cofre.

Etiquetas: **[CRÍTICO]** entra na rodada segura; **[MULTI]** depende de duas ou mais pessoas e tem roteiro em `tests/multi-aparelhos/roteiros/`; **[push: só produção]** o ambiente local não tem push.

Rode **em iOS e em Android**: nas colunas *iOS* e *Android* troque `☐` por `✔` (passou) ou `✘` (falhou, com anotação). Onde a regra depende de pessoas diferentes, vale o roteiro multi-aparelho, com os papéis invertidos entre as plataformas (organizador no iPhone e depois no Android).

## PRES-001 · Só se responde em jogo publicado

| Estado do jogo | Atleta responde | Organizador responde por alguém | iOS | Android |
| --- | --- | --- | --- | --- |
| Rascunho | Negado | Negado | ☐ | ☐ |
| Publicado | Permitido | Permitido | ☐ | ☐ |
| Cancelado | Negado | Negado | ☐ | ☐ |
| Concluído | Negado | Negado | ☐ | ☐ |

Rascunho e cancelado/concluído devolvem o mesmo erro de "presença congelada". Rascunho não aparece para o atleta, então o texto do erro não precisa distinguir.

## PRES-002 · Início do jogo trava o atleta; o prazo nunca nega [CRÍTICO] [MULTI]

| Momento | Atleta (Vou ou Não vou) | Organizador por outra pessoa | iOS | Android |
| --- | --- | --- | --- | --- |
| Antes do prazo | Permitido | Permitido | ☐ | ☐ |
| Exatamente no prazo | Dentro do prazo | Permitido | ☐ | ☐ |
| Depois do prazo, antes do início | Permitido (o "Vou" vai para a espera) | Permitido | ☐ | ☐ |
| Exatamente no início | Negado | Permitido | ☐ | ☐ |
| Depois do início | Negado | Permitido | ☐ | ☐ |

Erro devolvido: o mesmo de "presença congelada". Como testar: criar jogo, avançar o relógio nos pontos da tabela e responder.

## PRES-003 · Cada pessoa responde só por si [MULTI]

| Quem chama | Alvo | Resultado | iOS | Android |
| --- | --- | --- | --- | --- |
| Atleta, em si mesmo | Ele | Permitido | ☐ | ☐ |
| Atleta, em outro membro, pela API | Outro | 404 (tratado como jogo inexistente) | ☐ | ☐ |
| Dono ou admin, em outro membro | Outro | Permitido, com motivo | ☐ | ☐ |
| Atleta tentando a rota do organizador | Outro | 403 | ☐ | ☐ |
| Pessoa de fora do grupo | Qualquer | 404 | ☐ | ☐ |

A linha do atleta em outro membro é leitura do código; confirmar com teste de HTTP.

## PRES-004 · Não vou" sempre vale e libera a vaga [MULTI]

| Estado antes | Ação | Depois | Efeito | iOS | Android |
| --- | --- | --- | --- | --- | --- |
| Sem resposta | Não vou | Não vou | Nenhum | ☐ | ☐ |
| Confirmado | Não vou | Não vou | Libera 1 vaga; a fila anda | ☐ | ☐ |
| Na espera | Não vou | Não vou | Sai da fila | ☐ | ☐ |
| Não vou | Não vou de novo | Não vou | Nada muda | ☐ | ☐ |

- Anfitrião que desiste: os convidados saem junto, antes de a fila andar (PRES-008).
- Mensagem: "Sua vaga foi liberada."
- Cobrança do desistente continua pendente: o organizador decide (DEB-012).

## PRES-005 · Vou" depois do prazo vai sempre para a espera [CRÍTICO] [MULTI]

| Modo | Vaga livre | Resultado imediato do "Vou" | iOS | Android |
| --- | --- | --- | --- | --- |
| Ordem de chegada | Sim | Entra na espera e sobe na hora; confirmado, a fila antiga vem primeiro | ☐ | ☐ |
| Ordem de chegada | Não | Na espera, no fim da fila | ☐ | ☐ |
| Escolha manual | Qualquer | Na espera até o organizador liberar | ☐ | ☐ |

Depois do início do jogo vale PRES-002 (travado).

## PRES-006 · Vou" antes do prazo, em ordem de chegada [CRÍTICO] [MULTI]

| Tipo | Prioridade | Vaga livre | Resultado | iOS | Android |
| --- | --- | --- | --- | --- | --- |
| Mensalista | Ligada | Sim | Confirmado | ☐ | ☐ |
| Mensalista | Ligada | Não | Espera | ☐ | ☐ |
| Avulso | Ligada | Sim | **Espera** (sobe quando um confirmado desiste, ou no prazo) | ☐ | ☐ |
| Avulso | Ligada | Não | Espera | ☐ | ☐ |
| Mensalista | Desligada | Sim | Confirmado | ☐ | ☐ |
| Avulso | Desligada | Sim | Confirmado | ☐ | ☐ |
| Qualquer | Qualquer | Não | Espera | ☐ | ☐ |

Dois mensalistas na última vaga: o primeiro a chegar confirma, o outro vai para a espera. Cenário raro e nunca trava.
O que a pessoa lê: "Os mensalistas entram primeiro" e "Mensalistas têm prioridade até o fim do prazo".

## PRES-007 · Vou" antes do prazo, em escolha manual [MULTI]

| Tipo | Prioridade | Vaga livre | Resultado | iOS | Android |
| --- | --- | --- | --- | --- | --- |
| Mensalista | Ligada | Sim | Confirmado | ☐ | ☐ |
| Mensalista | Ligada | Não | Espera | ☐ | ☐ |
| Avulso | Ligada | Qualquer | Espera | ☐ | ☐ |
| Mensalista | Desligada | Qualquer | Espera | ☐ | ☐ |
| Avulso | Desligada | Qualquer | Espera | ☐ | ☐ |

Vagas que abrem por desistência: nada sobe sozinho; o organizador usa "Colocar no jogo" (PROM-004).

## PRES-008 · Convidado entra sempre pela espera [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Anfitrião confirmado adiciona convidado, jogo com vaga | Convidado na espera | ☐ | ☐ |
| Anfitrião na espera adiciona convidado | Permitido (entra no fim da fila) | ☐ | ☐ |
| Anfitrião sem resposta ou "Não vou" adiciona convidado | Bloqueado: "Responda 'Vou' para poder levar alguém" | ☐ | ☐ |
| Convidado sobe da espera | Confirmado, com cobrança no nome do anfitrião se o jogo tem valor | ☐ | ☐ |
| Anfitrião diz "Não vou" | Convidados saem (evento de sistema); a fila anda sem promover convidado que cairia | ☐ | ☐ |
| Anfitrião sai do grupo ou exclui a conta | Mesmo efeito | ☐ | ☐ |
| Admin remove convidado de outro anfitrião | Permitido | ☐ | ☐ |

Textos: "Entra no fim da lista de espera e sobe quando abre vaga."; "Ele sai da lista de espera. Nada é cobrado."

## PRES-009 · Repetir a mesma resposta não muda nada [CRÍTICO] [MULTI]

| Estado | Ação repetida | Resultado | Versão e evento | iOS | Android |
| --- | --- | --- | --- | --- | --- |
| Confirmado | Vou | Confirmado | Não muda | ☐ | ☐ |
| Na espera | Vou | Espera, mesma posição | Não muda | ☐ | ☐ |
| Não vou | Não vou | Não vou | Não muda | ☐ | ☐ |
| Qualquer | Toque duplo rápido | Uma só linha e um só evento | Pelo lock do jogo | ☐ | ☐ |

Confirmar de novo não cria segunda cobrança. Reduzir vagas abaixo dos confirmados não rebaixa ninguém.

## PRES-010 · O mesmo requestId só é repetição se for a última resposta [MULTI]

| Sequência pelo mesmo link | Resultado final | Eventos | iOS | Android |
| --- | --- | --- | --- | --- |
| Vou, Não vou, Vou | Confirmado | 3 | ☐ | ☐ |
| Não vou, Vou, Não vou | Recusado | 3 | ☐ | ☐ |
| Vou, Vou (retry imediato) | Confirmado | 1 (o segundo é repetição) | ☐ | ☐ |
| Vou, Não vou, retry do Não vou | Recusado | 2 (só repete a última) | ☐ | ☐ |
| Vou, Não vou, retry do primeiro Vou | Vale como resposta nova (Confirmado) | 3 | ☐ | ☐ |

Jogo cheio no terceiro toque: vai para a espera.

## PRES-011 · Quem desiste e volta vai para o fim da fila [MULTI]

| Antes | Depois | Resultado | iOS | Android |
| --- | --- | --- | --- | --- |
| Confirmado, desistiu | "Vou", jogo com vaga | Segue a regra do tipo e do prazo | ☐ | ☐ |
| Confirmado, desistiu | "Vou", jogo cheio | Espera, fim da fila, número novo | ☐ | ☐ |
| Na espera em 2º, desistiu | "Vou" | Espera, fim da fila | ☐ | ☐ |
| Na espera em 2º | "Vou" de novo | Continua em 2º (PRES-009) | ☐ | ☐ |

A posição mostrada é sempre calculada pela ordem da lista (PROM-007).

## PRES-012 · O organizador responde por alguém [MULTI]

| Caso | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Sem motivo, só espaços, 1 caractere, mais de 500, ou com quebra de linha | Rejeitado (400, campo `reason`) | ☐ | ☐ |
| Confirmar com vaga, antes ou depois do prazo | Confirmado; cobrança do avulso se o jogo tem valor | ☐ | ☐ |
| Confirmar com jogo cheio | Espera | ☐ | ☐ |
| Tirar alguém ("Não vou") | Tirado; a vaga liberada anda a fila (PRES-004) | ☐ | ☐ |
| Depois do início do jogo | Permitido (quem não está na espera) | ☐ | ☐ |
| Rascunho, cancelado ou concluído | Negado (PRES-001) | ☐ | ☐ |
| O próprio atleta mandando `reason` | Ignorado, não é gravado | ☐ | ☐ |

## PRES-013 · Aviso direto quando a pessoa é confirmada ou tirada por outra parte [MULTI] [push: só produção]

Para cada linha da tabela de Produto: gerar o evento e conferir push e item na central. Para cada linha de "não avisa": conferir que nada chega. Repetir a mesma ação (toque duplo) e conferir que não chega um segundo push. Compatibilidade: abrir a central de avisos nos apps 1.0.4 a 1.0.6 depois de um aviso novo.

## PRES-014 · Vaga por posição (5-1 e 6-0) [CRÍTICO] [MULTI] [push: só produção]

Precisa de um grupo 5-1 com vagas por posição (ex. 4 ponteiros(as), 4 centrais, 2 opostos(as), 2 levantadores(as), líbero 0 ou 2) e de atletas com posição no perfil. Telas de referência: capturas `deb-107` e `deb-109` do módulo de grupos.

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| 5-1 com soma diferente do total | Recusado (422); o app não deixa salvar | ☐ | ☐ |
| 6-0 ou sem sistema com posições | Recusado (lista tem de ser vazia) | ☐ | ☐ |
| "Vou" em posição cheia, com vaga em outra | Lista de espera | ☐ | ☐ |
| Sai alguém de uma posição | Sobe o primeiro da espera **dessa** posição | ☐ | ☐ |
| Organizador força entrada em posição cheia | Entra | ☐ | ☐ |
| App 1.0.8 confirma sem posição | Entra na posição mais livre | ☐ | ☐ |
| "Vou" na Início, no cartão do grupo ou na tela do jogo, jogo 5-1 | Abre a tela de escolher posição (tela inteira, barra "Presença"); nada é confirmado antes | ☐ | ☐ |
| "Vou" em jogo 6-0 ou sem posições | Confirma direto, como hoje | ☐ | ☐ |
| Posição do perfil com vaga | Vem marcada (pílula azul cheia, sem rádio), com o selo "Do seu perfil" | ☐ | ☐ |
| Sem posição no perfil, ou a do perfil cheia | Nada marcado; botão desligado "Escolha uma posição" | ☐ | ☐ |
| Escolhe posição cheia | Botão "Entrar na lista de espera"; aviso "Sem vaga de <posição>" / "Você entra na lista de espera dessa posição, em <Nº>."; abaixo, as posições com vaga | ☐ | ☐ |
| Todas as posições cheias | Aviso no topo: "Todas as posições estão cheias" / "Escolha em qual lista de espera entrar." / "Na de <posição>, você fica em <Nº>." | ☐ | ☐ |
| Posição lota com a tela aberta (outro aparelho confirma a última vaga) | "<posição> acabou de lotar" / "Entre na lista de espera dela, em <Nº>, ou escolha outra posição."; nada é enviado | ☐ | ☐ |
| Servidor devolve espera mesmo assim | O resultado é a espera, sem erro | ☐ | ☐ |
| Sem rede ao confirmar | "Não foi possível salvar sua resposta. Tente novamente." e o botão "Tentar novamente" | ☐ | ☐ |
| Enviando | Botão só com o spinner (leitor de tela: "Confirmando presença"); posições desligadas | ☐ | ☐ |
| Confirma vindo da Início, do grupo ou do jogo | Volta ao ponto de partida, cartão atualizado, sem aviso | ☐ | ☐ |
| Confirma pelo link do WhatsApp | Cartão de resultado com a posição, em verde (LINK-002) | ☐ | ☐ |
| "Vou" pela notificação, app 1.0.10 | Abre o app na tela; nada é confirmado por baixo; depois de confirmar, abre o jogo | ☐ | ☐ |
| "Não vou" pela notificação | Como hoje (responde em segundo plano) | ☐ | ☐ |
| Push de jogo 5-1 no iPhone com app 1.0.6 a 1.0.9 | Sem os botões "Confirmar"/"Não vou" (decisão do dono, 20/10); o toque abre o jogo | ☐ | — |
| App 1.0.6 a 1.0.9 abre o link ou confirma | Nada muda; o servidor escolhe pelo perfil | ☐ | ☐ |
| Fonte grande (130%) e celular pequeno (320 × 568) | Selos descem para baixo do nome; a lista rola e a barra não cobre a última linha | ☐ | ☐ |
| Administrador abre o grupo, jogo 5-1 | Placar "N Vão · N Não vão · N Na espera" e bloco "Posições na quadra" | ☐ | ☐ |
| Atleta (que não administra) abre o grupo | Cartão de sempre, sem placar compacto nem quadra | ☐ | ☐ |
| Posição completa e posição com vaga | Bolinha cheia com ✓ e bolinha tracejada; "Falta N" só onde falta, e a contagem só na primeira vez da posição | ☐ | ☐ |
| Grupo com líbero | Líbero no lugar do central do fundo | ☐ | ☐ |
| Grupo com 0 líbero | Central no fundo | ☐ | ☐ |
| Todas as posições completas | "✓ Todas completas" | ☐ | ☐ |
| Posição com gente na espera | "Na espera: N <posição>" | ☐ | ☐ |
| Próximos jogos (administrador), faltam posições | "Faltam: <posições>" | ☐ | ☐ |
| Próximos jogos (administrador), nenhuma posição fechada | "Faltam: todas as posições" | ☐ | ☐ |
| Rótulos de posição | Ponteiro(a), Central, Oposto(a), Levantador(a), Líbero; plurais ponteiros(as), opostos(as), levantadores(as); cadastro, membros e espera seguem Ponteiro/Ponteira | ☐ | ☐ |
| Selo "Você vai" | Texto verde escuro (`successForeground`, AA ≥ 4,5:1) | ☐ | ☐ |

## PRES-016 · Faltou: confirmou e não apareceu [MULTI]

_Decidida em 22/10/2026; vale depois da entrega do DEB-129 (app 1.0.12 onde houver tela)._

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Gestor marca avulso com diária de R$ 25 (⋮ em Jogaram → Marcar falta) | Aba Faltaram com ele; diária pendente com "Faltou" no acerto | ☐ | ☐ |
| Gestor dispensa no acerto | Dispensada, sai do "falta receber" | ☐ | ☐ |
| Gestor desfaz | Volta para Jogaram; etiqueta some | ☐ | ☐ |
| 8 dias depois do jogo | Não marca nem desfaz (sem ⋮) | ☐ | ☐ |
| Atleta abre o jogo | Vê a pessoa em Jogaram, sem aba Faltaram | ☐ | ☐ |
| Pessoa marcada | Não recebe aviso; "Jogos realizados" do perfil dela sem esse jogo | ☐ | ☐ |
| Gestor abre o perfil do membro | "Faltas" separa "Não vou" de falta sem aviso | ☐ | ☐ |
| Atleta tenta marcar pela API | 403 | ☐ | ☐ |
| Mensalista marcado | Só o registro, sem cobrança | ☐ | ☐ |

## PROM-001 · O que libera uma vaga [CRÍTICO] [MULTI]

| Evento | Libera? | A fila anda | iOS | Android |
| --- | --- | --- | --- | --- |
| Confirmado diz "Não vou" | Sim, 1 | Conforme o modo | ☐ | ☐ |
| Anfitrião sai, com 2 convidados confirmados | Sim, 3 | Conforme o modo | ☐ | ☐ |
| Anfitrião sai, com convidado na espera | 1 (o dele); o convidado só sai da fila | Conforme o modo | ☐ | ☐ |
| Membro sai ou é removido | Sim, nos jogos futuros | Conforme o modo | ☐ | ☐ |
| Organizador tira um confirmado | Sim, 1 | Conforme o modo | ☐ | ☐ |
| Aumento de vagas de 12 para 14 | Sim, 2 | Conforme o modo | ☐ | ☐ |
| Quem estava na espera desiste | Não | Nada muda | ☐ | ☐ |

## PROM-002 · Quem sobe primeiro [CRÍTICO] [MULTI]

| Fila (do mais antigo ao mais novo) | Vaga | Quando | Sobe | iOS | Android |
| --- | --- | --- | --- | --- | --- |
| Avulso A, Mensalista M, Avulso B | 1 | Antes do prazo, prioridade ligada | M | ☐ | ☐ |
| Avulso A, Mensalista M | 1 | Depois do prazo | A | ☐ | ☐ |
| Avulso A, Mensalista M | 1 | Prioridade desligada | A | ☐ | ☐ |
| Avulso A, Convidado do Mensalista M | 1 | Antes do prazo | A | ☐ | ☐ |
| Avulso A, Avulso B | 2 | Qualquer | A, depois B | ☐ | ☐ |

## PROM-003 · Ordem de chegada: promoção automática [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Confirmado desiste, fila com 2 | O 1º da ordem de PROM-002 sobe; o 2º continua | ☐ | ☐ |
| Aumento de 12 para 14, fila com 5 | Sobem 2 | ☐ | ☐ |
| Anfitrião desiste com 2 convidados confirmados | 3 vagas abertas, sobem até 3 | ☐ | ☐ |
| Fila vazia | Ninguém sobe | ☐ | ☐ |
| Desistência depois do início do jogo | Ninguém sobe | ☐ | ☐ |
| Origem do evento | "sistema", com o autor da ação que abriu a vaga | ☐ | ☐ |

## PROM-004 · Escolha manual: "Colocar no jogo [CRÍTICO] [MULTI] [push: só produção]

| Caso | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Modo manual, vaga livre, pessoa na espera | Confirmada | ☐ | ☐ |
| Modo manual, jogo cheio | Negado (400 em `promotion`) | ☐ | ☐ |
| Pessoa não está na espera | Negado (400 em `promotion`) | ☐ | ☐ |
| Modo ordem de chegada | Negado (400 em `promotionMode`) | ☐ | ☐ |
| Atleta comum | 403 | ☐ | ☐ |
| Mesmo `requestId` repetido | Mesmo resultado, sem segundo efeito | ☐ | ☐ |
| Rascunho, cancelado ou concluído | Negado (PRES-001) | ☐ | ☐ |
| Depois do início do jogo | Negado (a implementar) | ☐ | ☐ |

## PROM-005 · O job do prazo [MULTI]

| Jogo no prazo | Modo | Resultado | iOS | Android |
| --- | --- | --- | --- | --- |
| 3 vagas, 5 na espera | Ordem de chegada | Sobem 3, pela ordem; sobram 2 | ☐ | ☐ |
| 3 vagas, 5 na espera | Manual | Um aviso ao dono e admins; ninguém sobe | ☐ | ☐ |
| 0 vaga | Qualquer | Nada | ☐ | ☐ |
| Sem espera | Qualquer | Nada | ☐ | ☐ |
| Falha numa promoção | Ordem de chegada | Só aquele jogo reverte e vai para o log; os outros seguem | ☐ | ☐ |

A conferência é de minuto em minuto (cerca de 11 s a 70 s na rodada de 09/10).

## PROM-006 · Aumentar e reduzir vagas

| Caso | Resultado | iOS | Android |
| --- | --- | --- | --- |
| 12 vagas, 12 confirmados, 4 na espera, aumenta para 14, ordem de chegada | Sobem 2 | ☐ | ☐ |
| Mesmo caso, escolha manual | Ninguém sobe; 2 vagas abertas | ☐ | ☐ |
| Reduz de 12 para 8 com 12 confirmados | 12 continuam confirmados; "vagas" mostram 0; novos "Vou" vão para a espera | ☐ | ☐ |
| Valor 3 ou 101 | Rejeitado (capacidade inválida) | ☐ | ☐ |
| Atleta comum | 403 | ☐ | ☐ |
| Rascunho, cancelado, concluído ou depois do início | Congelado | ☐ | ☐ |
| Salvar com versão desatualizada | Conflito | ☐ | ☐ |

## PROM-007 · Posição na fila [MULTI]

| Fila (por chegada) | Antes do prazo, prioridade ligada | Depois do prazo | iOS | Android |
| --- | --- | --- | --- | --- |
| Avulso A, Mensalista M, Avulso B | 1º M, 2º A, 3º B | 1º A, 2º M, 3º B | ☐ | ☐ |
| Avulso A, Avulso B | 1º A, 2º B | 1º A, 2º B | ☐ | ☐ |

Depois que A sobe, o próximo é o 1º. O dono vê a mesma ordem que o atleta.

## PROM-008 · A cobrança de quem sobe [MULTI]

| Situação | Cobrança | iOS | Android |
| --- | --- | --- | --- |
| Avulso, jogo com valor, "Vou" confirma direto | Criada | ☐ | ☐ |
| Avulso, "Vou" vai para a espera | Não criada | ☐ | ☐ |
| Avulso sobe da espera (automático ou "Colocar no jogo") | Criada na promoção | ☐ | ☐ |
| **Mensalista**, qualquer caminho | **Não criada** | ☐ | ☐ |
| Confirma de novo | Nada novo | ☐ | ☐ |
| Jogo sem valor | Nunca criada | ☐ | ☐ |
| Convidado promovido | Criada em nome do anfitrião, com o nome do convidado | ☐ | ☐ |
| Convidado removido, cobrança pendente | Cancelada | ☐ | ☐ |
| Desistência do próprio confirmado | Continua pendente | ☐ | ☐ |

## PROM-009 · Os avisos da lista de espera e da promoção [MULTI]

Conferir cada linha da tabela com um jogo de teste. Lembrete manual e automático devem ter o mesmo corpo (prazo e vagas). A ordem da espera na lista deve igualar a posição mostrada no app (PROM-007).
