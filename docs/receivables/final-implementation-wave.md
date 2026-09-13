# Conclusão paralela de recebimentos

Base384b5894. Usuário autorizou concluir a implementação com o máximo de agentes paralelos.
RunOrca run_4d44f5ab61e0, sessão coordenadora nova; nenhuma sessão histórica é retomada.
Quatro posições: coordenador +3 implementadores. Compartilhar checkout com ownership de arquivos;
workers não fazem git, publicações, chamadas financeiras reais ou mudanças em configurações locais.
Coordenador integra commits atômicos após gates. Verificação fresca posterior, autor != verificador,
>=5 falhas comportamentais financeiras em cópia temporária, sem enfraquecer testes.

## Escopo e critérios de conclusão

F1 Carteira: saldo remoto disponível separado de recebíveis e extrato paginado; destino bancário e
saque explícito com autenticação recente do servidor. Centavos exatos, requestId estável e operação
persistida antes de IO; sem repetir saque incerto. Autorização por conta/titular/delegação atual,
sem vínculo obrigatório com grupo/plano para dinheiro existente. Concorrência/reserva local e
saldo/restrição do provedor. Banco/PII sem logs, sem dados em URL/rascunho. Nada de saque pelo adm.
F2 Gestão: correção cadastral permitida pelo contrato remoto e delegações mobile (titular concede/
revoga; administrador removido perde acesso). Nenhuma transferência de identidade financeira.
F3 Recorrência mensal: autorização explícita de pagador, termos e valor/meio revisados; uma competência
por grupo/membro, cancelamento e corte efetivo ao perder elegibilidade ou desativar grupo. Preservar
vencidas/avulsas emitidas, cancelar futuros antecipados no provedor, incerteza recuperável; retomada
exige novo aceite. Sem PAN/CVV no Saqz. Não inventar recursos de Pix automático suportados pelo Asaas.
F4 Pagamento: renovar Pix expirado da obrigação existente com snapshot original, inclusive após
corte, sem dívida duplicada; confirmar método remoto documentado antes de implementá-lo. Comprovante
exportável com estado real; retorno de navegador nunca confirma pagamento.
F5 Operação: fila administrativa de falhas e recuperação auditada, com autorização explícita,
sem poder de saque/reembolso e sem expor credenciais/PII. Conciliação de reembolsos externos, custos
residuais observados; não inventar valores de taxas. Páginas públicas de termos publicados e retorno
checkout sem confirmação nem dados financeiros em URL. Avisos persistentes de planos/pendências,
sem enviar comunicações reais nesta implementação.
F6 Integração: gateways tipados, DI/rotas/plataformas reais, DS/strings/tags/previews, sessões/gerações,
recuperação e rascunhos não sensíveis. UI Android/iOS e capturas inspecionadas; compilação e lint.
Backend contratos HTTP/JDBC/segurança/arquitetura; adm Node/navegador simulado. Testes existentes
preservados. Somente código conectado e exercitado conta como implementado; nada de placeholders.
F7 Homologação: preparar cenários integrados, runbook de liberação/reversão e observabilidade.
Homologação REAL Asaas, condições comerciais reais, termos aprovados e piloto não serão declarados
concluídos com mocks. Esses itens externos são separados dos critérios de implementação.

Decisão vinculante: solicitação/aprovação/execução de reembolso está FORA do produto, inclusive
planos do Saqz. Titular/operação tratam externamente; só conciliar fatos e histórico/caixa.

## Frentes e arquivos

A Carteira/backend: novos Wallet*/Bank*/Withdrawal* em receivables application/adapters e respectivos
testes; novo bootstrap ReceivablesWalletConfiguration. Migrações V56/V59 reservadas. Publicar contrato
HTTP no início em docs/receivables/final-wallet-contract.md e avisar coordenador; depois implementarF1.
Não editar OneOffPayments, HttpAsaasPayments ou arquivos reservados de B/C. F2 cadastro/delegação
fica em frente posterior após término do núcleo de carteira.
B Recorrência/Pix/backend: novos Recurrence* e respectivas integrações; OneOffPayments,
PaymentProvider,HttpAsaasPayments,JdbcPaymentExecution/JdbcPaymentStore pertencem a B. Novo bootstrap
ReceivablesRecurrenceConfiguration. Migrações V57/V60 reservadas. Contrato inicial em
final-recurrence-contract.md, incluindo rota de renovação; fonte oficial do provedor obrigatória.
C Operação/público: novos Operational*/Public* backend/adm, adm-web e landing-page; migraçõesV58/V61.
Novo bootstrap ReceivablesOperationsConfiguration. Contrato inicial final-operations-contract.md.
Se necessário editar IdentitySecurityConfiguration, enviar patch proposto ao coordenador. Não editar
código de pagamentos possuído por B; integração de custo residual por novo serviço/porta ou solicitar
alteração pontual coordenada.
Coordenador: mobile, contratos comuns, arquivos bootstrap existentes, integração e documentaçãoglobal.
Frentes mobile adicionais são despachadas quando liberarem vagas; wiring central permanece coordenado.

## Execução e gates

1. Cada implementador fixa primeiro contratos/ACs/arquivos exatos de sua frente e publica aviso.
2. Implementa tarefas atômicas e testes orientados a F1–F7, registra evidência file:line + asserção.
3. Gates isolados em scratch de base+arquivos próprios, ou reserva de build concedida pelo coordenador.
Não executar Gradle concorrente no mesmo workspace/build de outro agente. Não copiar secrets para
scratch; local.properties SDK é permitido. Nunca stash/reset/worktree/git add/commit em workers.
4. Coordenador revisa diff e gates, registra commit atômico por entrega e conecta clientes.
5. Reuso de vagas para gestão/mobile e verificadores frescos. Relatórios por faixa de commits.
6. Gate integrado e revisão independente final. Tarefas de tasks.md só fecham com escopo completo;
F7 externo permanece descrito com evidência do que de fato foi executado.
