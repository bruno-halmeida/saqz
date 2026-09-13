# Recebimentos — contrato de liberação v1

Fonte: usuário autorizou controle no adm por sistema (backend/mobile) e usuário, execução paralela supervisionada pelo Orca. Base e55fa717. Este documento pertence ao coordenador; propor alterações por mensagem antes de alterar contratos.

## Semântica

- Sistemas BACKEND e MOBILE, cada um com modo OFF, SELECTED_USERS ou ALL_USERS. Padrão OFF.
- Exceção por usuário/sistema INHERIT, ALLOW ou DENY. OFF é bloqueio absoluto; SELECTED_USERS só permite ALLOW; ALL_USERS permite exceto DENY. INHERIT remove a exceção.
- mobileEnabled = backendEnabled && regra MOBILE. Backend nunca confia na configuração enviada pelo cliente.
- Avaliar novamente nas operações, sem guardar decisão na sessão. Liberação financeira de grupo considera o titular financeiro; consulta mobile é do usuário autenticado. Delegação não ultrapassa bloqueios da conta/titular.
- Toggles controlam NOVAS operações e descoberta; leitura, pagamento de ordem já emitida, documentos, cancelamentos, saldo, saque, reembolso e conciliação não recebem bloqueio global por toggle. Não prometer endpoints de dinheiro ainda não implementados.
- Cadastro aprovado, elegibilidade comercial e ativação do grupo continuam obrigatórios.
- Flag operacional existente da conta permanece separada. Admin pode liberar/restringir a conta existente pelo endpoint abaixo; nenhuma toggle cria subconta automaticamente.
- Alterações são auditadas com ator autenticado, motivo, antes/depois, data e requestId. Reenvio idempotente, conflito de conteúdo/ator retorna 409. Sem credenciais/dados financeiros no payload.
- Mudança em painel não deve sobrescrever silenciosamente edição concorrente: expectedVersion obrigatório nas escritas. Replay conhecido é verificado antes da versão.

## API JSON autenticada

Respostas sem envelope, exceto erro: {error: "INVALID_INPUT|CONFLICT|NOT_FOUND|UNAUTHORIZED", requestId: UUID}. Cache-Control: no-store em leitura autenticada.

GET /api/receivables/availability
200 {backendEnabled: boolean, mobileEnabled: boolean, maintenanceAvailable: true}
Sem userId do cliente; identidade vem da sessão. Não atesta aprovação/plano nem autoriza uma operação financeira.

GET /admin/receivables/rollout
200 {version: long, systems: [{system:"BACKEND",mode:"OFF"},{system:"MOBILE",mode:"OFF"}]}
PUT /admin/receivables/rollout
{requestId, expectedVersion, reason, systems: [{system,mode}, {system,mode}]}
200 mesmo shape do GET (resultado da escrita original em replay).

GET /admin/receivables/rollout/users/{userId}
200 {userId, version: long, overrides:[{system:"BACKEND",decision:"INHERIT"},{system:"MOBILE",decision:"INHERIT"}], backendEnabled, mobileEnabled, accountId: UUID|null, accountOperationsEnabled: boolean|null}
404 usuário inexistente. Não enumerar usuários para usuários comuns. Adm pode selecionar pela listagem existente /admin/users; não é necessária nova API de busca.
PUT /admin/receivables/rollout/users/{userId}
{requestId, expectedVersion, reason, overrides:[{system,decision},{system,decision}], accountOperationsEnabled:boolean|null}
Null mantém flag de conta existente; boolean exige conta existente (caso contrário 400). 200 mesmo shape do GET.

GET /admin/receivables/rollout/history?page=1&size=25
200 {page,size,total,items:[{requestId,actorUserId,userId:UUID|null,reason,createdAt,before:object,after:object}]}
Pagina 1-based, size 1..100. Histórico somente leitura, acesso administrativo atualizado.

Todos endpoints admin exigem plataforma admin (401 sem autenticação, 403 sem poder). Body inválido 400; versão/content conflict 409. Reasons 3..500 caracteres após trim.

## Divisão de propriedade desta onda

- Backend: somente backend/**. Persistência, HTTP, enforcement onboarding/ativação, testes. Não implementa pagamentos nesta primeira tarefa; recebe follow-up após contrato verificado.
- Adm: somente adm-web/**. Tela Recebimentos, configuração geral, segmentação, liberação operacional da conta, auditoria, testes Node e navegador. Mesmo framework DC.
- Mobile: somente mobile/**. Feature receivables domain/data/presentation, gateway, estado que falha fechado para novas operações, DI, atualização na sessão/retorno do app conforme arquitetura. Preparar integração real sem entradas que abram telas de pagamentos inexistentes; não esconder manutenção. <2000 linhas neste commit. Testes e evidência visual se alterar estado visual.
- Coordenador: docs/** e .specs/**; integração, testes cruzados, próximo lote pagamentos. Sem worker alterar branch, staging, commits ou main; entregar diff testado e lista de arquivos. Coordenador fará commits por tarefa após gate.
