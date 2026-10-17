# Web app (Kotlin/Wasm) contra o stack local

O web app (`mobile/web-app`) é o mesmo `SaqzApp` dos apps nativos rodando no navegador. Para testar
contra este stack, sem tocar em produção:

```sh
tests/multi-aparelhos/stack.sh up
mobile/gradlew -p mobile :web-app:wasmJsBrowserDevelopmentExecutableDistribution
```

Sirva `mobile/web-app/build/dist/wasmJs/developmentExecutable` com um servidor que repasse `/api`,
`/public`, `/subscriptions` e `/plans` para `http://127.0.0.1:18080` e troque o `saqz-config.js`
por uma config com `projectId: "saqz-local"`, `apiKey: "fake-saqz-local-api-key"` e
`authEmulatorUrl: "http://127.0.0.1:9099"`.

Duas sessões ao mesmo tempo no mesmo navegador: abrir uma em `http://localhost:8787` e outra em
`http://127.0.0.1:8787` (origins diferentes, armazenamento separado).

## Contas de teste (só no Auth Emulator local)

| Papel | E-mail | Senha |
| --- | --- | --- |
| ORG | org.web@saqz.test | Webabf60ba2!a |
| ORG2 (build de produção) | org2.web@saqz.test | Web5a70328f!c |
| ORG3 (/comecar/ → web app) | org3.web@saqz.test | Web78f1f40c!d |
| ATL | atl.web@saqz.test | Webc8863879!b |

## Rodada de 13/10/2026 (Safari do iOS 27, simulador iPhone 17)

O web app abriu no Safari do simulador (`http://localhost:8787`, stack local). Conferido:

| Jornada | Resultado |
| --- | --- |
| Login (tela), cadastro do organizador por e-mail | OK, telefone formatado como no app |
| Voltar do Safari dentro do app | OK, volta uma tela (BrowserBackBridge) |
| Criar grupo com foto da galeria | OK depois da correção do fuso (`94b18a9c`); foto sobe (`PUT /photo` 204) |
| Marcar jogo (data, hora, quadra) | OK, horário e prazo de confirmação no fuso do grupo |
| Convite: criar link, copiar, folha de compartilhar, QR | OK depois da correção do QR (`b16357e7`) |
| Atleta pelo link (`?saqz_invite=`), cadastro e entrada no grupo | OK, cai no cadastro de atleta |
| Presença do atleta ("Vou") | OK, lista de espera (prioridade do mensalista) e o organizador vê "1 na espera" |
| Meu plano (teste do Organizador) | OK, prazo no fuso America/Sao_Paulo |

Bugs achados e corrigidos nesta rodada: fuso `SYSTEM` no navegador (faltava `@js-joda/timezone`) e QR do
convite vazio (o qrcode-kotlin devolve data URL no wasm).

Não testado aqui: Google (o emulador não faz o popup), push (não existe no web), comprovante financeiro.

## Link de convite e de presença no Android sem o app

A `links-page` manda o Android sem o app para o web app (`/app/?saqz_invite=` ou `?saqz_attendance=`,
com `saqz_intent=decline` no "não vou") em vez do Google Play, desde que haja WasmGC e
`/app/disponivel.json` responda. Sem isso, o Play com `referrer` continua sendo o destino. Onboarding e
link sem parâmetro seguem para o Play. Coberto por `links-page/tests/fallback.test.cjs`; no aparelho,
conferir pelo roteiro PROD-WEB05b (`tests/producao/08-site-e-links.md`).

Depois da resposta, o web app no Android oferece "Baixar o app" (Google Play); no iPhone
oferece a App Store (`APP_STORE_URL`) e no computador não aparece oferta.

## Validação em produção (13/10/2026, depois do pull no servidor)

Sem login (é o que dá para testar em produção sem conta real):

| Onde | Teste | Resultado |
| --- | --- | --- |
| HTTP | 37 checagens: /app, scripts versionados, .wasm com gzip e cache imutável, config de produção, proxy da API e de /__/auth/, landing, links, associação dos apps | 37/37 |
| Safari iOS 27 | "Criar meu grupo" da landing → web app (até 13/10; agora vai ao /comecar/); link de convite → web app com o convite; Google abre "continue to saqz.app" | OK |
| Chrome do Mac | link de convite → web app (convite guardado para depois do login); "Criar meu grupo" → web app (até 13/10; agora vai ao /comecar/); sem erro no console | OK depois do no-cache na landing |
| Chrome do Android 16 (emulador) | "Criar meu grupo" → /comecar/; link de convite → app nativo; web app direto abre o login | OK (com `-gpu swiftshader_indirect`; com a GPU do host o emulador não desenha WebGL) |

Achado e corrigido: a landing e os links saíam sem Cache-Control e o navegador reaproveitava a página antiga
pelo Last-Modified (`2815c193`).

Com login, no build de produção (o mesmo .wasm publicado) contra este stack, emulador no mesmo origin:
organizador por e-mail → grupo com foto → jogo → convite (copiar e QR) → atleta pelo link entrando com Google
(redirecionamento) → "Quase lá!" → cadastro de atleta → "Não vou" → Meu plano com "Continuar com o
Organizador". Tudo OK; o convite sobrevive ao redirecionamento do Google.

## Organizador de iPhone pelo /comecar/ e sessão do web app (13/10/2026)

Stack local servido como em produção (`/comecar/`, `/assinar/` e `/app/` no mesmo origin, build de produção do
web app, Auth Emulator no mesmo origin), Safari do simulador iPhone 17 (iOS 27).

| Jornada | Resultado |
| --- | --- |
| /comecar/: cadastro por e-mail, celular, cartão final | OK: "Continuar no Saqz" no lugar do painel da App Store |
| Toque em "Continuar no Saqz" | OK: /app/ já logado ("Fala, Org!"); a sessão do SDK compat da página vale no web app |
| Safari encerrado à força e reaberto em /app/ | OK: continua logado |
| Atleta logado, Safari encerrado, link de presença (`?saqz_attendance=`) | OK: vai direto a "Confirmar presença", sem login |
| Mesmo link com `saqz_intent=decline` | OK: folha "Não conseguirá ir?" e, depois do "Sim, não vou", DECLINED no banco |
| Atleta sem sessão abre o link de presença | OK até o login: "Você foi convidado para um grupo", e depois do login segue para "Confirmando sua presença…" |
| Atleta que recusou volta para "Vou" com o jogo na espera | Dava 500 no backend (`waitlist sequence must be the next monotonic value`, em todas as plataformas). Corrigido em `857510d8`; retestado: volta à espera na posição 1 |

Notas:
- O leitor de links do web app só aceita `http` em `localhost` e `127.0.0.1`. Para uma terceira sessão separada
  no simulador, saia da conta em vez de usar o IP da rede: pelo IP o código é descartado de propósito.
- Digitação pelo mobile-mcp nos campos do Compose: tocar e digitar no mesmo `mobile_batch_commands`; tocar e
  digitar em chamadas separadas costuma perder ou duplicar o texto.
- Com a folha "Não conseguirá ir?" aberta, o fundo já mostra "Avisamos o grupo que você não vai" antes da
  confirmação. É o mesmo código do app nativo (`AttendanceLinkRoot`), nada é gravado antes do toque.

## "Criar meu grupo" vai ao cadastro web (13/10/2026)

Pedido do usuário: o web app abre com o layout de celular, então o "Criar meu grupo" da landing passa a abrir
o /comecar/ em todos os aparelhos. O desvio para /app/ (`landing-page/assets/web-app-cta.js`) foi removido.
Depois do cadastro, iPhone e computador seguem para o web app pelo "Continuar no Saqz" do cartão final.

Conferido no Lightpanda contra uma landing local com `/app/disponivel.json` respondendo: a versão antiga
levava o clique ao /app/; a nova leva ao /comecar/, e nenhum link da landing aponta para /app/.

O Lightpanda não serve para o web app: tem WasmGC, mas não tem WebGL nem canvas, e o Compose quebra ao iniciar.
Ele serve para a landing, o /comecar/, o /assinar/ e a página de links.
