# Estado operacional — implementação em andamento

Não liberar o piloto com esta base isoladamente. Pagamentos, carteira, delegações,
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

Publicação de termos e liberação por titular serão feitas pelo painel nas próximas tarefas.
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
As migrações ainda são alterações locais de uma feature inédita, não publicadas;
V47 foi complementada durante o desenvolvimento da mesma entrega.
