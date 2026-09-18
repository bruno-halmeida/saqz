# GM4 · Mobile: convidado no acerto, em "Minhas cobranças" e na fila do hero (VUL-XXX)

**Onda 3 · depende de: GM1 (na main) · bloqueia: GD · paralelo com: GM3**

## Objetivo

Três leitores do roster/cobrança que hoje assumem "1 linha por membro" e erram com convidado
(pranchas 14 e 16 do canvas). Sem tela nova. Leia `CONTRATO.md`.

`PRES` = `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation`
`TPRES` = `mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation`
`RES` = `mobile/features/groups/presentation/src/commonMain/composeResources/values`
`G` = `JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile`

## Arquivos (lista FECHADA)

- EDITAR `PRES/ui/finance/settlement/GameSettlementViewModel.kt`
- EDITAR `PRES/details/GroupDetailsViewModel.kt`
- EDITAR `RES/strings_minhas_cobrancas.xml` (1 chave nova)
- EDITAR `TPRES/ui/finance/settlement/GameSettlementViewModelTest.kt`, `TPRES/details/GroupDetailsViewModelTest.kt`

Fora da lista = PARE, SendMessage para `main`, aguarde.

## Passo 0

`git fetch origin && git switch -c vul-XXX-convidado-acerto origin/main`;
`git grep -c "guestDisplayName" -- mobile/features/groups/domain` >= 1 (GM1 na main), senão PARE. Commit cedo.

## Passo 1 · acerto (`GameSettlementViewModel.kt`)

Bug latente: `roster.confirmed.associate { it.memberId to it.displayName }` — o convidado divide o `memberId` com o
anfitrião, então o nome do convidado SOBRESCREVE o do anfitrião. Troque as duas linhas (`val names` e o `.map { it.toDiarist(...) }`) por:

```kotlin
        val names = roster.confirmed.filterNot { it.isGuest }.associate { it.memberId to it.displayName }
        val gameCharges = charges
            .filter { it.kind == ChargeKind.Game && it.gameId == game.id && it.status != ChargeStatus.Cancelled }
            .map { charge ->
                val memberName = names[charge.memberId] ?: athleteById[charge.memberId]?.displayName ?: "Membro"
                charge.toDiarist(memberName)
            }
```
(mantenha o `.filter` exatamente como está hoje no arquivo.) E `toDiarist` vira:

```kotlin
    /** [memberName] é de quem PAGA. Cobrança de convidado mostra o convidado e diz quem paga. */
    private fun Charge.toDiarist(memberName: String): GameSettlementDiaristUi {
        val guest = guestDisplayName
        return GameSettlementDiaristUi(
            chargeId = id,
            memberId = memberId,
            name = guest ?: memberName,
            meta = if (guest != null) "Convidado de $memberName · quem paga é $memberName" else "Diarista",
            amountLabel = formatBrl(amountCents),
            amountCents = amountCents,
            dueDate = dueDate,
            chargeVersion = version,
            status = status,
            referenceLabel = if (guest != null) {
                "Convidado $guest · jogo de ${formatDate(dueDate)}"
            } else {
                "Diarista · jogo de ${formatDate(dueDate)}"
            },
        )
    }
```
(Os literais seguem o padrão deste arquivo, que já escreve "Diarista" direto.) `monthlyMemberCount`: troque
`roster.confirmed.count {` por `roster.confirmed.filterNot { it.isGuest }.count {` — convidado de mensalista não é mensalista.
Se alguma lista do estado usa `memberId` como chave de `LazyColumn`/`key` (`grep -n "key = " PRES/ui/finance/settlement/*.kt`),
confira que a chave é `chargeId`; se for `memberId`, PARE e relate (arquivo de UI fora da lista).

## Passo 2 · `RES/strings_minhas_cobrancas.xml`

Logo depois de `own_charges_game`, acrescente: `<string name="own_charges_guest">Convidado: %1$s</string>`.

## Passo 3 · `GroupDetailsViewModel.kt`

3a. Em `Charge.toOwnCharge`, o ramo `ChargeKind.Game ->` vira:
`ChargeKind.Game -> guestDisplayName?.let { getString(Res.string.own_charges_guest, it) } ?: getString(Res.string.own_charges_game)`
(+ import `br.com.saqz.groups.resources.own_charges_guest`).

3b. Em `toWaitlist(...)`, o convidado do próprio usuário NÃO é "você": troque
`isSelf = member.memberId == memberId` por `isSelf = !member.isGuest && member.memberId == memberId`.

3c. Procure outros pontos do arquivo que casem o roster pelo id (`grep -n "memberId ==" PRES/details/GroupDetailsViewModel.kt`)
e aplique o MESMO `!it.isGuest &&` onde o sentido for "é a linha de quem olha". Liste-os no PR. Se o sentido for outro, não toque.

## Passo 4 · testes (estilo e fakes dos próprios arquivos)

`GameSettlementViewModelTest`: `guestChargeShowsTheGuestAndWhoPays` (host confirmado + convidado confirmado no roster, duas cobranças
GAME do mesmo `memberId`, uma com `guestDisplayName = "Rafa Moreira"` → duas linhas; a do host mantém o NOME DO HOST; a do convidado tem
`name == "Rafa Moreira"` e meta com o host) · `guestDoesNotCountAsMonthlyMember`.
`GroupDetailsViewModelTest`: `ownGuestChargeIsTitledWithTheGuestName` · `myGuestInTheQueueIsNotMarkedAsSelf`.

## Gates

```
G :features:groups:presentation:detektAll
G :features:groups:presentation:iosSimulatorArm64Test --tests "*GameSettlementViewModelTest*" --tests "*GroupDetailsViewModelTest*"
G :features:groups:presentation:iosSimulatorArm64Test
G :features:groups:presentation:recordRoborazziAndroidHostTest     # nenhum PNG pode mudar
G :compose-app:iosSimulatorArm64Test
G :android-app:testDevDebugUnitTest
```

## PR

Título: `fix(groups): acerto, cobranças e fila leem o convidado de jogo (VUL-XXX)`. Sem prints (nenhum PNG muda). Commits PT-BR
terminando com `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`; PR ready com `GH_TOKEN=$(gh auth token --user bruno-halmeida)`,
corpo terminando com `🤖 Generated with [Claude Code](https://claude.com/claude-code)`. PARE depois do PR; depois só `CORRECAO:`.
