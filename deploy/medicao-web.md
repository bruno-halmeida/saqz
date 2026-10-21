# Medição do site e do funil web

Vale para `saqz.app` (landing), `saqz.app/comecar/` (cadastro e teste) e `saqz.app/assinar/` (assinatura).
Os IDs ficam em `landing-page/assets/analytics-config.js`; o carregador é `landing-page/assets/analytics.js`.
Campo vazio no config desliga aquele serviço.

| Serviço | ID | Onde fica a conta |
|---|---|---|
| Google Analytics 4 | `G-M3X6XDE0YS` | propriedade `saquz-app` (498923006), stream web do app "Saqz Web" no Firebase |
| Microsoft Clarity | `yx3xoevhh7` | clarity.microsoft.com, projeto do site saqz.app |
| Pixel da Meta | `1093367840008038` | conjunto de dados "Saqz", negócio Egysis IA |

Nada carrega antes da escolha no aviso de cookies (aparece na landing, em /comecar/ e em /assinar/):
"Aceitar todos" liga GA4, Clarity e o pixel da Meta; "Só estatísticas" liga GA4 e Clarity; "Recusar" não liga
nenhum (e `saqzTrack` vira no-op). A escolha fica em `localStorage` (`saqz_consent_ads` = `all`, `stats` ou
`none`; `yes`/`no` do formato antigo valem como `all`/`stats`) e vale para as três páginas, que estão no
mesmo domínio. O link "Cookies" no rodapé (`data-saqz-cookies`) reabre o aviso; reduzir a escolha limpa os
cookies de medição e recarrega a página. A política de privacidade descreve os três.

## Funil

Ordem de leitura no GA4 (Explorar → Exploração de funil). A coluna Meta mostra o evento padrão enviado
quando há consentimento; os demais vão como evento customizado.

| # | Evento | Página | Quando dispara | Meta |
|---|---|---|---|---|
| 1 | `page_view` | landing | visita | PageView |
| 2 | `cta_click` | landing | clique em botão para `/comecar/` ou `/assinar/`; `cta_position`, `cta_label`, `plan` | |
| 3 | `signup_start` | comecar | tocou "Criar minha conta" ou "Continuar com o Google"; `method` | |
| 4 | `sign_up` ou `login` | comecar | o Firebase aceitou a conta; `method` | CompleteRegistration |
| 5 | `account_ready` | comecar | o backend criou a sessão; `phone_required` | |
| 6 | `profile_completed` | comecar | celular salvo | |
| 7 | `trial_options` | comecar | consulta do teste respondeu; `status`, `preauthorized` | |
| 8 | `trial_released` | comecar | teste liberado pelo backend; `via` = enrollment ou coupon | StartTrial |
| 9 | `store_click` / `app_link_ready` / `app_open` | comecar | selo da loja, link do app gerado, "Continuar no app" | |
| 10 | `checkout_view` | assinar | planos carregados; `mode` = new ou change | |
| 11 | `plan_selected` / `checkout_resumed` | assinar | escolheu um plano, ou retomou um pendente; `plan`, `cycle`, `value` | |
| 12 | `coupon_applied` | assinar | cupom aceito | |
| 13 | `begin_checkout` | assinar | tocou "Gerar Pix" ou "Pagar com cartão" | InitiateCheckout |
| 14 | `payment_pending` / `payment_refused` | assinar | Pix gerado ou cartão autorizado; cartão recusado | |
| 15 | `purchase` | assinar | `/subscriptions/me` confirmou; uma vez por checkout (`transaction_id` = requestId) | Purchase |
| 16 | `store_click` | assinar | no computador, selo da loja depois do pagamento ou em "já é assinante"; `store`, `origem` | |
| — | `plan_change_requested` / `plan_changed` | assinar | troca de plano de quem já assina | |
| — | `faq_open` | landing | abriu uma pergunta | |

O passo seguinte do funil, criar o primeiro grupo, acontece no app: o Android já manda `group_created`
para a mesma propriedade do GA4. Por isso o `user_id` da web é o id interno da conta (o mesmo que o app
usa em `SaqzAnalytics.setUser`), não o uid do Firebase. O cadastro conhece esse id pela sessão; a
assinatura reaproveita o par guardado no navegador quando o login é o mesmo.

## Conferir depois de publicar

1. Abrir `saqz.app` e o GA4 em Relatórios → Tempo real; os eventos aparecem em até um minuto.
2. No Clarity, as gravações aparecem em até duas horas.
3. No Gerenciador de Eventos da Meta, o conjunto "Saqz" só recebe algo depois do "Aceitar".
4. Marcar no GA4 (Administrador → Eventos principais) `sign_up`, `trial_released` e `purchase`.
