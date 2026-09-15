# Execução

- [x] T1 Backend: endpoint autenticado, migrações, notificações privadas idempotentes; gate testes de integração de cobrança/comunicação e compilação bootstrap.
- [x] T2 Push: registro autenticado de instalação, fila durável e adaptador FCM; gate testes de registro, fila/retry/token inválido e bootstrap.
- [x] T3 Mobile: gateway, ViewModel compartilhado do envio, sheet de seleção e navegação CHARGE; gate testes gateway/envio/seleção + captura visual + lint.
- [x] T4 Aparelhos: port compartilhado e vínculo de sessão, adaptadores Android/iOS e configuração; gate testes de sessão, compilação Android e iOS.
- [x] T5 Verificação independente: conferir ACs e sensor em cópia temporária, documentar limitações externas.

Arquivos: backend groups communication/charge notifications + migration + HTTP + testes; bootstrap push/config; mobile groups communication/finance sheets + testes; compose-app push/DI/navigation; Android Firebase/manifest; iOS Firebase/push/entitlements/projeto.

## Gates executados

- Backend: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home backend/gradlew -p backend :features:groups:integrationTest --tests "*ChargeReminderIntegrationTest" --tests "*GroupCommunicationIntegrationTest" :bootstrap:compileKotlin`
- HTTP: mesmo JAVA_HOME, `backend/gradlew -p backend :bootstrap:test --tests "*ChargeNotificationHttpTest"`
- Sessão/nativos: `mobile/gradlew -p mobile :compose-app:iosSimulatorArm64Test --tests "*NotificationSessionBindingTest" :compose-app:detektAll :features:groups:data:detektAll :android-app:compileDevDebugKotlin`
- Dados: `mobile/gradlew -p mobile :features:groups:data:iosSimulatorArm64Test`
- iOS: `xcodebuild -project mobile/ios-app/SaqzIOS.xcodeproj -scheme SaqzIOS -configuration Debug -sdk iphonesimulator -destination "generic/platform=iOS Simulator" CODE_SIGNING_ALLOWED=NO build`

Verificação independente concluída com ressalvas de cobertura e entrega externa registradas em validation.md; não significa validação de push em aparelho.
