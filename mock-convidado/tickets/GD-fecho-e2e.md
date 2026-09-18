# GD · Fecho: cenário e2e `attendance-guest` + suíte tocada (VUL-XXX)

**Onda 4 · depende de: GB2, GM3, GM4 (todos na main)**

## Objetivo

Provar de ponta a ponta, no app instalado contra o backend real, as decisões A–F do `CONTRATO.md`:
membro na fila leva um convidado pela UI, o gestor tira o convidado, a desistência do anfitrião derruba o
convidado, a promoção cobra a linha do convidado em nome do anfitrião. E rodar os cenários que a feature
encosta. Nenhum código de produção muda aqui: bug achado = PARE e relate (vira ticket).

`E` = `mobile/android-app/src/e2e/kotlin/br/com/saqz/androidapp`
`X` = `tests/e2e/android`

## Arquivos (lista FECHADA)

- NOVO `E/GameGuestE2eTest.kt`
- EDITAR `X/guard.mjs` (1 linha na tabela `scenarios`), `X/guard.test.mjs` (lista de nomes), `X/fixture.mjs` (fixture do cenário), `X/README.md` (1 linha na tabela + nome na lista)

Fora da lista = PARE e relate por SendMessage para `main`.

## Passo 0

`git fetch origin && git switch -c vul-XXX-convidado-e2e origin/main`;
`git grep -c "attendance/guests" -- backend/features/groups/src/main` >= 1 e `git grep -c "GameGuestTags" -- mobile` >= 1; senão PARE. Commit cedo.

## Passo 1 · `X/guard.mjs` + `X/guard.test.mjs`

Na tabela `scenarios`, logo depois de `'attendance-order': [...]`, acrescente `'attendance-guest': ['GameGuestE2eTest', 1],`.
Em `guard.test.mjs`, insira `'attendance-guest'` na lista esperada na MESMA posição (depois de `'attendance-order'`).
Rode `node --test tests/e2e/android/guard.test.mjs` — verde.

## Passo 2 · `X/fixture.mjs`

O cenário começa com `attendance`, então herda o bloco `if (name.startsWith('attendance'))`: jogo publicado
(capacidade 2, taxa do grupo 2000 → `useDefaultGameFee: true`), `owner` e `other` CONFIRMADOS, `athlete` (MENSALISTA)
sem resposta, `mensalista_priority=false`. Acrescente, dentro desse bloco, depois do `if (name === 'attendance-order') {...}`:

```js
        if (name === 'attendance-guest') {
          // O anfitrião entra na fila pela API (precondição); levar o convidado é ação de UI.
          const waiting = await request(`${api}/api/groups/${group}/games/${game.id}/attendance`, 'PUT',
            { requestId: randomUUID(), intent: 'CONFIRM' }, athlete.token);
          assert.equal(waiting.attendance.status, 'WAITLISTED');
        }
```

## Passo 3 · `E/GameGuestE2eTest.kt`

Mesma armação de `AttendanceE2eTest` (`InstalledE2e("attendance-guest")`). Tags: `group-details-view-game` abre o jogo a partir
do detalhe do grupo; as do convidado estão em `GameGuestTags` (`game-guest-add`, `game-guest-name`, `game-guest-submit`,
`game-guest-row-<host>#<seq>`, `game-guest-remove-<host>#<seq>`, `game-guest-remove-confirm`, `game-guest-notice`). O harness
exige exatamente 1 nó por tag. Um teste:

```kotlin
    @Test
    fun hostBringsAGuestOrganizerRemovesItAndHostDeclineDropsTheRest() {
        val attendancePath = "/api/groups/$group/games/${data.getString("game")}/attendance"
        val host = actor("athlete").getString("id")

        // 1) anfitrião na fila leva "Rafa Moreira" pela UI → fila: anfitrião 1º, convidado 2º
        login("athlete")
        openGroup()
        click("group-details-view-game", scroll = true)
        click("game-guest-add", scroll = true)
        input("game-guest-name", "Rafa Moreira")
        click("game-guest-submit")
        waitText("Rafa Moreira entrou na lista de espera.")
        waitTag("game-guest-row-$host#1")
        ui.onNodeWithText("Convidado seu").performScrollTo().assertIsDisplayed()
        var roster = api("athlete", "$attendancePath/roster")
        var waitlisted = roster.getJSONArray("waitlisted")
        assertEquals(2, waitlisted.length())
        assertEquals(1, waitlisted.getJSONObject(1).getInt("guestSeq"))
        assertEquals("Rafa Moreira", waitlisted.getJSONObject(1).getString("displayName"))
        assertEquals(host, waitlisted.getJSONObject(1).getString("memberId"))
        assertEquals(2, api("athlete", attendancePath).getInt("waitlistCount"))
        // regra E: convidado não é "sem resposta" nem "não vai"
        assertEquals(0, api("athlete", attendancePath).getInt("declinedCount"))

        // 2) segundo convidado → guestSeq 2; sem limite
        click("game-guest-add", scroll = true)
        input("game-guest-name", "Ju Andrade")
        click("game-guest-submit")
        waitTag("game-guest-row-$host#2")
        back(); back()
        logout()

        // 3) gestor tira o 2º convidado pela UI; o 1º fica
        login("owner")
        openGroup()
        click("group-details-view-game", scroll = true)
        ui.onNodeWithText("Convidado de ${actor("athlete").getString("displayName")}").performScrollTo().assertIsDisplayed()
        click("game-guest-remove-$host#2", scroll = true)
        click("game-guest-remove-confirm")
        waitText("Ju Andrade saiu do jogo.")
        roster = api("owner", "$attendancePath/roster")
        waitlisted = roster.getJSONArray("waitlisted")
        assertEquals(2, waitlisted.length())
        // 4) gestor desiste (era CONFIRMADO) → FIFO promove o ANFITRIÃO (1º da fila), não o convidado
        back()
        click("group-game-response-change", scroll = true)
        click("group-game-response-not-going", scroll = true)
        waitText("Você não vai jogar.")
        roster = api("owner", "$attendancePath/roster")
        val confirmed = roster.getJSONArray("confirmed")
        assertEquals(2, confirmed.length())
        assertEquals(host, confirmed.getJSONObject(1).getString("memberId"))
        assertEquals(0, confirmed.getJSONObject(1).getInt("guestSeq"))
        assertEquals(1, roster.getJSONArray("waitlisted").length())
        // 5) gestor aumenta a capacidade para 3 → convidado promovido; cobrança nasce em nome do anfitrião
        val gamePath = "/api/groups/$group/games/${data.getString("game")}"
        val version = api("owner", gamePath).getLong("version")
        api("owner", "$gamePath/capacity", "PUT", JSONObject().put("requestId", java.util.UUID.randomUUID().toString()).put("capacity", 3), headers = mapOf("If-Match" to "\"$version\""))
        roster = api("owner", "$attendancePath/roster")
        assertEquals(3, roster.getJSONArray("confirmed").length())
        val charges = api("owner", "/api/groups/$group/charges").getJSONArray("charges")
        val guestCharge = (0 until charges.length()).map { charges.getJSONObject(it) }
            .single { it.optString("guestDisplayName", "") == "Rafa Moreira" }
        assertEquals(host, guestCharge.getString("memberId"))
        assertEquals("PENDING", guestCharge.getString("status"))
        assertEquals(2000L, guestCharge.getLong("amountCents"))
        logout()

        // 6) anfitrião desiste → convidado confirmado cai junto e a cobrança dele é cancelada (regras B e F)
        login("athlete")
        openGroup()
        click("group-game-response-change", scroll = true)
        click("group-game-response-not-going", scroll = true)
        waitText("Você não vai jogar.")
        roster = api("athlete", "$attendancePath/roster")
        assertEquals(1, roster.getJSONArray("confirmed").length())
        val after = api("owner", "/api/groups/$group/charges").getJSONArray("charges")
        val cancelled = (0 until after.length()).map { after.getJSONObject(it) }
            .single { it.optString("guestDisplayName", "") == "Rafa Moreira" }
        assertEquals("CANCELLED", cancelled.getString("status"))
        // 7) sem resposta própria, o botão fica desabilitado (regra A)
        click("group-details-view-game", scroll = true)
        ui.onNodeWithText("Responda “Vou” para poder levar alguém.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("game-guest-add").assertIsNotEnabled()
    }
```

Confira antes de escrever: (a) o campo do nome do ator no fixture (`displayName`?) — use o que `actor("athlete")` expõe;
(b) a assinatura de `api(...)` para `headers`/`PUT` (`E2eSupport.kt:120`); (c) o texto exato das strings em
`strings_game_guest.xml` (copie de lá, não daqui); (d) se `back()` a partir do jogo volta ao detalhe do grupo.
Se a resposta de capacidade for 428/412 por `If-Match`, copie o formato usado em outro teste que manda `If-Match`.

## Passo 4 · README

Na tabela de `tests/e2e/android/README.md`, linha nova depois de `AttendanceOrderE2eTest`:
`| \`GameGuestE2eTest.hostBringsAGuestOrganizerRemovesItAndHostDeclineDropsTheRest\` | VUL-239, convidado de jogo | Anfitrião na fila leva convidado pela UI (fila, seq 1 e 2); gestor tira um; desistência do gestor promove o anfitrião e não o convidado; capacidade maior promove o convidado e cobra o anfitrião; desistência do anfitrião derruba o convidado e cancela a cobrança; sem resposta própria não há botão. |`
e `attendance-guest` na lista de nomes disponíveis.

## Gates e execução

```
node --test tests/e2e/android/guard.test.mjs
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :android-app:compileDevDebugAndroidTestKotlin -Psaqz.e2e=true
```
Depois o e2e de verdade (siga `tests/e2e/android/README.md` §Executar; emulador `Saqz_API_30` headless; Docker via Colima;
firebase-tools 15.25.1): `attendance-guest`, depois `attendance`, `attendance-order`, `leave`, `settlement`, `payments`,
`reminders`. Cenário vermelho: NÃO conserte — guarde as últimas 80 linhas e o assert; siga; tabela no PR. Desligue o
emulador no fim (`adb -s <serial> emu kill`). O `attendance-guest` vermelho por assert do PRÓPRIO teste (texto/tag) pode
ser ajustado UMA vez conferindo a string/tag real; vermelho por comportamento do app/backend = PARE e relate.

## PR

Título: `test(e2e): convidado de jogo de ponta a ponta (VUL-XXX)`. Corpo: tabela cenário → resultado, e a lista
"decisão A–F → passo do teste que a prova". Commits PT-BR terminando com `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`;
PR ready com `GH_TOKEN=$(gh auth token --user bruno-halmeida)`, corpo terminando com
`🤖 Generated with [Claude Code](https://claude.com/claude-code)`. PARE depois do PR; depois só `CORRECAO:`.
