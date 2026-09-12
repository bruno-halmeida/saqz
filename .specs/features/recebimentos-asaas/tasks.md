# Execução — recebimentos Asaas

Fonte de intenção: plano de implementação fornecido pelo usuário em 2026-09-12.
O plano substitui as decisões ainda pendentes e propostas conflitantes de context.md
e direcionamento.md. O estado de cada tarefa está indicado abaixo; itens parciais permanecem abertos.

## Regras vinculantes

- Tarifas sempre repassadas ao membro; valores comerciais são configuração, sem defaults inventados.
- Trial ativo, Organizador e Ilimitado permitem novas ordens; Titular e direito expirado não permitem.
- Corte interrompe ciclos futuros, preservando vencidas e avulsas emitidas; retomada exige autorização.
- Dinheiro existente e conciliação independem do direito comercial e da existência do grupo.
- Identidade financeira não acompanha transferência de grupo; delegação é revogável e sem redelegação.
- Nenhum retorno de navegador confirma pagamento. Resultado incerto exige recuperação, nunca recriação cega.

## Execution Plan

### Fase 1 — domínio e ativação (lote 1)

- [x] T01 Criar módulo receivables, contratos financeiros e interfaces de elegibilidade/grupos; atualizar arquitetura. Gate: domínio e arquitetura.
- [x] T02 Adicionar migrações de contas, vínculos, delegações, termos, tarifas, ordens, instrumentos, recorrências, operações, eventos e movimentos. Gate: PostgreSQL, unicidade e preservação das cobranças manuais.
- [x] T03 Implementar armazenamento cifrado de credenciais e operações persistidas recuperáveis. Gate: sigilo, concorrência, idempotência e timeout.
- [ ] T04 (parcial: cadastro, documentos e HTTP verificados; correção cadastral e recuperação operacional ainda pendentes) Integrar criação/recuperação voluntária de subconta, aceite versionado, onboarding e documentos. Gate: API simulada e contratos HTTP.
- [ ] T05 (parcial: concessão/revogação/diretório e integração transacional com grupos verificados; faltam permissões aplicadas às operações futuras) Implementar autorização financeira, delegações e revogação por remoção de administrador. Gate: sessão aberta, troca de titular do grupo e isolamento entre contas.
- [ ] T06 (parcial: elegibilidade central e limites temporais verificados; ativação por grupo pendente) Implementar ativação por grupo e composição com elegibilidade central. Gate: matriz trial/planos, aprovação e ativação explícita.

### Fase 2 — cobrança e dinheiro (lote 2; depende da fase 1)

- [ ] T07 (parcial: núcleo decimal e split fixo verificados; simulação HTTP, consulta e publicação de termos/tarifas verificadas; emissão pendente) Implementar tarifas versionadas e cálculo decimal em centavos com split fixo. Gate: casos fixos/percentuais, arredondamento e snapshot aceito.
- [ ] T08 Implementar ordens e instrumentos Pix/cartão hospedado com recuperação de resultado incerto. Gate: pagamento simulado, renovação após corte e timeout sem duplicação.
- [ ] T09 Persistir/processar webhooks e conciliar pagamento, liquidação, split e caixa. Gate: duplicados, fora de ordem, outra conta, evento perdido e divergência.
- [ ] T10 Integrar cobranças manuais, cancelamento e seleção de pendências antigas. Gate: corrida entre baixa manual, cancelamento e pagamento.
- [ ] T11 Implementar carteira, destinos, autenticação recente e saques. Gate: saldo insuficiente, restrição, concorrência e acesso após expiração/exclusão do grupo.
- [ ] T12 Implementar reembolso integral e chargebacks com reversão única. Gate: taxas integrais, custo residual e dívida não reaberta.
- [ ] T13 Implementar recorrência, corte efetivo e retomada autorizada. Gate: competência única, ciclos futuros antecipados, vencidas preservadas e corte incompleto recuperável.

### Fase 3 — clientes e operação (lote 3; depende da fase 2)

- [ ] T14 Criar módulos KMP domain/data/presentation e gateways tipados. Gate: gateways Ktor, erros, requestId e fronteiras.
- [ ] T15 Implementar descoberta, cadastro, ativação e delegações no mobile. Gate: ViewModels, navegação, rascunhos não sensíveis e evidências visuais.
- [ ] T16 Implementar pagamento avulso, recorrência e histórico do membro. Gate: Pix expirado, cartão recusado, retorno pendente, abandono e cancelamento.
- [ ] T17 Implementar Perfil → Recebimentos, carteira, saque e reembolso permanentes. Gate: novo login, perda de vínculo/plano, Android e simulador iOS.
- [ ] T18 Implementar painel operacional, tarifas/termos/piloto e recuperação auditada. Gate: testes Node e navegador/API; admin sem poder de saque.
- [ ] T19 Implementar termos públicos, retorno do checkout, avisos de plano e notificações operacionais. Gate: callback sem confirmação e ausência de dados financeiros no endereço.
- [ ] T20 Executar homologação integrada e documentar liberação/reversão e monitoramento. Gate: todos os cenários obrigatórios do plano; BaaS, termos e condições reais são pré-requisitos de produção.
- [ ] T21 Executar verificação individual, conforme escolha do usuário,, corrigir lacunas e registrar evidências por critério. Gate: relatório do verificador, incluindo teste de discriminação em cópia temporária.

## Critérios de entrega

Cada tarefa recebe testes derivados dos cenários do plano e commit próprio após o gate.
PRs mobile têm teto de 2.000 linhas alteradas e imagens dos estados modificados.
Não marcar homologação como concluída apenas com mocks ou sem credenciais de sandbox.
Comandos concretos e evidências serão registrados por tarefa após leitura dos módulos afetados.
