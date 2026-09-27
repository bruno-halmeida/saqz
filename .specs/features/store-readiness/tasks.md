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
| T4 | Endpoint exclusão: caso de uso, persistência, integrações, controller | SR4 | Unitários e integração PostgreSQL | Concluída; F1–F6 revalidados |
| T5 | Jornada exclusão: profile, composição, reautenticação, página pública | SR5 | VM/UI, gateway, página e sessão | Concluída; F7 revalidado |
| T6 | Capacidade de lançamento: navegação, CTAs, disponibilidade e chat | SR6 | Entradas/rotas, ausência de compra, rollout | Concluída |
| T7 | Declaração de privacidade: política, manifesto, permissões | SR7 | Plist, manifesto mesclado, testes páginas | Concluída |
| T8 | Associação de produção: links-page, entitlements, instruções/evidências | SR8 | Contratos e validação fingerprint; verificador final | Concluída; PASS local, certificado Play pendente externo |

Cada tarefa depende da anterior apenas na ordem de execução; T5 reutiliza T3/T4.
Testes acompanham a entrega correspondente. Não publicar mudanças nesta execução.

## Correções após verificação independente

- F1/F3: migração V90 permite só a redação de motivo de presença de conta excluída;
  snapshots de jogos/séries próprios também são redigidos.
- F2: lock transacional por identidade antes de bootstrap/soft-delete; teste
  concorrente sincronizado na espera real do PostgreSQL.
- F4/F5: composição tolera ausência de billing sem esquecer assinatura ativa;
  teste de exclusão usa a composição real e preserva as novas expectativas.
- F6: testes do adapter Firebase e do cancelamento real com PostgreSQL, incluindo
  falha/retry, cancelamento idempotente e purge das credenciais de cartão.
- F7: teste Compose integrado confirma a conta enviada à API, espera pelo aceite,
  sessão encerrada, chave de sessão removida e pilha contendo só Login.
- Fixture preexistente de trial: jogo deve permanecer futuro perante os relógios
  do trial e do sistema; data absoluta venceu em 27/09, sem alterar assertions.

Revalidação concluída em 96a04c02: 2.378 testes backend e 10 de navegação passaram,
6/6 mutações foram detectadas (incluindo M5/M6) e os três probes independentes
passaram. F1–F7 encerrados no escopo local; limites externos em validation.md.
Nenhuma falha foi resolvida apagando/pulando testes.
