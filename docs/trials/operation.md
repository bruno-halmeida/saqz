# Trial e campanhas

No administrativo, abra **Cupons → Trial e campanhas**.

- **Ligado:** oferece trial público de 14 dias a novos organizadores elegíveis. Um cupom válido pode definir outro prazo.
- **Desligado:** impede novos trials, inclusive com cupom aplicado anteriormente.
- **Somente com cupom:** exige cupom de trial válido para liberar o primeiro grupo.

O modo inicial da migração é Ligado, preservando o comportamento anterior. Alterações na oferta ou desativação de cupom não mudam a data de término de trials já iniciados.

Ao criar um cupom, defina código alfanumérico (até 32 caracteres) e **dias de trial (1 a 365)**. Campanha/parceiro, validade e limite de usos são opcionais. O formulário começa com 14 dias. A validade administrativa usa o fim da data em UTC.

No app, **Criar grupo** abre as opções do organizador. Aplicar cupom consulta o prazo; continuar leva ao formulário do grupo. A seleção fica salva por conta, mas não reserva uso nem inicia contagem. Na criação do primeiro grupo, o servidor revalida oferta, elegibilidade, validade e limite; trial, grupo e uso são gravados na mesma transação. Se o cupom ficar indisponível, o modo somente com cupom bloqueia; o modo ligado ainda pode conceder os 14 dias públicos sem atribuir essa concessão ao cupom.

O contador **Usos** representa trials iniciados. ID do cupom e snapshot de código/campanha permanecem ligados ao trial para acompanhamento futuro. Cupons de desconto de assinatura continuam na seção própria. Relatórios de receita, conversão e retenção ainda não fazem parte desta entrega.

## API

| Método | Caminho | Uso |
|---|---|---|
| GET / PUT | `/admin/trial-offer` | Ler/salvar `{ "mode": "ON" \| "OFF" \| "COUPON_ONLY" }` |
| GET / POST | `/admin/trial-coupons` | Listar/criar `{ code, trialDays, campaign?, validUntil?, maxUses? }` |
| POST | `/admin/trial-coupons/{id}/deactivate` | Desativar sem apagar histórico |
| GET | `/subscriptions/trial` | Acesso, modo, dias, cupom selecionado e possibilidade de aplicar cupom |
| POST | `/subscriptions/trial/coupon` | Selecionar `{ "code": "ARENA45" }` para o usuário autenticado |

Administração exige conta administrativa. Aplicação exige sessão. Erros de entrada/cupom inválido retornam 400; código administrativo duplicado ou aplicação com oferta desligada/conta inelegível retornam 409; desativação inexistente retorna 404.

## Verificação

Evidências detalhadas: `.specs/features/trial-coupons/evidence.md` e `validation.md`. Capturas usam fixtures locais: comprovam a interface, sem alterar campanhas reais. Migração: V56__trial_campaigns.sql. Backend deve ser atualizado antes de liberar o novo app/painel.
