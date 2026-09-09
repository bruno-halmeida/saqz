# Testes de aceitação: execução manual e base para automação

Catálogo inicial dos fluxos críticos, com passos reproduzíveis e resultados observáveis. Os arquivos `.feature` são roteiros Gherkin em português: **ainda não existe um executor Cucumber ligado a estes arquivos**. A presença de um cenário não significa que ele foi executado ou aprovado.

- [Ligações do app: saída, perfil, mensalidades, mapa e comunicação](app-ligacoes.feature)
- [Regressão crítica: acesso, grupos, jogos e financeiro](app-regressao.feature)
- [Painel administrativo e checkout](adm.feature)
- [Evidências técnicas desta entrega](evidencias.md)

## Preparação segura

Use backend e aplicativos da **mesma versão**, com migrações aplicadas em banco de teste. A comunicação interna exige a migration `V45__add_group_communications.sql`. Não a execute diretamente em produção para testar. Para checkout, use exclusivamente credenciais e pagamentos sandbox. Os scripts de seed existentes podem apontar para Firebase de desenvolvimento; confira o destino antes de executá-los.

Prepare contas descartáveis distintas; não reutilize dados pessoais nem contas de produção:

| Alias | Estado inicial |
| --- | --- |
| DONO | Dono de G1, plano válido com capacidade disponível para criar grupo |
| ADM | Administrador de G1, não é o dono; também participa de G2 |
| ATLETA | Atleta ativo de G1 e G2, mensalista de G1; telefone oculto para outros atletas |
| PAR | Atleta ativo de G1, sem resposta ao próximo jogo |
| FORA | Conta autenticada sem vínculo com G1 |
| PAINEL | Administrador da plataforma autorizado em `/admin/me`; não confundir com ADM |
| G1 | “Vôlei QA”, modalidade quadra, fuso `America/Sao_Paulo`, Pix de teste, endereço `Rua São João, 100 & Praça / Quadra #2` |
| G2 | “Praia QA”, outro grupo de ATLETA, com dono distinto |
| J1 | Jogo publicado de G1, amanhã às 20h no fuso do grupo, confirmação até amanhã às 18h, capacidade 2 |
| J2 | Outro jogo futuro de G1, depois de J1 |
| M1–M4 | Mensalidades de ATLETA em G1: pendente R$80, paga R$80, isenta R$80, cancelada R$80; competências distintas |

Crie também uma cobrança de PAR e uma cobrança por jogo de ATLETA: ambas devem ficar fora de “Minhas mensalidades”. Nos testes de lembrete, o elenco completo de G1 deve ser: DONO (remetente), PAR (ativo sem resposta), ATLETA e ADM (confirmados), um terceiro atleta recusado e um quarto inativo. Não acrescente outros membros ativos sem resposta. Deixe preferências habilitadas, exceto quando o cenário disser o contrário. O remetente não recebe notificação de si mesmo.

Cada cenário deve restaurar seu próprio estado inicial. Para os testes de saída, use uma cópia da conta/vínculo ou reinscreva por convite antes do próximo cenário. Não “limpe” histórico financeiro manualmente. Use IDs diferentes por execução e registre o mapeamento dos aliases para IDs reais em um arquivo privado de execução, sem tokens/senhas.

## Como executar e registrar

1. Registre commit, ambiente, data, executor, dispositivo/SO e tamanho de fonte; execute app em Android e iPhone. Inclua iPad se suportado pela distribuição.
2. Execute primeiro os cenários `@p0`; depois `@p1`. Leia o Contexto e os Dados antes das ações.
3. Marque cada ID como `NÃO EXECUTADO`, `PASSOU`, `FALHOU` ou `BLOQUEADO`. Registre resultado real, não apenas um checkbox.
4. Para falhas, anexe captura/vídeo, passos, esperado/observado, IDs descartáveis e horário. Capture status e payloads de rede **sem Authorization, cookies, senhas ou dados de cartão**.
5. Reabra/recarregue após uma escrita para confirmar persistência; verifique com outra conta quando houver permissão ou destinatário envolvido.

Modelo por execução:

| ID e exemplo | Plataforma | Commit | Resultado | Observado | Evidência/defeito |
| --- | --- | --- | --- | --- | --- |
| APP-L01 / ATLETA | Android | preencher | NÃO EXECUTADO | — | — |

Testes que exigem timeout após commit precisam de proxy/harness controlado: deixe a requisição alcançar o backend e descarte apenas a resposta. Modo avião antes do toque cobre falha de conectividade, **não** perda de resposta após persistência. Na execução automatizada, aguarde estado observável/requisição, nunca um `sleep` fixo para assumir sucesso.

## Reaproveitamento automatizado

Os IDs dos cenários devem ser preservados nos nomes/tags dos testes. Cada linha de Exemplos vira uma execução independente. As fixtures acima viram fábricas de dados de teste e os passos de persistência/permissão viram assertions na API/banco.

| Camada | Responsabilidade |
| --- | --- |
| Domínio/VM | Permissões, estados, efeitos, cancelamento, duplo toque, geração, retry |
| Gateway/HTTP | Método, rota, query, autenticação, payload, mapeamento de erro |
| Integração com Postgres | Persistência, isolamento por usuário/grupo, histórico, concorrência e idempotência |
| UI com gateways controlados | Controles, estados, layout, rotas e recarga no retorno |
| E2E real | App/navegador + API + banco + autenticação do ambiente; duas contas e integrações nativas |

Não substituir um E2E real por respostas mockadas e manter a etiqueta E2E. Os testes Compose atuais usam gateways controlados; as integrações JDBC/HTTP usam Postgres, mas não dirigem o aplicativo.

Seletores novos estáveis: `GroupLeaveTags`, `MemberProfileTags`, `OwnMonthlyPaymentsTags`, `GroupThreadTags`, `NotificationCenterTags`. Priorize papel/nome acessível no painel. IDs de mensagem/grupo entram nas tags dinâmicas; não dependa da posição do item. Para iOS nativo, confirme a exposição dos seletores no driver escolhido antes de implementar o runner.

## Limites conhecidos

- Comunicação atual é texto persistido dentro do app, com Atualizar/Carregar anteriores. Não há WebSocket, push, anexos, edição/exclusão de mensagem ou nova integração WhatsApp.
- Desabilitar uma categoria afeta notificações futuras, não apaga as antigas nem impede consultar o conteúdo do grupo.
- O dono não pode sair do próprio grupo; saída não é exclusão de conta/grupo nem perdão de dívida.
- O painel ainda usa dados demonstrativos em **Suporte e moderação** (`VUL-171`). Não aprovar esse fluxo como integrado; cenário correspondente está bloqueado por implementação.
- Listas do painel carregam a primeira página de até 25 registros; não há navegação de páginas na interface. Registre esse limite ao testar bases maiores.
- Este catálogo é a primeira bateria crítica, não uma alegação de cobertura exaustiva de todas as combinações do produto.
