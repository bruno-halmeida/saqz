# D · Fecho do detalhe do grupo (VUL-XXX)

**Onda 4 · depende de: tudo (S, A, T, V1, B, V2, C1, C2, C3, C4, C5, H — todos na main) · bloqueia: nada**

## Objetivo

Tirar o andaime: apagar o código morto da tela antiga, enxugar as duas assinaturas com
`@Suppress("UnusedParameter")`, preencher o preview-base com os rótulos novos (hoje o hero sai
vazio nas cenas dos outros blocos), regravar TODOS os prints, e rodar o e2e que nenhum PR rodou.
Leia `CONTRATO.md` (regras, gates, protocolo do worker). **Nenhum comportamento muda.**

`DET` = `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/details`
`SDET` = `mobile/features/groups/presentation/src/androidHostTest/kotlin/br/com/saqz/groups/presentation/ui/details`
`RES` = `mobile/features/groups/presentation/src/commonMain/composeResources/values`
`G` = `JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile`

## Arquivos (lista FECHADA)

- APAGAR `DET/GroupDetailsSections.kt`, `DET/GroupGameResponseSection.kt`
- EDITAR `DET/GroupDetailsScreen.kt`, `DET/GroupTopBlock.kt`, `DET/GroupOwnDebtBlock.kt`, `DET/GroupDetailsPreviewData.kt`
- EDITAR `SDET/GroupDetailsScreenshotTest.kt`
- EDITAR `RES/strings.xml` e `RES/strings_group_details.xml` — SÓ remoção de chave órfã (passo 5)
- EDITAR `mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/SaqzHeroCard.kt` — só KDoc
- PNGs regravados em `mobile/features/groups/presentation/screenshots/**`

Tocar em arquivo fora desta lista = parar e avisar o orquestrador. **NÃO apague**
`DET/GroupOwnChargesSection.kt` (a tela "Minhas mensalidades" usa).

## Passo 0

```
git fetch origin && git switch -c vul-XXX-fecho-detalhe-grupo origin/main
git log origin/main --oneline | grep -c -E "VUL-23[0-6]"   # tem que dar 7
```
Diferente de 7: PARE.

## Passo 1 · apagar o código morto

```
git rm DET/GroupDetailsSections.kt DET/GroupGameResponseSection.kt
G :features:groups:presentation:compileKotlinMetadata
```
Tem que compilar. Se acusar referência não resolvida, PARE e relate o erro (não recrie função).
Nota: o `GroupInviteCard` usado em `ui/list/` é OUTRA função (de `GroupListSections.kt`); não é afetada.

## Passo 2 · `GroupTopContent` sem parâmetro morto

Em `DET/GroupTopBlock.kt`, substitua o KDoc + `@Suppress` + assinatura de `GroupTopContent` por:

```kotlin
/** O topo da coluna rolável: só o banner da foto (pós-criação). */
@Composable
internal fun ColumnScope.GroupTopContent(photoFailed: Boolean) {
    if (photoFailed) GroupPhotoFailedBanner()
}
```

Em `DET/GroupDetailsScreen.kt` troque a chamada por `GroupTopContent(photoFailed = photoFailed)`.
Remova de `GroupTopBlock.kt` os imports que ficarem sem uso (para cada import suspeito,
`grep -c "\bNome\b" arquivo` = 1 → apague).

## Passo 3 · `GroupOwnChargesSettledBlock` sem `onIntent`

Em `DET/GroupOwnDebtBlock.kt`: apague a linha `@Suppress("UnusedParameter")`, apague o parágrafo
do KDoc que começa em `[onIntent] fica sem uso de propósito` (até o fim desse parágrafo), e apague
o parâmetro `onIntent: (GroupDetailsIntent) -> Unit,` da assinatura. Atualize as DUAS chamadas:

- `DET/GroupDetailsScreen.kt`: `GroupOwnChargesSettledBlock(state = state)`
- preview no fim de `GroupOwnDebtBlock.kt`: `GroupOwnChargesSettledBlock(state = GroupOwnDebtPreviewData.settled)`

Conferência: `git grep -n "@Suppress" -- DET` → **nenhuma linha**.

## Passo 4 · preview-base com os rótulos novos (`DET/GroupDetailsPreviewData.kt`)

4a. No `val nextGame = NextGameUi(`, logo depois da linha `deadline = "Encerra hoje · 18h",` acrescente:

```kotlin
        display = "Terça, 19h30",
        meta = "28 de julho · CERET — Quadra 2",
        address = "R. Canuto Abreu, s/n · Tatuapé",
        deadlineLine = "As confirmações encerram hoje às 18h00.",
        deadlineShort = "Encerra 28/07 · 18h00",
        bellLabel = "Avisamos você se abrir vaga até 18h00 de 28/07.",
```

4b. No `val ownCharges = OwnChargesUi(`, logo depois da linha `pix = PixUi(...)`, acrescente:

```kotlin
        debt = GroupOwnDebtUi(
            eyebrow = "Mensalidade · Agosto",
            totalLabel = "R$ 95,00",
            dueLabel = "Venceu em 10/08",
            overdue = true,
            countLabel = "2 cobranças em aberto",
            receiverLabel = "Pix de Lucas Prado",
        ),
```
(+ `import br.com.saqz.groups.presentation.details.GroupOwnDebtUi` se o pacote exigir — siga os imports vizinhos.)

4c. Em `memberOwnChargesSettled`, troque `ownCharges.copy(pending = emptyList(), pix = null)` por
`ownCharges.copy(pending = emptyList(), pix = null, debt = null)`.

4d. No `val admin`, troque a linha do `cashbox` por `cashbox = CashboxUi(summary = "Saldo R$ 380,00"),`.

**NÃO** referencie `GroupWaitingPreviewData`/`GroupAgendaPreviewData` dentro deste object: eles já
leem `GroupDetailsPreviewData` e o ciclo de inicialização entre objects entrega `null` em silêncio.
A composição com agenda/espera mora no teste (passo 7).

Se (no passo 7) `GroupWaitingPreviewData.waiting` ou algum `GroupAgendaPreviewData.*` for `private`,
NÃO mude a visibilidade: PARE e relate. NÃO edite os `*PreviewData.kt` dos blocos (as cópias redundantes lá são inofensivas).

## Passo 5 · strings órfãs (só remoção)

Rode, da raiz:

```sh
for f in RES/strings.xml RES/strings_group_details.xml; do
  grep -o 'name="group_details_[a-z0-9_]*"' $f | sed 's/name="//;s/"//' | while read k; do
    n=$(git grep -c "\b$k\b" -- mobile ':!*.xml' | wc -l | tr -d ' ')
    [ "$n" = "0" ] && echo "ORFA $f $k"
  done
done
```
(troque `RES`). Para cada linha `ORFA`, apague a linha `<string name="...">` correspondente.
`group_details_notify_pending` TEM que aparecer na lista; se não aparecer, PARE. Só chaves com
prefixo `group_details_` entram; não toque em nenhuma outra. Liste no corpo do PR as chaves removidas.

## Passo 6 · KDoc do `SaqzHeroCard`

Na primeira linha do KDoc (`* Bloco de destaque da Home nova: ...`), troque `da Home nova` por
`da Home nova e do detalhe do grupo (2º uso, VUL-231)`. Nada mais.

## Passo 7 · cenas de tela cheia com todos os blocos (`SDET/GroupDetailsScreenshotTest.kt`)

Acrescente no topo do arquivo (depois dos imports, antes da classe):

```kotlin
private val fullAdmin = GroupDetailsPreviewData.admin.copy(
    waiting = GroupWaitingPreviewData.waiting,
    agenda = listOf(GroupAgendaPreviewData.pending, GroupAgendaPreviewData.going, GroupAgendaPreviewData.draft),
)
private val fullMember = GroupDetailsPreviewData.member.copy(
    agenda = listOf(GroupAgendaPreviewData.pending, GroupAgendaPreviewData.going, GroupAgendaPreviewData.waitlisted),
)
```

e troque, nas cenas `admin()` e `member()`, `GroupDetailsPreviewData.admin` → `fullAdmin` e
`GroupDetailsPreviewData.member` → `fullMember`. As outras 4 cenas ficam como estão. Nenhuma cena nova.

## Gates (nesta ordem, todos verdes)

```
G :features:groups:presentation:detektAll :core:design-system:detektAll
G :features:groups:presentation:iosSimulatorArm64Test
G :features:groups:presentation:recordRoborazziAndroidHostTest
G :android-app:testDevDebugUnitTest
G :compose-app:iosSimulatorArm64Test
G :android-app:compileDevDebugAndroidTestKotlin -Psaqz.e2e=true
```

Teste de UI que quebrar por causa do passo 4 (ex.: passava porque o hero estava vazio): PARE e
relate o assert — não edite teste de outro dono.

**Olhe os PNGs**: abra `screenshots/details/group-details-member.png` e `group-details-admin.png`
e confira contra `_mock-grupo/png/Main.png` e o PNG do gestor: barra colada ao topo; hero com
"Terça, 19h30"; membro = hero → auto-confirmar → Minhas cobranças (ticket R$ 95,00) → Próximos
jogos → Mural → Galera → Sair; gestor = hero com placar → Esperando você → Próximos jogos → Mural
→ Gestão (Caixa "Saldo R$ 380,00"). Abra também 1 PNG de cada pasta `hero/`, `own-debt/`,
`waiting/`, `agenda/`, `shell/` e confirme que nenhum hero aparece sem título. Commite TODOS os PNGs
regravados.

## e2e (obrigatório neste ticket)

Siga `tests/e2e/android/README.md` §Executar. Suba o emulador se não houver:
`emulator -avd Saqz_API_30 -no-window -no-audio -no-snapshot-save &` e espere
`adb shell getprop sys.boot_completed` = `1`. Docker: `export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock`
(se `docker ps` falhar, `colima start`). Rode, um por vez:

```
node tests/e2e/android/run.mjs --serial <serial> --scenario leave
... attendance · attendance-order · reminders · communication · payments · settlement · finance
```

Cenário vermelho: **NÃO conserte**. Guarde a saída (últimas 60 linhas + o nome do assert) e siga
para o próximo; no fim relate a tabela cenário → verde/vermelho. Emulador/Docker impossível de
subir: relate o erro exato e siga com o PR marcando e2e como pendente.

## PR

Título: `chore(groups): fecho do detalhe do grupo — código morto, previews e e2e (VUL-XXX)`.
Corpo: o que saiu (arquivos, linhas, chaves), tabela do e2e, e os prints `group-details-member.png`,
`group-details-admin.png`, `group-details-admin-no-game.png`, `group-details-member-no-game.png`
lado a lado com os do mock (`https://raw.githubusercontent.com/bruno-halmeida/saqz/screenshots/mock-grupo/<arquivo>.png`),
empurrados para `screenshots` em `vul-XXX/`. Teto de 2000 linhas: PNG não conta; se o diff de
texto passar (as remoções somam ~900), está dentro. PR ready, e PARE.
