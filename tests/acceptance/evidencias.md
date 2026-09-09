# Evidências e limites — 09/09/2026

Esta entrega conecta saída do grupo, cadastro esportivo próprio, perfil de outro membro, mensalidades próprias, mapa e comunicação interna. **Não equivale a homologação E2E de todo o produto.** Os 49 cenários do catálogo continuam `NÃO EXECUTADOS` manualmente, exceto Suporte do painel, explicitamente bloqueado por implementação.

## Resultados técnicos

Os números abaixo vêm dos relatórios XML dos gates locais; incluem testes existentes, não apenas os adicionados nesta entrega. As tarefas podem reutilizar cache Gradle. Nenhum teste ignorado foi contado como aprovado.

| Gate | Casos | Falhas | Evidência principal |
| --- | ---: | ---: | --- |
| Groups/data — iOS simulator | 249 | 0 | Rotas, query separada do path, payload, autenticação e erros tipados |
| Groups/presentation — iOS simulator | 489 | 0 | VM/UI, envio/retry, privacidade, lembretes por geração, saída e confirmação sem sobreposição |
| Profile/presentation — iOS simulator | 35 | 0 | Efeitos para cadastro, mensalidades, configurações e notificações |
| Compose-app — iOS simulator | 118 | 0 | Grafo e jornadas reais do host com gateways controlados; saída recarrega lista/perfil/Início, edição retorna ao perfil |
| Android unit — DevDebug | 171 | 0 | Inclui adaptador de mapas: URL e falha nativa |
| Comunicação — Postgres | 7 | 0 | Permissões, isolamento, preferências, paginação, saída e oito retries concorrentes |
| Comunicação/saída — HTTP + Postgres | 4 | 0 | API autenticada real com verificador de identidade de teste; DELETE seguido de leitura negada no elenco/mensagens e perfil sem vínculo |

Também passaram os gates direcionados de serviço/endpoint de atletas e persistência de desvinculação, incluindo manutenção de histórico financeiro e remoção somente no grupo solicitado. A revisão independente da primeira fase executou 676 casos backend relevantes; isso é uma execução anterior distinta, não deve ser somada à tabela como novos casos únicos.

Foram geradas e inspecionadas **30 capturas novas** de estados, incluindo confirmação/carregamento/erro de saída, perfil privado/autorizado, quatro estados de mensalidade, avisos somente leitura, chat vazio/enviando/falha, inbox e preferências. A inspeção encontrou sobreposição nos botões de saída; o layout foi corrigido e agora existe assertion de separação geométrica. Capturas ficam em `mobile/features/groups/presentation/screenshots/connected-flows/` (ignoradas pelo Git), reproduzíveis pelo teste `ConnectedFlowsScreenshotTest`.

Build nativo `SaqzDev`, Debug, simulador iOS: **BUILD SUCCEEDED** após as últimas mudanças. O build usou o worktree local, preservando as quatro alterações iOS que já existiam; essas alterações preexistentes não entraram nos commits desta entrega.

## Revisão independente

O verificador não escreveu código de produção. Revisou contratos, permissões, persistência e rotas, e injetou defeitos comportamentais somente em cópia temporária. Na rodada final, **7/7 mutações foram detectadas**: alvo errado na saída, emissão falsa de sucesso, publicação indevida de aviso, inbox alheio, preferência ignorada, lembrete a quem já respondeu e remoção da serialização de retries concorrentes.

Defeitos encontrados e corrigidos: estatísticas privadas derrubando perfil básico; falha de mapa silenciosa no iOS; feedback de lembrete do jogo anterior aparecendo no próximo; sobreposição visual na confirmação. Foram reforçados testes que antes deixavam passar grupo errado e navegação de sucesso em falha.

## O que permanece sem aprovação

- **XCTest dos adaptadores Swift:** tentado, mas o sistema de build do Xcode encerrou com `unexpected service error: The Xcode build system has crashed`, inclusive com DerivedData novo. O build do aplicativo passou, porém os novos XCTest não foram executados com sucesso. Não confundir com os testes Kotlin no simulador, que passaram.
- **Detekt global:** falha em código intocado de `ChangePlanScreen`, `GroupPhotoImage` e `MemberEditorViewModel`. Os apontamentos introduzidos nesta entrega foram corrigidos sem regenerar baseline. O gate global não está verde.
- **Backend global:** revisão identificou 10 falhas preexistentes em `PasswordResetEndpointIntegrationTest`, no helper que extrai código de email MIME quoted-printable. O gate global não está verde; isso não foi mascarado nem corrigido como parte das ligações do app.
- **E2E/aceitação real:** não foram executadas as jornadas em aparelhos Android/iOS contra autenticação e API de homologação, nem os roteiros do painel/checkout. Capturas e gateways controlados não substituem essa etapa.
- A sequência HTTP pós-DELETE cobre elenco, perfil e comunicação; **não há assertion específica de GET de jogos depois do mesmo DELETE**. Existem testes de autorização de jogos separados, mas isso não constitui a sequência inteira.
- Comunicação não possui push, atualização em tempo real ou nova integração WhatsApp. A migration V45 ainda precisa ser aplicada pelo processo normal de deploy no ambiente onde o app será homologado.

## Como reproduzir os gates

Use JDK 21 e as dependências locais descritas no projeto. Na raiz:

```sh
mobile/gradlew -p mobile :features:groups:data:iosSimulatorArm64Test :features:groups:presentation:iosSimulatorArm64Test :features:profile:presentation:iosSimulatorArm64Test :compose-app:iosSimulatorArm64Test :android-app:testDevDebugUnitTest
backend/gradlew -p backend :features:groups:integrationTest --tests '*GroupCommunicationIntegrationTest'
backend/gradlew -p backend :bootstrap:test --tests '*GroupCommunicationEndpointIntegrationTest'
mobile/gradlew -p mobile :features:groups:presentation:recordRoborazziAndroidHostTest --tests '*ConnectedFlowsScreenshotTest*'
mobile/gradlew -p mobile detektAll
```

Os limites globais acima devem ser investigados separadamente antes de exigir um gate geral inteiramente verde. O catálogo não tem runner Gherkin instalado: os comandos acima executam suítes Kotlin independentes, não os arquivos `.feature`.
