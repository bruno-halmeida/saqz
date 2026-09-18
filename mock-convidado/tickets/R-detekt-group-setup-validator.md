# R · detekt: `GroupSetupValidator.validate` abaixo do teto de complexidade (VUL-247)

Refatoração pura — nenhuma regra muda, nenhum teste é editado. `GroupSetupValidatorTest` (ou o teste que cobre
`validate`) intacto e verde é a prova.

`F` = `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/setup/GroupSetupValidator.kt`
`G` = `JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile`

## Arquivos (lista FECHADA)
- EDITAR `F` — único arquivo.

## Passo 0
`git fetch origin`; trabalhe na branch já criada a partir de `origin/main`. Rode
`G :features:groups:presentation:detektAll --continue 2>&1 | grep -c "GroupSetupValidator.kt"` — tem que dar >= 1 (o vermelho existe). Commit cedo.

## Passo 1 · extrair a validação da quadra
Em `validate`, REMOVA os três blocos da quadra (o `if (state.recurring && venue == null) {...}`, o
`if (venue != null && venue.name...)` e o `if (venue != null && venue.address...)`), junto com seus comentários, e no
lugar deles (mesma posição, logo depois do `if (state.recurring && form.regularSlots.isEmpty()) ...`) escreva:

```kotlin
        addAll(validateVenue(state.recurring, venue))
```

Depois de `validatePix`, acrescente (os comentários são os MESMOS que estavam nos blocos; copie-os):

```kotlin
private fun validateVenue(recurring: Boolean, venue: GroupSetupVenue?): Set<GroupSetupError> = buildSet {
    // Recorrência ligada gera os jogos na criação, e jogo não existe sem local: o backend
    // (`CreateGroup`) recusa horário regular sem quadra padrão. Os dois erros já têm campo dono.
    if (recurring && venue == null) {
        add(GroupSetupError.VenueNameRequired)
        add(GroupSetupError.VenueAddressNotFound)
    }
    // (comentário original do bloco do nome)
    if (venue != null && venue.name.codePointLength() < GroupTextLimits.VenueNameMin) {
        add(GroupSetupError.VenueNameRequired)
    }
    // (comentário original do bloco do endereço)
    if (venue != null && venue.address.codePointLength() < GroupTextLimits.VenueAddressMin) {
        add(GroupSetupError.VenueAddressNotFound)
    }
}
```
O tipo de `venue` é o tipo real de `form.defaultVenue` — confira com `grep -n "defaultVenue" F` e no arquivo do form;
se não for `GroupSetupVenue`, use o nome real. Se `venue` for um `Set` em vez de `List` em `addAll`, tudo bem: `buildSet` devolve `Set`.

## Gates
```
G :features:groups:presentation:detektAll
G :features:groups:presentation:iosSimulatorArm64Test --tests "*GroupSetup*"
G :features:groups:presentation:iosSimulatorArm64Test
```
`detektAll` tem que ficar TOTALMENTE verde (sem `--continue`). Se `validate` continuar acima do teto, extraia também
`validateCustomLevel(form)` (o bloco do `customLevel`) no mesmo padrão. Sem `@Suppress`.

## PR
Título: `refactor(groups): validate do GroupSetupValidator abaixo do teto de complexidade do detekt (VUL-247)`.
Corpo: "Refatoração pura; detektAll do módulo volta a verde; testes intactos." Commits PT-BR terminando com
`Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`; PR ready com `GH_TOKEN=$(gh auth token --user bruno-halmeida)`;
corpo terminando com `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
