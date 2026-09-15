# Recebimentos Asaas — contexto e acesso após expiração

Data: 2026-09-12
Status: em discussão; documento de integração entre recebimentos e período gratuito.
Não representa funcionalidade implementada nem contratação/habilitação do BaaS.

Direcionamento geral de ativação opcional, descoberta, tarifas e cobrança:
[Financeiro — direcionamento](direcionamento.md).

## Decisões expressas pelo usuário

- Usar Asaas no piloto, aproveitando a integração existente.
- Oferecer cartão, Pix, recorrência e split de pagamentos.
- Iniciar o cadastro financeiro quando o gestor optar por cobrar pelo aplicativo.
- A ativação é opcional e expressa; onboarding e campanhas podem apresentar o recurso,
  mas não criam subconta nem autorizam cobrança automaticamente.
- O gestor deve ver as tarifas explicitamente e pode escolher gerar a cobrança já
  com os acréscimos para os membros. Apresentar total ao pagador antes da confirmação;
  valores comerciais e componentes dos acréscimos ainda serão definidos.
- Para o gestor, agrupar os custos sob o rótulo “Taxas de serviço e pagamento”,
  com valor agregado na tela e composição entre processamento Asaas e remuneração
  Saqz detalhada nos termos de aceite, disponíveis antes da ativação. Mostrar também
  preço base, total da cobrança e líquido previsto antes da emissão.
- O término do período gratuito, a expiração ou a falta de pagamento da assinatura
  Saqz não podem prender o dinheiro do gestor nem condicionar seu acesso à renovação.
- O período gratuito está sendo desenvolvido em paralelo; este contrato precisa ser
  considerado naquele trabalho sem presumir sua implementação ou modificar suas regras.

## Regra obrigatória de produto

FIN-ACCESS-01: a situação comercial do plano Saqz não pode negar ao titular
autenticado e autorizado acesso aos seus recursos financeiros existentes.

Isso inclui saldo disponível, valores a receber, histórico, comprovantes, solicitação
e acompanhamento de saques, correção do cadastro necessária para retirar valores,
gestão de devoluções e contestação e cancelamento de cobranças/recorrências existentes.
As operações continuam sujeitas à disponibilidade de saldo, às regras do provedor
e às verificações de identidade e de autorização; expiração do Saqz nunca é motivo
para bloquear essas operações ou exigir upgrade.

FIN-ACCESS-02: recebimentos que liquidarem depois da expiração precisam continuar
sendo conciliados e disponibilizados ao titular. Webhooks, consultas de recuperação
e processamento financeiro não dependem de plano ativo.

FIN-ACCESS-03: o término do plano não encerra a subconta, não apaga suas credenciais,
não transfere a titularidade e não utiliza o saldo para quitar o plano Saqz.
A comissão por split segue as condições aceitas para a transação; não muda
retroativamente por causa da expiração.

## Proposta de arquitetura para orientar as duas implementações

- Separar situação comercial do Saqz, situação cadastral/operacional da subconta,
  identidade do titular e estado das transações.
- Não usar `entitled` ou `readOnly` da assinatura como bloqueio global financeiro.
  Permitir ações por finalidade; liberar todo POST indiscriminadamente também é errado.
- Garantir entrada para a área de recebimentos mesmo se o restante do produto tiver
  limitações comerciais, inclusive após novo login ou acesso por link.
- Autorizar por titularidade financeira, sem depender exclusivamente de o grupo
  continuar ativo ou de o usuário continuar sendo seu administrador.
- Não transferir a subconta ou o direito a saldos passados ao trocar o gestor do grupo.
- Manter o acesso do atleta às próprias cobranças, comprovantes e cancelamentos
  aplicáveis quando o plano do organizador expirar.
- Prever transferência do saldo disponível à conta do titular. Agendamento automático
  de repasses é uma possibilidade a validar com o Asaas e com os custos do contrato.
- Em falha do provedor, mostrar estado pendente/indisponível e permitir recuperação;
  não interpretar falha como saldo zero nem exigir renovação do plano.
- Processar solicitações repetidas e eventos fora de ordem sem duplicar transferências,
  cobranças ou devoluções. Pagamento confirmado não deve ser tratado automaticamente
  como saldo já disponível para saque.

## Matriz proposta após término do direito comercial

| Operação | Tratamento |
| --- | --- |
| Consultar saldo, recebíveis, histórico e comprovantes | Preservar acesso |
| Solicitar e acompanhar saque de saldo disponível | Preservar, sujeito às regras financeiras |
| Corrigir cadastro necessário à movimentação | Preservar |
| Gerir devolução, contestação e cancelamento existente | Preservar, sujeito às regras financeiras |
| Conciliar pagamento tardio e acompanhar transferência pendente | Continuar processamento |
| Criar novas cobranças ou novas recorrências | Decisão comercial pendente; proposta: restringir sem plano/trial elegível |
| Gerar próximos ciclos de recorrências já contratadas pelos atletas | Decisão pendente; não cancelar nem manter silenciosamente por suposição |

## Questão de produto a resolver

A expiração do plano do gestor deve interromper futuras renovações das mensalidades
dos atletas ou permitir sua continuidade com as taxas previamente aceitas?

Proposta para discussão: manter as recorrências existentes, preservando sua gestão,
e restringir a criação de novas. Benefício: evita interromper pagamentos contratados
por atletas por um problema comercial do gestor. Contraponto: mantém um serviço de
cobrança recorrente para quem deixou de assinar Saqz, com custos operacionais e de suporte.
Se a opção for interromper, definir aviso, data de corte, instruções já enviadas ao
provedor, cobranças já emitidas, retentativas e retomada sem duplicação. Apenas ocultar
botões ou parar um job local não garante a interrupção da recorrência no provedor.

Desativação explícita de recebimentos pelo gestor é evento diferente da expiração
do plano e precisa de regras próprias para cobranças e recorrências futuras.

## Critérios de aceitação para a futura implementação

1. Após expirar trial ou assinatura, o titular consegue entrar diretamente na área
   financeira, consultar seu saldo e solicitar transferência disponível sem paywall.
2. Um cartão pago antes da expiração, mas liquidado depois, é conciliado e seu saldo
   permanece acessível. Webhooks repetidos não duplicam valores.
3. Transferência solicitada antes da expiração continua sendo acompanhada até o
   resultado final; repetir a solicitação não cria um segundo saque.
4. Uma assinatura expirada não impede cancelamento de recorrência existente,
   solicitação de devolução elegível nem atendimento de contestação.
5. Um usuário sem titularidade/autorização não acessa nem movimenta a subconta alheia,
   mesmo com assinatura ativa ou após assumir a administração do grupo.
6. Um bloqueio do Asaas é distinguido da expiração comercial: mostra motivo/ação
   aplicável, sem prometer que pagar Saqz liberará o dinheiro.
7. Expiração não encerra a subconta, não exclui histórico e não desconta dívida Saqz
   dos recebíveis do gestor.
8. A navegação do atleta para suas cobranças/comprovantes existentes continua
   acessível após expirar o plano do gestor.
9. Critérios de geração dos próximos ciclos serão fechados após a decisão pendente.

## Evidência atual e limites da análise

- `backend/features/subscriptions/src/main/kotlin/br/com/saqz/subscriptions/domain/Subscription.kt`
  define `isEntitlingAt` e os estados ACTIVE, PAST_DUE e CANCELED.
- `backend/features/subscriptions/src/main/kotlin/br/com/saqz/subscriptions/application/GetMySubscription.kt`
  expõe `entitled` e `readOnly`. Estes indicadores comerciais não bastam para
  autorizar movimentação de recebimentos.
- O fluxo de subcontas/saques do gestor ainda será implementado. Este documento
  não afirma que o código atual já bloqueia dinheiro ou implementa o trial em paralelo.

## Referências do provedor, consultadas em 2026-09-12

- [BaaS Asaas](https://docs.asaas.com/docs/cria%C3%A7%C3%A3o-de-subcontas-baas):
  aprovação cadastral e operação via plataforma; não presumir painel Asaas disponível
  ao titular como alternativa automática ao aplicativo.
- [Transferências](https://docs.asaas.com/docs/transferencias): saída depende do saldo
  disponível e possui processamento/status próprios.
- [Assinaturas](https://docs.asaas.com/docs/assinaturas): cobranças são geradas ao longo
  da recorrência e precisam de acompanhamento individual.

Confirmar com Asaas o caminho de continuidade/saída dos titulares caso a operação
BaaS do próprio Saqz seja encerrada; não confundir isso com o fim do plano de um gestor.
