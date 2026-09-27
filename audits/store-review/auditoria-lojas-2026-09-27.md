# Auditoria de preparação para App Store e Google Play — Saqz

> Registro histórico do diagnóstico inicial. As correções autorizadas e as
> pendências atuais estão em [Preparação final](preparacao-final-2026-09-27.md).

**Parecer: não submeter esta versão ainda.** Há lacunas concretas em exclusão de conta, login iOS, moderação e configuração de produção, além de um fluxo de cobrança digital que precisa ser adequado às lojas. A aprovação depende também dos binários e dos consoles; este parecer não é uma validação oficial das lojas.

- Data: **27/09/2026**. Distribuição: **somente Brasil**, conforme informado pelo responsável.
- Código examinado: commit `b484dac1192fb08aa707bca8848ccb283979b30c`.
- Escopo: app KMP/Compose, integrações nativas Android/iOS, endpoints relacionados a conta, pagamentos e comunicação, páginas públicas, configuração de distribuição e verificações automatizadas disponíveis.
- Trabalho realizado: inspeção de código/configuração, pesquisa oficial, requisições públicas somente de leitura, testes existentes, lint, tentativa de build Android de produção e inspeção de bibliotecas nativas.
- Limites: sem acesso autenticado ao App Store Connect, Play Console, Firebase ou Asaas; sem credenciais de revisão; sem execução manual do app em aparelho; sem IPA/AAB de produção assinado. Testes completos de backend e simulador iOS não foram executados. Não houve criação/exclusão de contas nem transações reais.
- Alterações nesta auditoria: apenas documentação. As correções abaixo ainda precisam ser implementadas.

Os requisitos, exceções e datas consultadas estão no [levantamento de políticas oficiais](politicas-oficiais-2026-09-27.md). O [registro de verificação](verificacoes-2026-09-27.md) detalha comandos e resultados.

## 1. Achados prioritários

Prioridade **P0**: risco de impedir abertura do app. **P1**: resolver antes da submissão por risco relevante de rejeição ou quebra de jornada central. A coluna de evidência distingue defeito observado de consequência ainda dependente do release.

| ID | Prioridade | Loja | Achado | Evidência / condição |
| --- | --- | --- | --- | --- |
| A01 | P0 | Google Play | Crashlytics sem plugin e sem build ID | Configuração e bytecode do SDK confirmados; crash no release ainda não reproduzido em aparelho. |
| A02 | P1 | Ambas | Upgrade da assinatura digital por Asaas/Pix dentro do app | Fluxo alcançável confirmado; não há implementação dos sistemas/programas de billing das lojas no código examinado. |
| A03 | P1 | App Store | Google Login sem opção equivalente à regra 4.8 | UI, port e adapter iOS confirmados. |
| A04 | P1 | Ambas | Exclusão de conta não está disponível na interface | Gateway existe, mas não há jornada de exclusão; site promete ação inexistente. |
| A05 | P1 | Ambas | Exclusão do backend não conclui o ciclo de conta/dados/cobrança | Remoção parcial confirmada; não foi localizada coordenação com Firebase, assinaturas e dados associados. |
| A06 | P1 | Ambas | Chat e UGC sem denúncia/moderação operacional | Chat real; painel de denúncias explicitamente demonstrativo. Bloqueio também ausente. |
| A07 | P1 | Ambas, especialmente Apple | Declarações de privacidade precisam refletir a coleta real | Manifest próprio vazio, dados funcionais e financeiros transmitidos, analytics vinculado à conta; consoles não inspecionados. |
| A08 | P1 | App Store | Release iOS pode cair silenciosamente no Firebase local | Ausência de plist ativa emulador sem condicionar a Debug. |
| A09 | P1 | Google Play | App Links reconhecem somente certificado de debug | Fingerprint publicado coincide com o `debug.keystore` local. |
| A10 | P1 condicional | Ambas | Termos de recebimentos sem conteúdo disponível | API pública retorna 404; bloqueia preparação se Recebimentos fizer parte da versão submetida. |

### A01 — Crashlytics pode derrubar a inicialização do Android com Firebase real

**Evidência.** O [Gradle do Android](../../mobile/android-app/build.gradle.kts#L3) inclui o SDK Crashlytics, mas não aplica seu plugin. O comentário na linha 228 trata a ausência de minificação como justificativa. O [Application](../../mobile/android-app/src/main/kotlin/br/com/saqz/androidapp/SaqzApplication.kt#L12) inicializa o Firebase padrão e o [bootstrap de notificações](../../mobile/android-app/src/main/kotlin/br/com/saqz/androidapp/SaqzMessagingService.kt#L57) só evita esse caminho em ambiente de emulador.

A resolução de dependências desta auditoria contém `firebase-crashlytics:20.0.6`. Nos recursos mesclados não há `mapping_file_id` nem configuração de dispensa de build ID. A inspeção do bytecode desse AAR confirmou que `CrashlyticsCore.onPreExecute` exige o identificador por padrão e lança `IllegalStateException` quando ele falta. A chamada de inicialização do SDK não captura essa exceção. O [guia oficial de integração](https://firebase.google.com/docs/crashlytics/android/get-started) também exige o plugin; sua responsabilidade não se restringe ao mapping de R8.

**Impacto.** Risco alto de o app encerrar antes do login em ambiente real. Os testes locais com Firebase emulado não validam esse caminho.

**Correção e aceite.** Integrar os plugins e recursos exigidos pelo SDK, mantendo a configuração dos ambientes consistente. Instalar o release por uma faixa interna, fazer abertura a frio com Firebase real e verificar ausência de erro de build ID. Validar o recebimento de um relatório de diagnóstico em ambiente de teste controlado. Não esconder o problema desligando a exigência do SDK.

**Confiança.** Falha de configuração confirmada; consequência em aparelho inferida do caminho de inicialização e do SDK exato. Não foi apresentado um crash de produção coletado nesta auditoria.

### A02 — A assinatura digital está sendo comprada/alterada fora do billing das lojas

**Evidência.** A navegação liga `Perfil → Meu plano → Trocar de plano` em [SaqzNavHost](../../mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHost.kt#L667). A [confirmação de troca](../../mobile/features/subscriptions/presentation/src/commonMain/kotlin/br/com/saqz/subscriptions/presentation/changeplan/ChangePlanViewModel.kt#L103) chama o backend, e a tela oferece Pix e [abertura de fatura](../../mobile/features/subscriptions/presentation/src/commonMain/kotlin/br/com/saqz/subscriptions/presentation/ui/changeplan/ChangePlanScreen.kt#L256). O [gateway](../../mobile/features/subscriptions/data/src/commonMain/kotlin/br/com/saqz/subscriptions/data/subscription/KtorSubscriptionGateway.kt) e o backend operam a assinatura Asaas. Não foi encontrada integração StoreKit, Play Billing ou APIs de faturamento alternativo.

O [gate de assinatura](../../mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/subscriptiongate/SubscriptionGateScreen.kt) também envia informações para contratação por e-mail. Avaliar esse botão isoladamente seria insuficiente: já existe aquisição de benefício digital no fluxo de upgrade.

**Enquadramento.** A assinatura Saqz libera capacidade do software. A mensalidade do grupo paga pelo atleta pode custear uma atividade presencial. São contraprestações diferentes e devem continuar separadas. [Apple — pagamentos digitais e serviços presenciais](https://developer.apple.com/app-store/review/guidelines/#business); [Google — Payments](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en).

**Decisão antes de implementar.** Há três caminhos a avaliar:

1. Usar StoreKit/IAP e Play Billing para aquisição e upgrade do SaaS; manter Asaas nas cobranças esportivas presenciais.
2. Aderir e implementar os programas alternativos aplicáveis ao Brasil, incluindo interfaces, elegibilidade, relatórios e contratos. A [opção Apple Brasil](https://developer.apple.com/support/payment-options-on-the-app-store-in-brazil) contempla iPhone/iOS 26.5+ e exige entitlement; compras alternativas in-app e links acionáveis exigem IAP junto. A [escolha de faturamento Google](https://support.google.com/googleplay/android-developer/answer/12570971?hl=en) também exige adesão e implementação, incluindo Play Billing. Checkout externo sozinho não representa adesão.
3. Redesenhar a experiência para uma exceção efetivamente aplicável, sem compra/upgrade ou chamadas incompatíveis com ela. Não presumir que chamar o app de companion resolve o enquadramento.

**Aceite.** Documentar o caminho escolhido; testar compra, upgrade, renovação, cancelamento, restauração/recuperação em outro aparelho e indisponibilidade do provedor. Se usar a opção Apple Brasil, respeitar storefront, idade e versões elegíveis; o app hoje suporta iOS 15. Incluir o fluxo de e-mail nessa revisão. As permissões de comunicação externa da [FAQ Google](https://support.google.com/googleplay/android-developer/answer/10281818?hl=en) não justificam automaticamente o upgrade atual.

### A03 — Login Google no iOS sem alternativa equivalente

**Evidência.** O [login compartilhado](../../mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/ui/LoginScreen.kt#L215) oferece Google e e-mail/senha. [NativeAccessPorts](../../mobile/features/access/domain/src/commonMain/kotlin/br/com/saqz/access/domain/port/NativeAccessPorts.kt) e [IOSAuthAdapter](../../mobile/ios-app/SaqzIOS/IOSAuthAdapter.swift) não implementam Apple ou outro serviço equivalente. Um [teste existente](../../mobile/features/access/src/commonTest/kotlin/br/com/saqz/access/ui/LoginScreenTest.kt#L121) exige que o botão Apple não apareça.

**Impacto.** O caso de uso observado não demonstra uma exceção à [regra Apple 4.8](https://developer.apple.com/app-store/review/guidelines/#login-services). E-mail/senha convencional não demonstra a mesma proteção do endereço exigida para a alternativa.

**Correção e aceite.** Implementar Sign in with Apple no iOS, ou justificar e demonstrar outra solução/exceção válida. Validar primeiro acesso, retorno, cancelamento, e-mail privado, associação de contas, reautenticação financeira e exclusão/revogação. Atualizar os testes que congelam a ausência dessa opção. Conferir também capability, Firebase provider e entrega de e-mail ao relay Apple.

### A04 — O usuário não consegue iniciar exclusão de conta

**Evidência.** Existe `deleteSession()` em [KtorProfileGateway](../../mobile/features/profile/data/src/commonMain/kotlin/br/com/saqz/profile/data/KtorProfileGateway.kt#L120), mas a busca das chamadas não encontrou uso por uma jornada de UI. O [contrato do Perfil](../../mobile/features/profile/presentation/src/commonMain/kotlin/br/com/saqz/profile/presentation/own/OwnProfileContract.kt#L43) oferece logout, não exclusão. O [teste da folha de saída](../../mobile/features/profile/presentation/src/commonTest/kotlin/br/com/saqz/profile/presentation/exit/ProfileExitScreenTest.kt#L25) confirma a ausência de “Excluir conta”. A [política pública](../../landing-page/privacidade/index.html#L47) e os termos dizem que a exclusão está em Perfil.

**Correção e aceite.** Criar caminho encontrável em Perfil/Conta, confirmação dos efeitos, reautenticação apropriada e conclusão/recibo do pedido. Testar contas Google e e-mail, organizador e atleta, com e sem assinatura. Logout não encerra uma conta. [Apple — exclusão de conta](https://developer.apple.com/support/offering-account-deletion-in-your-app).

**Google: via web.** Não foi localizada uma jornada pública explícita de exclusão de conta. A política menciona direitos de eliminação por e-mail, mas isso precisa virar instrução direta e funcional para excluir a conta, sem mandar reinstalar o app ou usar o botão inexistente. Uma seção destacada da política pode atender; não é obrigatório criar um site separado. Configurar seu link no Play Console e testar a solicitação sem app instalado. O valor atual desse campo no console não foi verificado. [Google — requisitos de exclusão](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).

### A05 — O endpoint atual faz uma exclusão parcial e deixa pendências

**Evidência.** [JdbcSessionRepository.softDelete](../../backend/features/access/src/main/kotlin/br/com/saqz/access/adapter/output/jdbc/session/JdbcSessionRepository.kt#L101) remove foto de perfil e limpa alguns campos, mas mantém `display_name` e `firebase_subject`. A [orquestração](../../backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/AccessSessionConfiguration.kt#L234) remove vínculos e marca grupos como apagados. Não foi encontrada coordenação para excluir a identidade Firebase, revogar sessões, cancelar a assinatura SaaS, tratar recebimentos/recorrências ou eliminar/anonimizar conteúdo associado sem fundamento de retenção.

O [serviço de exclusão](../../backend/features/access/src/main/kotlin/br/com/saqz/access/application/session/DeleteAccount.kt#L29) recusa contas suspensas integralmente. Mensagens guardam cópia do nome do autor e conteúdo no [repositório de comunicação](../../backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/jdbc/communication/JdbcGroupCommunicationRepository.kt#L48). Não foi localizada rotina de expurgo que complete esse processo.

**Impacto.** Ligar o botão ao endpoint existente não basta. Pode permanecer uma identidade utilizável, conteúdo pessoal e cobrança futura sem conta ativa. Há divergência com a promessa de remoção na política pública.

**Correção e aceite.** Definir um processo idempotente de exclusão com tarefas acompanháveis para identidade, conteúdo, arquivos, tokens/dispositivos e provedores. Tratar assinatura e saldo pendente explicitamente, preservando o direito de pedir exclusão. Retenção financeira, antifraude ou regulatória pode ser necessária; especificar dados, motivo, prazo e acesso, sem aplicar retenção geral a todo perfil/conteúdo. Tratar também pedidos de contas suspensas por um fluxo efetivo. [Requisitos Apple](https://developer.apple.com/support/offering-account-deletion-in-your-app), [requisitos Google](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).

**Verificação adicional.** A política afirma que excluir exige verificação renovada de identidade, mas [AccessSessionController.delete](../../backend/features/access/src/main/kotlin/br/com/saqz/access/adapter/input/http/AccessSessionController.kt#L163) não verifica `auth_time` nesse caminho. Alinhar implementação e promessa ao concluir a jornada; não substituir exclusão por mera desativação.

### A06 — Chat sem proteção e denúncias sem operação real

**Evidência.** [GroupCommunicationService.publish](../../backend/features/groups/src/main/kotlin/br/com/saqz/groups/application/communication/GroupCommunicationService.kt#L20) aceita texto de atletas no chat e valida tamanho/caracteres. A [tela](../../mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/communication/GroupThreadRoot.kt#L91) exibe e envia mensagens sem denunciar conteúdo/autor ou bloquear. O [contrato](../../mobile/features/groups/domain/src/commonMain/kotlin/br/com/saqz/groups/domain/communication/CommunicationGateway.kt) também não possui essas ações. Há ainda fotos, nomes e descrições de grupos.

O [painel administrativo](../../adm-web/index.html#L815) declara que o suporte continua mockado; sua denúncia de exemplo informa que o fluxo ainda não existe no produto. O fato de um organizador poder remover participantes não cobre a denúncia de abuso por um atleta.

**Correção e aceite.** Entregar denúncia de mensagem, imagem, grupo e usuário, bloqueio apropriado, política de conteúdo, prevenção/moderação proporcional, fila administrativa real e processo de resposta. A Apple exige bloqueio no contexto de UGC; no Google, os detalhes de bloqueio variam com o tipo de interação, mas denúncia/moderação de grupos privados continuam relevantes. [Apple — UGC](https://developer.apple.com/app-store/review/guidelines/#user-generated-content); [Google — UGC](https://support.google.com/googleplay/android-developer/answer/9876937?hl=en-GB).

Conferir aceite dos termos **antes** de publicar: o Google Login pode criar conta sem passar pelo [cadastro que contém os links](../../mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/ui/RegisterScreen.kt#L294). Não foi localizada etapa equivalente de aceite no percurso social. Testar uma denúncia criada no app chegando ao operador e produzindo a ação esperada; a tela demonstrativa não conta como validação. [Google — operação de moderação](https://support.google.com/googleplay/android-developer/answer/12923286?hl=en).

### A07 — Privacidade não está conciliada com a coleta própria do app

**Evidência.** [PrivacyInfo.xcprivacy](../../mobile/ios-app/SaqzIOS/PrivacyInfo.xcprivacy#L9) contém `NSPrivacyCollectedDataTypes` vazio. O aplicativo envia perfil, fotos, conteúdo, presença e cobranças ao backend. [AndroidAnalyticsSink](../../mobile/android-app/src/main/kotlin/br/com/saqz/androidapp/AndroidAnalyticsSink.kt) e [IOSAnalyticsSink](../../mobile/ios-app/SaqzIOS/IOSAnalyticsSink.swift) vinculam Analytics e Crashlytics ao ID da conta.

Há coleta financeira adicional: [KtorFinancialOnboardingGateway](../../mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/KtorFinancialOnboardingGateway.kt#L32) envia CPF/CNPJ, renda, endereço e nascimento, além de documentos quando exigidos. A [política](../../landing-page/privacidade/index.html#L20) descreve cobrança de forma geral, mas não explicita bem cadastro financeiro, documentos de identificação, renda e dados de saque. Também promete exclusão/reautenticação que não corresponde ao fluxo atual.

**Correção e aceite.** Inventariar campos, destinatários, finalidades, vínculo e retenção. Completar a declaração da coleta própria e gerar o relatório de privacidade do archive. Reconciliar esse inventário com App Privacy e Data safety, inclusive informações financeiras e conteúdo de usuários. Ampliar a política pública e disponibilizá-la facilmente no Perfil/Configurações, junto com suporte e termos, sem depender de voltar ao cadastro.

**Limites da conclusão.** O manifest do app não precisa repetir tudo que os manifests dos SDKs já descrevem. Array vazio não prova que o binário inteiro declara zero coleta, e as respostas dos consoles não foram vistas. Analytics/Crashlytics também não provam rastreamento publicitário nem tornam ATT automaticamente obrigatório. [Apple — manifests e coleta própria](https://developer.apple.com/documentation/bundleresources/describing-data-use-in-privacy-manifests), [Apple — App Privacy](https://developer.apple.com/app-store/app-privacy-details/), [Google — Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).

**Ponto específico Android.** O manifest fonte remove `com.google.android.gms.permission.AD_ID` e desliga coleta de ad ID no Analytics. Entretanto, o manifest mesclado dev ainda recebe `ACCESS_ADSERVICES_AD_ID` e `ACCESS_ADSERVICES_ATTRIBUTION` de dependências. Conferir necessidade/configuração e declarações no release; presença de permissão não prova que ela está sendo utilizada. A política afirma não usar identificador publicitário.

### A08 — Ausência de configuração de produção não interrompe o release iOS

**Evidência.** [LocalFirebaseConfiguration.bundled](../../mobile/ios-app/SaqzIOS/SaqzIOSApp.swift#L130) retorna `.local` se o plist não estiver no bundle; isso usa projeto fictício e Auth Emulator em `127.0.0.1:9099`. Não existe condição que restrinja esse fallback a Debug. A [fase de cópia do Xcode](../../mobile/ios-app/SaqzIOS.xcodeproj/project.pbxproj#L460) simplesmente ignora arquivo ausente; a fase de Crashlytics emite aviso e segue.

Neste checkout não existem os arquivos Firebase de produção de Android/iOS. É normal que configurações de ambiente sejam fornecidas fora do git; **a ausência local, isoladamente, não é um defeito do produto**. O problema é o iOS permitir empacotar um release que autentica contra um emulador inexistente no telefone do revisor.

**Correção e aceite.** Fazer o archive Release falhar cedo se a configuração exigida faltar ou indicar ambiente local. Validar projeto, bundle ID e URL da API esperados. Permitir fallback emulado somente no ambiente destinado a desenvolvimento. Inspecionar o archive e executar login com a distribuição TestFlight. Android já falha cedo por configuração ausente, comportamento confirmado nesta auditoria.

### A09 — Os App Links Android estão preparados apenas para debug

**Evidência.** O [assetlinks.json versionado](../../links-page/.well-known/assetlinks.json#L7) e o [publicado](https://links.saqz.app/.well-known/assetlinks.json) têm um único SHA-256. A comparação local confirmou que ele pertence ao certificado Android de debug. O manifest usa HTTPS com `autoVerify`; a [página de fallback](../../links-page/index.html#L54) só oferece abertura pelo esquema nativo para iOS.

**Impacto.** O app distribuído com outra assinatura não será reconhecido automaticamente por essa associação, prejudicando convite, onboarding e confirmação de presença. No Android, cair na página web não oferece o mesmo botão nativo que o iOS.

**Correção e aceite.** Publicar o fingerprint da chave de **assinatura do app no Play App Signing**, não apenas o da chave de upload. Revisar se debug deve continuar autorizado no domínio de produção. Instalar pela faixa interna Play e validar App Links, convite e presença a partir de navegador/WhatsApp/e-mail. [Android — associação de domínio e certificado Play](https://developer.android.com/training/app-links/configure-assetlinks).

### A10 — A página de termos de recebimentos existe, mas não entrega o documento

**Evidência em produção, 27/09/2026.** `https://saqz.app/termos/recebimentos/` retorna HTML 200. O [script da página](../../landing-page/assets/receivables-terms.js#L13) busca `/public/receivables/terms/current`. Esse endpoint retornou **404, corpo vazio** nos dois hosts de API, com CORS `*`, usando agentes de navegador/cliente. O [controller](../../backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/input/http/PublicReceivablesController.kt#L38) retorna 404 quando não encontra termos vigentes. A causa provável é ausência de documento vigente na implantação; não houve consulta ao banco para confirmá-la.

**Impacto.** O JavaScript mostra indisponibilidade em lugar dos termos. Se Recebimentos estiver habilitado, o percurso financeiro não estará pronto para avaliação integral. A [jornada de cadastro financeiro](../../mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/FinancialOnboardingViewModel.kt) depende de termos carregáveis.

**Correção e aceite.** Publicar/configurar a versão correta e vigente no ambiente que será submetido, conferir 200 com conteúdo e datas válidas e abrir a página real. Verificar aceite dessa mesma versão no app. Se o produto não incluir Recebimentos nessa entrega, alinhar a disponibilidade pública, a interface, as screenshots e a descrição ao escopo real. Requisitos de funcionamento: [Apple — revisão](https://developer.apple.com/app-store/review/), [Google — funcionalidade](https://support.google.com/googleplay/android-developer/answer/9898783?hl=en).

## 2. Ajustes menores e resultados que não são reprovação automática

| Item | Constatação | Ação |
| --- | --- | --- |
| Lint Android | 9 erros, todos em arquivos de teste, e 19 warnings. | Corrigir os usos de Compose nos testes e tratar os warnings relevantes; não apresentar esses erros como crash comprovado do app. |
| Teste de configuração iOS | 1 de 3 falha: Debug usa `?mode=developer`, enquanto o teste espera domínio sem sufixo. As 2 verificações restantes passam, incluindo configuração de Release. | Alinhar contrato/teste de Debug; não remover configuração correta de Release para satisfazer a asserção. |
| Copy de produto | “Espaço para o seu futebol crescer” em [strings_myplan.xml](../../mobile/features/subscriptions/presentation/src/commonMain/composeResources/values/strings_myplan.xml#L48), enquanto o produto se apresenta como vôlei. | Corrigir texto e revisar screenshots/metadados. |
| Permissão da câmera iOS | Texto diz foto privada do grupo, mas câmera também serve para foto de perfil. | Descrever as duas utilizações reais. Não adicionar permissão ampla da galeria para PHPicker. |
| Link de download | [Baixar o app](../../links-page/index.html#L38) leva à landing, com comentário para substituir pelos IDs das lojas. | Completar os destinos quando as fichas existirem e testar fallback por plataforma. |
| Telas grandes e acessibilidade | Orientação travada; lint aponta limitações para grandes telas. | Exercitar telas pequenas, tablet/compatibilidade iPad conforme distribuição, fonte ampliada, TalkBack e VoiceOver. Não há prova visual de defeito nesta auditoria. |
| Ícones e recursos | Lint alerta forma dos ícones legados e vetor longo de splash. | Conferir máscara, recortes, contraste e splash no binário; são ajustes de qualidade, não proibições demonstradas. |
| Backup Android | `allowBackup=false`; lint sugere regras explícitas de extração em Android recente. | Validar transferência de dispositivo e proteção dos dados locais no release. |

Não classifiquei o app como “shell vazio” com base no texto antigo de `mobile/AGENTS.md`: a navegação atual contém jornadas reais de grupos, jogos, finanças e perfil. Tampouco considerei um arquivo chamado `FinancePlaceholderScreen` como prova de tela incompleta no produto: não foi encontrada ligação dele na navegação de produção.

## 3. Pontos positivos verificados

- Android configura `compileSdk=36` e `targetSdk=36`, confirmado também no manifest mesclado. Isso coincide com o requisito vigente consultado para novos apps. [Google — target API](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).
- Xcode instalado é 26.2; deployment target principal é iOS 15. São parâmetros distintos. O requisito consultado de compilação pede Xcode/SDK 26+, mas o SDK do archive final ainda precisa ser conferido. [Apple — SDK mínimo](https://developer.apple.com/news/upcoming-requirements/?id=04282026a).
- As 8 bibliotecas `.so` mescladas em devDebug têm segmentos ELF alinhados a 16 KB; há variantes arm64 e x86_64. Isso é evidência favorável das dependências examinadas, sem certificar o empacotamento ZIP do AAB/APKs de produção nem execução em dispositivo 16 KB. [Android — páginas de 16 KB](https://developer.android.com/guide/practices/page-sizes).
- Não há permissões amplas de contatos, localização, SMS ou fotos/vídeos no manifest Android mesclado examinado. A galeria iOS usa PHPicker e a câmera tem descrição de uso.
- Política de privacidade, termos gerais, landing e arquivos de associação de domínio responderam 200 por HTTPS. A privacidade identifica o operador, contato e principais provedores.
- O backend público de produção respondeu `UP` no health check. Isso demonstra disponibilidade desse endpoint naquele momento, não aprovação de todas as jornadas autenticadas.
- Existe cancelamento da assinatura no app e texto de trial sem cobrança automática; precisam continuar coerentes com o modelo de billing escolhido.
- Push de chat/aviso/cobrança usa texto resumido, sem reproduzir mensagem ou valor da dívida no alerta. Há controles de notificação e canais.
- Logs de rede inspecionados evitam registrar corpo e token. Há controles de acesso e reautenticação para operações financeiras; a exclusão ainda precisa do tratamento descrito em A05.

## 4. O que precisa ser conferido nos consoles e na operação

Estas são **pendências de comprovação**, não afirmações de que as contas estão configuradas incorretamente.

| Área | Conferência antes do envio |
| --- | --- |
| Identidade do desenvolvedor | Cadastro, contratos, dados de contato e entidade que publica devem corresponder ao operador do Saqz. Verificar tipo de conta e obrigações dos recursos financeiros. |
| Brasil / Android | Confirmar verificação do desenvolvedor e registro do pacote/chave para a exigência com início em **30/09/2026**. [Android — developer verification](https://developer.android.com/developer-verification). |
| Monetização digital | Produtos/assinaturas, contratos fiscais/bancários e sandbox; ou adesão efetiva ao programa alternativo escolhido, com APIs e entitlements. |
| App Privacy / Data safety | Reconciliar inventário abaixo com SDKs, backend, configurações e destinos reais. Não marcar “nenhum dado coletado”. |
| Exclusão web | Link direto e procedimento operacional acessível sem aplicativo; prazo, confirmação e dados retidos claramente descritos. |
| Conteúdo e idade | Declarar chat, UGC e público-alvo real; responder questionários atuais. Os termos aceitam menores acompanhados, então não presumir que escrever “18+” elimina esse cenário. |
| Recursos financeiros | O app possui cadastro com documentos, carteira e saque. Avaliar as opções pertinentes no formulário financeiro; não assinalar ausência apenas porque usa Asaas. Conferir entidade, contratos e responsabilidades. [Google — declaração financeira](https://support.google.com/googleplay/android-developer/answer/13849271?hl=en). |
| Saúde / anúncios | Preencher declarações com a função real. Organizar vôlei não implica alegações médicas; Analytics não implica anúncios. [Google — declaração de saúde](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en). |
| Suporte e listagem | E-mail/URL de suporte ativos, descrição honesta, nome/ícone definitivos, screenshots do release e indicação correta de funcionalidades pagas. |
| Acesso do revisor | Credenciais reutilizáveis, contas fictícias com dados, premium acessível, sem depender de intervenção humana para código de acesso. No Google, instruções em inglês. [Google — acesso para revisão](https://support.google.com/googleplay/android-developer/answer/15748846?hl=en). |
| Testes exigidos pela conta | Se for conta pessoal Google criada após 13/11/2023, conferir o requisito de teste fechado com 12 participantes por 14 dias contínuos. Não se aplica automaticamente a uma conta de organização. [Google — acesso à produção](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en). |
| Release final | Chaves/provisionamento, Play App Signing, Firebase OAuth/SHA de produção, APNs, URLs HTTPS e números de versão. Archive/AAB válidos e testes TestFlight/faixa interna. |

### Inventário inicial para o preenchimento de privacidade

Não é um formulário pronto para copiar: a classificação final depende do tratamento efetivo e das definições de cada loja.

| Dados observados | Fluxo/finalidade | Ponto de atenção |
| --- | --- | --- |
| Nome, e-mail, telefone, apelido, cidade, foto, ID da conta | Cadastro, identificação e participação em grupos | Vínculo à pessoa; telefone tem configuração de visibilidade. |
| Fotos/descrições de grupo, mensagens, nomes de convidados | Conteúdo de usuários e comunicação | Acesso por outros participantes, moderação, retenção e exclusão. |
| Jogos, local informado, presença e escalação | Organização esportiva | Distinguir local digitado de geolocalização obtida do aparelho; não foi encontrada permissão de GPS. |
| Plano, cobranças, histórico, recibos e comprovantes | Assinatura e finanças de grupo | Separar compra digital do SaaS de pagamento presencial. |
| CPF/CNPJ, renda, nascimento, endereço, documentos, conta para saque | Cadastro financeiro e verificação pelo provedor | Dados sensíveis para fins das políticas das lojas; explicar coleta, destinatário e retenção. “Sensível” aqui não é classificação jurídica automática da LGPD. |
| Token de push, ID de instalação, preferências | Entrega de notificações | Associados à conta/dispositivo; tratar logout e exclusão. |
| Eventos, identificador de usuário, diagnósticos e logs | Firebase Analytics/Crashlytics | Coleta vinculada à conta; conferir configurações publicitárias e manifests dos SDKs. |

Transferência a operador não significa automaticamente “compartilhamento” na taxonomia Google, mas continua podendo ser coleta. Dados transitórios, tratamento fora do app e conteúdo enviado pelo próprio usuário têm regras próprias: conferir cada campo, sem preencher por analogia.

## 5. Roteiro de aceite antes da submissão

Executar na **mesma versão assinada que será enviada**, por TestFlight e faixa interna Play. Anexar resultado e evidência por plataforma.

| Jornada | Resultado esperado |
| --- | --- |
| Instalação limpa / abertura a frio | Sem crash, configuração local, tela vazia, aviso de debug ou dependência de computador. |
| Login/cadastro | E-mail/senha e Google funcionam; solução Apple validada; recuperação, e-mail privado, sessão expirada e logout coerentes. |
| Termos e permissões | Termos/privacidade acessíveis; aceite antes de publicar; negar câmera/push não impede funções independentes. |
| Organizador novo | Trial correto, criação de grupo, convite e jogo sem exigir pagamento real do revisor. |
| Participante | Convite válido, entrada, presença, lista/escalação, privacidade e saída do grupo. |
| Conteúdo e abuso | Denúncia e bloqueio exercitados com duas contas; operador recebe e trata o caso. |
| Assinatura SaaS | Compra/upgrade conforme estratégia, preço/renovação claros, recuperação/restore, cancelamento, expiração e falha de rede. |
| Cobrança esportiva | Pix/cartão do serviço presencial, retorno, estado pendente, confirmação e prevenção de duplicação. Usar ambiente/dados declarados para revisão. |
| Cadastro financeiro / carteira | Termos vigentes, documentos, aprovação e saque exercitáveis com roteiro seguro que não peça documentos pessoais do revisor. |
| Exclusão | Pedido no app e web, consequências explícitas, dados removidos/retidos como informado, identidade e cobranças tratadas; confirmação ao usuário. |
| Convites e notificações | Links abrem o app de loja; alternância de contas, logout e exclusão não expõem dados anteriores; encerramento das Live Activities. |
| Resiliência e acessibilidade | Offline, timeout, retomada, encerramento do processo, fonte grande, leitor de tela, tamanhos de tela e navegação de volta. |
| Integridade do pacote | Manifest final, privacidade agregada, SDKs, assinatura, 64 bits e alinhamento/execução 16 KB conferidos. |

Preparar duas contas fictícias separadas, organizador e atleta, com grupo, próximo jogo e dados suficientes. Fornecer conta/jornada adicional para testar exclusão sem destruir a massa principal. Descrever nas notas de revisão a diferença entre assinatura do software e mensalidade da quadra. Não esconder funcionalidades do revisor; explicar os ambientes e dados de demonstração.

## 6. Ordem de execução recomendada

1. Corrigir A01 e A08 para impedir builds que falham ao abrir/autenticar.
2. Decidir e implementar A02; essa escolha altera compra, cancelamento, restauração e notas de revisão.
3. Concluir A03, A04, A05 e A06, com testes das jornadas completas.
4. Conciliar A07 e política pública; preparar formulários e suporte.
5. Corrigir A09 e, se o recurso estiver no lançamento, A10; resolver os ajustes menores e os checks locais.
6. Montar o pacote de revisão e executar o roteiro nos artefatos finais, fechando as pendências dos consoles.

**Critério para seguir:** achados aplicáveis resolvidos, evidências das jornadas críticas nos releases assinados e cadastros das lojas conferidos. A análise permite reduzir riscos conhecidos; ainda não há base para afirmar que a versão atual está pronta para aprovação na primeira tentativa.
