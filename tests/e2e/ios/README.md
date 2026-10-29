# E2E do app instalado — iOS

Jornadas reais no `SaqzIOS.app` (esquema `SaqzDev`, Debug) no simulador, com autenticação Firebase nativa contra o Auth
Emulator, backend Spring real e PostgreSQL descartável. É a mesma orquestração da [suíte Android](../android/README.md):
o runner importa de lá `fixture.mjs` (seed por API e SQL), `process.mjs` (comandos e limpeza) e `requireFreePort`, e usa
os mesmos nomes de cenário, então cada fixture serve às duas plataformas. Os testes são XCUITest no alvo
`SaqzIOSUITests` (`mobile/ios-app/SaqzIOSUITests/*E2eTest.swift`, base em `E2ESupport.swift`).

Ação sob teste pela tela; outras pessoas agem pela API; o resultado gravado é conferido por chamadas autenticadas
independentes (`api(...)`, com o token de cada papel tirado do Auth Emulator). Depois do login, o teste confere no Perfil
que a conta logada é a do papel pedido.

## Cenários

| `--scenario` | Classe | Cobre | Não cobre |
| --- | --- | --- | --- |
| `attendance-list` | `GroupListConfirmE2eTest` | GER-003: Confirmar na foto do grupo, Vou no jogo lotado → "Na espera", API WAITLISTED posição 1 | Jogo com posições, falha de rede |
| `game-fee` | `GameFeeE2eTest` | JOGO-008 (V1–V10): cartão do valor só para o dono; editor sem escolha / Cobrar 0 não salva; Sem diária salva; avulso vê "Diária de R$ 20, cobrada ao confirmar" e o Vou gera a diária | Editar grupo (V8), link de presença (V11), sugestão ÷ 4 (V12) |
| `cashbox` | `CashboxE2eTest` | FIN-006 (C1–C13): saldo inicial 250 → 300, saída de ontem com atalho R$ 120, entrada de patrocínio, placar, extrato e filtro, 400/403 da API | "Começar do zero" |
| `uncharged-review` | `UnchargedReviewE2eTest` | FIN-016 (R2–R11): faixa no caixa e no acerto, revisão R$ 10 / Já pagou em dinheiro / Não cobrar, reabrir e cobrar a convidada, mesmo requestId, 403, sem aviso ao atleta | Central de avisos pela tela |
| `group-pix` | `GroupPixE2eTest` | FIN-008 (X2–X7): sem a seção com cobrança aberta; Recebi; seção depois de Minhas cobranças (atleta) e da Galera (gestor); Copiar; grupo sem chave sem seção | Menu ⋮ do devedor (X3) |
| `receivables-off` | `ReceivablesOffE2eTest` | DEB-141/142/149: gestor sem oferta/tela de recebimento no grupo, caixa e editar grupo; atleta copia o Pix sem "Pagar" | Recebimento ligado |
| `advance-payment` | `AdvancePaymentE2eTest` | PROM-010 (A1–A14, A17): opção só na Escolha manual; espera com "Pague R$ 20 para entrar"; Falta pagar → Recebi → Colocar; folha do convidado; Não vou mantém a baixa; Ordem de chegada desliga | Acerto do jogo encerrado (A15–A16) |
| `group-list` | `GroupListE2eTest` | DEB-145: três grupos em ordem, cabeçalho, tocar fora fecha sem responder, Vou → "Você vai", Não vou → "Não vai", Admin só do dono | Foto conferida só pela fixture (o print mostra) |
| `app-update` | `AppUpdateE2eTest` | DEB-117 (F5): mínima do iOS acima → folha que não fecha ("Atualizar na App Store"; voltar e tocar fora não fecham); recomendada fecha uma vez; sem versão nada | O botão não abre a App Store no simulador: só se confere que existe |

`app-update` roda por último: mexe nas versões do app no servidor (o teste devolve ao fim).

## Executar

Pré-requisitos: Xcode (`DEVELOPER_DIR`), simulador iOS ligado, JDK 21 no `JAVA_HOME`/`PATH` (backend), JBR do Android
Studio para o Gradle do mobile (`SAQZ_E2E_MOBILE_JAVA_HOME`, padrão `/Applications/Android Studio.app/Contents/jbr/Contents/Home`),
Docker, Node e o Firebase CLI 15.25.1 (`SAQZ_E2E_FIREBASE_BIN`, como no Android).

```sh
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
export SAQZ_E2E_FIREBASE_BIN=~/.saqz-multidev/tools/node_modules/firebase-tools/lib/bin/firebase.js
node tests/e2e/ios/run.mjs --udid BAB08A28-4E8C-4AA3-BF6A-54904566BF0F                      # todos
node tests/e2e/ios/run.mjs --udid <UDID> --scenario cashbox,game-fee                          # alguns
```

O runner:

1. gera o `saqz-api-config.plist` com `http://127.0.0.1:18080` e faz `build-for-testing` uma vez em
   `mobile/ios-app/build/e2e-derived` (refaz o build uma vez se o Info.plist sair com outro endereço; não usa as portas,
   então roda antes da trava);
2. segura a trava `/tmp/saqz-e2e.lock` (dono `ios`; espera a do Android), recusa 18080/9099/4400/4500 ocupadas, sobe
   Postgres no Docker, Auth Emulator e o bootJar com o mesmo ambiente do Android, e faz o seed dos cenários escolhidos;
3. por cenário: desinstala `app.saqz`, reseta o keychain (a sessão do Firebase sobrevive à desinstalação), limpa a área de
   transferência e roda `test-without-building -only-testing:SaqzIOSUITests/<Classe>` com `-resultBundlePath`. A fixture
   vai em base64 por `TEST_RUNNER_SAQZ_E2E_FIXTURE`; a versão do build, por `TEST_RUNNER_SAQZ_E2E_APP_VERSION`;
4. exige no xcresult a contagem exata (executados = aprovados = esperado, zero falha e zero skip). Fora do runner o teste
   é pulado, e skip conta como falha. Nos cenários que copiam o Pix, confere com `simctl pbpaste` a chave da fixture
   (o processo do XCUITest não lê o que o app copiou);
5. encerra serviços, remove o container e solta a trava sempre, também em falha.

Testes do runner: `node --test tests/e2e/ios/guard.test.mjs`.

## Resultado e prints

- xcresult por cenário: `mobile/ios-app/build/e2e-results/<cenário>.xcresult`.
- Prints: `mobile/ios-app/build/e2e-shots/<cenário>/<NN>-<passo>.png`, exportados dos anexos `shot("<passo>")`
  (`<cenário>-<NN>-<passo>`, `lifetime = .keepAlways`). Em falha, o último é `NN-falha.png`, e a árvore de acessibilidade
  vai como anexo de texto no xcresult.
- Logs do backend e do Auth Emulator: pasta `saqz-e2e-ios-*` anunciada pelo runner.

## Como o Compose aparece no XCUITest

- `testTag` vira `accessibilityIdentifier`, mas num `Other`. Campos editáveis (`TextView`) e opções de segmentado vêm
  como **irmãos** dentro do mesmo retângulo, não filhos: `type` e `option` procuram pelo retângulo do contêiner.
- Lista preguiçosa só expõe o que está composto: `waitTag`/`waitText` rolam procurando quando o item não aparece.
- O login guarda o último e-mail: `type` apaga o que houver, conferindo o valor.
