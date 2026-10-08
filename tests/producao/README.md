# Testes em produção: caminhos felizes

Roteiro manual para conferir, no ambiente real, que as jornadas principais do Saqz funcionam de
ponta a ponta: app das lojas, `api.saqz.app`, Firebase `saquz-app`, `saqz.app` e `links.saqz.app`.
Começa pelos caminhos felizes. As variações (erro, rede, permissão) entram depois, como tabelas de
"Variações" em cada jornada, no formato de [`fluxo-1-autenticacao.md`](../../fluxo-1-autenticacao.md).

O catálogo de [`tests/acceptance`](../acceptance/README.md) é de ambiente isolado (relógio controlado,
Mailpit, sandbox) e não serve para produção como está.

| Arquivo | Jornadas |
| --- | --- |
| [01-acesso.md](01-acesso.md) | PROD-AC01–AC08: cadastro, e-mail, login, Google/Apple, senha, sessão, troca de conta |
| [02-inicio-e-perfil.md](02-inicio-e-perfil.md) | PROD-IN01–IN04 e PROD-PF01–PF08: Início, perfil, privacidade, exclusão de conta |
| [03-grupos-e-convites.md](03-grupos-e-convites.md) | PROD-GR01–GR06, PROD-CV01–CV07 e PROD-MB01–MB05 |
| [04-jogos-e-presenca.md](04-jogos-e-presenca.md) | PROD-JG01–JG13: marcar, responder, espera, convidado, encerramento, acerto e link de presença |
| [05-avisos-e-notificacoes.md](05-avisos-e-notificacoes.md) | PROD-NT01–NT07: push, avisos, central, preferências, denúncia e bloqueio |
| [06-financeiro.md](06-financeiro.md) | PROD-FI01–FI08: mensalidade, baixa, lançamentos, extrato, cobrança |
| [07-assinatura.md](07-assinatura.md) | PROD-AS01–AS07: teste grátis, compra nas lojas, troca, cancelamento |
| [08-site-e-links.md](08-site-e-links.md) | PROD-WEB01–WEB07: saqz.app, /comecar, /assinar e links |
| [problemas-conhecidos.md](problemas-conhecidos.md) | O que já sabemos que não funciona como o app promete |

**Prioridade.** `P0` impede uma jornada, dá acesso indevido ou mexe com dinheiro. `P1` é jornada
frequente que tem contorno.

## Regras para testar em produção

1. **Só contas de teste.** Nunca entrar em grupo de cliente, mandar convite, aviso ou cobrança para
   pessoa real, nem usar as contas `revisao.*@saqz.app` (são da revisão da Apple e o seed refaz o estado
   delas).
2. **Tudo com "QA" no nome**: grupos (`QA Vôlei <data>`), jogos e apelidos. Assim dá para achar e apagar
   depois, e para descontar nas métricas.
3. **Dinheiro.**
   - O financeiro do grupo não move dinheiro: é só registro.
   - Assinatura no Android só com conta Google que seja **testador de licença** (Play Console →
     Configurações → Teste de licença); fora disso a cobrança é real.
   - Assinatura no iPhone só pelo **TestFlight** (sandbox).
   - O `/assinar` da web cobra de verdade pelo Asaas: só com decisão prévia e reembolso planejado.
4. **E-mail real.** Cadastro e recuperação de senha mandam e-mail de verdade (Hostinger, remetente
   `@saqz.app`). As contas de teste precisam de caixa de entrada que alguém leia.
5. **Sem mexer no banco nem em configuração** durante a execução. Cenário que precise de preparação
   especial fica `BLOQUEADO` até ter roteiro próprio.
6. **Repositório público.** Nada de e-mail, telefone, senha ou ID real nestes arquivos; o mapa de
   apelidos para contas reais fica num arquivo privado, fora do git.

## Elenco e aparelhos

| Apelido | Quem é | Onde |
| --- | --- | --- |
| ORG | Organizador. Conta nova a cada rodada, criada em PROD-AC01; dono do grupo QA | Aparelho A |
| ATL | Atleta. Conta nova criada pelo convite em PROD-CV02 | Aparelho B |
| ATL2 | Segundo atleta. Conta Google nova (PROD-AC04), entra por "Tenho um convite" | Aparelho B, trocando de conta |
| WEB | Conta criada pelo site em PROD-WEB02 | Navegador + aparelho B |

- Aparelho A e B: um iPhone (TestFlight) e um Android (Google Play). Na rodada seguinte, inverter os
  papéis para cobrir as duas plataformas como organizador.
- E-mails: caixas reais, por exemplo apelidos `+qa-org`, `+qa-atl` de uma conta Gmail de testes, ou
  caixas `qa-…@saqz.app` na Hostinger.
- Celulares fictícios no formato aceito pelo app: `(11) 90000-0001`, `(11) 90000-0002`…

## Ordem de execução (uma rodada)

Os blocos dependem dos anteriores. São 80 jornadas: uma rodada completa leva umas 4 horas no primeiro
dia, mais as checagens e a limpeza do dia seguinte.

| Bloco | Jornadas | Observação |
| --- | --- | --- |
| 0. Preparação | — | Builds da loja e do TestFlight instalados; anotar as versões do app e do backend (`v.0.0.x`); as duas contas de loja prontas (testador de licença e sandbox) |
| 1. Organizador chega | AC01, NT01, AC02, IN01 | Aparelho A |
| 2. Monta o grupo | GR01, AS01, GR02, GR03, CV01 | GR03 aplica o contorno do PC-01. Criar já o jogo de JG11, que termina durante a rodada |
| 3. Galera entra | CV02, NT01, AC04, CV04, MB01 | Aparelho B: ATL pelo link, ATL2 pelo Google e "Tenho um convite" |
| 4. Primeiro jogo | JG01, JG07, IN02, JG02, JG03, JG04, JG05, JG06, NT02, NT03, NT04 | Jogo de amanhã às 20h, 2 vagas, prazo "6h antes" |
| 5. Grupo redondo e recorrência | GR04, JG08, IN03, GR05, JG09, JG10, MB05, CV05 | MB05 remove ATL2, e CV05 o traz de volta |
| 6. Comunicação | NT05, NT06, NT07 | NT06 e NT07 conferem o e-mail da equipe |
| 7. Financeiro | MB02, FI02, FI03, FI04, FI05, FI06, FI07, FI08 | FI01 só no dia seguinte |
| 8. Jogo que termina | JG11, JG12 | Até 5 min depois do fim do jogo |
| 9. Assinatura | AS02 (ou AS03), MB03, AS04, AS06, AS07, AS05 | AS04 só com o app 0.0.4 (6) no Android; cancelar (AS05) por último |
| 10. Perfil e conta | PF01–PF07, AC03, AC06, AC07, AC08, AC05 | AC05 só no iPhone |
| 11. Site e links | WEB01, WEB02, WEB03, CV06, WEB05, WEB06, WEB07, CV03, JG13 | CV03 e JG13 desinstalam o app do aparelho B; antes de instalar, conferir a versão em "Sobre este app". JG13 só com o WhatsApp ligado. WEB04 só com decisão de pagamento real |
| Dia seguinte | FI01, IN04, PF05 | Depois das 03:10: mensalidade automática e "Minhas cobranças" na Início |
| 12. Limpeza | MB04, CV07, GR06, PF08 | Só depois do dia seguinte: apagar o grupo QA e as contas descartáveis |

## Como registrar

Cada execução vira uma linha. Resultado: `PASSOU`, `FALHOU`, `BLOQUEADO` ou `NÃO EXECUTADO` — nunca só
um tique. Falha leva print ou vídeo, horário e o que se esperava. Esbarrou num item de
[problemas-conhecidos.md](problemas-conhecidos.md)? Citar o PC e seguir.

| ID | Plataforma | App / backend | Resultado | Observado | Evidência |
| --- | --- | --- | --- | --- | --- |
| PROD-AC01 | iPhone (TestFlight) | 0.0.3 (5) / v.0.0.7 | NÃO EXECUTADO | — | — |

## Horários que importam (São Paulo)

- **Mensalidade automática:** 03:10, para mensalistas com dia de vencimento até hoje.
- **"Jogo liberado":** na hora em que o jogo vira o próximo do grupo, e de novo às 14:00 para quem não
  respondeu.
- **Lembrete automático de presença:** a cada 7 h, contadas da última subida do backend.
- **Jogo encerrado:** até 5 min depois do início + duração.
- **Push:** chega uns 15 s depois do evento.
