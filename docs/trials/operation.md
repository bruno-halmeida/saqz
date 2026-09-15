# Trial e campanhas

No administrativo, abra **Cupons → Trial e campanhas**.

- **Ligado:** oferece trial público de 14 dias a novos organizadores elegíveis. Um cupom válido pode definir outro prazo.
- **Desligado:** impede novos trials, inclusive com cupom aplicado anteriormente.
- **Somente com cupom:** exige cupom de trial válido para liberar o primeiro grupo.

O modo inicial da migração é Ligado, preservando o comportamento anterior. Alterações na oferta ou desativação de cupom não mudam a data de término de trials já iniciados.

Ao criar um cupom, defina código alfanumérico (até 32 caracteres) e **dias de trial (1 a 365)**. Campanha/parceiro, validade e limite de usos são opcionais. O formulário começa com 14 dias. A validade administrativa usa o fim da data em UTC.

No app, **Criar grupo** mostra o teste do **Organizador** para contas elegíveis sem liberação prévia, com um único aceite. O teste inclui **até 3 grupos e atletas ilimitados**. O primeiro grupo inicia o prazo; o segundo e o terceiro compartilham o mesmo término. Isso vale também para campanhas com prazo próprio e para testes ativos, sem modificar suas datas.

Cupons são aplicados na página web de contratação, em `/comecar/`, acessível também por `/assinar/`. A liberação pública pela web fica em `trial_enrollments`; a seleção de cupom continua em `trial_coupon_selections`. Ambas são por conta, sem iniciar contagem ou reservar uso. A API devolve `preauthorized` quando a conta pode seguir diretamente para criar grupo.

Na criação do primeiro grupo, o servidor revalida oferta, elegibilidade, validade e limite; trial, grupo e uso são gravados na mesma transação. Se o cupom ficar indisponível, o modo somente com cupom bloqueia; o modo ligado ainda pode oferecer os 14 dias públicos sem atribuir essa concessão ao cupom. Os demais grupos usam as vagas restantes e não consomem outro cupom.

Após o teste, é necessário assinar para continuar; não há cobrança automática. O Organizador é destacado na contratação. O Titular continua disponível para uso compatível (1 grupo e até 25 atletas). A criação de assinatura valida ocupação antes de chamar o provedor e retorna `DOWNGRADE_BLOCKED` quando o uso não cabe. Nenhum dado é excluído automaticamente. Reduções no app só são possíveis enquanto houver acesso de escrita; atletas removidos podem ocupar vagas por 30 dias, conforme a política existente.

Um checkout inicial pendente de plano menor limita novas inclusões durante o teste para impedir crescimento incompatível antes do pagamento. Não libera acesso pago, nem prolonga o teste. Cancelar o checkout remove esse teto; confirmar o pagamento aplica o plano contratado.

O contador **Usos** representa trials iniciados. ID do cupom e snapshot de código/campanha permanecem ligados ao trial para acompanhamento futuro. Cupons de desconto de assinatura continuam na seção própria. O painel **Cupons → Conversão de cupons** acompanha conversão e receita bruta de trials e descontos. Definições e limites em `docs/coupons/analytics.md`. Retenção ainda não faz parte do painel.

## API

| Método | Caminho | Uso |
|---|---|---|
| GET / PUT | `/admin/trial-offer` | Ler/salvar `{ "mode": "ON" \| "OFF" \| "COUPON_ONLY" }` |
| GET / POST | `/admin/trial-coupons` | Listar/criar `{ code, trialDays, campaign?, validUntil?, maxUses? }` |
| POST | `/admin/trial-coupons/{id}/deactivate` | Desativar sem apagar histórico |
| GET | `/subscriptions/trial` | Acesso, modo, dias, limites do Organizador (`maxGroups: 3`, `maxAthletes: null`), cupom e `preauthorized` |
| POST | `/subscriptions/trial/enrollment` | Liberar teste público para a conta autenticada sem iniciar prazo |
| POST | `/subscriptions/trial/coupon` | Selecionar `{ "code": "ARENA45" }` para o usuário autenticado |

Administração exige conta administrativa. Aplicação exige sessão. Erros de entrada/cupom inválido retornam 400; código administrativo duplicado ou aplicação com oferta desligada/conta inelegível retornam 409; desativação inexistente retorna 404.

## Verificação

Cenários: `tests/acceptance/trial-cupons.feature` (TRIAL-01–18). Suítes de backend verificam limites, concorrência, prazo, uso e proteção da contratação. Suítes mobile cobrem contrato nulo de atletas ilimitados, aceite, Meu plano e capturas. Os testes web cobrem liberação, cupom e seleção dos planos.

Migrações de campanhas e liberação: `V56__trial_campaigns.sql` e `V66__trial_enrollments.sql`. Atualizar app e backend de forma coordenada: clientes anteriores que exigem `maxAthletes` inteiro não aceitam o novo `null` de atletas ilimitados. Capturas com fixtures não substituem a execução do app instalado ou do checkout sandbox.
