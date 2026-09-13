# Contrato final de gestão financeira

Base: F2/T04/T05/T15. Este contrato cobre manutenção de conta já existente e delegação; não cria
transferência de titularidade, saque, cobrança, recorrência, operação administrativa ou qualquer
pedido/aprovação/execução de reembolso.

## Decisões e fonte do provedor

- O Asaas documenta `GET /v3/myAccount/commercialInfo/` para recuperar os dados comerciais atuais e
  `POST /v3/myAccount/commercialInfo/` para substituí-los. O POST deve reenviar todos os campos
  aplicáveis, exige `incomeValue`, usa a `access_token` da subconta e pode iniciar nova análise.
- Fontes oficiais: <https://docs.asaas.com/reference/recuperar-dados-comerciais>,
  <https://docs.asaas.com/reference/atualizar-dados-comerciais> e
  <https://docs.asaas.com/docs/confirma%C3%A7%C3%A3o-anual-de-dados-comerciais-para-subcontas>.
- O cliente Saqz só pode propor `email`, `phone`, `mobilePhone`, `site`, `incomeCents`, `postalCode`,
  `address`, `addressNumber`, `complement` e `province`. `incomeCents` é inteiro em centavos.
- `personType`, `cpfCnpj`, `birthDate`, `companyType`, `companyName` e `taxRegime` são identidade ou
  classificação legal: não aparecem no request público. O adaptador os lê do Asaas e os reenvia
  sem alteração quando aplicáveis. A API não oferece transferência de titularidade.
- O Saqz não persiste os valores corrigidos em claro. Persiste somente identificadores, digest do
  comando, estado/resultado e payload cifrado necessário à recuperação de resultado incerto.

## HTTP autenticado

Todas as respostas incluem `Cache-Control: no-store`, `requestId` e envelope `FinancialResult`.
Falhas de autorização retornam 404 para não revelar a existência de outra conta.

- `GET /api/receivables/accounts`: lista contas próprias e delegadas atualmente acessíveis, sem
  consultar rollout, plano, grupo ativo ou aprovação cadastral. Delegação revogada ou administrador
  removido desaparece na mesma requisição.
- `GET /api/receivables/accounts/{accountId}/management`: titular ou delegado atual obtém campos
  corrigíveis mascarados/adequados à edição e sua capacidade: `OWNER` ou `DELEGATE`. Identidade legal
  não é retornada por esta rota.
- `POST /api/receivables/accounts/{accountId}/corrections`: corpo `{requestId,email,phone,
  mobilePhone,site,incomeCents,postalCode,address,addressNumber,complement,province}`. Titular ou
  delegado atual pode corrigir; a autorização é refeita imediatamente antes de qualquer IO remoto.
- `POST /api/receivables/accounts/{accountId}/corrections/{requestId}/recover`: só consulta e resolve
  a operação já persistida. Nunca cria outra correção; revogação atual impede recuperação pelo antigo
  delegado.
- `GET /api/receivables/accounts/{accountId}/delegations`: titular e delegado atual podem consultar;
  o delegado não concede nem revoga.
- `POST /api/receivables/accounts/{accountId}/delegations`: somente titular; exige administrador
  atual, aceite explícito de acesso à conta inteira, versão vigente e `requestId` estável.
- `DELETE /api/receivables/accounts/{accountId}/delegations/{userId}?requestId=...`: somente titular.
  A remoção/rebaixamento de administrador também revoga a delegação na transação de grupos e a
  autorização fresca bloqueia imediatamente a sessão já aberta.

## Estados, repetição e recuperação

- Primeiro uso de `requestId`: o backend persiste ator, conta, digest, payload cifrado e estado antes
  de IO. Mesmo `requestId` + mesmo ator/conta/conteúdo retoma; conteúdo diferente retorna 409.
- Timeout/conexão perdida após POST produz `RESULT_PENDING`/HTTP 202. O cliente mantém somente
  `{actor,accountId,requestId,kind,targetId?,termsVersion?}` e oferece recuperação explícita; não repete POST automaticamente.
- A recuperação faz GET remoto e compara todos os campos corrigíveis com o comando cifrado. Igual
  conclui `SUCCEEDED`; diferente permanece `RESULT_PENDING`. Rejeição HTTP 4xx definitiva retorna
  `INVALID_INPUT`; indisponibilidade/5xx continua recuperável.
- Resposta tardia de geração/sessão antiga é descartada no mobile. Logout limpa tela, efeitos e
  marcadores de outro ator. Dinheiro é mostrado literalmente em BRL a partir de centavos, nunca por
  `Double`.

## Critérios de aceite

- **AC-M01:** titular lista a própria conta e delegações atuais; delegado atual lista somente contas
  autorizadas; remoção/revogação elimina acesso na requisição seguinte, mesmo na mesma sessão.
- **AC-M02:** só o titular concede/revoga e só para administrador atual; delegado não redelega e novo
  titular de grupo não recebe propriedade financeira.
- **AC-M03:** titular/delegado atual corrige exatamente os dez campos permitidos, sem gate comercial;
  o request ao Asaas preserva identidade legal recuperada e nunca aceita CPF/CNPJ/nome/titularidade.
- **AC-M04:** comando conflitante é 409; timeout é 202 e recuperação não envia segundo POST; resultado
  definitivo carrega o mesmo `requestId`.
- **AC-M05:** mobile Android/iOS distingue ator, conta, papel, requestId, valor literal e resultado;
  persiste só identificadores, descarta geração/sessão antiga e oferece retry/recovery explícitos.
- **AC-M06:** nenhuma rota, domínio, UI ou texto oferece pedido, aprovação ou execução de reembolso.

## Limites reais

Mocks e testes locais não comprovam homologação, condições comerciais, análise cadastral, atualização
real ou latência do Asaas. Mudança de município pode reinicializar configuração fiscal e cancelar
NFS-e agendadas segundo a documentação do provedor; a UI deve advertir antes do envio. Wiring de
rotas/DI centrais permanece com o coordenador.

## Integração final

O titular consulta `GET /api/receivables/accounts/{accountId}/delegations/candidates` para escolher
administradores atuais por nome e grupos. A resposta é um envelope com lista de
`{userId,displayName,groupNames}`; delegado/terceiro recebe 404. O servidor recalcula candidatos de grupos
não excluídos e continua validando administração atual no comando de concessão.

A identidade comercial anterior ao POST é cifrada em `identity_snapshot_encrypted` (V62). Confirmação
imediata ou recuperada exige os seis campos legais inalterados e os dez campos solicitados coincidentes.
Ausência/divergência mantém o resultado pendente sem repetir POST. A UI limpa dados após revogação e
não expõe UUIDs nem exige que o titular conheça identificadores internos.
