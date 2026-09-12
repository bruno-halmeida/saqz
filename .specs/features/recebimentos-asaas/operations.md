# Estado operacional — implementação em andamento

Não liberar o piloto com esta base isoladamente. Pagamentos, carteira, ativação por grupo,
recorrência, mobile e painel ainda dependem das próximas tarefas.

## Cadastro financeiro

O bootstrap registra os endpoints de cadastro quando a proteção de dados financeiros
está configurada. Propriedades devem vir do secret manager/ambiente, fora do banco:

- `saqz.receivables.encryption-key`: chave AES de 32 bytes, codificada em base64.
- `saqz.receivables.encryption-key-id`: identificador da chave.
- `saqz.receivables.identity-lookup-key`: chave independente de pelo menos 32 bytes,
  base64, usada no HMAC de CPF/CNPJ. Não trocar sem migração dos digests.
- `saqz.receivables.asaas-platform-key`: credencial da conta principal.
- `saqz.receivables.asaas-base-url`: padrão sandbox `/v3`; produção exige configuração expressa.
- `saqz.receivables.baas-enabled`: padrão `false`; habilitar apenas após liberação BaaS pelo Asaas.

O switch BaaS restringe criação de contas. Não remover chaves nem configuração de
leitura para interromper novas operações: credenciais são necessárias à manutenção
dos valores existentes. `new_operations_enabled` começa falso em toda conta.

A biblioteca de cifragem aceita um keyring para leitura de chaves antigas. O bootstrap
atual injeta uma chave; rotação operacional com múltiplas chaves ainda precisa ser
conectada antes de substituir a chave de um ambiente com dados.

A interface de publicação de termos e liberação por titular será feita pelo painel nas próximas tarefas.
Nenhum termo ou valor comercial foi inventado ou semeado na migração.

## Recuperação de cadastro incerto

A criação remota é precedida de conta local, aceite e operação persistidos juntos.
Repetir o cadastro não cria outra conta. Resposta com `apiKey` é cifrada e persistida
antes de concluir a operação. Recuperação encontra credenciais já persistidas e fecha
a operação; se a resposta com a chave foi perdida antes de persistir, a operação fica
UNKNOWN para tratamento operacional, sem emitir outro POST de criação.

O Asaas retorna a chave apenas uma vez. Recuperar identificadores via listagem não
recupera a chave e não prova autorização para vincular uma conta a outro usuário.
O fluxo operacional de recuperação/rotação da chave ainda precisa de homologação BaaS.

Documentos só são consultados 15 segundos após a resposta de criação. Quando existe
`onboardingUrl`, upload pela API é recusado. Upload aceito não implica aprovação;
a aprovação é obtida pelo `general` de `/myAccount/status`.

## Fontes oficiais verificadas em 2026-09-12

- https://docs.asaas.com/reference/criar-subconta
- https://docs.asaas.com/reference/listar-subcontas
- https://docs.asaas.com/reference/verificar-documentos-pendentes
- https://docs.asaas.com/reference/enviar-documentos
- https://docs.asaas.com/docs/onboarding-e-envio-de-documentos-via-link
- https://docs.asaas.com/docs/split-de-pagamentos

## Limites da homologação

Testes locais usam PostgreSQL real e HTTP Asaas simulado. Não houve chamada a uma
subconta real, envio de documento real, cobrança, saque ou publicação em produção.
V49 contém a base financeira; V50 adiciona a data de criação remota necessária
ao onboarding. A separação preserva o checksum de V49 entre as entregas.

A migração do trial central foi renumerada para V48, resolvendo a colisão anterior
com V46 de convites permanentes. O conteúdo SQL do trial foi preservado.
A elegibilidade usa OrganizerTrialAccessLookup e Subscription.isEntitlingAt;
o corte de downgrade usa pendingPlanEffectiveAt, sem recalcular o trial.

## Simulação de condições

`POST /api/receivables/charges/simulate` recebe requestId, baseCents inteiro e methods
(PIX e/ou CARD). Retorna quotes com base, taxas, total, líquido previsto e versão dos
termos/tarifas, sem gerar aceite ou dívida. Sem configuração aplicável retorna 503 com
CONFIGURATION_UNAVAILABLE. `GET /api/receivables/terms/{version}` consulta versões
publicadas, incluindo históricas e condições futuras já anunciadas. Ambas as rotas
usam a autenticação normal da API e independem de BaaS, subconta ou plano elegível.
A página pública de termos continua pendente; a publicação administrativa por API está descrita abaixo.

## Publicação administrativa

Rotas sob `/admin/receivables`, protegidas pelo cadastro de administrador da plataforma:
- `POST /terms`: requestId, version, content, effectiveAt.
- `POST /fees`: requestId, method, providerRate, providerFixedCents, commissionRate,
  commissionFixedCents, termsVersion, effectiveAt. O requestId também identifica a tabela.
- `POST /fees/simulate`: mesmos campos de tarifas e baseCents; não publica.

Taxas percentuais usam frações decimais (0.01 significa 1%). Valores fixos e base usam
centavos inteiros. Todas as condições devem ser fornecidas; não há defaults comerciais.
Publicação exige vigência presente/futura e termos já publicados aplicáveis nessa data.
Retries com o mesmo ator e conteúdo preservam a primeira resposta; mudança conflita.
V51 guarda auditoria imutável junto da versão, na mesma transação. Não há edição destrutiva.
A interface visual, listagem operacional e liberação do piloto continuam pendentes.
