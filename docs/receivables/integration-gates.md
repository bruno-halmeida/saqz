# Gates coordenados — toggles e segmentação

Contrato: rollout-contract.md. Verificação requerida antes de integrar em main.

| Cenário | Backend | Adm | Mobile |
|---|---|---|---|
| Instalação padrão | BACKEND/MOBILE OFF | mostra configuração efetiva, sem alteração automática | novas jornadas indisponíveis |
| Piloto selecionado | só ALLOW em SELECTED_USERS | busca/seleção usuário, salva regra e motivo | consulta autenticação própria |
| Bloqueio absoluto | OFF supera ALLOW e MOBILE depende BACKEND | exibe efeito e distinção da flag operacional da conta | mobileEnabled false bloqueia descoberta nova |
| Todos com exceção | ALL_USERS permite exceto DENY | INHERIT restaura política geral | atualização ao retornar app |
| Revogação na sessão | consulta fresca no cadastro/provisionamento/ativação | PUT validado e histórico atualizado | descarta estado e resposta antiga de sessão |
| Dinheiro existente | leitura/documentos/cancelamento independentes | não há poder admin de sacar | manutenção não usa toggle nem readOnly comercial |
| Concorrência | versão esperada, conflito 409, audit imutável | não sobrescreve conflito, mantém retry incerto com mesmo requestId | não aplica resposta atrasada |
| Sigilo/autorização | 401/403, usuário não consulta regra de terceiros | limpa memória no logout, usa sessão admin | nenhum segredo ou override vindo cliente |

Não confundir disponibilidade da feature com autorização financeira: aprovação cadastral,
flag operacional da conta, titular/delegação e elegibilidade continuam verificadas nas operações.
maintenanceAvailable significa somente que a toggle não bloqueia manutenção; autenticação e
permissões próprias de cada ação continuam obrigatórias.

Evidências devem registrar comandos reais, contagens, falhas/limitações e paths de testes.
Testes com respostas simuladas não equivalem a integração Asaas ou liberação de produção.
