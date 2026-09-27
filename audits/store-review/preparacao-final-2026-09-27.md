# Saqz — correções e preparação para as lojas

Distribuição prevista: **somente Brasil**. Este documento atualiza a
[auditoria inicial](auditoria-lojas-2026-09-27.md), que retrata o código anterior.
As alterações estão no branch `analise-apple-google`; não foram publicadas e não
representam aprovação das lojas.

## Implementado

| Área | Resultado |
|---|---|
| Inicialização Android | Plugins Firebase/Crashlytics geram o build ID exigido pelo SDK. Produção sem configuração é recusada. |
| Configuração iOS | Release exige Firebase válido e API de produção; fallback local fica restrito a Debug. |
| Login Apple | Botão nativo iOS, nonce, autenticação Firebase, tratamento de cancelamento e reautenticação. Google e senha continuam. |
| Exclusão | Perfil → Excluir conta, confirmação e reautenticação. Backend anonimiza dados, remove vínculos e mantém uma fila durável para cancelar assinatura e excluir Firebase, com retomada em falhas externas. Consentimento Apple é revogado no fluxo nativo. |
| Exclusão sem app | Página `https://saqz.app/excluir-conta/` preparada, com instruções e contato direto, incluindo solicitação para conta suspensa. |
| Assinatura da plataforma | App consulta o acesso obtido na web; não oferece contratação, upgrade, checkout nem envio de link de compra por e-mail. |
| Lançamento | Chat sem entrada/rota; recebimentos e assinatura automática dos grupos indisponíveis. Backend também começa com recebimentos desabilitados. Avisos, gestão de grupos e controle financeiro manual permanecem. |
| Privacidade | Política atualizada, acesso no login/perfil, manifesto próprio com dados/finalidades. Analytics iOS sem produto de IDFA; permissões publicitárias removidas do Android. |
| Links | iOS com associação pública de produção. Android com fallback explícito e ferramenta para configurar/verificar o SHA-256 real do Play. |

O bloqueio de funcionalidades vale para esta versão distribuída a todos, incluindo
a revisão. Uma futura ativação deve passar por nova avaliação de produto e das
políticas; não há mecanismo para liberar funcionalidades somente após o review.

## O que ainda impede concluir a preparação para envio

1. **Certificado do Google Play:** informar o SHA-256 da **chave de assinatura do
   app**. O arquivo atual contém o certificado de debug e o verificador recusa
   publicá-lo como produção. Há [passo a passo e comandos](../../links-page/README.md).
2. **Firebase de produção:** fornecer `mobile/android-app/src/prod/google-services.json`
   e `mobile/ios-app/SaqzIOS/Config/Prod/GoogleService-Info.plist` do app `app.saqz`.
   Registrar SHA-1/SHA-256 Play no Firebase e habilitar Google Login.
3. **Apple/Firebase:** ativar Sign in with Apple no App ID/perfil de distribuição e
   no Firebase Authentication; configurar os identificadores e a chave exigidos
   pelo provedor, incluindo revogação. Registrar os remetentes no Private Email
   Relay e validar a entrega para endereços privados. Não houve acesso aos consoles.
4. **Publicação:** implantar as migrações V89/V90 e o backend, as páginas de
   privacidade/exclusão e a associação de domínio. A landing e o host de links têm
   deploys distintos. Verificar os endpoints publicados sem login e sem redirects.
5. **Conteúdo de usuários:** remover chat reduz a superfície, mas **fotos, nomes,
   descrições e avisos de grupos continuam sendo UGC**. Não foi implementado o
   sistema completo de denúncia, bloqueio e moderação. Esse continua sendo risco
   de rejeição a resolver/avaliar antes da submissão, conforme o uso real e o
   acesso ao conteúdo. [Apple 1.2](https://developer.apple.com/app-store/review/guidelines/#user-generated-content),
   [Google UGC](https://support.google.com/googleplay/android-developer/answer/9876937?hl=pt-BR).
6. **Fichas e binários:** concluir os formulários abaixo e homologar AAB/IPA
   assinados em Play interno/TestFlight. IDs das fichas e credenciais de revisão
   não foram fornecidos. Aprovação em primeira submissão não pode ser garantida.
7. **Retenção operacional:** definir prazos e acesso aos registros financeiros,
   backups e dados dos operadores. A entrega apaga/redige os dados pessoais e
   preserva o histórico necessário; não adiciona expurgo automático por idade
   desse histórico. A política publicada precisa refletir os prazos adotados.

Configuração de lançamento: manter `SAQZ_RECEIVABLES_LAUNCH_ENABLED=false` e
pagamentos de recebimentos desligados. Não desativar o processamento durável de
exclusão: pedidos aceitos precisam concluir cancelamento de cobrança e remoção de
identidade mesmo quando o app já encerrou a sessão. Monitorar/reprocessar pedidos
pendentes sem registrar tokens ou dados pessoais nos logs.

## Preenchimento dos consoles

- Disponibilidade: Brasil. Política: `https://saqz.app/privacidade/`. Exclusão
  web no Play: `https://saqz.app/excluir-conta/`. Publicar antes de preencher.
- Descrição, screenshots e classificação etária devem retratar esta versão,
  sem promover chat, recebimentos ou compras desabilitados. Declarar o conteúdo
  criado por usuários que permanece acessível. Conferir os questionários atuais
  diretamente em cada console; não foram preenchidos nesta execução.
- Fornecer conta de revisão estável, com acesso suficiente às funções, grupo e
  jogo de demonstração. Explicar como chegar ao perfil/exclusão, consultar plano,
  entrar em grupo e confirmar presença. Evitar dependência de trial que expire
  durante a revisão ou de códigos acessíveis somente à equipe.
- Em notas de revisão, descrever o app como gestão de grupos esportivos e o acesso
  à assinatura da plataforma já contratada na web. Não declarar que isso garante
  uma exceção: a avaliação considera a funcionalidade real. O app não apresenta
  links ou chamadas para contratação. [Apple 3.1.3(f)](https://developer.apple.com/app-store/review/guidelines/#other-purchase-methods),
  [Google Payments](https://support.google.com/googleplay/android-developer/answer/9858738?hl=pt-BR).
- Conferir público-alvo, classificação etária, permissões de câmera/notificação,
  ausência de publicidade, declarações de recursos financeiros e eventuais
  exigências da conta de desenvolvedor. Não marcar prestação de crédito,
  investimento ou serviços de saúde inexistentes no produto.

## Rascunho de dados para conferir no archive e nos formulários

Base: código atual e configuração dos SDKs; **não é um formulário já enviado**.
Para App Privacy, os tipos abaixo estão declarados como vinculados ao usuário,
sem tracking publicitário. Para Data safety, avaliar coleta/compartilhamento por
operador segundo as definições próprias do Google, inclusive a exceção de
prestadores de serviço; não copiar automaticamente os rótulos da Apple.

| Dados | Uso nesta versão | Observação |
|---|---|---|
| Nome, e-mail, ID de usuário | Conta, sessão, grupos e suporte | Apple pode fornecer e-mail privado; identificadores também aparecem em analytics. |
| Telefone | Cadastro/contato e integrações habilitadas | Declarar a opcionalidade conforme o fluxo realmente oferecido. |
| Endereço e cidade/localização aproximada | Local dos jogos, perfil e analytics agregado por região | Não há pedido de localização GPS nesta configuração. |
| Fotos | Perfil/grupo | Acesso por seleção do usuário ou câmera. |
| Conteúdo criado pelo usuário | Nome/descrição de grupo e avisos | Chat desabilitado não elimina esses dados. |
| Informações de pagamento/financeiras | Chave Pix e registros manuais de valores/debitos dos grupos | API de recebimentos desligada; não confundir com inexistência de dados financeiros. |
| Histórico de compras/plano | Direito de acesso à plataforma, gestão e analytics | Contratação web, sem checkout no app. |
| IDs de instalação/dispositivo | Push, diagnóstico e analytics | Sem AD_ID/IDFA; retirar esses IDs publicitários não elimina IDs próprios dos SDKs. |
| Interações, falhas e desempenho | Melhoria de produto e diagnóstico | Firebase/Crashlytics devem ser conferidos no relatório agregado do archive. |

O manifesto não substitui a declaração App Privacy. Finalidades, opcionalidade,
retenção e prestadores devem coincidir com o tratamento real em produção.
[Apple — declarações de dados](https://developer.apple.com/app-store/app-privacy-details/),
[Google — Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=pt-BR).

## Homologação final em produção controlada

Instalar o app pela faixa interna/TestFlight; testar abertura a frio, offline,
login por senha/Google/Apple (inclusive e-mail privado e cancelamento), recuperação
de senha e retorno de links reais. Confirmar que o login Google funciona com o
certificado distribuído pelo Play, não apenas com instalação local.

Em contas de teste autorizadas, excluir atleta e organizador com assinatura;
confirmar anonimização dos dados, saída do app, revogação Apple, término da fila,
ausência de identidade Firebase e cancelamento efetivo no provedor. Simular
indisponibilidade externa e conferir retomada. Preservar o histórico financeiro
que precisa permanecer sem reativar o perfil excluído.

Conferir acesso já adquirido na web e mudança de aparelho; tentar rotas antigas de
chat/checkout/recebimentos e confirmar bloqueio. Testar seleção de foto com
permissão negada, recebimento de notificação e links com app fechado/aberto.
Validar no archive final as permissões, símbolos/Crashlytics, privacidade dos SDKs,
assinatura, ícones, versão/build, SDK/API exigidos e suporte a páginas de 16 KB.

Os [resultados reproduzíveis](../../.specs/features/store-readiness/evidence.md)
registram as suítes executadas. Testes com fakes e simuladores não substituem essa
homologação nem a verificação dos consoles.

O Android Lint completo continua com nove erros preexistentes em testes Compose
(`remember` retornando `Unit` e `mutableStateOf` criado durante composição), além
de 19 avisos. O build de desenvolvimento, testes Android e detekt passaram; isso
não equivale a um Lint integralmente verde. As falhas estão registradas nas evidências.
