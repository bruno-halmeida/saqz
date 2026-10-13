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
