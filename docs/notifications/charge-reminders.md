# Lembretes de cobrança

“Cobrar” e “Cobrar todos” usam POST `/api/groups/{groupId}/charges/notify` com `requestId` e `chargeIds`.
O servidor valida o gestor e todas as cobranças antes de gravar. Uma notificação privada por cobrança fica na central do destinatário. O lembrete não cria pagamentos e não muda o estado da cobrança.

## Push com Firebase

- Backend: Firebase Admin usa o mesmo FirebaseApp/credenciais já configurados para autenticação. FCM envia ao token registrado por cada aparelho.
- Android: Firebase Messaging, canal `saqz-reminders`, permissão `POST_NOTIFICATIONS` no Android 13+, token da aplicação Firebase padrão.
- iOS: FirebaseMessaging encaminha pelo APNs. A Release assina `SaqzIOS/SaqzIOS.entitlements` (`aps-environment=production` + Universal Links); a Debug assina `SaqzIOS/SaqzIOS.debug.entitlements` (**push em sandbox** `aps-environment=development`, sem associated-domains — Universal Links seguem só na Release). Debug com push exige a equipe real com a capability habilitada; Personal Team não assina com esse entitlement.
- A central no app funciona sem permissão de push. A preferência de lembretes desativa o push de cobrança; não apaga o aviso na central.
- O toque no push abre a **central de notificações** do app (Android e iOS). Na central, “Abrir” leva ao grupo, onde o usuário vê suas próprias cobranças.
- O token é revogado quando a sessão termina ou troca de conta e antes do primeiro registro após iniciar o processo. Falhas de revogação bloqueiam o registro seguinte e são tentadas novamente ao retomar o app. Sem rede, a revogação não é imediata: um push genérico pode chegar até a revogação concluir; não contém grupo, pessoa ou valor. Tokens revogados são removidos do backend quando FCM retorna UNREGISTERED. Falhas de registro também são tentadas novamente ao retomar o app.

## Configuração externa para entrega real

1. Habilitar a API Firebase Cloud Messaging no projeto usado pelo app e pelo backend. A identidade do backend precisa poder enviar mensagens FCM.
2. No Firebase Console → Project settings → Cloud Messaging, cadastrar a chave/certificado APNs do aplicativo iOS correspondente.
3. Assinar o app iOS com Apple Developer Team e perfil que permitam Push Notifications (a equipe já configurada no projeto). A Debug usa `SaqzIOS.debug.entitlements` com `aps-environment=development`; a APNs Key precisa estar no Firebase Console (Cloud Messaging) do projeto do ambiente.
4. Instalar a versão atualizada em um aparelho, entrar e conceder permissão de notificações. Criar cobranças de teste e executar o fluxo com contas de teste.

O Firebase Auth Emulator não implementa FCM. Builds locais com o emulador não registram tokens reais. Uma compilação no simulador comprova a integração de código, não a entrega APNs.

## Fila e operação

`notification_push_queue` é preenchida na mesma transação do aviso. O worker executa a cada 15 segundos (`saqz.notifications.push-delay-ms`), até 20 avisos por rodada. Falhas transitórias têm até 10 tentativas com atraso crescente, limitado a uma hora; inspecionar filas com `attempts >= 10 AND completed_at IS NULL` para diagnosticar configuração indisponível. Tokens rejeitados como UNREGISTERED são removidos. Recibos em `notification_push_deliveries` evitam reenviar aos aparelhos já atendidos ao repetir uma rodada. Uma interrupção entre FCM aceitar e o commit pode repetir o push; o Android usa uma tag estável por aviso.

Referências: [FCM Android](https://firebase.google.com/docs/cloud-messaging/android/get-started), [FCM iOS/APNs](https://firebase.google.com/docs/cloud-messaging/ios/get-started), [Firebase Admin](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk).
