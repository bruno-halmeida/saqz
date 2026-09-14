# Runbook F5/F7 — operação de recebíveis

Este documento prepara homologação e liberação. Nenhuma etapa abaixo foi executada contra o Asaas real
nesta entrega; credenciais, condições comerciais e termos aprovados continuam pré-requisitos externos.

## Pré-condições de homologação

1. Usar conta sandbox exclusiva. Para validar saque, habilitar somente a permissão necessária nessa conta de teste; o adm Saqz continua sem poder de saque ou reembolso.
2. Publicar um termo jurídico aprovado via fluxo administrativo; não inserir texto de teste em produção.
3. Confirmar a configuração comercial retornada pelo Asaas e registrar valores observados, sem copiá-los
   como defaults do Saqz.
4. Configurar webhook com token próprio (não a API key), entrega sequencial quando a ordem for necessária,
   e armazenar cada `event.id` antes de responder HTTP 200.
5. Ativar primeiro usuário piloto pelo rollout; manter novas operações desligadas para os demais.

## Cenários de homologação integrada

| Cenário | Ação controlada | Evidência esperada |
|---|---|---|
| Operação incerta | Interromper a resposta de uma criação sandbox já persistida | Uma linha `UNKNOWN`; recuperação consulta a cobrança por ID/referência antes de qualquer mutação |
| Concorrência | Dois admins recuperam a mesma operação | Uma reserva; a segunda resposta é 409; uma auditoria por `requestId` |
| Timeout na consulta | Fazer a consulta sandbox exceder o timeout | `STILL_UNKNOWN`, HTTP 503, nenhum novo pagamento/movimento |
| Revogação de admin | Remover o papel e repetir GET/POST | 403 imediato e nenhuma auditoria nova |
| Proibição de poderes | Inserir falha histórica `WITHDRAW`/`REFUND` e abrir detalhe | `recoverable=false`; interface sem ação; POST retorna 409 |
| Termo público | Publicar versão futura e depois torná-la vigente | 404 antes da vigência; conteúdo aprovado literal após vigência |
| Retorno checkout | Voltar com query contendo IDs/status | Tela permanece “em verificação”; query não aparece no DOM e não confirma pagamento |
| Aviso de plano | Publicar janela curta | Titular elegível lê durante a janela; nenhum e-mail/push/SMS é enviado |
| Reembolso externo | Concluir fora do Saqz e receber fatos | Histórico é conciliado uma vez; nenhuma solicitação/execução nasce no Saqz |
| Custo residual | Localizar no extrato uma taxa efetivamente debitada e vinculada ao pagamento | Centavos exatos viram um `RESIDUAL_COST` negativo uma vez; ausência de lançamento não vira estimativa |
| Corte/manutenção | Desativar novas emissões e manter consulta/recuperação | Operações novas bloqueadas; fila, termos, histórico e recuperação de incerteza continuam disponíveis |

## Observabilidade sem PII

- Métricas: contagem por `kind/status/failureCode`, idade da operação mais antiga, reservas em andamento,
  recuperações `CONFIRMED/REJECTED/STILL_UNKNOWN`, conflitos e duração da consulta.
- Alertas: crescimento de `UNKNOWN`, lease expirado, fila de webhook interrompida, evento mais antigo perto
  de 14 dias e divergência de custo residual.
- Logs: `operationId`, `requestId`, `kind`, ator administrativo, resultado e duração. Nunca registrar API key,
  payload bruto, CPF/CNPJ, banco, e-mail, URL de checkout ou texto jurídico completo.
- Auditoria SQL: `receivable_operation_audit` e `receivable_operational_recoveries`; ambas preservam intenção,
  ator, motivo e resultado. Movimentos seguem append-only.

## Liberação

Na integração com a `main`, a migração de destinos bancários passou de V56 para V64,
sem alteração do SQL. A V56 de cupons/trial já publicada foi preservada. As V57–V63
não dependem das novas colunas dos destinos bancários; o conjunto completo deve ser aplicado.

1. Aplicar o conjunto versionado completo pendente (incluindo V57–V64 desta onda) e verificar constraints/índices/triggers; não aplicar apenas as migrações do painel.
2. Fazer smoke test anônimo apenas em `GET /public/receivables/terms/*` e
   `GET /public/receivables/checkout-return`; confirmar 401 em POST e paths irmãos.
3. Fazer smoke test admin com conta autorizada e outra revogada.
4. Publicar aviso de manutenção previamente aprovado, sem acionar canal externo.
5. Ativar piloto e observar ao menos uma janela completa de webhook/recuperação antes de ampliar rollout.
6. Registrar IDs de evidência, horários UTC e valores em centavos; não anexar payloads sensíveis.

## Reversão operacional

- Desligar novas operações pelo rollout. Não apagar operações, auditorias, avisos, termos ou movimentos.
- Preservar rotas de leitura e recuperação para dinheiro/obrigações já existentes, inclusive após o corte.
- Se o painel novo falhar, retirar sua navegação estática; o backend continua negando ações não autorizadas.
- Não desfazer migrações destrutivamente. Corrigir adiante com nova migração e manter histórico append-only.
- Se a observação remota permanecer incerta, manter `UNKNOWN` e reconciliar por webhook/extrato; nunca
  recriar a cobrança, efetuar saque ou iniciar reembolso.

## Fontes Asaas verificadas em 2026-09-13

- Webhooks são at-least-once e eventos devem ser persistidos por ID:
  https://docs.asaas.com/docs/sobre-os-webhooks
- Após 15 falhas a fila pode parar; eventos parados são retidos por até 14 dias:
  https://docs.asaas.com/docs/faq-de-webhooks
- Consulta de cobranças aceita `externalReference`, é paginada e limita a página a 100:
  https://docs.asaas.com/reference/list-payments
- O extrato (`GET /financialTransactions`) é paginado, contém débitos/taxas efetivos e pode vincular
  lançamentos por `paymentId`; novos tipos podem surgir:
  https://docs.asaas.com/reference/recuperar-extrato
- Taxas de cobrança não são necessariamente devolvidas no estorno; portanto custo residual deve vir do
  lançamento observado, não de tabela estimada:
  https://docs.asaas.com/reference/refund-payment
- Um item de estorno só é concluído com `status=DONE`, e todos os itens devem ser percorridos:
  https://docs.asaas.com/docs/estornos

## Limites ainda externos

- Homologação real Asaas, piloto real, termos aprovados e condições comerciais não foram executados.
- O adaptador de pagamentos alimenta `ReconcileExternalResidualCost` somente com taxa efetivamente observada e correlacionada ao pagamento revertido. Não há estimativa nem fallback.
- O Saqz não oferece solicitação, aprovação ou execução de reembolso, inclusive para planos do próprio app.

## Site estático e API pública

O site é publicado separadamente da API. A meta `saqz-api-base-url` em
`landing-page/termos/recebimentos/index.html` aponta para a origem HTTPS da API configurada no projeto.
`receivables-terms.js` consulta essa origem sem cookies/credenciais. A API permite CORS somente GET/OPTIONS
nas rotas públicas; demais métodos e caminhos continuam autenticados. Antes de publicar em outro ambiente,
ajustar a origem da meta para a API correspondente e testar a consulta real entre as duas origens.

A recuperação administrativa usa `JdbcPaymentExecution.reconcileObserved`: somente leitura do provedor,
aplicação de fatos correlacionados e atualização das operações locais incertas, preservando leases ativos.
Nenhuma recuperação no adm emite ou cancela uma cobrança remotamente.

## Jornada completa para homologação F1–F7

Executar no sandbox autorizado com dados de teste e registrar evidência sanitizada de cada etapa.
Esta lista prepara a execução; resultados de mocks não a encerram.

| Jornada | Cenário e prova necessária |
|---|---|
| Cadastro PF/PJ | Aceite da versão vigente, criação única, documentos, estado remoto de aprovação; timeout não cria segunda subconta |
| Correção e delegação | Corrigir contato/endereço sem alterar identidade legal; conceder/revogar administrador atual e comprovar perda de acesso durante sessão aberta |
| Pagamento Pix | Liberar pendência com snapshot aceito, exibir QR/copia e cola, pagar, observar webhook e consulta remota, conferir base/tarifas/total/split em centavos |
| Cartão | Abrir checkout hospedado, simular recusa e conclusão; retorno/abandono não confirma localmente; nenhum dado de cartão entra no Saqz |
| Renovação Pix | Expirar QR, renovar data na mesma obrigação/instrumento/provider payment e conferir valores originais, inclusive após corte do plano |
| Dinheiro e comprovante | Confirmado, liquidado e disponível permanecem distintos; saldo, recebíveis e extrato batem com provedor; compartilhamento só após fato remoto e callback nativo |
| Saque | Reautenticar mesma identidade, aceitar destino/valor, verificar saldo insuficiente e concorrência; timeout recupera mesma transferência sem segundo POST |
| Mensalidade Pix/cartão | Novo aceite completo, uma competência por grupo/membro; Pix manual mensal, cartão hospedado; observar assinatura e futuras cobranças reais |
| Corte | Perder elegibilidade/desativar grupo, comprovar assinatura inativa e cancelamento de futuras antecipadas; preservar vencidas/avulsas, saldo e histórico |
| Retomada | Somente após encerramento remoto e novo preview/aceite; nova assinatura sem reativação silenciosa |
| Operação | Webhook duplicado/fora de ordem/perdido, falhas na consulta, fila e recuperação GET-only; custo residual externo apenas quando débito correlacionado foi observado |
| Clientes | Login novo, restauração após processo encerrado, troca de sessão durante IO e share, navegação Android/iOS, retorno do checkout e termos entre origens reais |

Pausar novas vendas pelo rollout/desativação comercial. Manter a composição de pagamentos ativa
para atender webhooks, consultas e obrigações existentes; não desligar `payments-enabled` após haver
operações vivas. Não revogar a manutenção financeira como mecanismo de pausa comercial.

A exceção de chave perdida após criação de subconta segue o
[procedimento de recuperação de credencial](credential-recovery-runbook.md): importação operacional
validada por consultas, cifra e auditoria, sem emissão de subconta/chave pelo comando e sem rota pública.
