# Execução — recebimentos Asaas

Fonte de intenção: plano de implementação fornecido pelo usuário em 2026-09-12, com as revisões de escopo posteriores registradas abaixo.
O plano substitui as decisões ainda pendentes e propostas conflitantes de context.md
e direcionamento.md. O estado de cada tarefa está indicado abaixo; itens parciais permanecem abertos.

## Regras vinculantes

- Tarifas sempre repassadas ao membro; valores comerciais são configuração, sem defaults inventados.
- Trial ativo, Organizador e Ilimitado permitem novas ordens; Titular e direito expirado não permitem.
- Corte interrompe ciclos futuros, preservando vencidas e avulsas emitidas; retomada exige autorização.
- Dinheiro existente e conciliação independem do direito comercial e da existência do grupo.
- Revisão explícita de escopo: reembolsos são tratados fora do Saqz pelo titular ou pela operação, inclusive os dos planos do próprio app. Não implementar solicitação, aprovação ou execução de reembolso no mobile, painel administrativo ou API do Saqz. Manter a conciliação dos fatos externos e seus reflexos no histórico/caixa; não é funcionalidade adiada.
- Identidade financeira não acompanha transferência de grupo; delegação é revogável e sem redelegação.
- Nenhum retorno de navegador confirma pagamento. Resultado incerto exige recuperação, nunca recriação cega.

## Execution Plan

### Fase 1 — domínio e ativação (lote 1)

- [x] T01 Criar módulo receivables, contratos financeiros e interfaces de elegibilidade/grupos; atualizar arquitetura. Gate: domínio e arquitetura.
- [x] T02 Adicionar migrações de contas, vínculos, delegações, termos, tarifas, ordens, instrumentos, recorrências, operações, eventos e movimentos. Gate: PostgreSQL, unicidade e preservação das cobranças manuais.
- [x] T03 Implementar armazenamento cifrado de credenciais e operações persistidas recuperáveis. Gate: sigilo, concorrência, idempotência e timeout.
- [x] T04 (cadastro/documentos/correção e recuperação operacional da credencial perdida concluídos; e40d5c0b, 75 testes independentes e 11 falhas detectadas) Integrar criação/recuperação voluntária de subconta, aceite versionado, onboarding e documentos. Emissão da chave substituta é operação externa documentada; importação local validada não cria outra conta.
- [x] T05 (autorização atual aplicada a todas as operações, delegações mobile e revogação durante saldo/claim verificadas) Implementar autorização financeira, delegações e revogação por remoção de administrador. Gate: sessão aberta, troca de titular do grupo e isolamento entre contas.
- [x] T06 (elegibilidade central e ativação/desativação com revisão e aceite verificadas; execução remota do corte em T13) Implementar ativação por grupo e composição com elegibilidade central. Gate: matriz trial/planos, aprovação e ativação explícita.

### Fase 2 — cobrança e dinheiro (lote 2; depende da fase 1)

- [x] T07 (cálculo, publicação, simulação e snapshots imutáveis aceitos na emissão verificados) Implementar tarifas versionadas e cálculo decimal em centavos com split fixo. Gate: casos fixos/percentuais, arredondamento e snapshot aceito.
- [x] T08 (Pix/cartão hospedado, renovação da mesma obrigação após corte e recuperação sem POST automático verificados) Implementar ordens e instrumentos Pix/cartão hospedado com recuperação de resultado incerto. Gate: pagamento simulado, renovação após corte e timeout sem duplicação.
- [x] T09 (webhooks autenticados, recuperação, estados distintos e reflexo único verificados com PostgreSQL/HTTP simulado; homologação real em T20) Persistir/processar webhooks e conciliar pagamento, liquidação, split e caixa. Gate: duplicados, fora de ordem, outra conta, evento perdido e divergência.
- [x] T10 (reserva/concorrência/cancelamento no backend e seleção/revisão/aceite explícitos no caixa mobile verificados em 6001480d; recuperação consulta antes de replay e preserva comando no retorno) Integrar cobranças manuais, cancelamento e seleção de pendências antigas. Gate: corrida entre baixa manual, cancelamento e pagamento.
- [x] T11 (carteira backend/mobile, destino/saque, autenticação recente, concorrência e manutenção após corte verificados) Implementar carteira, destinos, autenticação recente e saques. Gate: saldo insuficiente, restrição, concorrência e acesso após expiração/exclusão do grupo.
- [x] T12 (fatos externos, reversão única e custos residuais observados/correlacionados verificados; nenhuma solicitação de reembolso no produto) Conciliar reembolsos realizados externamente e chargebacks com reversão única. Solicitação e execução pelo Saqz excluídas por decisão do usuário. Gate: valores e taxas observados no provedor, custo residual, idempotência e dívida não reaberta.
- [x] T13 (assinatura mensal, corte remoto antes de cancelar futuras, preservação de vencidas e retomada com novo aceite verificados) Implementar recorrência, corte efetivo e retomada autorizada. Gate: competência única, ciclos futuros antecipados, vencidas preservadas e corte incompleto recuperável.

### Fase 3 — clientes e operação (lote 3; depende da fase 2)

- [x] T14 (gateways KMP completos e conectados, incluindo carteira, gestão, recorrência, renovação e avisos; Android/iOS e DI verificados) Criar módulos KMP domain/data/presentation e gateways tipados. Gate: gateways Ktor, erros, requestId e fronteiras.
- [x] T15 (cadastro/documentos, configuração, gestão cadastral e seleção de administradores atuais conectados e verificados) Implementar descoberta, cadastro, ativação e delegações no mobile. Gate: ViewModels, navegação, rascunhos não sensíveis e evidências visuais.
- [x] T16 (pagamento avulso, recorrência, renovação Pix, histórico e compartilhamento real de comprovante implementados; seis falhas injetadas detectadas na revisão final) Implementar pagamento avulso, recorrência e histórico do membro. Gate: Pix expirado, cartão recusado, retorno pendente, abandono e cancelamento.
- [x] T17 (Perfil → Recebimentos permanente com cadastro/gestão, carteira e saque; manutenção independente de grupo/plano verificada) Implementar Perfil → Recebimentos, carteira e saque permanentes; exibir no histórico os reembolsos conciliados externamente. Gate: novo login, perda de vínculo/plano, Android e simulador iOS.
- [x] T18 (fila operacional, recuperação auditada somente por consulta remota, termos/tarifas/piloto e painel implementados e verificados) Implementar painel operacional, tarifas/termos/piloto e recuperação auditada. Gate: testes Node e navegador/API; admin sem poder de saque.
- [x] T19 (termos públicos vigentes, retorno sem confirmação, avisos internos de plano/pendências e fila operacional verificados; sem envio de comunicação externa nesta entrega) Implementar termos públicos, retorno do checkout, avisos de plano e notificações operacionais. Gate: callback sem confirmação e ausência de dados financeiros no endereço.
- [ ] T20 (runbook integrado, cenários, liberação/reversão e monitoramento preparados; falta execução real autorizada) Executar homologação integrada e documentar liberação/reversão e monitoramento. Gate: todos os cenários obrigatórios do plano; BaaS, termos e condições reais são pré-requisitos de produção.
- [x] T21 Executar verificação independente via Orca, corrigir lacunas e registrar evidências por critério. Quatro revisões finais aprovadas: backend recorrência/operação 6/6 falhas detectadas, carteira/gestão 7/7, mobile 6/6 e credencial operacional 11/11; todas restauradas e aprovadas.

## Critérios de entrega

Cada tarefa recebe testes derivados dos cenários do plano e commit próprio após o gate.
PRs mobile têm teto de 2.000 linhas alteradas e imagens dos estados modificados.
Não marcar homologação como concluída apenas com mocks ou sem credenciais de sandbox.
Comandos concretos e evidências serão registrados por tarefa após leitura dos módulos afetados.

## Evidência da conclusão de implementação — 2026-09-13

Backend `a401eb7b`, web `de5e7eed`, mobile `6f700201`, `af0a043a`, `777abef4`, além de
`91410d94`, `fda7607d`, `cd0a4a3c` desta onda. Entregas mobile separadas em três commits de
menos de 2.000 linhas alteradas cada. Nenhum PR/push/deploy nesta onda.

- Revisão backend recorrência/operação: `docs/receivables/evidence/final-recurrence-operations-review.md`, 81 testes e 6/6 falhas detectadas.
- Revisão carteira/gestão: `docs/receivables/evidence/final-wallet-management-review.md`, PASS final com 7/7 falhas detectadas após fechar o teste de autenticação recente do destino.
- Revisão mobile: `docs/receivables/evidence/final-recurring-mobile-review.md`, PASS com seis falhas detectadas e todos os discriminantes corrigidos.
- Gate integrado final mobile: 213 Android, domínio 7+7, dados 67+67, apresentação 107+118,
  rede 95 iOS e composição 9 iOS, sem falhas/erros/skips de teste; detektAll e compilação Xcode aprovados.
- T04 credencial perdida concluída e revisada em e40d5c0b. T20 continua aberto
  exclusivamente pela execução real de homologação; runbook completo preparado em `docs/receivables/final-operations-runbook.md`.
