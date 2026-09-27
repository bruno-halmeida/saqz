# Registro de verificação — 27/09/2026

Complementa a [auditoria principal](auditoria-lojas-2026-09-27.md). Commit de referência: `b484dac1192fb08aa707bca8848ccb283979b30c`. Nenhum código de produto foi alterado. Relatórios ficam em `audits/` porque `docs/` está ignorado pelo `.gitignore` do projeto.

## Ambiente e limites

| Item | Resultado |
| --- | --- |
| Java padrão do shell | 17; comandos Gradle abaixo usaram explicitamente JDK 21 instalado. |
| Xcode | 26.2, build 17C52. |
| Android SDK | Plataforma API 36 e ferramentas de build presentes. |
| Dispositivos | Nenhum Android conectado e nenhum simulador iOS iniciado na inspeção. Não foram executadas jornadas manuais. |
| Firebase de produção | Ausentes `mobile/android-app/src/prod/google-services.json` e `mobile/ios-app/SaqzIOS/Config/Prod/GoogleService-Info.plist`. Conteúdo de credenciais não foi solicitado nem exposto. |
| Release | Sem AAB/IPA de produção assinado validado. |
| Serviços externos | Somente GETs públicos; sem conta criada, pagamento, publicação ou alterações em consoles. |

## Verificações executadas

### Páginas públicas e configuração estática iOS

```sh
node --test landing-page/tests/*.test.cjs mobile/ios-app/tests/signing-config.test.cjs
```

Resultado: **27 testes, 26 aprovados e 1 falha**, executados nesta auditoria.

- Os 24 testes da landing passaram.
- Das 3 verificações estáticas iOS, passaram a preservação de Associated Domains em Release e a configuração de identidade/domínio/esquema nativo.
- Falhou `SaqzDev runs Debug with sandbox push and the public links domain`: esperado `applinks:$(LINKS_DOMAIN)`, encontrado `applinks:$(LINKS_DOMAIN)?mode=developer`.
- Esses testes não executam o binário iOS nem a API pública real. A aprovação dos testes de HTML não contradiz a indisponibilidade do documento de recebimentos observada por HTTP.

### Testes Android e lint

Comando executado dentro de `mobile/`:

```sh
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/Users/bruno_almeida/Library/Android/sdk \
  ./gradlew :android-app:testDevDebugUnitTest :android-app:lintDevDebug --console=plain
```

Resultado: a tarefa de testes foi **FROM-CACHE**. Os XMLs recuperados contêm **229 testes, 43 arquivos de suíte, zero falhas, zero erros e zero ignorados**. Não se afirma que essas 229 execuções aconteceram novamente nesta auditoria. A evidência corresponde ao resultado reutilizado pelo Gradle para suas entradas.

O lint executou e **falhou com 9 erros e 19 warnings**. A execução conjunta terminou com exit code 1. Resumo dos erros:

| Regra | Quantidade | Arquivo/linhas |
| --- | --- | --- |
| `RememberReturnType` | 1 | `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/AndroidReduceMotionTest.kt:83` |
| `UnrememberedMutableState` | 8 | `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHostViewModelScopeTest.kt`: 137, 172, 192, 224, 259, 283, 327 e 366 |

Todos os erros reportados são em código de teste. Warnings incluem orientação/telas grandes, regras de extração de dados, ícones, vetor de splash, referência a permissão de API recente e sugestões KTX. Não há base para transformar os nove erros de teste em nove defeitos de produção.

Relatórios locais regeneráveis:

- [Lint HTML](../../mobile/android-app/build/reports/lint-results-devDebug.html).
- [Lint XML](../../mobile/android-app/build/reports/lint-results-devDebug.xml).
- [Resultados JUnit](../../mobile/android-app/build/test-results/testDevDebugUnitTest/).

Não foi executada a suíte de backend/Postgres, a suíte completa de `commonTest` em iOS ou E2E instalado. Um resultado JVM não cobre automaticamente todos os testes KMP.

### Tentativa de bundle Android de produção

No mesmo ambiente JDK/Android SDK:

```sh
./gradlew :android-app:bundleProdRelease --console=plain -q
```

Resultado: **falhou na configuração**, antes de gerar o bundle:

```text
Missing Android Firebase config: .../mobile/android-app/src/prod/google-services.json
```

Esse resultado confirma a barreira de configuração Android e limita a auditoria do artefato final. Arquivos de ambiente fora do git são esperados; não se conclui que o ambiente de distribuição real também esteja sem eles.

### Bibliotecas nativas Android / 16 KB

```sh
./gradlew :android-app:mergeDevDebugNativeLibs --console=plain -q
```

Resultado: sucesso. Foi feita leitura dos cabeçalhos ELF e segmentos `PT_LOAD` das bibliotecas mescladas, verificando `p_align >= 16384` e congruência entre offset e endereço virtual módulo 16384.

| ABI | Bibliotecas | Resultado ELF |
| --- | --- | --- |
| `arm64-v8a` | `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so` | Ambas compatíveis com alinhamento de 16 KB. |
| `x86_64` | As mesmas duas | Ambas compatíveis com alinhamento de 16 KB. |
| `armeabi-v7a` | As mesmas duas | Ambas com alinhamento de 16 KB. |
| `x86` | As mesmas duas | Ambas com alinhamento de 16 KB. |

São **8 arquivos**, não 8 bibliotecas distintas. Esta inspeção não verifica ZIP alignment dos APKs gerados pelo AAB, nem execução em dispositivo configurado para 16 KB. Repetir a inspeção no release final se as dependências ou configuração variarem.

### Crashlytics

Evidências combinadas:

1. Recursos mesclados identificam `com.google.firebase:firebase-crashlytics:20.0.6`.
2. Nenhum recurso `mapping_file_id` ou `RequireBuildId` foi encontrado no merge examinado.
3. `javap -c -p` sobre `classes.jar` do AAR dessa versão confirmou exigência de build ID por padrão e `IllegalStateException` no caminho em que ele falta.
4. A chamada a `onPreExecute` na inicialização de `FirebaseCrashlytics` não envolve essa exceção em um catch de recuperação.
5. O Gradle do app não aplica o plugin Crashlytics; o caminho com Firebase padrão é executado fora do modo emulado.

Conclusão: problema de configuração com alto risco de crash inicial. **Não foi reproduzido um crash do release assinado em aparelho.** A integração oficial também exige o plugin, como registrado no achado A01.

### Estruturas iOS e ícone

```sh
plutil -lint mobile/ios-app/SaqzIOS/Info.plist \
  mobile/ios-app/SaqzIOS/PrivacyInfo.xcprivacy \
  mobile/ios-app/SaqzIOS/SaqzIOS.entitlements \
  mobile/ios-app/SaqzLiveActivity/Info.plist \
  mobile/ios-app/SaqzIOS.xcodeproj/project.pbxproj
```

Os cinco arquivos passaram na validação sintática. Isso não valida o conteúdo das declarações de privacidade nem o provisionamento do archive.

O ícone selecionado pelo asset catalog, `icon_1024-solid-blue.png`, tem **1024 × 1024 e nenhum canal alpha**, confirmado com `sips`. O conteúdo visual não foi homologado em aparelho.

## Requisições públicas

Resultados observados durante a auditoria, sem autenticação. Disponibilidade pode mudar depois desta data.

| URL | Resultado |
| --- | --- |
| `https://saqz.app/` | HTTP 200, HTML da landing Saqz. |
| `https://saqz.app/privacidade/` | HTTP 200, política identificada pelo título. |
| `https://saqz.app/termos/` | HTTP 200, termos gerais. |
| `https://saqz.app/termos/recebimentos/` | HTTP 200, HTML que depende do carregamento do documento pela API. |
| `https://api.saqz.app/public/receivables/terms/current` | HTTP 404, corpo vazio, `Access-Control-Allow-Origin: *`. |
| `https://saqz-api.brunoalmeida.dev/public/receivables/terms/current` | HTTP 404, corpo vazio, `Access-Control-Allow-Origin: *`. |
| `https://api.saqz.app/actuator/health` | HTTP 200, `status: UP`. |
| `https://links.saqz.app/.well-known/apple-app-site-association` | HTTP 200, JSON com `8JG4JP8VMT.app.saqz`. |
| `https://links.saqz.app/.well-known/assetlinks.json` | HTTP 200, JSON com `app.saqz` e um fingerprint. |

A requisição inicial dos termos com o User-Agent padrão do Python recebeu 403 do filtro de assinatura do navegador. Novas requisições com User-Agent explícito da auditoria, Ktor, Android e Chrome retornaram 404. Por isso A10 trata a indisponibilidade do documento; não acusa erro de CORS nem conclui que navegadores comuns estão bloqueados.

Não foi realizada navegação visual em browser. A mensagem de indisponibilidade da página é a consequência do 404 no JavaScript inspecionado, não uma screenshot coletada.

O fingerprint Android publicado é igual ao único fingerprint do arquivo versionado e ao SHA-256 do certificado local `~/.android/debug.keystore`. Apenas o certificado público foi comparado. A chave privada não foi lida/exportada. O certificado de assinatura Play não estava disponível para confronto.

## O que este registro não certifica

- Aprovação pelas lojas, preenchimento dos consoles ou adesão a programas de billing.
- Compra/renovação/cancelamento reais, exclusão efetiva de dados ou aceitação de documentos financeiros.
- Login real no release, entregas de e-mail/WhatsApp/push/APNs ou navegação por App Links do app instalado pela loja.
- Layout, fluidez, acessibilidade e comportamento em toda a matriz de aparelhos.
- Segurança integral do backend ou conformidade jurídica completa.

Essas limitações estão incorporadas ao roteiro de aceite da auditoria. Os achados de código e configuração permanecem acionáveis independentemente delas.
