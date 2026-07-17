# Validação do Redesign da Tela de Login

**Data:** 2026-07-17
**Spec:** `.specs/features/login-screen-redesign/spec.md`
**Escopo de diff:** commits selecionados `93702bb` (spec) e `8cb6cdf` (implementação/testes); `597d97c` foi explicitamente excluído
**Verifier:** subagente independente (autor != verificador)

---

## Veredito

**PASS ✅ — 6/6 critérios atendidos.** O gate móvel proporcional passou e os quatro
mutantes que chegaram a executar foram mortos. A quinta tentativa, necessária para
completar a profundidade P0 de cinco mutações, ficou inconclusiva por lentidão anormal
no link nativo e foi interrompida sem tocar no worktree real. Isso limita a força do
sensor, mas não constitui falha funcional observada nem mutante sobrevivente.

## Conclusão das Entregas

| Entrega | Status | Evidência |
| --- | --- | --- |
| Especificação | ✅ Concluída | `93702bb` adiciona os critérios `LOGIN-UI-01..06`. |
| Implementação e testes | ✅ Concluída | `8cb6cdf` altera apenas a apresentação compartilhada e seus testes/consumidores móveis. |
| Validação independente | ✅ Concluída | Evidência por AC, gate fresh e sensor em clone descartável documentados abaixo. |

Não existe `tasks.md` para esta feature; os dois commits acima são as unidades
atômicas disponíveis. Mudanças locais fora da feature foram ignoradas e preservadas.

## Critérios de Aceite Ancorados na Spec

| Critério | Resultado definido pela spec | `file:line` + expressão/assertion | Resultado |
| --- | --- | --- | --- |
| `LOGIN-UI-01` | Marca, headline, apoio, email, senha, recuperação, Entrar, divisor, Google e cadastro, nessa ordem visual. | `mobile/features/access/src/commonTest/kotlin/br/com/saqz/access/ui/LoginScreenTest.kt:60` — `onNodeWithText("Organize seu grupo.", substring = true).assertExists()`; linhas `61-67` verificam headline, apoio, recuperação, divisor, Google e cadastro. `mobile/android-app/src/androidTest/kotlin/br/com/saqz/androidapp/AndroidAccessibilityTest.kt:60` — `assertTrue("heading precedes email", headingTop < emailTop)`; linhas `61-63` encadeiam email, senha, Entrar e Google. A ordem completa é a ordem declarativa do `Column` em `mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/ui/LoginScreen.kt:124-228`. | ✅ PASS |
| `LOGIN-UI-02` | Somente email, senha e Google; telefone, Apple e Facebook ausentes. | `mobile/features/access/src/commonTest/kotlin/br/com/saqz/access/ui/LoginScreenTest.kt:72-74` — três `assertDoesNotExist()` para telefone, Apple e Facebook. `LoginScreen.kt:164-215` contém apenas os campos email/senha e a ação Google; busca case-insensitive por `telefone|phone|apple|facebook` no código/strings de produção retornou zero ocorrências. | ✅ PASS |
| `LOGIN-UI-03` | Callbacks e estados existentes preservados, senha mascarada e visibilidade controlável. | `LoginScreenTest.kt:35-55` — valores exatos de email/senha e callbacks Entrar/Google; `LoginScreenTest.kt:87-105` — cadastro, recuperação, loading e erro. `mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/component/SaqzInputTest.kt:120-125` — `PasswordVisualTransformation`, máscara `•••••••` e modo revelado; linhas `140-143`, `158-161` e `177-182` verificam que o toggle preserva valor, seleção e foco. | ✅ PASS |
| `LOGIN-UI-04` | Em viewport compacto e fonte 2x, ações roláveis, alvos >=48 dp, nomes acessíveis e ordem de leitura visual. | `LoginScreenTest.kt:123-131` — `Density(..., fontScale = 2f)` em `280.dp x 320.dp` e `performScrollTo().assertExists()` para cadastro/recuperação; linhas `79-82` — quatro `assertHeightIsAtLeast(48.dp)`. `SaqzInputTest.kt:195-196` verifica o toggle 48x48. `mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component/SaqzButton.kt:112-124` fornece nome de ação e alvo mínimo aos demais botões. `AndroidAccessibilityTest.kt:51-63` verifica marca anunciada uma vez e ordem geométrica de leitura. | ✅ PASS |
| `LOGIN-UI-05` | Fundo claro, motivo Saqz sutil no topo, duas ondas azuis no rodapé, sem semântica/interceptação. | `LoginScreen.kt:105-110` aplica fundo e desenha o backdrop antes do conteúdo interativo; `LoginScreen.kt:236-250` usa imagem decorativa com `contentDescription = null` e ondas com `clearAndSetSemantics {}`; linhas `252-281` desenham as duas ondas. `AndroidAccessibilityTest.kt:51` — `assertEquals(1, ...onAllNodesWithText("saqz")...)` confirma ausência de marca decorativa duplicada. Inspeção independente de `/private/tmp/saqz-login-visible.png` confirmou fundo claro, motivo superior sutil e duas ondas sem sobreposição dos controles. | ✅ PASS |
| `LOGIN-UI-06` | Preview sem parâmetros no fim do arquivo, dimensões de telefone e conteúdo representativo. | `LoginScreen.kt:403-413` — `@Preview(widthDp = 354, heightDp = 796, ...)` e `private fun LoginScreenPreview()` com email representativo. `tail -n 14` confirmou que o preview é o último elemento do arquivo. O preview de `SaqzInput.kt:204-208` também permanece no fim do próprio arquivo. | ✅ PASS |

**Spec-anchored check:** 6/6 ACs correspondem aos resultados concretos definidos na
spec; nenhuma lacuna de precisão foi encontrada.

## Discrimination Sensor

O sensor foi executado sobre um clone descartável de `8cb6cdf` em
`/private/tmp/saqz-login-verifier.YFNOEi/repo`. O baseline direcionado foi
`LoginScreenTest`: 14/14 testes verdes. Nenhuma mutação foi aplicada ao worktree real.

| # | Local original | Mutação comportamental | Resultado |
| --- | --- | --- | --- |
| 1 | `mobile/features/access/src/commonMain/composeResources/values/strings.xml:4` | Headline `Organize seu grupo.` alterada para texto incorreto. | ✅ Morto por `approved visual hierarchy exposes the complete login journey`. |
| 2 | `mobile/features/access/src/commonMain/composeResources/values/strings.xml:10` | Campo `E-mail` alterado para `E-mail ou telefone`. | ✅ Morto por exclusão de telefone e associação exata do label. |
| 3 | `mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/ui/LoginScreen.kt:213` | Ação Google desviada de `onGoogle` para `onSubmit`. | ✅ Morto por `google action invokes provider flow`. |
| 4 | `mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/ui/LoginScreen.kt:119` | Removida a rolagem vertical do conteúdo. | ✅ Morto por `compact viewport at maximum font scale keeps actions reachable`. |
| 5 | `mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component/SaqzInput.kt:97` | Altura mínima inline reduzida de 56 dp para 40 dp. | ⚠️ Inconclusivo: link nativo ficou anormalmente lento; tentativa interrompida, sem resultado de teste. |

**Profundidade:** P0 parcial — 5 tentativas, 4 concluídas.
**Resultado concluído:** 4/4 mortos, 0 sobreviventes.
**Limitação:** não foi possível alcançar as cinco execuções concluídas exigidas pelo
alvo P0; portanto o sensor oferece confiança forte, mas não a profundidade P0 plena.

## Inspeção Visual

| Evidência | Resultado | Observações |
| --- | --- | --- |
| Captura Android portrait `/private/tmp/saqz-login-visible.png` | ✅ Pass | Branding/headline centralizados; campos e ações completos; fundo claro; motivo superior sutil; duas ondas no rodapé; nenhum telefone/Apple/Facebook. |
| Comparação direta lado a lado com a imagem original da referência | ⚠️ Não realizada | A imagem original não foi fornecida ao verifier independente; os elementos concretos descritos na spec foram inspecionados. |

## Qualidade do Código

| Princípio | Status | Evidência/nota |
| --- | --- | --- |
| Mudança mínima para o escopo | ✅ | Novos helpers são privados e atendem diretamente branding, divisor, ícones e backdrop pedidos. |
| Mudança cirúrgica | ✅ | `8cb6cdf` toca UI compartilhada, recurso textual, design-system necessário e testes consumidores; nenhum backend/Firebase/adapter nativo. |
| Sem scope creep | ✅ | Fluxos e portas de autenticação não foram alterados. |
| Padrões existentes | ✅ | Compose/KMP, `SaqzTheme`, resources e componentes do design system foram preservados. |
| Integridade dos testes | ✅ | 11 testes diretos de login antes da feature, 14 depois (`+3`); nenhuma remoção, skip ou assertion enfraquecida observada. |
| Outcome check ancorado na spec | ✅ | Assertions verificam textos, callbacks, exclusões, loading/erro, acessibilidade, tamanho e rolagem definidos pelos ACs. |
| Payload/conjunction rule | ✅ | Callbacks são verificados por contagem/valor exato, não apenas por existência de clique. |
| Testes reivindicados | ✅ | Os três novos testes mapeiam a `LOGIN-UI-01`, `LOGIN-UI-02` e `LOGIN-UI-04`; ajustes restantes mantêm contratos consumidores. |
| Previews no fim dos arquivos | ✅ | `LoginScreen.kt:403-413` e `SaqzInput.kt:204-208`. |
| Guidelines | ✅ | `AGENTS.md`, `RTK.md`, `validate.md` e `coding-principles.md`. |

`git diff 8cb6cdf^ 8cb6cdf --check` não encontrou erro de whitespace.
Nenhum `SPEC_DEVIATION` foi encontrado na produção móvel em escopo.

## Gate

- **Comando fresh:** `rtk ./gradlew :features:access:iosSimulatorArm64Test :core:design-system:iosSimulatorArm64Test :compose-app:iosSimulatorArm64Test --console=plain --rerun-tasks`
- **Resultado:** `BUILD SUCCESSFUL` em 1m12s; 409 testes, 409 passaram, 0 falharam, 0 erros, 0 skips.
- **Teste direto da tela:** 14 passaram; antes da feature eram 11 (`+3`).
- **Android instrumentado:** 39/39 foi informado pelo autor, mas não foi reexecutado pelo verifier e não entra na contagem do gate acima.
- **Gate raiz completo:** já estava bloqueado no backend por `FirebaseAdminTokenVerifierEmulatorTest`, falha informada como não relacionada; não foi repetido nesta validação proporcional.

## Edge Cases

- [x] Viewport `280 x 320 dp` com fonte 2x mantém ações alcançáveis por rolagem.
- [x] Loading desabilita Entrar/Google em assertion e recuperação/cadastro por `enabled = !state.isLoading` na implementação.
- [x] Erro de credenciais permanece visível e estável.
- [x] Senha começa mascarada e o controle de visibilidade preserva valor, seleção e foco.
- [x] Arte decorativa não adiciona nome semântico duplicado.
- [x] Telefone, Apple e Facebook permanecem ausentes.

## Rastreabilidade

| Requirement | Estado anterior | Estado verificado |
| --- | --- | --- |
| `LOGIN-UI-01` | Implementado em `8cb6cdf` | ✅ Verificado |
| `LOGIN-UI-02` | Implementado em `8cb6cdf` | ✅ Verificado |
| `LOGIN-UI-03` | Implementado em `8cb6cdf` | ✅ Verificado |
| `LOGIN-UI-04` | Implementado em `8cb6cdf` | ✅ Verificado |
| `LOGIN-UI-05` | Implementado em `8cb6cdf` | ✅ Verificado |
| `LOGIN-UI-06` | Implementado em `8cb6cdf` | ✅ Verificado |

## Resumo

**Overall:** ✅ Ready, com limitação explícita de profundidade do sensor.

**O que funciona:** hierarquia visual completa; somente email/senha/Google; callbacks,
loading, erro e senha preservados; viewport compacto acessível; decoração silenciosa;
preview representativo no fim do arquivo.

**Gaps funcionais encontrados:** nenhum.
**Limitação de validação:** mutante 5 inconclusivo e comparação lado a lado com a
referência visual original não realizada.
**Próximo passo:** nenhum fix de produto exigido; se for necessário elevar a garantia
ao P0 pleno, repetir somente o mutante de alvo mínimo quando o link nativo estiver
estável.
