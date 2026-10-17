# Auditoria de duplo/triplo toque (regra GER-001) — 17/10/2026

**Regra (dono):** toda ação com efeito ignora novos toques enquanto a primeira está em andamento, e o backend aguenta
a repetição sem duplicar efeito (linha, evento, aviso, cobrança, push/WhatsApp).

**Como foi feito:** li cada ViewModel de `mobile/features/**/presentation`, `features/access`, `compose-app` e cada
controller de `backend/features/**` que muda estado, e conferi (a) a trava no app, (b) o que o backend faz com a repetição,
(c) se existe teste. "Teste" abaixo é o que **já existia** antes desta auditoria; o que foi escrito agora vem marcado com ➕.

Legenda de trava no app: `flag X` = campo do estado ligado antes da chamada e conferido na entrada do intent;
`attempt` = tentativa persistida no `SavedStateHandle` com `requestId` fixo (retoma sem duplicar).
Legenda do backend: `requestId` = o servidor grava o id e devolve o mesmo resultado; `If-Match` = token de versão
(segunda chamada com o token velho dá 409); `estado` = a própria transição de estado barra a segunda;
`lock` = trava de linha/advisory na transação; `limite` = janela de rate limit.

## Resultado em uma linha

Das ~70 ações com efeito, **2 tinham furo** e foram corrigidas (link do "não vou" no app; denúncia repetida no backend).
O resto já tinha trava no app **e** idempotência no backend. Faltavam os testes de toque duplo do "Vou/Não vou" nos três
ViewModels (➕ escritos). Itens que dependem de decisão do dono estão no fim.

---

## 1. Dinheiro

| Ação | Tela / ViewModel | Trava no app | Repetição no backend | Risco se repetir | Teste |
|---|---|---|---|---|---|
| Cobrar devedores (aviso de cobrança) | `ChargeReminderViewModel` | `flag sending` na entrada do `handleIntent`; `requestId` salvo por seleção (`SavedStateHandle`) | `POST /charges/notify`: `requestId` por (grupo, ator) devolve o mesmo recibo; seleção diferente com o mesmo id = 409; janela de 12 h por cobrança (`recentlyReminded`) | aviso a terceiros (cada devedor) | `inFlightRequestsAreNotDuplicatedAndEmptySelectionDoesNothing`; `FinanceSheetsTest` (botão desabilitado) |
| Dar baixa (recebido) no fechamento do jogo | `GameSettlementViewModel.markReceived` | `flag updatingChargeId` + `isLoading`; otimista com rollback | `POST /charges/{id}/status`: `If-Match` + `estado` (cobrança já paga não paga de novo) | dinheiro | `GameSettlementViewModelTest` (escrita em voo não fecha; recebido com/sem recibo) |
| Dar baixa no caixa do grupo (mensalidade) | `GroupCashboxViewModel.markReceived` | `flag updatingChargeId` | idem acima | dinheiro | `GroupCashboxViewModelTest` |
| Gerar mensalidades do mês | `MonthlyGenerationViewModel` | `flag isSaving`/`completed`; `requestId` | `POST /charges/monthly`: índice único `uq_group_charges_month_member` (a repetição não cria cobrança nova) | dinheiro (cobrança duplicada) | `MonthlyGenerationViewModelTest` |
| Lançar despesa/entrada no caixa | `NewEntryViewModel` | `flag isSaving` | `POST /expenses`: `requestId` obrigatório | dinheiro | `NewEntryViewModelTest` |
| Anular/editar despesa | `GroupCashbox`/estado da despesa | via `If-Match` | `PUT /expenses/{id}`, `POST …/void`: `If-Match` | dinheiro | `ExpenseControllerTest` |
| Pagar cobrança avulsa (Pix/cartão) | `MemberPaymentViewModel.create` | `flag loading` + `pending` ligados antes da chamada; `command` com `requestId` reaproveitado no replay | `POST /orders/{id}/instruments`: `requestId` | dinheiro | `MemberPaymentViewModelTest` |
| Renovar Pix vencido | `MemberPaymentViewModel.renewPix` | `canRenew` exige `!loading && !pending && !renewalPending` | `POST /orders/{id}/pix-renewal` com `requestId` | dinheiro | `MemberPaymentViewModelTest` |
| Aprovar / cancelar cobrança (conta financeira) | `ChargeApprovalViewModel.begin/write` | `flag loading` + `attempt` persistido (`requestId`, `fingerprint`) | `POST /charges/{id}/approve`, `/orders/{id}/cancel`: `requestId` | dinheiro | `ChargeApprovalViewModelTest` |
| Contratar/cancelar recorrência | `RecurrenceViewModel` | `loading`/`pending` na entrada; `canSubmit`/`canCancel` | `POST /recurrences`, `/{id}/cancel`, `/{id}/resume`: `requestId` + estado | dinheiro | `RecurrenceViewModelTest` |
| Cadastro da conta financeira / recuperar / enviar documento | `FinancialOnboardingViewModel` | `loading`/`picking` na entrada; `attempt` (`CREATE`/`RECOVER`/`UPLOAD`) | `POST /financial-accounts`, `/me/recover`, `/documents/{id}`: `requestId` + lock advisory por dono | dinheiro / cadastro | `FinancialOnboardingViewModelTest` |
| Salvar conta bancária / sacar | `ReceiptWalletViewModel` | `idle` exigido na entrada; `attempt` (`BANK`/`WITHDRAW`) com `requestId` | `POST /bank-destinations`, `/withdrawals`: `requestId` + lock advisory por conta | dinheiro | `ReceiptWalletViewModelTest` |
| Ativar/desativar recebimento no grupo | `ReceiptConfigurationViewModel.mutate` | `loading`/`pendingMutation`; comando salvo | `POST /receivables/activate`, `/deactivate`: `requestId` | dinheiro | `ReceiptConfigurationViewModelTest` (`inFlightMutationBlocksEdits…`) |
| Correções/delegação da conta financeira | `FinancialManagementViewModel` | `pending` exigido em cada intent | `POST /corrections` (`requestId`), delegações por chave natural | dinheiro | `FinancialManagementViewModelTest` |
| Assinar plano (checkout web) | tela de assinatura (web) / `MyPlanViewModel.OpenSubscribe` | só abre a tela; a compra roda no checkout | `POST /subscriptions`: `requestId` + `AlreadySubscribed` | dinheiro | testes de `CreateSubscription` no backend (não relidos nesta auditoria) |
| Trocar de plano | `ChangePlanViewModel` | `flag isSubmitting` nos três pontos de entrada; `requestIdFor(plano)` estável | `POST /subscriptions/me/change-plan`: `requestId` | dinheiro | `ChangePlanViewModelTest` |
| Cancelar assinatura | `MyPlanViewModel.cancel` | `flag isCanceling` + plano já cancelado | `POST /subscriptions/me/cancel`: `estado` | dinheiro | `MyPlanViewModelTest` (cancelar) |
| Reativar assinatura | `MyPlanViewModel.reactivate` | `flag isReactivating` | `POST /subscriptions/me/reactivate`: `estado` (409/404 recarrega) | dinheiro | `MyPlanViewModelTest` |
| Comprar na App Store / Play, restaurar, reenviar confirmação | `AppStorePaywallViewModel` | `isBusy` = comprando ∨ restaurando ∨ confirmando ∨ assinado, conferido nos 3 intents e no seletor de ciclo; botões `enabled = !isBusy` | `POST /subscriptions/app-store/transactions`, `/google-play/purchases`: chave primária `transaction_id` (V92): a mesma transação não entra duas vezes | dinheiro | `AppStorePaywallViewModelTest`, `AppStoreTransactionSyncTest` |
| Pedir e-mail com os dados da compra | `SubscriptionGateViewModel.requestPurchaseInformation` | `operationActive` | `POST /subscriptions/me/purchase-information`: `limite` + `InProgress` (503 com `retryAfter`) | e-mail a terceiro (o próprio dono) | `SubscriptionGateViewModelTest` |

## 2. Avisos a terceiros (push, WhatsApp, e-mail, mural, convite)

| Ação | Tela / ViewModel | Trava no app | Repetição no backend | Risco se repetir | Teste |
|---|---|---|---|---|---|
| **Vou / Não vou** (Início) | `HomeViewModel.respond` | `flag responding` ligado na otimização, conferido em `canRespond`; resposta velha descartada por `responseGeneration` | `PUT /attendance`: `requestId` + lock do jogo; V130: mesmo `requestId` só é repetição se for a última resposta (N-18) | dado/aviso (lista de espera, push ao organizador) | ➕ `second Vou or Nao vou while the first is in flight is ignored and answers again once it ends` |
| **Vou / Não vou** (grupo) | `GroupDetailsViewModel.respond` | `flag responding` (também bloqueia `RetryRoster`) | idem | idem | ➕ mesmo nome, em `GroupDetailsViewModelTest` |
| **Vou / Não vou** (jogo) | `GameDetailViewModel.respond` | `response.responding` em `canRespond` | idem | idem | ➕ `secondVouOrNaoVou…` em `GameDetailViewModelTest`; `GameDetailScreenTest` (`answer in flight blocks both buttons`) |
| **Vou / Não vou** (link de presença: confirmar) | `AttendanceLinkViewModel.resolve` | só roda na abertura (`generation`); sem botão de segunda tentativa | `requestId` derivado do código (metade inicial) | idem | `AttendanceLinkViewModelTest` |
| **Não vou** (link de presença: folha) | `AttendanceLinkViewModel.decline` | **não tinha**: a folha ficava em `DeclineSheet` até a resposta e cada toque disparava outra chamada. **Agora** `flag declining` + botão `enabled = !declining` | mesmo `requestId` derivado (metade final): a repetição não duplica | dado/aviso | ➕ `secondDeclineWhileTheFirstIsInFlight…`, ➕ `declineFailureReleasesTheSheet…` |
| Avisar quem não respondeu (WhatsApp/push) | `GroupDetailsViewModel.notifyPending` | `flag notifying`; `requestId` guardado por jogo (`reminderRequest`) até o sucesso | `POST …/notify-pending`: `requestId` + janela do lembrete | aviso a terceiros (todos os pendentes) | `GroupDetailsViewModelTest` (`notifying` ligado/desligado, 2ª chamada) |
| Publicar aviso/mensagem no mural | `GroupThreadViewModel.send` | `flag sending`; `requestId` salvo (sobrevive a recriação) | `POST /messages`: `requestId` | aviso a terceiros (push a todos) | `CommunicationViewModelTest` (`inFlightSendIsSingleAndFailedRetryKeeps…`) |
| Publicar rascunho do jogo | `GameDetailViewModel.publish` | `flag publishing` | `POST /games/{id}/publish`: `If-Match` (2ª chamada = 409 → recarrega) | aviso a terceiros (abre confirmações) | `GameDetailViewModelTest` (`publish is ignored…`) |
| Cancelar jogo | `GameDetailViewModel.cancel` | `flag cancelling` | `POST …/cancel`: `If-Match` | aviso a terceiros + destrutiva | `GameDetailViewModelTest` (`second confirm while cancellation is in flight is ignored`) |
| Criar jogo / série semanal | `GameEditorViewModel.save` | `flag isSaving` | `POST /games`: o `requestId` **é** o id do jogo (PK); `POST /game-series`: replay devolve 200 | dado duplicado | `GameEditorViewModelTest` |
| Editar jogo | `GameEditorViewModel.save` | `flag isSaving` | `PUT /games/{id}`: `If-Match` | só estético | `GameEditorViewModelTest` (`version conflict reloads…`) |
| Horário regular do grupo | `GroupScheduleViewModel.save` | `flag isSaving` | `PUT /schedule`: `If-Match` | dado | `GroupScheduleViewModelTest` (`pending save blocks duplicate requests`) |
| Convidado no jogo (adicionar/remover) | `GameDetailViewModel.submitGuest/confirmRemoveGuest` | `guest.adding` (em `canSubmit`) / `guest.removing` | `POST/DELETE …/guests`: `requestId` / chave (anfitrião, seq) | dado duplicado | `GameDetailViewModelTest` |
| Promover da fila / mudar capacidade | `GameDetailViewModel.promote/saveCapacity` | `promotingMemberId` / `savingCapacity` | `…/attendance/promote`, `…/capacity`: `requestId` | aviso (promovido) | `GameDetailViewModelTest` |
| Auto-confirmação | `GroupDetailsViewModel.toggleAutoConfirmation` | `flag autoConfirmationUpdating` | `PUT …/auto-confirmation`: idempotente (valor) | só estético | `GroupDetailsViewModelTest` |
| Gerar/trocar link e código de convite | `GroupInviteViewModel.rotate` | `flag isGenerating` | `POST /groups/{id}/invite`: **sem `requestId`**; cada chamada troca o código | link antigo vira inválido (ver "Sem correção") | `GroupInviteViewModelsTest` (`a rotation in flight ignores a second confirmation`), `GroupInviteScreenTest` |
| Exigir aprovação de entrada | `GroupInviteViewModel.toggleApproval` | `flag isUpdatingApproval` | `PUT …/settings`: `If-Match` | só estético | `GroupInviteViewModelsTest` |
| Aprovar / recusar pedido de entrada | `GroupMembersViewModel.accept/decline` | `busyRequestIds` por pedido (`beginRequestAction`) | `POST …/entry-requests/{u}/approve`: `estado` (já membro = sucesso sem recriar); `DELETE` idempotente | aviso a terceiro (a pessoa) | `GroupMembersViewModelTest` (`a double tap on accept calls the api once`), `GroupMembersScreenTest` |
| Entrar por convite (resgate) | `InviteLandingViewModel`; `GroupInviteCoordinator` | `flag isRedeeming`/`requestSent`; no coordenador a `generation` é dona do fluxo | `POST /invites/redeem`: já membro devolve o papel; pedido repetido cai no mesmo `request_id` e não avisa de novo; trava da janela de tentativas por usuário | aviso aos administradores | `InviteLandingViewModelTest`, `GroupInviteCoordinatorTest` (`repeated authentication does not start a second redeem generation`) |
| Cadastro do atleta no pedido de entrada | `AthleteRegistrationViewModel.save` | `flag isSaving` | `PUT …/entry-requests/me/athlete-profile`: sobrescreve (idempotente) | só estético | `AthleteRegistrationViewModelTest` |
| Ligar/desligar WhatsApp do grupo | `WhatsAppBindingViewModel.setEnabled` | `flag saving` | `PATCH/PUT …/whatsapp-binding`: idempotente (valor) | aviso (mensagem de boas-vindas ao grupo) | `WhatsAppBindingViewModelTest` |
| Preferências de notificação / marcar lida | `NotificationCenterViewModel` | `busy` na entrada de todo intent | `PUT /me/notification-preferences`, `…/{seq}/read`: idempotentes | só estético | sem arquivo de teste dedicado (coberto em `CommunicationViewModelTest` só para o mural) |
| Denunciar conteúdo | `ModerationViewModel.submitReport` | `report.sending` | `POST /api/reports`: **não tinha**; reenvio (resposta perdida, 2º toque após erro) gravava outra linha e mandava outro e-mail à equipe. **Agora**: trava advisory + `EXISTS` (mesmo denunciante, alvo e motivo, ainda aberta) → não grava de novo; resposta continua 204 | e-mail à equipe + painel poluído | ➕ unidade `ContentModerationServiceTest` (2), ➕ integração `the same report sent twice in a row…`, `…at the same time…`, `another reason is a new report…` (3) |
| Bloquear / desbloquear pessoa | `ModerationViewModel.confirmBlock/unblock` | `block.blocking` / `busy` | `PUT/DELETE /me/blocks/{u}`: `ON CONFLICT DO NOTHING`; a denúncia BLOCKED só nasce no 1º bloqueio | e-mail à equipe | `ContentModerationIntegrationTest` (bloquear duas vezes) |
| Onboarding por link do app (código) | `AppOnboardingViewModel.complete` | `flag isLoading` | `PUT /session/onboarding`: idempotente; `POST /session/app-link`: só emite código | só estético | `AppOnboardingAuthCoordinatorTest` |
| Reenviar e-mail de verificação / pedir código de senha | `RegisterViewModel`/`ForgotPasswordViewModel`/`ResetCodeViewModel.resend` | `isSubmitting`/`resending` + contagem regressiva (`canResend`) | `POST /email-verification/request`, `/password-reset/request`: `limite` + `TooSoon` (429 com `retryAfter`) | e-mail a terceiro (o próprio usuário) | `ForgotPasswordViewModelTest`, `ResetCodeViewModelTest`, gateways (`request is not called twice…`) |

## 3. Destrutivas

| Ação | Tela / ViewModel | Trava no app | Repetição no backend | Risco | Teste |
|---|---|---|---|---|---|
| Remover atleta do grupo | `GroupMembersViewModel.remove` | `actionInFlight` (em `perform` e `remove`) | `DELETE …/athletes/{u}`: idempotente | destrutiva | `GroupMembersViewModelTest` |
| Promover / rebaixar | `GroupMembersViewModel.changeRole`; `MemberEditorViewModel` | `actionInFlight`; `operation != null` | `PUT …/memberships/{u}/role`: idempotente (papel alvo) | só estético | `GroupMembersViewModelTest`, `MemberEditorViewModelTest` |
| Editar jogador / cobrança do mensalista | `MemberEditorViewModel.patch` | `operation != null` na entrada | `PATCH …/athletes/{u}`: `If-Match` | dado | `MemberEditorViewModelTest` |
| Sair do grupo | `GroupDetailsViewModel.leave` | `flag leaving` + `confirmingLeave` | `DELETE …/memberships/me`: 2ª chamada já não é membro | destrutiva | `GroupDetailsViewModelTest` (sair/cancelar saída) |
| Excluir grupo | `GroupSetupViewModel.delete` | `flag isDeleting` (também bloqueia salvar) | `DELETE /groups/{id}`: soft delete (2ª chamada não encontra o grupo) | destrutiva | `GroupSetupViewModelTest` |
| Criar grupo / editar dados | `GroupSetupViewModel.save` | `isLoading ∨ isSaving ∨ isDeleting` na entrada | `POST /groups`: `requestId` (chave de criação única); `PUT`: `If-Match` | dado duplicado (grupo) | `GroupSetupViewModelTest`; `GroupListViewModelTest` (`tapping create again while the check is in flight emits exactly one effect`) |
| Foto do grupo / do perfil (trocar, apagar) | `GroupPhotoViewModel`, `ProfilePhotoViewModel` | `isLoading` na entrada | `PUT/DELETE …/photo`: idempotentes | só estético | `GroupPhotoViewModelTest` (perfil: sem teste de toque duplo) |
| Excluir a conta | `AccountDeletionViewModel.delete` | `isBusy` em todo intent + `deleted` | `DELETE /session`: repetível (teste do gateway) | destrutiva | `AccountDeletionViewModelTest` (`double submission cannot start another deletion…`), `KtorProfileGatewayTest` |
| Editar perfil | `EditProfileViewModel.save` | `canSave` (inclui `!isSaving`) | `PATCH /session/profile`: sobrescreve | só estético | `EditProfileViewModelTest` (sem teste de toque duplo) |
| Login, cadastro, completar identidade | `AuthenticationCoordinator`, `VerifiedSessionCoordinator`, `RegisterViewModel` | `isLoading`/`beginSubmit` | `PUT /session`: idempotente | só estético | `AuthenticationCoordinatorTest` (`second password login while loading is ignored`), `RegisterViewModelTest` (`a submit already in flight is ignored`) |

## Furos achados e o que foi feito

| # | Furo | Correção | Commit |
|---|---|---|---|
| 1 | Link do "não vou": segundo toque no botão da folha mandava outra resposta (o `requestId` fixo evitava duplicar no servidor, mas o app não travava) | `AttendanceLinkState.declining` + botão desabilitado | `6a812054b` |
| 2 | `POST /api/reports` sem idempotência: repetição gravava outra denúncia e outro e-mail para a equipe | trava advisory + `EXISTS` por (denunciante, alvo, motivo, aberta) | `20f9b9f87` |
| 3 | Sem teste de toque duplo no "Vou/Não vou" (3 ViewModels) | 3 testes | `f648821a3` |

## Sem correção (decisão do dono ou risco de contrato)

- **Trocar o código de convite (`POST /groups/{id}/invite`)**: não tem `requestId`; duas chamadas concorrentes de
  dois aparelhos deixam o link que um deles mostra inválido. No mesmo aparelho a trava `isGenerating` já cobre. Corrigir no
  backend exigiria `requestId` novo no corpo (apps 1.0.4–1.0.6 não enviam). **Recomendação:** deixar como está; só admins
  trocam e a tela recarrega o código atual ao voltar.
- **Reaproveitar o `requestId` do "Avisar" após sucesso**: depois de um aviso enviado o botão volta e o próximo toque
  gera outro `requestId` e avisa de novo — é o comportamento desejado (novo lembrete), limitado pelo organizador.
  Mudar isso é decisão de produto.
- **BE-4b (cobrar cobrança já paga → `VERSION_CONFLICT`)**: não é duplo toque, é corrida com baixa; o app recarrega.
  Mantido como o dono decidiu em 13/10.
- **`Home.load()` zera `responding`** numa recarga completa com resposta em voo: a resposta velha é descartada por
  `responseGeneration` e um novo toque seria aceito. O servidor trata o `requestId` novo normalmente (lock do jogo; N-18).
  Janela mínima, não corrigida.
- **Sem teste de toque duplo dedicado** (guarda existe e foi lida): `NotificationCenterViewModel`, `GroupThreadViewModel`
  (coberto só pelo teste de comunicação), `ProfilePhotoViewModel`, `EditProfileViewModel`, `OwnProfileViewModel`.
  São de risco baixo (só estético); deixei como está.
