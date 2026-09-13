# T04 — recuperação operacional da credencial de subconta

Pesquisa em 2026-09-13, somente documentação pública oficial; nenhuma chamada Asaas real foi executada. Este documento trata exclusivamente da resposta/chave perdida na criação de subconta existente.

## Contrato confirmado e dependências

A chave original não pode ser recuperada depois da resposta da criação. O caminho atual é localizar a **mesma subconta** e emitir uma chave substituta. Não excluir nem recriar a subconta: isso perderia a identidade financeira e não atende T04. [Criar subconta](https://docs.asaas.com/reference/criar-subconta).

A consulta autenticada pela conta-pai `GET /v3/accounts?cpfCnpj=...` permite localizar subcontas vinculadas a ela; conferir CPF/CNPJ normalizado, id e walletId, nunca somente nome/email. Zero resultados, múltiplos resultados, paginação incompleta ou divergência não provam que a criação falhou e não autorizam outro POST de conta. [Listar subcontas](https://docs.asaas.com/reference/listar-subcontas).

Existe `POST /v3/accounts/{id}/accessTokens` para a conta-pai emitir chave na mesma subconta. A referência narrativa chama o segredo retornado de `access_token`; o OpenAPI embutido, schema `CustomerApiAccessTokenSaveResponseDTO`, define `apiKey` e `id` da chave. Esta divergência precisa ser exercitada com fixture e confirmada na homologação; nunca interpretar o `id` como segredo. Não há parâmetro de idempotência documentado para essa emissão. [Criar chave](https://docs.asaas.com/reference/criar-chave-de-api-para-uma-subconta).

O acesso depende de habilitação manual em Integrações > Chaves de API > Gerenciamento de Chaves de API de Subcontas > Habilitar acesso, válida por duas horas, whitelist ativa e IP de saída autorizado. Usa a chave da conta-pai e a elegibilidade BaaS/filiais aplicável; não é possível prometer recuperação autônoma permanente. Após validar a chave nova, tratar a antiga conforme o procedimento de rotação e dependências. [Gerenciamento de chaves de subcontas](https://docs.asaas.com/docs/gerenciamento-de-chaves-de-api-de-subcontas).

`GET /v3/accounts/{id}/accessTokens` retorna IDs/configurações, nunca o segredo. Se a resposta da própria substituição se perder, a listagem não permite recuperar a chave; exige conciliação e nova emissão controlada. [Listar chaves](https://docs.asaas.com/reference/listar-chaves-de-api-de-uma-subconta).

## Evidência local anterior

`FinancialOnboarding.kt` recupera CREATE_ACCOUNT somente quando já há credencial local; caso contrário mantém UNKNOWN. `HttpAsaasOnboarding.kt` expõe criação/documentos/status/upload, sem emissão de chave substituta. `JdbcFinancialOnboardingStore.kt` cifra por conta/finalidade, mas `saveProviderAccount` só aceita `provider_account_id IS NULL`, e não implementa recuperação operacional auditada. A resposta perdida permanece pendente com segurança, porém isso sozinho não fecha a lacuna T04.

## Procedimento operacional

1. Registrar incidente com UUID da conta Saqz, UUID da operação CREATE_ACCOUNT, ambiente e operador autorizado. Não copiar CPF/CNPJ, chave, JSON bruto do provedor ou ciphertext para tickets/logs. Confirmar que a criação está UNKNOWN e não existe worker de criação ainda ativo; não mudar a operação para READY.
2. Usar a conta-pai e o ambiente originais. Confirmar titular jurídico da conta Saqz contra consulta autenticada da conta-pai, usando dados já cifrados localmente. Havendo id/wallet local, exigir correspondência exata. Ambiguidade ou ausência exige o gerente/suporte Asaas; não assumir unicidade por nome/email.
3. Habilitar a janela de gestão de chaves na interface Asaas e validar IP/contrato. Não colocar credenciais em argumentos de shell, histórico, screenshots ou UI Saqz.
4. O operador habilitado emite a chave substituta pelo mecanismo oficial Asaas e captura a resposta diretamente em cofre aprovado, sem logs ou exibição na UI Saqz. O backend implementado **não emite chaves**: importa uma chave já obtida, valida por GET e anexa segredo cifrado e resultado de forma atômica. Não ativa meios de pagamento nem marca cadastro aprovado.
5. Se houver timeout ou perda da resposta durante a emissão manual no Asaas: manter o incidente pendente. A listagem de chaves pode identificar metadados da tentativa, mas não recuperar seu segredo. Operador precisa confirmar efeitos/uso de qualquer chave órfã antes de nova emissão; nunca limpar o marcador só para tentar novamente.
6. Com a chave no cofre, executar o comando abaixo. A importação valida a identidade da própria credencial, conta-pai, conta/id/carteira e titular locais. Falha de GET pode ser repetida com a mesma chave/request, pois não há POST; falha de commit deixa a intenção PENDING, e commit com resposta local perdida retorna ALREADY_RECOVERED na repetição exata.
7. Confirmar armazenamento local e autenticação com a nova chave; retomar consulta de documentos/status após inicialização. Só então avaliar desabilitação da chave anterior com o responsável pela integração. Encerrar a janela de gestão de chaves e o incidente mantendo apenas IDs e estados não sensíveis.

## Implementação e validação

Implementado em novos arquivos da feature: `FinancialCredentialRecovery.kt`, `HttpAsaasCredentialRecovery.kt`, `JdbcFinancialCredentialRecovery.kt`, migração `V63__financial_credential_recovery.sql` e `FinancialCredentialRecoveryIntegrationTest.kt`. Nenhuma mudança nos três arquivos anteriores de onboarding foi necessária.

A consulta da chave candidata usa `GET /v3/myAccount/commercialInfo/` para CPF/CNPJ e `GET /v3/wallets/` para carteira; ambos precisam coincidir com o registro único retornado à conta-pai e os identificadores esperados pelo operador. Campos ausentes, respostas incompletas ou dados diferentes interrompem a importação. As três consultas exigem HTTP 200; HTTP 202 nunca é prova conclusiva. A contagem total precisa ser o inteiro exato 1, inclusive para valores que excedem Int/Long. [Dados comerciais](https://docs.asaas.com/reference/recuperar-dados-comerciais), [WalletId da conta autenticada](https://docs.asaas.com/reference/recuperar-walletid).

Elegibilidade local: operação original `CREATE_ACCOUNT` em `UNKNOWN`, ator original igual ao titular, conta sem credencial. Qualquer id/carteira/referência remota já persistida deve coincidir. `READY`, `RUNNING`, `REJECTED`, `SUCCEEDED`, conta estrangeira e credencial existente são recusados. Se o scheduler estiver recuperando UNKNOWN e a operação estiver momentaneamente RUNNING, esperar a próxima conclusão; nunca forçar estados ou revogar leases por SQL.

A intenção preserva request, operador, titular, operação, referências, versão da conta e chave candidata cifrada com finalidade `recovery-key`; repetição com conteúdo/segredo diferente retorna CONFLICT. Após validação, uma transação bloqueia e reconfere conta/operação, cifra a chave como `provider-key`, atualiza cadastro somente de INCOMPLETE para UNDER_REVIEW, marca a criação original SUCCEEDED e grava evento IMPORTED. Eventos REQUESTED/IMPORTED são imutáveis por trigger, sem segredo ou CPF/CNPJ. Falhas e concorrência não substituem credenciais já anexadas. A operação PENDING não transmite nada automaticamente: nova execução repete apenas consultas de validação.

Uma chave incorreta deixa a solicitação PENDING; registrar novo request para a chave corrigida, mantendo a anterior como histórico. Isso não cria conta/chave no provedor e continua sujeito à ausência de credencial local e mesma operação UNKNOWN. A primeira importação concluída vence; tentativas concorrentes posteriores não podem substituí-la.

## Comando operacional integrado pelo coordenador

Usar o artefato do backend que contém o comando, sob identidade de operador de deployment autorizada. O UUID `--operator-id` registra quem executou; não substitui o controle de acesso ao host/cofre. A migração V63 deve estar aplicada antes. O contexto deste comando é NON_WEB, sem scan de componentes, agendadores nem execução de migrações; ele não inicia a aplicação normal.

Manter configuração de deployment original: `spring.datasource.url`, `.username`, `.password`, `saqz.receivables.encryption-key-id`, `.encryption-key`, `.identity-lookup-key`, `.asaas-base-url` e `.asaas-platform-key`. Confirmar ambiente e conta-pai; a chave candidata não é lida de variável de ambiente ou argumento.

Executar em console seguro e informar a chave no prompt sem eco:

```sh
java -jar app.jar recover-receivables-credential \
  --request-id REQUEST_UUID \
  --account-id ACCOUNT_UUID \
  --owner-id OWNER_UUID \
  --creation-operation-id ORIGINAL_OPERATION_UUID \
  --operator-id OPERATOR_UUID \
  --provider-account-id PROVIDER_ACCOUNT_REF \
  --wallet-id WALLET_REF
```

Os nomes maiúsculos são placeholders a substituir somente por identificadores não secretos. Para automação autorizada, conectar stdout do leitor do cofre diretamente ao stdin desse mesmo comando, em uma única linha ASCII (até 8192 caracteres); o leitor é o mecanismo aprovado pelo deployment, não um utilitário inventado por este runbook. Desativar gravação de terminal/shell tracing; não usar `echo CHAVE`, argumento de shell, arquivo temporário de texto, redirecionamento para log, captura de stdout do cofre ou UI administrativa.

| Resultado | Tratamento |
| --- | --- |
| RECOVERED / ALREADY_RECOVERED | Exit 0; anexada ou repetição exata já concluída, sem segunda conta. Retomar documentos/status no fluxo normal. |
| IDENTITY_MISMATCH | Exit 1; conferir ambiente, conta-pai, titular e carteira. Não anexar por SQL nem escolher o primeiro resultado. |
| CONFLICT | Exit 1; request reutilizado com conteúdo diferente, versão alterada ou referência divergente. Reconciliar antes de nova solicitação. |
| NOT_ELIGIBLE | Exit 1; estado original não é UNKNOWN, dono/operação incorretos ou credencial já existente. Não é ferramenta geral de rotação. |
| UNAVAILABLE | Exit 1; falha de consulta/configuração/persistência sanitizada. Corrigir dependência e repetir o mesmo request/chave; consultas não criam recursos. |
| INVALID_ARGUMENTS / SECRET_REQUIRED / INVALID_SECRET | Exit 2; corrigir entrada local. Chave nunca faz parte da saída. |

O coordenador é responsável pelos gates da integração do comando. Neste worker passaram 12 testes de recuperação, 10 regressões de onboarding e 20 de arquitetura em scratch/JDK21, sem chamadas Asaas reais. Evidência completa: [relatório do autor](evidence/credential-recovery/implementation.md).

Dependência operacional restante: habilitação/eligibilidade no Asaas, emissão/captura manual da chave e homologação real autorizada. Não há promessa de consulta da chave antiga, emissão automática ou resolução automática de ambiguidade.
