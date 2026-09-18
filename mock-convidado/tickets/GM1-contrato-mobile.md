# GM1 · Mobile: contrato do convidado em domain + data (VUL-XXX)

**Onda 1 · depende de: nada (codifica contra a API do `CONTRATO.md`) · bloqueia: GM2 · paralelo com: GB1**

## Objetivo

Ensinar o domínio e o gateway a (1) ler convidado no roster, (2) adicionar e tirar convidado,
(3) promover convidado, (4) ler o nome do convidado numa cobrança. **Nenhuma tela muda.** Campos novos
têm default, então JSON antigo (backend ainda sem GB2) continua parseando. Leia `CONTRATO.md` (seção API).

`DOM` = `mobile/features/groups/domain/src/commonMain/kotlin/br/com/saqz/groups/domain`
`DATA` = `mobile/features/groups/data/src/commonMain/kotlin/br/com/saqz/groups/data`
`TDATA` = `mobile/features/groups/data/src/commonTest/kotlin/br/com/saqz/groups/data`
`G` = `JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile`

## Arquivos (lista FECHADA)

- EDITAR `DOM/attendance/Attendance.kt`, `DOM/finance/Finance.kt`
- EDITAR `DATA/attendance/KtorAttendanceGateway.kt`, `DATA/finance/KtorFinanceGateways.kt`
- EDITAR `TDATA/attendance/KtorAttendanceGatewayTest.kt`

Fora da lista = PARE e avise (`SendMessage` para `main`) e aguarde. Nenhum fake de teste precisa mudar:
os métodos novos da interface têm corpo padrão. Se algum arquivo fora da lista deixar de compilar, PARE e relate.

## Passo 0

`git fetch origin && git switch -c vul-XXX-convidado-contrato-mobile origin/main`. Primeiro commit logo após o passo 1.

## Passo 1 · `DOM/attendance/Attendance.kt`

1a. `AttendanceRosterMember` — acrescente no fim:

```kotlin
    /** 0 = o próprio membro; 1..n = convidado dele. Para convidado, [memberId] é o id do ANFITRIÃO. */
    val guestSeq: Int = 0,
    /** Nome do anfitrião; só vem quando [guestSeq] > 0. */
    val hostDisplayName: String? = null,
) {
    val isGuest: Boolean get() = guestSeq > 0

    /** Chave única de linha: dois convidados do mesmo anfitrião dividem o [memberId]. */
    val rowKey: String get() = if (isGuest) "$memberId#$guestSeq" else memberId
}
```
(o `)` final da data class vira o `) {` acima.)

1b. `AttendancePromotionCommand` — acrescente no fim `val guestSeq: Int = 0,`.

1c. Depois de `AttendancePromotionCommand`, acrescente:

```kotlin
data class AddGuestCommand(
    val requestId: String,
    val displayName: String,
)
```

1d. Na interface `AttendanceGateway`, depois de `updateAutoConfirmation(...)`, acrescente:

```kotlin
    // ponytail: corpo padrão para os fakes de teste existentes não mudarem; o único adapter real
    // (KtorAttendanceGateway) sobrescreve. Tirar o padrão se surgir um segundo adapter.
    suspend fun addGuest(
        groupId: GroupId,
        gameId: String,
        command: AddGuestCommand,
    ): SaqzResult<AttendanceRosterMember, AttendanceError> = SaqzResult.Failure(AttendanceError.HiddenResource)

    suspend fun removeGuest(
        groupId: GroupId,
        gameId: String,
        hostId: String,
        guestSeq: Int,
    ): SaqzResult<Unit, AttendanceError> = SaqzResult.Failure(AttendanceError.HiddenResource)
```

**NÃO** crie variante nova em `AttendanceError` (quebra todo `when` exaustivo): `HOST_NOT_GOING` mapeia
para `AttendanceError.Conflict` (passo 3d) — a tela já impede o caso.

## Passo 2 · `DOM/finance/Finance.kt`

Em `data class Charge`, acrescente como ÚLTIMO parâmetro:

```kotlin
    /** Nome do convidado quando a cobrança de jogo é de um "+1" do membro; senão `null`. */
    val guestDisplayName: String? = null,
```

## Passo 3 · `DATA/attendance/KtorAttendanceGateway.kt`

3a. `AttendanceRosterMemberTransport` ganha `val guestSeq: Int = 0,` e `val hostDisplayName: String? = null,`.
O mapeador `AttendanceRosterMemberTransport.toDomain()` (no fim do arquivo) passa os dois:
`guestSeq = guestSeq, hostDisplayName = hostDisplayName`.

3b. `AttendancePromotionRequest` ganha `val guestSeq: Int = 0,`; `AttendancePromotionCommand.toRequest()` passa `guestSeq = guestSeq`.

3c. Transports novos, depois de `AttendancePromotionRequest`:

```kotlin
@Serializable
internal data class AddGuestRequest(
    val requestId: String,
    val displayName: String,
)

@Serializable
internal data class GuestTransport(
    val memberId: String,
    val guestSeq: Int,
    val displayName: String,
    val waitlistPosition: Long? = null,
)
```

3d. Métodos novos na classe, depois de `promote(...)`:

```kotlin
    override suspend fun addGuest(
        groupId: GroupId,
        gameId: String,
        command: AddGuestCommand,
    ): SaqzResult<AttendanceRosterMember, AttendanceError> =
        retryTransport(command.requestId.safety(), delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Post,
                "${attendanceRoute(groupId, gameId)}/guests",
                GuestTransport.serializer(),
                NetworkRequest(json.encodeToString(AddGuestRequest(command.requestId, command.displayName))),
            )
        }.let { result ->
            when (result) {
                is NetworkResult.Success -> SaqzResult.Success(
                    AttendanceRosterMember(
                        memberId = result.value.memberId,
                        displayName = result.value.displayName,
                        waitlistPosition = result.value.waitlistPosition,
                        guestSeq = result.value.guestSeq,
                    ),
                )
                is NetworkResult.Failure -> SaqzResult.Failure(result.error.toDomain())
            }
        }

    override suspend fun removeGuest(
        groupId: GroupId,
        gameId: String,
        hostId: String,
        guestSeq: Int,
    ): SaqzResult<Unit, AttendanceError> =
        when (val result = network.executeNoContent(HttpMethod.Delete, "${attendanceRoute(groupId, gameId)}/guests/$hostId/$guestSeq")) {
            is NetworkResult.Success -> SaqzResult.Success(Unit)
            is NetworkResult.Failure -> SaqzResult.Failure(result.error.toDomain())
        }
```
Se `executeNoContent` devolver outro tipo que não `NetworkResult<Unit>` (confira em
`mobile/core/network/.../AuthenticatedNetworkClient.kt:42`), adapte SÓ o `when` para o tipo real, copiando o
padrão de `KtorAthleteGateway.removeAthlete`. Linha > 140 colunas: quebre a rota numa `val route`.

3e. Em `NetworkError.toDomain()`, acrescente ao `when (problem.code)`, antes do `else`:
`"ATTENDANCE_HOST_NOT_GOING" -> AttendanceError.Conflict`.

## Passo 4 · `DATA/finance/KtorFinanceGateways.kt`

`ChargeTransport` ganha no fim `val guestDisplayName: String? = null,`. Em `ChargeTransport.toDomain()`,
acrescente o argumento nomeado `guestDisplayName = guestDisplayName,` depois de `paidMethod.toPaidMethod(),`.
Se existir outro transport de cobrança própria do membro no mesmo arquivo que mapeie para `Charge`
(`grep -n "Charge(" DATA/finance/KtorFinanceGateways.kt`), repita o mesmo campo+mapeamento nele.

## Passo 5 · testes em `TDATA/attendance/KtorAttendanceGatewayTest.kt`

Copiando o estilo dos testes vizinhos de `roster`/`promote` (mesmo motor de rede falso do arquivo), acrescente:

1. `rosterParsesGuestsAndKeepsOldPayloadWorking` — payload com uma entrada sem os campos novos e uma
   com `"guestSeq":1,"hostDisplayName":"Bia Souza"`; assert `isGuest`, `rowKey == "<host>#1"`, e a antiga com `guestSeq == 0`.
2. `addGuestPostsNameAndReturnsTheWaitlistedGuest` — confere método POST, rota `.../attendance/guests`,
   corpo com `requestId` e `displayName`, e o retorno (`guestSeq`, `waitlistPosition`).
3. `addGuestMapsHostNotGoingToConflict` — problema `ATTENDANCE_HOST_NOT_GOING` → `AttendanceError.Conflict`.
4. `removeGuestDeletesByHostAndSeq` — DELETE em `.../attendance/guests/<host>/2`, sucesso → `SaqzResult.Success(Unit)`.
5. `promoteSendsGuestSeq` — corpo do promote contém `"guestSeq":2`.

## Gates

```
G :features:groups:domain:detektAll :features:groups:data:detektAll
G :features:groups:data:iosSimulatorArm64Test
G :features:groups:domain:iosSimulatorArm64Test :features:groups:presentation:iosSimulatorArm64Test
G :compose-app:iosSimulatorArm64Test
G :android-app:testDevDebugUnitTest
```

## PR

Título: `feat(groups): contrato mobile do convidado de jogo (VUL-XXX)`. Sem prints (não há UI). Commits
PT-BR terminando com `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`; PR ready com
`GH_TOKEN=$(gh auth token --user bruno-halmeida)`, corpo terminando com
`🤖 Generated with [Claude Code](https://claude.com/claude-code)`. PARE depois do PR; depois só `CORRECAO:`.
