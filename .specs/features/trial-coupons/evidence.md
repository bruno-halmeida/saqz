# Evidências

## T1
Gate JDK21: subscriptions:test, groups:test, bootstrap:test --tests *Trial*: PASS.
backend/features/subscriptions: 247 testes, 0 falhas, 0 ignorados
backend/features/groups: 645 testes, 0 falhas, 0 ignorados
backend/bootstrap: 29 testes, 0 falhas, 0 ignorados

| AC / mapeamento reverso (todos necessários) | Evidência | Resultado exigido |
|---|---|---|
| AC1 | TrialCampaignIntegrationTest.kt:59 `assertEquals(now.plusSeconds(14 * 86400L), granted?.endsAt)`; :62 bloqueio; :63 preservação | 14 dias público, OFF bloqueia novos |
| AC3 | :71 `assertNull(trials.find(owner))`; :77 45 dias; :78 uso único; :80–82 snapshot; :76 retry | Seleção sem início, duração configurada e atribuição |
| AC4 | :94 UNAVAILABLE; :110 bloqueio; :114 zero usos no fallback; :121 rollback; :137–140 disputa | Expiração/desativação, rollback, limite concorrente e 7 dias |
| AC3 elegibilidade | StartOrganizerTrialTest.kt e OrganizerTrialCreationIntegrationTest.kt (gate passou) | Histórico anterior impede novo trial |

Nenhum teste removido/ignorado; testes com PostgreSQL real conforme padrão bootstrap.

## T2
Gate bootstrap:test --tests '*Trial*' --tests '*AdminCoupons*': PASS (JDK21).

| AC / mapeamento reverso | Evidência (TrialCampaignEndpointIntegrationTest.kt) | Resultado |
|---|---|---|
| AC2 dias/código/campanha/limite | :54–60 assertions dos campos + :61 `assertEquals(409, create(c).statusCode())` | 45 dias, normalização e duplicidade |
| AC5 oferta e seleção | :66 `assertTrue(before["canRedeemCoupon"].booleanValue())`; :71–76 valores de AVAILABLE/dias/código e startedAt null | Seleção sem início |
| AC1, AC5 OFF/autorização | :86–95 assertFalse e status exatos 401/403/409 | OFF e fronteiras de sessão/admin |
| AC2, AC5 inválidos | :98–106 assertEquals 400/404/201 | Validação e dias limites 1/365 |

Necessários: todos os testes novos mapeiam AC2/AC5. Nenhum teste removido. Controller trata corpo inválido como 400 (incluindo enum inválido), sem cair no handler global 500.

## T3
`node --test adm-web/tests/*.test.cjs`: 51 testes, 0 falhas. Navegador Chromium via Playwright: formulário real cria ARENA45 com 45 dias; modos ON/COUPON_ONLY/OFF; desativação; loading desabilita formulário; falha apresenta retry. Capturas desktop/tablet/loading/erro em docs/trials/evidence; desktop/tablet/erro inspecionados.

| AC / mapeamento reverso | Evidência | Resultado |
|---|---|---|
| AC6 modo confirmado e bloqueio de duplicatas | trial-coupons.test.cjs:25 `assert.equal(requests.length,1)` e estado ON após falha | Persistência só confirmada pela API |
| AC6 criação/dias/validação | :33 `assert.deepEqual(JSON.parse(requests[0].options.body),...)`; :41–47 dados inválidos e duplicados | 45 dias e erro sem limpar rascunho |
| AC6 loading/retry/listagem | :17–21 assertions mode/coupons/busy | Dados e retry |
| AC6 desativação/logout | :52–60 assertions active/uses/form/data/requests | Histórico mantido e sessão isolada |

Todos os testes novos são necessários para AC6. Ajustados bindings HTML para o runtime DC existente e linhas de cupom fora de tbody para evitar reparação automática do DOM pelo navegador.

## Integração HTTP do prazo concedido
`bootstrap:test --tests '*TrialCampaign*'`: 10 testes PASS. TrialCampaignEndpointIntegrationTest, teste `applied coupon creates group...`: `assertEquals(201,group.statusCode())`, `assertEquals(45,trial["trialDays"].intValue())`, fim = início + 45×86400, mesmo endsAt após OFF e novo resgate 409. AC3/AC5 exigem prazo correto tanto na oferta quanto no trial já concedido; resposta passou a derivar os dias concedidos das datas imutáveis.
