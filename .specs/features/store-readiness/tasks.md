# Tarefas

Autorizadas pelos nove itens do usuário. Execução individual em oito entregas
coesas, seguida do verificador independente exigido pela skill tlc-spec-driven.
Ferramentas: shell, apply_patch, documentação oficial via web e simuladores locais.

## Test Coverage Matrix

Diretrizes: mobile/AGENTS.md; Gradle de mobile/backend; testes Node das páginas.

| Camada | Tipo | Cobertura | Local | Comando |
|---|---|---|---|---|
| Configuração nativa | Build + contrato | Build ID, rejeição de config ausente/inválida, identidade | android-app/src/test; ios-app/tests | testes Android; node --test |
| Autenticação/estado KMP | Unitário | Sucesso, cancelamento, falha, sessão obsoleta | commonTest; SaqzIOSTests | iosSimulatorArm64Test; xcodebuild test |
| Exclusão backend | Unitário + integração | Sucesso, repetição, autenticação antiga, falha de provedor | src/test | Gradle test dos módulos afetados |
| UI e navegação | Unitário/UI | Intenções, confirmação, erros, rotas bloqueadas | commonTest; android-app/src/test | testDevDebugUnitTest; iosSimulatorArm64Test |
| Página pública | Node | Conteúdo, links, solicitação | landing-page/tests | node --test landing-page/tests/*.test.cjs |

## Gate Check Commands

JDK 21 e ANDROID_HOME local conforme README. Rodar suítes focadas por tarefa;
compilar os módulos afetados, detekt e testes integrados ao fechar a entrega.
Falhas ambientais e gates prévios devem ser registrados, nunca apresentados como
sucesso. Não apagar testes para acomodar falhas. Testes de comportamento alterado
explicitamente pelo usuário devem acompanhar a nova decisão.

## Execution Plan

T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8

| Tarefa | Entrega / arquivos delimitados | Requisito | Verificação | Estado |
|---|---|---|---|---|
| T1 | Integração Crashlytics Android: catálogo, gradle app, teste de recurso | SR1 | Build e teste do ID real gerado | Concluída |
| T2 | Validação Firebase iOS: script, pbxproj, bootstrap, testes | SR2 | Contrato executando script com configurações inválidas/válidas | Concluída |
| T3 | Login Apple: ports/coordenador, UI login, adaptador Swift, entitlements | SR3 | Estados e callbacks; compilação Kotlin/Swift | Concluída |
| T4 | Endpoint exclusão: caso de uso, persistência, integrações, controller | SR4 | Unitários e integração PostgreSQL | Concluída |
| T5 | Jornada exclusão: profile, composição, reautenticação, página pública | SR5 | VM/UI, gateway, página e sessão | Concluída |
| T6 | Capacidade de lançamento: navegação, CTAs, disponibilidade e chat | SR6 | Entradas/rotas, ausência de compra, rollout | Concluída |
| T7 | Declaração de privacidade: política, manifesto, permissões | SR7 | Plist, manifesto mesclado, testes páginas | Concluída |
| T8 | Associação de produção: links-page, entitlements, instruções/evidências | SR8 | Contratos e validação fingerprint; verificador final | Concluída; aguardando verificador |

Cada tarefa depende da anterior apenas na ordem de execução; T5 reutiliza T3/T4.
Testes acompanham a entrega correspondente. Não publicar mudanças nesta execução.
