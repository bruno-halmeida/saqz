# Correção da cobertura instrumentada legada Android

As duas falhas registradas antes da onda de recebimentos foram reproduzidas no AVD Saqz_API_30
sobre o workspace atual: `/tmp/saqz-finalize-old-android.log`, 2 testes/2 falhas.
O produto atual já retirou o bloqueio por e-mail não verificado (VUL-84) e modernizou o formulário.

- `registrationSubmitRemainsSingleFlightAcrossRecreation`: seletor textual antigo não encontrava
  `Criar conta ›`; tags `registration-submit` passaram a `register-submit`. Inputs agora têm
  telefone obrigatório e o nó editável é descendente do container com tag. O teste usa tags
  estáveis e preenche os quatro campos válidos. Conserva as duas asserções literais
  `assertEquals(1, state.auth.registrationCalls)` antes/depois de `scenario.recreate()` e
  `assertIsDisplayed()` no submit restaurado. Foco isolado passou no AVD.
- `restoredUnverifiedSessionSkipsLoginAndSurvivesRecreation`: a antiga tela `identity-verify`
  deixou de existir. A expectativa foi alinhada ao contrato atual já testado em
  `VerifiedSessionCoordinatorTest.unverified authentication bootstraps instead of blocking`.
  Esta fixture recusa o token: o resultado preciso é `SessionAccessState.BootstrapError`,
  com a mensagem de falha de carga, sem login. A mesma situação persiste após recriação;
  foram acrescentadas asserções de uma composição e um observador. Não simula sessão Ready
  nem atesta login real; preserva as verificações de ausência do login nos dois momentos.

Nenhum caso removido ou ignorado; correção de seletores/fixture e expectativa obsoleta,
sem mudar código de autenticação ou regras do produto. O pedido de finalizar incluiu a dívida
instrumentada registrada na continuação; não houve relaxamento da garantia de chamada única.

Gate final: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile
:android-app:connectedDevDebugAndroidTest`, `/tmp/saqz-finalize-android-full.log`, exit0,
**44 testes, 0 falhas/erros/skips**, Saqz_API_30 Android11. Os dois testes anteriores estão
incluídos nos 44. Esse gate é nativo com ports fake; não é homologação financeira Asaas.
