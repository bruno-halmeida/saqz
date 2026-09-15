# Origem das tarifas de Recebimentos

## Regra

O ADM publica somente a comissão do Saqz por meio de pagamento (`commissionRate`, fração decimal; `commissionFixedCents`, centavos), com versão de termos e vigência. `providerRate` e `providerFixedCents` enviados por clientes antigos são rejeitados com HTTP 400 nos endpoints administrativos de publicação e simulação.

As tarifas do Asaas vêm de [GET /myAccount/fees/](https://docs.asaas.com/reference/recuperar-taxas-da-conta), autenticado com a chave da conta que recebe. A simulação informativa sem recebedor e a prévia do ADM usam `saqz.receivables.asaas-platform-key`. Ativação de grupo, cobranças avulsas e novas recorrências passam a conta autorizada pelo servidor; sua chave é obtida das credenciais criptografadas do cadastro financeiro. Ausência da chave de uma subconta nunca faz a consulta usar a chave da plataforma.

## Conversão do contrato Asaas

- Pix: tarifa fixa ou percentual com mínimo e máximo; considera a franquia mensal disponível retornada pelo Asaas.
- Cartão à vista: `operationValue` e `oneInstallmentPercentage`. O fluxo atual não emite parcelamento.
- Descontos: usa a tarifa promocional enquanto `discountExpiration` estiver vigente. Datas com offset são absolutas; o formato local `yyyy-MM-dd HH:mm:ss` usa `America/Sao_Paulo`.
- Valores monetários são convertidos de reais para centavos sem arredondamento implícito. Percentuais são convertidos para frações decimais, preservando a precisão retornada. O cálculo aplica os limites de Pix ao custo total do provedor.
- Contratos incompletos, ambíguos, inválidos ou indisponíveis impedem uma nova cotação. Não há tarifas comerciais padrão nem fallback para custos publicados manualmente.

São tarifas consultadas no momento da prévia. Franquias podem ser consumidas e descontos podem vencer até a liquidação; a conciliação existente registra diferenças do provedor. Ordens e recorrências já aceitas preservam seus valores, sem recalcular históricos ou consentimentos.

## Persistência e atualização

`ProviderFinancialConditions` combina a comissão publicada com as tarifas atuais do Asaas. As colunas legadas de provedor em `receivable_fee_schedules` permanecem para compatibilidade histórica; novas publicações administrativas as preenchem com zero e nenhuma cotação de produção as usa como fonte de tarifas. Os snapshots das cobranças continuam guardando os valores calculados e a versão da comissão.

Não há migração de banco nesta alteração. É necessário atualizar o backend e os arquivos estáticos do ADM em conjunto. O app também recebeu a identificação das tarifas Asaas e seus limites mínimo/máximo na composição de taxas. O ADM não tem etapa de compilação. O controle de disponibilidade reutiliza o endpoint de rollout existente.
