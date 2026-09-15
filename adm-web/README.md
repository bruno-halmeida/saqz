# adm-web — Painel administrativo do Saqz

Painel estático conectado à API administrativa. Autenticação de administrador da plataforma é verificada em `/admin/me`; ser administrador de um grupo não concede acesso ao painel.

## Rodar localmente

```sh
cd adm-web
python3 -m http.server 8123 --bind 127.0.0.1
```

Abra http://127.0.0.1:8123. Configure `assets/firebase-config.js` para o ambiente de teste, incluindo a base da API, e libere essa origem no backend. Não use contas ou pagamentos de produção para homologação.

HTML + React UMD + `dc-runtime.js` + `saqz-design-system.js`; não há etapa de build.

## Fluxos conectados

- Visão geral: métricas e período consultados na API.
- Usuários: busca/filtros, detalhe, suspensão e reativação.
- Grupos: busca/filtros e detalhe.
- Assinaturas: lista, detalhe e cancelamento.
- Cupons: consulta, criação e desativação.
- Usuários, grupos e assinaturas: páginas de 25, Anterior/Próxima, atualização e retentativa; controles bloqueados durante carga. Filtros são preservados na navegação, e o índice volta a 1 quando mudam. Se o total encolher, consulta a última página válida.
- Expiração/logout limpa dados administrativos e impede que respostas da sessão antiga repovoem a tela.

**Suporte e moderação continuam demonstrativos e sem persistência.** A definição de origem das denúncias, privacidade e efeitos da resolução está pendente no VUL-171. Não tratar as ações dessa seção como atendimento real.

Não inferir recursos de cobrança, reembolso ou percentuais a partir do desenho original: somente ações ligadas aos endpoints atuais são funcionais.

## Testes

```sh
node --test adm-web/tests/pagination.test.cjs
```

A suíte executa a lógica de produção extraída do HTML com API controlada. Cobre três listas, filtros, limites, redução do total, erros, respostas fora de ordem, logout e retorno de detalhe. Não é E2E contra backend.

Roteiros manuais e insumos de automação: [tests/acceptance](../tests/acceptance/README.md). Resultados executados e limites: [evidencias.md](../tests/acceptance/evidencias.md). Não existe runner dos arquivos Gherkin instalado.

## Publicação

O target `adm-web` de Firebase Hosting está configurado no `firebase.json` da raiz. A publicação exige configuração Firebase/API do ambiente, origem autorizada no backend e autorização operacional. Nenhum deploy é feito pelos testes acima.

O hosting serve arquivos públicos; dados e operações administrativas dependem da autorização do backend.

## Recebimentos

Em **Recebimentos → Disponibilidade de Recebimentos**, a toggle **Ativo/Inativo** controla a liberação da funcionalidade. Escolha o público, informe o motivo e clique em **Salvar alterações**. O estado confirmado aparece abaixo da toggle. Ativar grava o mesmo público nos controles de backend e mobile; desativar grava `OFF` nos dois, bloqueando novos cadastros e operações mesmo para usuários com exceção `ALLOW`.

A seção usa o contrato `docs/receivables/rollout-contract.md`, com exceções por usuário, estado efetivo, controle operacional da conta existente e histórico de 25 itens por página. Para selecionar uma pessoa, abra **Usuários → detalhe → Controles de recebimentos**. A liberação não substitui aprovação, plano ou ativação do grupo e não cria conta automaticamente. Contas existentes continuam acessíveis para manutenção e ordens já emitidas podem ser pagas.

**Condições comerciais:** somente a comissão do Saqz é configurável. As tarifas de pagamento são consultadas no Asaas pelo backend. A simulação administrativa usa a conta Saqz; a emissão usa a conta recebedora. Sem uma resposta válida do Asaas, a simulação fica indisponível. Veja [origem das tarifas](provider-fees.md).

Cada escrita exige motivo e versão lida; conflito bloqueia nova escrita até **Recarregar** e revisar. Timeout, falha de rede ou HTTP 5xx preservam o corpo e o requestId em memória: **Reenviar mesma tentativa** repete exatamente a operação, com os campos bloqueados até confirmação. Logout elimina também esse estado; não há armazenamento local de payloads administrativos.

Gate: `node --test adm-web/tests/*.test.cjs`. A suíte de recebimentos executa a lógica embarcada de produção. A homologação visual desta entrega usa API e sessão explicitamente simuladas, sem credenciais ou backend real; veja `tests/receivables-evidence.md`.
