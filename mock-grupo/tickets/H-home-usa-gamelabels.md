# H · `HomeViewModel` usa `game/GameLabels.kt` (VUL-XXX)

Refatoração pura: **nenhum texto da Início muda**. O `HomeViewModel` tem cópias privadas dos
rótulos de data/hora que o V1 levou para `PRES/game/GameLabels.kt`. Este ticket troca as cópias
pelas funções compartilhadas. Leia `CONTRATO.md` (regras, gates, protocolo do worker).

`PRES` = `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation`

## Arquivos (dono exclusivo)

- `PRES/home/HomeViewModel.kt` — ÚNICO arquivo editado.

**Proibido:** editar `GameLabels.kt` (está no teto de 10 funções não privadas do detekt), editar
qualquer teste, editar `strings*.xml`. Se um teste da Início ficar vermelho, a troca mudou um
texto: PARE e relate o diff do assert — não ajuste o teste.

## Passo 0 · branch e checagem

```
git fetch origin && git switch -c vul-XXX-home-usa-gamelabels origin/main
grep -n "home_month_january\b" PRES/game/GameLabels.kt   # precisa achar 1+ linha
```
Se o grep não achar nada, o mês curto do `GameLabels` usa outra chave: PARE e relate.

## Passo 1 · imports

Acrescente (ordem alfabética, junto dos outros `br.com.saqz.groups.presentation.*`):

```kotlin
import br.com.saqz.groups.presentation.game.gameBellLabel
import br.com.saqz.groups.presentation.game.gameDateLabel
import br.com.saqz.groups.presentation.game.gameDeadlineSentence
import br.com.saqz.groups.presentation.game.gameDeadlineShort
import br.com.saqz.groups.presentation.game.gameHeroDisplay
import br.com.saqz.groups.presentation.game.gameHeroMeta
import br.com.saqz.groups.presentation.game.gameShortMonthLabel
import br.com.saqz.groups.presentation.game.gameTimeLabel
import br.com.saqz.groups.presentation.game.gameTimeZone
import br.com.saqz.groups.presentation.game.gameWeekdayLabel
```

## Passo 2 · `HomeNextGame.toUi()`

2a. Dentro do `val dateTime = startsAtLocal?.let { getString(Res.string.home_game_date_time, ...) }`,
troque só o 2º e o 3º argumento:

```kotlin
                getString(it.date.dayOfWeek.shortResource()),
                it.date.gameDateLabel(),
                it.gameTimeLabel(),
```

2b. Troque a linha do `val time`:

```kotlin
        val time = startsAtLocal?.gameTimeLabel() ?: startsAt
```

2c. A linha `val weekday = startsAtLocal?.let { getString(it.date.dayOfWeek.longResource()) } ?: ""`
**fica como está** (o `HomeNextGameUi.weekday` é minúsculo; `gameWeekdayLabel` capitaliza).

2d. Troque a chamada do `heroLabels`:

```kotlin
        val (display, meta) = heroLabels(startsAtLocal, dateTime, local)
```

2e. Troque `deadlineBellLabel = deadline?.let { bellDeadlineLabel(it) } ?: "",` por:

```kotlin
            deadlineBellLabel = deadline?.gameBellLabel() ?: "",
```

Se `time` deixar de ser usado em `toUi()` depois de 2d, o compilador avisa; nesse caso NÃO apague —
ele ainda é usado no `HomeNextGameUi(...)`. Se de fato ficar sem uso (warning "unused variable"),
PARE e relate.

## Passo 3 · `HomeUpcomingGame.toUi()`

Troque as três linhas `val time`, `val weekday`, `val dateLabel` por:

```kotlin
        val time = local?.gameTimeLabel() ?: startsAt
        val weekday = local?.date?.dayOfWeek?.gameWeekdayLabel() ?: ""
        val dateLabel = local?.date?.gameDateLabel() ?: startsAt
```

e a linha `month = local?.let { getString((it.month.ordinal + 1).monthResource()) } ?: "",` por:

```kotlin
            month = local?.let { gameShortMonthLabel(it.month.ordinal + 1) } ?: "",
```

## Passo 4 · `deadlineLabel` (substitua a função inteira)

```kotlin
    private suspend fun deadlineLabel(deadline: kotlinx.datetime.LocalDateTime?, zone: TimeZone): String =
        deadline?.gameDeadlineSentence(now.now().toLocalDateTime(zone).date) ?: ""
```

## Passo 5 · apague o `gameTimeZone` privado

Apague as duas linhas `private fun gameTimeZone(zoneId: String): TimeZone = runCatching { ... }`.
As chamadas `gameTimeZone(zoneId)` passam a resolver para o import do passo 1.

## Passo 6 · mês da cobrança (a outra chamada de `monthResource()`)

Troque `month = getString(monthIndex.monthResource()),` por:

```kotlin
            month = gameShortMonthLabel(monthIndex),
```

## Passo 7 · `HomeGameToSettle.toUi()`

Troque o bloco `val formattedDate = local?.let { getString(Res.string.home_date, ...) } ?: startsAt` por:

```kotlin
        val formattedDate = local?.date?.gameDateLabel() ?: startsAt
```

## Passo 8 · `heroLabels` (substitua a função inteira, KDoc fica)

```kotlin
    private suspend fun heroLabels(
        startsAtLocal: kotlinx.datetime.LocalDateTime?,
        dateTime: String,
        local: String,
    ): Pair<String, String> {
        startsAtLocal ?: return dateTime to local
        return startsAtLocal.gameHeroDisplay() to startsAtLocal.gameHeroMeta(local)
    }
```

## Passo 9 · `adminHeroDeadlineLabel` (substitua a função inteira)

```kotlin
    private suspend fun adminHeroDeadlineLabel(deadline: kotlinx.datetime.LocalDateTime, open: Boolean): String =
        if (open) {
            deadline.gameDeadlineShort()
        } else {
            getString(Res.string.home_admin_hero_deadline_closed, deadline.date.gameDateLabel(), deadline.gameTimeLabel())
        }
```

## Passo 10 · apague o que ficou morto

1. A função `bellDeadlineLabel` inteira.
2. A função top-level `private fun Int.monthResource(): StringResource` inteira.
3. `private fun Int.twoDigits()` **só se** `grep -c "twoDigits()" PRES/home/HomeViewModel.kt` der 1
   (sobrou só a declaração). Se der mais (ex.: `formatCivilDate` usa), deixe.
4. **Ficam:** `shortResource()`, `longResource()`, `homeLongMonthResource()`, `capitalized()` se
   ainda tiverem uso (confira com grep; o que der contagem 1 = só a declaração → apague).
5. Imports: para cada `import br.com.saqz.groups.resources.home_*` e para `DatePeriod`/`plus`, rode
   `grep -c "<nome>\b" PRES/home/HomeViewModel.kt`; contagem 1 = só o import → apague a linha.
   Esperado sair: os 12 `home_month_<mês>` curtos, `home_deadline_today`, `home_deadline_tomorrow`,
   `home_deadline_date`, `home_waitlist_reserva_bell`, `home_game_display`, `home_game_meta`,
   `home_admin_hero_deadline`. `home_date`/`home_time` só saem se a contagem der 1.

## Gates (todos verdes, nesta ordem)

```
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:compileKotlinMetadata
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:testAndroidHostTest --tests "*HomeViewModelTest*" --tests "*GameLabelsTest*"
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:testAndroidHostTest
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:detektAll
git diff --stat origin/main...HEAD    # só HomeViewModel.kt; remoções > adições
```

Se o nome de alguma task não existir, rode `JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:tasks --all | grep -i -E "test|detekt"` e use a equivalente do `CONTRATO.md`.

## PR

Título: `refactor(home): HomeViewModel usa os rótulos de game/GameLabels (VUL-XXX)`.
Corpo: "Sem mudança visual — refatoração; `HomeViewModelTest` intacto e verde é a prova." Sem prints
(não há UI tocada). PR ready, e PARE.
