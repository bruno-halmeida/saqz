# Cupons e controle de trial

Pedido de 2026-09-13: criar cupons de trial, controlar ligado/desligado/somente cupom e integrar na main ao finalizar.

## Critérios de aceitação
- AC1: modo persistido ON (padrão compatível), OFF ou COUPON_ONLY, alterável apenas por administrador. OFF impede novos trials inclusive com cupom selecionado; não modifica trials ativos ou assinaturas.
- AC2: administrador cria/lista/desativa cupons de trial com código normalizado (ASCII alfanumérico, 1–32), campanha opcional (até 120 caracteres), validade futura opcional e limite positivo opcional. Cada cupom define trialDays inteiro de 1 a 365; o formulário começa em 14. Código duplicado sem distinção de caixa retorna 409; campos inválidos 400; inexistente ao desativar 404. Cupons de desconto continuam separados.
- AC3: organizador autenticado elegível aplica cupom válido; selecionar não inicia contagem nem consome uso. O primeiro grupo inicia exatamente trialDays × 24 horas para cupom válido ou 336 horas no trial público, salva cupom e campanha e consome um uso na mesma transação. Reenvios não renovam trial. Um organizador com grupo anterior, trial anterior ou pagamento anterior não ganha outro.
- AC4: cupom expirado (inclusive no instante limite), desativado, inexistente ou esgotado não libera trial no modo COUPON_ONLY. Revalidar na criação do grupo. Em ON, cupom inválido não remove a oferta pública; não atribuir esse trial à campanha inválida. Alterações preservam histórico concedido. Concorrência não excede limite do cupom; falha criando grupo desfaz uso/trial.
- AC5: APIs de configuração/cupons exigem administrador; aplicação de cupom exige sessão e usa o usuário autenticado, sem ownerId no corpo. Seleção inválida 400, modo OFF ou usuário inelegível 409. Leitura informa modo, possibilidade de aplicar e código válido selecionado.
- AC6: administrativo apresenta os três modos e formulário/listagem de cupons de trial com usos (trials iniciados), prazo e limite; loading/erro/retry e sucesso só após resposta. Operações pendentes não atravessam logout.
- AC7: entrada de criação de grupo no app consulta servidor, permite aplicar cupom e seguir após sucesso; OFF não anuncia trial; COUPON_ONLY sem cupom não abre formulário; trial existente/assinatura continuam abrindo conforme permissão. Erro de rede permite tentar novamente; repetição durante envio não duplica operação.

## Premissas e limite
Atualização explícita do usuário: número de dias configurável por cupom. Prazo continua no primeiro grupo. Código selecionado não reserva vaga e pode perder validade até criar. Campos campanha, validade e limite são opcionais. Padrão ON preserva operação atual no deploy. Atribuição persistida e contador de trials fazem parte; relatório de receita, retenção e links/QR automáticos fica para outra entrega. Merge e push na main autorizados pelo usuário.
