# Conclusão da implementação de recebimentos

A jornada completa está implementada no backend, Android/iOS e interfaces web. Reembolsos são
tratados fora do Saqz; o produto somente concilia fatos externos e seus reflexos no histórico/caixa.
Nenhum push, deploy, publicação de termos, ativação de piloto ou operação financeira real foi
executado nesta onda. `context.md` e `direcionamento.md` do usuário foram preservados.

## Entregas e critérios

| Critério | Entrega |
|---|---|
| F1 | Carteira permanente, saldo separado de recebíveis, extrato, destino bancário, saque explícito com autenticação recente e recuperação sem duplicação; manutenção após expiração do plano |
| F2 | Cadastro/documentos já existentes, correção dos campos permitidos com identidade legal preservada e delegações para administradores atuais selecionados por nome/grupo |
| F3 | Mensalidade Pix manual/cartão hospedado, aceite completo, cancelamento com estado pendente, corte remoto de futuras cobranças e retomada com novo aceite |
| F4 | Renovação Pix na mesma obrigação/instrumento e snapshot original; comprovante baseado em pagamento observado e compartilhamento nativo real |
| F5 | Fila administrativa, recuperação auditada somente por GET remoto, custos residuais externos observados, termos públicos, retorno sem confirmação e avisos internos |
| F6 | Gateways tipados, DI/navegação/restauração, sessão/generation, Android/iOS, recursos/tags/previews e capturas inspecionadas |
| T04 | Recuperação operacional da credencial perdida: chave obtida externamente, validada por conta/titular/carteira, importada cifrada com auditoria atômica e sem criar outra subconta |
| F7 / T20 | Cenários de homologação e runbook de liberação/reversão preparados; execução real permanece externa |

## Validação

- Backend integrado: 65 testes unitários, 57 PostgreSQL/HTTP, 70 bootstrap focados e 20 de arquitetura,
  sem falhas. As duas regressões finais da recuperação de credencial ampliaram o conjunto focado para
  14 testes, todos aprovados; a migração preserva dados manuais e executa 14 versões/34 tabelas financeiras.
- Mobile final: Android completo 213; domínio 7 Android +7 iOS; dados 67+67; apresentação 107+118;
  rede 95 iOS; DI/navegação 9 iOS. Sem falhas, erros ou testes ignorados nessas suítes.
- Comparação final dos 1.671 arquivos backend/mobile com o scratch testado: nenhuma diferença de conteúdo funcional; apenas limpeza de espaços finais/linhas vazias em dez arquivos.
- Detekt dos módulos afetados aprovado, incluindo `detektAll` nos módulos KMP. Sem novas supressões,
  baselines ou enfraquecimento de testes. Framework final e Xcode arm64 `build-for-testing` aprovados.
- Reautenticação nativa: testes Android e 31 iOS aprovados na entrega fda7607d.
- Web: sete novos testes Node e quatro cenários Playwright com API simulada; termos consultam a origem
  configurada da API. Capturas Android finais incluem sete estados de recorrência e 35 de pagamento,
  além de carteira/gestão. Evidência visual permanece local, sem publicação da branch screenshots.
- Revisões independentes: recorrência/operação backend 6/6 falhas injetadas detectadas; carteira/gestão
  7/7; mobile recorrência/Pix/comprovante 6/6. Os casos originalmente falhos foram preservados como
  regressões. A revisão da credencial aprovou 75 testes e 11/11 falhas injetadas, incluindo o executável real com stdin, PostgreSQL descartável e API loopback.

Relatórios: [backend](evidence/final-recurrence-operations-review.md),
[carteira e gestão](evidence/final-wallet-management-review.md),
[mobile](evidence/final-recurring-mobile-review.md),
[recuperação de credencial](evidence/final-credential-recovery-review.md).
Logs e hashes: [manifesto](evidence/final-integrated-log-digests.json).

## Liberação de produção

Restam executar a homologação real autorizada no sandbox Asaas, confirmar as condições comerciais,
publicar os termos aprovados e liberar/acompanhar o piloto. Mocks e testes locais não substituem
essas etapas. O [runbook integrado](final-operations-runbook.md) reúne os cenários de cadastro,
split, webhooks, Pix/cartão, saque, recorrência/corte, retorno e conciliação externa.
A recuperação excepcional de credencial segue o [procedimento operacional](credential-recovery-runbook.md),
que depende da emissão manual autorizada no Asaas e não oferece API/UI pública para o segredo.

## Commits de entrega

`91410d94` autenticação verificada; `fda7607d` reautenticação nativa; `cd0a4a3c` carteira backend;
`a401eb7b` recorrência/gestão/operação backend; `de5e7eed` web; `6f700201` contratos/gateways mobile;
`af0a043a` carteira/gestão mobile; `777abef4` pagamento/recorrência e wiring; `e40d5c0b` recuperação
operacional de credencial. Os três commits mobile finais têm individualmente menos de 2.000 linhas.
A documentação de fechamento complementa essas entregas sem publicar ou ativar produção.
