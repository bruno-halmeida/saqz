# Cobrança por notificação e push

Pedido: substituir WhatsApp por notificação individual no app e push, mantendo seleção múltipla e Cobrar todos.

## Aceitação
- AC1: somente organizador/admin de grupo pode enviar; corpo informa 1..200 IDs distintos de cobranças pendentes do grupo. Seleção inválida falha sem entrega parcial.
- AC2: uma notificação privada por cobrança, somente ao titular ativo; detalhes originados no servidor; canal CHARGE não aparece em mensagens do grupo nem aceita publicação genérica.
- AC3: requestId garante replay idempotente; reutilização com seleção diferente retorna conflito. Não altera pagamentos.
- AC4: notificação no app persiste mesmo sem dispositivo/permissão; push respeita preferência de lembretes, usa fila persistente, retenta falhas transitórias e remove token inválido. Push não expõe valores na tela bloqueada.
- AC5: app envia IDs selecionados, bloqueia duplicação em voo, mostra sucesso/falha, permite tentar novamente com o mesmo requestId; não exige Pix nem abre WhatsApp. Abrir notificação CHARGE leva ao grupo, que exibe as cobranças do próprio usuário.
- AC6: Android/iOS registram token autenticado, pedem permissão nativa, tratam atualização do token e revogam o token ao sair/trocar conta; falhas offline são tentadas novamente ao retomar o app e bloqueiam o registro na próxima conta até a revogação. Push abre o app; a central oferece acesso à cobrança.

## Limites de validação externa
Entrega real depende de credenciais FCM/APNs e permissão em dispositivo real. Não enviar cobranças reais durante os testes.
