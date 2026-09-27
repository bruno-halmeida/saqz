# Políticas oficiais para a submissão do Saqz — Brasil

Pesquisa consultada em **27/09/2026**, para acompanhar a auditoria do commit `b484dac1192fb08aa707bca8848ccb283979b30c`. Escopo informado: Android/iOS, gestão de grupos de vôlei amador, assinatura digital do SaaS e cobranças de atividades presenciais por Asaas. Lançamento somente no Brasil.

Este documento registra requisitos e interpretações condicionais. Os achados de código, o funcionamento do binário e o preenchimento dos consoles precisam ser confrontados com estas condições. Não houve acesso autenticado ao App Store Connect ou Play Console nesta pesquisa. As fontes abaixo são documentação oficial; datas de vigência são mencionadas somente quando publicadas pela fonte. Páginas podem mudar depois desta consulta.

## 1. Pagamentos: classificar o que está sendo vendido

| Operação do Saqz | Enquadramento a verificar | Consequência para a submissão |
| --- | --- | --- |
| Assinatura que aumenta limites de grupos, libera recursos ou funcionalidades do software | Benefício digital consumido no app | Precisa de uma estratégia válida de monetização digital em cada loja. |
| Upgrade pago, diferença proporcional, reativação ou fatura que libera funcionalidades premium | Continua sendo a aquisição de benefício digital | Chamar o fluxo de “troca de plano”, “gestão” ou “fatura” não muda sua natureza. |
| Mensalidade para custear quadra, jogo presencial ou outro serviço esportivo fora do app | Serviço físico, se essa for efetivamente a contraprestação | Pode usar o provedor adequado a pagamentos físicos; não deve ser confundida com a assinatura SaaS. |
| Pagamento misto que também desbloqueia recursos digitais | Depende da composição real da oferta | Documentar os componentes; não presumir que a presença de um serviço físico isenta todo o pacote. |

A Apple exige IAP para funcionalidades digitais, sujeito às exceções aplicáveis, e exige outro meio para serviços consumidos fora do app (3.1.1 e 3.1.3(e)). O companion de ferramenta web paga só se beneficia de 3.1.3(f) sem compras nem chamadas para compra externa. [Apple — App Review Guidelines, Business](https://developer.apple.com/app-store/review/guidelines/#business).

No Google Play, funcionalidades, assinaturas digitais e software em nuvem normalmente usam Play Billing. Pagamentos de serviços físicos, como academias e ingressos de eventos presenciais, estão fora desse sistema. As permissões regionais para alternativas são condicionadas aos programas correspondentes. [Google — Payments, itens 2–4 e 8–9](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en).

**Aplicação ao Saqz:** registrar dois produtos independentes no material da revisão: o serviço digital vendido pelo Saqz e a cobrança do organizador pelo esporte presencial. A análise de código encontrou, segundo a auditoria principal, troca de plano com cobrança por Asaas. Esse caminho é mais relevante que discutir isoladamente o botão que envia informações por e-mail: é necessário resolver o fluxo completo de aquisição/upgrade digital.

### Apple: o Brasil já tem uma alternativa própria em 2026

Existe um programa oficial para **iPhone, storefront Brasil, iOS 26.5 ou posterior**. Ofertas externas com ou sem link precisam do entitlement `com.apple.developer.storekit.custom-purchase-link.allowed-regions`, com `br`. Pagamento alternativo dentro do app e link acionável para compra no navegador exigem apresentar IAP simultaneamente, com destaque pelo menos equivalente, além do aviso ao usuário e das APIs aplicáveis. Antes do fluxo, conferir `canMakePayments`, elegibilidade e aviso. Há regras específicas para menores: ofertas externas em website não são permitidas a menores de 18 anos. Existem comissões, relatórios e responsabilidades de suporte. [Apple — Payment options on the App Store in Brazil](https://developer.apple.com/support/payment-options-on-the-app-store-in-brazil).

A alteração do contrato do Developer Program teve aceite exigido até **06/07/2026** para membros existentes. O programa brasileiro não torna a compra externa automaticamente permitida em todo iOS/iPadOS nem elimina os requisitos de implementação. [Apple — Changes to iOS in Brazil](https://developer.apple.com/support/app-distribution-in-brazil).

**Decisão necessária:** implementar IAP; aderir e implementar integralmente o programa brasileiro onde elegível; ou redesenhar o produto como uma experiência legitimamente abrangida por uma exceção. A existência apenas de um checkout web de um app mobile não demonstra, por si, que há uma ferramenta web paga com companion gratuito. Ocultar um fluxo somente do revisor também não é uma solução.

### Google: alternativa no Brasil exige adesão e implementação

O programa de escolha de faturamento contempla o Brasil para apps móveis/tablets que não sejam jogos. Exige desenvolvedor registrado como empresa, adesão no Play Console, alternativa ao lado do Play Billing, integração com as APIs e comunicação das transações autorizadas em até 24 horas. As obrigações incluem suporte, contestação de transações e taxas. Não equivale a autorização genérica para abrir qualquer checkout externo. [Google — Enrolling in the user choice billing pilot](https://support.google.com/googleplay/android-developer/answer/12570971?hl=en).

### E-mail e informações de compra: não concluir com uma regra simplificada

A FAQ Google permite comunicação de ofertas **fora** do aplicativo, inclusive e-mail. Também admite informações sem links diretos para produtos/serviços de consumo, mas define “consumption-only” como ausência de compras dentro do app, inclusive de serviços físicos. Na mesma página, o direcionamento a pagamento alternativo dentro do app permanece restrito às exceções. A FAQ atual consultada não autoriza expressamente qualquer botão “enviar instruções de compra”. [Google — Understanding Google Play’s Payments policy, perguntas sobre alternative ways to pay e consumption-only](https://support.google.com/googleplay/android-developer/answer/10281818?hl=en).

**Interpretação para esta auditoria:** um e-mail enviado fora do app não é automaticamente infração. Tampouco garante conformidade de uma tela de planos que conduz à compra, especialmente quando existem upgrades e cobranças no mesmo aplicativo. Avaliar telas, destinos dos links e execução real, não apenas os nomes das ações. Na Apple, comparar o caminho escolhido com o programa Brasil ou com a exceção de companion; não transplantar exceções dos EUA/EEE.

## 2. Assinaturas: transparência, restauração e cancelamento

Na Apple, a contratação deve informar nome, duração, benefícios e preço integral de renovação localizado, com caminho para assinantes existentes entrarem/restaurarem. Links para termos de uso e política de privacidade devem existir no app e nos metadados. Quando houver plano anual, o total cobrado deve dominar a apresentação; valores mensais equivalentes não podem escondê-lo. Trials precisam informar duração e valor posterior. [Apple — Auto-renewable Subscriptions, Clearly describing subscriptions](https://developer.apple.com/app-store/subscriptions/).

O Google exige termos, preço, periodicidade, renovação automática e indicação se a assinatura é necessária, sem ação adicional para descobrir essas informações. É obrigatório oferecer no app um caminho online fácil para gerenciar/cancelar; pode ser o centro de assinaturas Play ou cancelamento direto aplicável ao provedor. A assinatura precisa entregar valor contínuo. [Google — Subscriptions](https://support.google.com/googleplay/android-developer/answer/9900533?hl=en).

**Critérios de aceite propostos para o Saqz:** testar compra e recuperação em outro aparelho, restauração após reinstalação, cancelamento, vencimento, upgrade/downgrade e perda de conectividade. A exclusão da conta e o cancelamento da cobrança devem ter consequências explícitas. Não prometer “cancelar quando quiser” sem um caminho operacional acessível.

## 3. Criação e exclusão de conta

### Apple

Se o app oferece criação de conta, deve oferecer o início da exclusão no app. Desativação temporária não basta. Dados associados, inclusive conteúdo publicado pelo usuário, devem ser excluídos, salvo retenção legal explicada. Pode existir confirmação/reautenticação razoável e processamento posterior com prazo e confirmação. Exigir ligação ou e-mail ao suporte não atende apps comuns. Ao adotar Sign in with Apple, revogar seus tokens na exclusão. Informar a situação de assinaturas e oferecer gestão; não transformar o término da assinatura em impedimento para excluir imediatamente a conta. [Apple — Offering account deletion in your app](https://developer.apple.com/support/offering-account-deletion-in-your-app).

### Google

É necessário um caminho no app e um recurso web funcional para solicitar exclusão da conta e dados. O endereço deve identificar o app/desenvolvedor e permitir o pedido sem reinstalar o aplicativo. Pode ser formulário ou contato de suporte claramente dedicado; uma seção existente de privacidade pode servir se for destacada e efetivamente permitir a solicitação. Retenções justificadas, como fraude ou obrigações regulatórias, precisam ser informadas. Preencher também as perguntas de exclusão no Data safety. [Google — App account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).

**Critérios de aceite propostos:** verificar usuário comum e organizador com grupo ativo, cadastro por Google e por e-mail, Firebase Authentication, dados do backend, imagens em storage, identificadores e tokens de push, integrações do provedor e cobranças futuras. Não é necessário apagar documentos financeiros cuja retenção seja legalmente exigida; deve existir escopo, fundamento, período e restrição de acesso. Um `deleted_at` isolado não demonstra que os dados associados foram removidos. Uma página que manda o usuário encontrar um botão inexistente não entrega o fluxo exigido.

## 4. Login e acesso dos revisores

Na Apple, login Google para a conta principal exige opção equivalente com coleta limitada a nome/e-mail, proteção do e-mail e ausência de uso publicitário sem consentimento; existem exceções específicas. Sign in with Apple atende esse desenho. E-mail/senha comum não demonstra proteção equivalente do endereço (4.8). [Apple — Login Services](https://developer.apple.com/app-store/review/guidelines/#login-services).

Ao implementar Sign in with Apple, configurar capability/App ID, autorização e fontes de e-mail para o relay. O uso de “Ocultar meu e-mail” precisa funcionar sem pedir o endereço real como condição de acesso. [Apple — Configuring Sign in with Apple support](https://developer.apple.com/documentation/xcode/configuring-sign-in-with-apple).

Na Apple, as informações de revisão permitem credenciais adicionais nas notas, e a conta demo não deve expirar. O Support URL precisa levar a informações reais de contato. [Apple — Platform version information, App Review information e Support URL](https://developer.apple.com/help/app-store-connect/reference/app-information/platform-version-information/).

No Google, fornecer acesso sempre disponível, reutilizável e independente da localização. OTP/2FA precisam de uma solução de acesso reutilizável para a revisão; não depender do revisor receber um código do desenvolvedor. As instruções devem ser em inglês. Áreas premium e tipos de conta também precisam estar acessíveis gratuitamente ao revisor. [Google — Requirements for providing sign in details for review](https://support.google.com/googleplay/android-developer/answer/15748846?hl=en).

**Pacote de revisão recomendado:** contas fictícias de organizador e participante, grupo com membros, próximos jogos e dados suficientes para exercitar escalação, presença e finanças; convite/QR válido; instruções de assinatura e de pagamento presencial; cenários de cancelamento e exclusão; servidor acessível durante toda a revisão. Declarar claramente quaisquer dados ou recursos de demonstração. Não usar dados pessoais de clientes reais para compor o acesso.

## 5. Conteúdo de usuários, mesmo em grupos privados

Apple 1.2 requer filtragem, denúncia, resposta, bloqueio de usuários abusivos e contato público para apps com UGC. [Apple — User-Generated Content](https://developer.apple.com/app-store/review/guidelines/#user-generated-content).

Para o Google, conteúdo visível a **um subconjunto** de usuários já é UGC. A política exige aceite de termos antes da criação/upload e moderação contínua proporcional. Mesmo ambientes limitados a usuários identificados precisam de denúncia no app para conteúdo e usuários. Interação individual e conteúdo público trazem exigências explícitas de bloqueio. [Google — User-generated content](https://support.google.com/googleplay/android-developer/answer/9876937?hl=en-GB).

Denúncia e bloqueio devem ser encontráveis e claramente identificados, e deve haver resposta efetiva às denúncias. [Google — Understanding moderation requirements](https://support.google.com/googleplay/android-developer/answer/12923286?hl=en).

**Aplicação ao Saqz:** a auditoria principal confirmou chat de grupo, além de fotos, nomes e descrições criados por membros/organizadores, e não localizou fluxos efetivos de denúncia/bloqueio. Isso eleva o risco de rejeição; não se trata apenas de uma hipótese sobre nomes de grupo. Remover um integrante pelo administrador não prova, sozinho, que o participante consegue denunciar abuso ou que a plataforma atua. Confirmar o significado de bloqueio no domínio: convites, contato e exposição a conteúdo do usuário bloqueado. A implementação pode ser proporcional a grupos esportivos, mas o caráter privado não é isenção geral. As evidências locais ficam no relatório principal.

## 6. Privacidade: quatro peças que precisam concordar

1. **Política publicada:** o Google exige URL pública, ativa, sem geobloqueio, não PDF, com identidade do app/desenvolvedor, contato, coleta/uso/compartilhamento, segurança e retenção/exclusão; também acessível no app. Dados financeiros e documentos de identificação não podem ser divulgados publicamente. [Google — User Data](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).
2. **App Privacy no App Store Connect:** obrigatório para submissão; inclui coleta própria e parceiros, mesmo para funcionalidade, e dados vinculados ao usuário. Processamento estritamente local ou efêmero tem critérios específicos; não equivale a dispensar coleta persistida no backend. [Apple — App privacy details](https://developer.apple.com/app-store/app-privacy-details/).
3. **Data safety no Play Console:** inventariar transmissão para fora do dispositivo, incluindo SDKs. Exceções para classificar transferências como “sharing”, como provedores que processam sob instruções do desenvolvedor, não eliminam automaticamente a necessidade de declarar “collection”. Verificar também finalidades, segurança e exclusão. [Google — Provide information for Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).
4. **Manifests no binário Apple:** representam práticas do app e de cada SDK e alimentam o relatório do Xcode. Não substituem as respostas do console. A TN3184 orienta declarar cada tipo coletado, associação ao usuário, tracking e finalidade. [Apple — TN3184](https://developer.apple.com/documentation/technotes/tn3184-adding-data-collection-details-to-your-privacy-manifest).

O manifest do app **não precisa duplicar** coleta já declarada pelos SDKs em seus próprios manifests. Por isso, `NSPrivacyCollectedDataTypes` vazio não permite concluir isoladamente que todo o binário declara ausência de coleta. Entretanto, o app deve descrever sua coleta própria; conferir o pacote final e o relatório agregado. [Apple — Describing data use in privacy manifests](https://developer.apple.com/documentation/bundleresources/describing-data-use-in-privacy-manifests).

**Inventário inicial sugerido ao Saqz, a confirmar pelo tráfego e configuração real:** nome, e-mail, ID de conta, fotos, conteúdo dos grupos, dados de jogos/presença, histórico de compras/cobranças, documentos/dados bancários quando solicitados, tokens/IDs de instalação, eventos de uso e diagnósticos. Determinar quem recebe e persiste cada campo e por quanto tempo. Não enviar senhas, tokens de autenticação, CPF ou detalhes bancários a analytics/logs de diagnóstico.

### SDKs e APIs que precisam de motivo

SDKs listados pela Apple precisam de manifest válido e, quando dependências binárias, assinatura nas condições descritas. O artefato submetido precisa conter esses arquivos, inclusive dependências transitivas. [Apple — Third-party SDK requirements](https://developer.apple.com/support/third-party-SDK-requirements/).

Para cada categoria de required reason API realmente utilizada, o bundle correspondente deve declarar o motivo aprovado. O SDK não pode confiar no manifest do app para cobrir seu próprio uso. Conferir o código e as bibliotecas compiladas antes de escolher motivos de UserDefaults, timestamps ou espaço em disco; não copiar listas preventivas de códigos sem aderência ao uso. [Apple — Describing use of required reason API](https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api).

Firebase informa que seus manifests refletem coleta padrão/sempre ativa, e o aplicativo deve complementar as declarações conforme o uso real. Authentication pode coletar ID e e-mail; Crashlytics coleta diagnósticos e recebe logs/IDs personalizados conforme integração; Messaging associa token APNs a instalação e pode registrar interações via Analytics. [Firebase — Prepare for Apple's App Store data disclosure requirements](https://firebase.google.com/docs/ios/app-store-data-collection).

### ATT não é sinônimo de analytics

ATT é necessário para tracking conforme a definição Apple e acesso ao IDFA: por exemplo, vinculação com dados de outras empresas para publicidade/mensuração ou compartilhamento com data broker. É responsabilidade do app considerar o comportamento dos SDKs. A presença de Firebase Analytics ou Crashlytics, isoladamente, **não prova tracking**, e ausência de prompt ATT não prova infração. Conferir configuração, recursos publicitários e destinos efetivos. [Apple — User privacy and data use](https://developer.apple.com/app-store/user-privacy-and-data-use/).

Quando coleta sensível não é razoavelmente esperada para a função, o Google exige disclosure destacado no fluxo e consentimento afirmativo antes da coleta; texto só nos termos/privacidade não o substitui. [Google — Best practices for prominent disclosure and consent](https://support.google.com/googleplay/android-developer/answer/11150561?hl=en).

## 7. Fotos, notificações e Live Activities

Para upload ocasional de foto de perfil/grupo, Google orienta usar seletor do sistema e proíbe depender de `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO` sem o caso central de acesso amplo aprovado. Inspecionar o manifesto **mesclado** do release, não apenas o arquivo fonte. [Google — Photo and Video Permissions policy](https://support.google.com/googleplay/android-developer/answer/14115180?hl=en-CA).

Push não pode ser condição de funcionamento; marketing exige opt-in/opt-out e informações sensíveis devem ser evitadas (Apple 4.5.4). [Apple — Apple Sites and Services](https://developer.apple.com/app-store/review/guidelines/#apple-sites-and-services).

Live Activities devem acompanhar tarefas/eventos com começo e fim, evitando publicidade e informação privada na tela bloqueada. É possível exibir resumo neutro ou redação de conteúdo sensível. [Apple — Human Interface Guidelines: Live Activities](https://developer.apple.com/design/human-interface-guidelines/live-activities).

**Critérios de teste propostos:** recusar permissões, desativar notificações depois do onboarding, encerrar uma partida, sair do grupo, fazer logout e excluir a conta. Garantir que push/Live Activity não mantenham dados da conta anterior nem exponham dívida, documento ou conta bancária na tela bloqueada.

## 8. Requisitos técnicos e vigências confirmadas

| Item | Fonte oficial consultada e consequência |
| --- | --- |
| Android target SDK | Novos apps e updates de celular precisam de **API 36/Android 16 desde 31/08/2026**; verificar eventual extensão no console, não pressupô-la. `minSdk` é outro parâmetro. [Google — Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en). |
| iOS/iPadOS SDK de compilação | Desde **28/04/2026**, Xcode 26+ e SDK iOS/iPadOS 26+. Isso não obriga deployment target 26. [Apple — SDK minimum requirements](https://developer.apple.com/news/upcoming-requirements/?id=04282026a). |
| iOS deployment target | Página atual informa mínimo **iOS 13 desde 09/09/2026**. Não confundir com SDK de build. [Apple — Upcoming Requirements](https://developer.apple.com/news/upcoming-requirements/). |
| Play Billing Library | Se adotada agora, usar versão suportada: prazo da **v7 terminou em 31/08/2026**, com extensão até 01/11/2026 quando concedida; v8 tem prazo até 31/08/2027. [Android — Billing Library deprecation](https://developer.android.com/google/play/billing/deprecation-faq). |
| Android nativo / 64 bits / 16 KB | Novos bundles com código nativo devem suportar 64 bits e páginas de 16 KB. Kotlin/Java puro é compatível por padrão, mas bibliotecas nativas transitivas mudam a análise. [Google — Play Console technical quality requirements](https://support.google.com/googleplay/android-developer/answer/17492799?hl=en). |
| Verificação Android no Brasil | Entra em vigor em **30/09/2026** para lojas participantes, incluindo Play, em dispositivos certificados Android 7+. Conferir identidade e registro do pacote/chave no Play Console; a maior parte dos apps Play já é registrada automaticamente. [Android — Developer verification](https://developer.android.com/developer-verification). |

**Cuidado com o histórico de 16 KB:** o anúncio oficial de 08/05/2025 estabelecia 01/11/2025. Já o guia técnico consultado agora informa bloqueio de **updates** incompatíveis a partir de **01/02/2027**. Não usar o anúncio antigo como prova de uma data universal atual, nem extrapolar a data de updates para dispensar novos aplicativos. Validar o AAB final, o App Bundle Explorer e qualquer aviso individual do Play Console. [Android — anúncio original](https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html), [Android — Support 16 KB page sizes, Google Play compatibility requirement](https://developer.android.com/guide/practices/page-sizes).

No artefato final, conferir alinhamento ZIP e segmentos ELF das bibliotecas `.so`, dependências pré-compiladas e execução em ambiente 16 KB. Atualizar AGP/NDK não corrige por si uma dependência binária incompatível. O guia recomenda AGP 8.5.1+ e NDK r28+ combinados com bibliotecas compatíveis. [Android — Support 16 KB page sizes, Build your app with support](https://developer.android.com/guide/practices/page-sizes).

## 9. Metadados, funcionamento e formulários de publicação

Apple destaca crashes, placeholders, links quebrados, informação incompleta e funcionalidade diferente da anunciada como problemas de revisão. A experiência deve ser útil e completa; se houver serviço regulado, a entidade apresentadora e as autorizações também são relevantes. [Apple — App Review: Avoiding common issues](https://developer.apple.com/app-store/review/).

Google exige experiência estável, responsiva e funcional; aplicativos que não instalam, não carregam, congelam ou oferecem funcionalidade mínima inadequada podem ser rejeitados. [Google — Functionality, Content, and User Experience](https://support.google.com/googleplay/android-developer/answer/9898783?hl=en).

A descrição e imagens precisam representar corretamente o produto, sem afirmações enganosas de preço ou classificação. [Google — Metadata](https://support.google.com/googleplay/android-developer/answer/9898842?hl=en). O e-mail de suporte é obrigatório na ficha Play. [Google — Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en&rd=2).

**Conferências antes do envio:** nome/ícone finais, screenshots do release, categoria, idiomas, descrição dos recursos pagos, termos/privacidade/suporte públicos, política de anúncios coerente com o binário, domínios e convites HTTPS funcionais, backend de produção disponível e ambiente sem texto de desenvolvimento. Remover ou concluir ações visíveis ainda sem implementação. Testar rede indisponível e falha dos provedores sem telas vazias ou crash.

### Classificação etária e crianças

A Apple atualizou seu sistema etário; perguntas atualizadas tinham prazo de resposta de **31/01/2026**. Responder no console conforme funcionalidades e conteúdo efetivos. [Apple — Upcoming Requirements, Age Rating Updates](https://developer.apple.com/news/upcoming-requirements/).

No Play, declarar público-alvo e conteúdo com precisão; crianças no público-alvo e recursos sociais podem trazer requisitos adicionais de segurança, participação de adultos e SDKs. [Google — Families Policies](https://support.google.com/googleplay/android-developer/answer/9893335?hl=en). A política específica de Child Safety Standards cobre categorias Social/Dating e apps de chat anônimo/aleatório; isso não deve ser atribuído automaticamente a todo aplicativo esportivo com grupo. Se aplicável, exige padrões publicados, mecanismo de feedback, ação sobre abuso e contato responsável. [Google — Child Safety Standards](https://support.google.com/googleplay/android-developer/answer/14747720?hl=en).

### Financeiro: declarar a função real, sem transformar toda cobrança em “banco”

Todos os desenvolvedores precisam preencher a declaração de recursos financeiros, mesmo quando a resposta correta é ausência de recursos financeiros. As opções incluem pagamentos móveis/carteiras e transferências. [Google — Financial features declaration](https://support.google.com/googleplay/android-developer/answer/13849271?hl=en).

Contas que oferecem produtos/serviços financeiros têm requisitos de organização e verificação. [Google — Choose a developer account type](https://support.google.com/googleplay/android-developer/answer/13634885?hl=en).

**Aplicação ao Saqz:** receber pagamento pelo próprio serviço físico não demonstra, sozinho, serviço financeiro regulado. Porém, a presença de onboarding financeiro, carteira, saldo, saque, transferência ou gestão de recebíveis para terceiros exige examinar exatamente o que o usuário consegue fazer e a relação contratual com Asaas. Documentar quem processa, quem recebe, quem atende contestação e quais dados o Saqz armazena. Não assinalar “sem recursos financeiros” apenas porque o processador é terceiro, nem presumir necessidade de licença bancária sem analisar a função.

Todos os desenvolvedores também precisam preencher a declaração de saúde. Organizar partidas não implica automaticamente recurso médico; declarar a função real se houver registro de atividade física, treino ou dados de saúde. [Google — Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en).

### Acesso a produção no Google Play

Se a conta for **pessoal criada depois de 13/11/2023**, a liberação de produção exige teste fechado com no mínimo 12 participantes inscritos continuamente por 14 dias e solicitação de acesso à produção. É condição da conta, não um defeito no código. [Google — App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

## 10. Ordem sugerida para resolver os riscos

1. Fechar o modelo de aquisição/upgrade da assinatura digital em ambas as plataformas, preservando a separação das cobranças esportivas.
2. Entregar exclusão de conta acessível e efetiva no app, recurso web Google, cancelamento/retenção coerentes e documentação pública correspondente.
3. Resolver a equivalência do login iOS e validar Google/e-mail/Apple no ambiente de produção.
4. Implementar moderação, denúncia e bloqueio compatíveis com os grupos e com cada política.
5. Conciliar privacidade, SDKs, manifests, App Privacy e Data safety com comportamento real.
6. Validar artefatos assinados, funcionamento em aparelhos, requisitos de SDK/16 KB e cadastro/verificação Brasil.
7. Preencher formulários e preparar contas, dados fictícios e roteiro de acesso integral dos revisores.

Essa ordem é uma recomendação de preparação baseada no produto descrito e nas condições pesquisadas; o relatório principal deve apontar, para cada item, a evidência de código/runtime e o que depende do console.
