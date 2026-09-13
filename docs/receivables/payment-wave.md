# Pagamentos avulsos — onda de implementação

Base: main 3a9f6b6d. Fonte: plano do usuário e continuação de 2026-09-13.

## Regra de meios confirmada

O titular escolhe Pix, cartão ou ambos por grupo. Ativação com conjunto vazio é inválida no
backend e na interface. Desligar recebimentos é ação explícita separada. O backend e o banco já
protegem essa invariável; ampliar cobertura e aplicar a seleção às ordens/pagamentos.
Delegação financeira expressa mantém o alcance definido no plano original.

Novas ordens capturam meios e condições aprovados para o grupo. Alteração de configuração não
reescreve silenciosamente ordens/aceites/instrumentos anteriores nem invalida dinheiro recebido.
Uma ordem existente preserva os meios aceitos no momento da emissão; qualquer nova contratação
usa a configuração vigente. Pagar ordem existente não exige rollout ou plano elegível.

## Frentes e propriedade

- Backend (backend/**): primeiro pagamento avulso real por adaptador Asaas, ordens vinculadas a
  cobrança existente, snapshot de meios/tarifas, instrumentos recuperáveis, consulta e cancelamento,
  webhook persistido/autenticado por conta, conciliação e efeito único no caixa. Proteção de baixa
  manual e cancelamento de jogo. Sem saque, reembolso solicitado ou recorrência neste lote.
- Mobile (mobile/**): jornada real de configuração de recebimentos do grupo, seleção Pix/cartão,
  revisão e aceite, configuração/estado e desativação. Usa os contratos existentes de accounts e
  groups; composição por callbacks e acesso de manutenção independente do rollout. Não criar
  cadastro financeiro fictício: ausência de conta deve ser estado explícito. Limite 2.000 linhas.
- Adm (adm-web/**): publicar termos e tarifas versionadas e simular pelo backend, usando as APIs
  existentes. Manter framework. Sem valores comerciais predefinidos/inventados; sem cálculo local
  de gross-up. Reenvio idempotente e proteção contra respostas de sessão anterior.
- Coordenador (docs/**, .specs/**): contratos, revisão, gates, commits e integração. Nenhum worker
  altera staging/branch/commit/push. Não editar arquivos de outra frente; propor contrato por Orca.

## Contratos existentes estáveis para os clientes

Ler controllers reais antes de implementar; APIs financeiras retornam {value,requestId} ou
{error,requestId}, diferentemente da disponibilidade/rollout que não têm envelope.

Mobile: GET /api/receivables/accounts; POST /api/receivables/groups/{groupId}/preview,
/activate e /deactivate. Preview recebe requestId/accountId/methods. Activate inclui fingerprint
original e accepted=true; meios vazios falham. Exibir termos de GET /api/receivables/terms/{version}
antes do aceite. O titular pode ativar só PIX, só CARD ou ambos. Não selecionar meios automaticamente.

Adm: POST /admin/receivables/terms, /fees e /fees/simulate; structs em
AdminReceivableConditionsController.kt. Frações percentuais no contrato; UI pode aceitar percentual
convertendo decimal exato como texto. Valores monetários inteiros em centavos. Não inventar listagem
administrativa de histórico de tarifas que o backend ainda não oferece.

O implementador backend deve enviar cedo sua proposta de contratos charges/instruments/eventos
para o coordenador registrar, e só então estabilizar clientes de pagamentos na etapa seguinte.

## Verificação

Backend: HTTP+PostgreSQL+Asaas simulado; métodos individuais/ambos/vazio, tentativa de método não
aceito, ordem após corte, timeout, webhook duplicado/fora de ordem/outra conta, baixa concorrente,
callback sem confirmação. Não chamar Asaas real nesta implementação sem coordenação explícita.
Mobile: gateways/ViewModels/navegação, Android e iOS, revisão visual com evidências reais.
Adm: Node e navegador, comprovando parsing de centavos/percentuais e reenvio idêntico.
Verificador independente após cada frente pronta. Produção e homologação financeira não são
atestadas por mocks. Mantêm-se OFF inicial e todas as permissões financeiras no servidor.

## Ajustes coordenados durante a implementação

GET /api/receivables/groups/{groupId}?accountId=UUID retorna {value:{state:{accountId,groupId,
enabled,pixEnabled,cardEnabled},permissions:{READ:{allowed,reason},CANCEL:{allowed,reason}}},requestId}.
Leitura não depende de termos, tarifas, rollout ou elegibilidade. Vínculo existente pode ser lido
pelo operador autorizado da conta mesmo após exclusão/troca de titular do grupo. Sem vínculo,
validar grupo ativo e titular correspondente; retornar estado desativado sem persistir nada.
Isolamento 404 e Cache-Control no-store.

Para cartão avulso será usada a fatura hospedada retornada em invoiceUrl de POST /payments com
billingType=CREDIT_CARD, sem dados de cartão no Saqz. Essa via documentada permite recuperar a
cobrança por externalReference; o produto separado /checkouts não fornece a mesma garantia de
consulta documentada. Pix usa payments e consulta de QR Code. Fontes verificadas em 2026-09-13:
https://docs.asaas.com/docs/cobrancas-via-cartao-de-credito e
https://docs.asaas.com/reference/criar-cobranca-com-cartao-de-credito.
