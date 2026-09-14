# Testes de aceitação: execução manual e base para automação

Catálogo das jornadas principais, com passos reproduzíveis e resultados observáveis. Os arquivos `.feature` são roteiros Gherkin em português: **ainda não existe um executor Cucumber ligado a estes arquivos**. A presença de um cenário não significa que ele foi executado ou aprovado.

- [Autenticação: 12 jornadas com caminho principal e variações](../../fluxo-1-autenticacao.md)
- [Primeiro acesso, Início e perfil pessoal](app-inicio-perfil.feature)
- [Cadastro de grupos e gestão de membros](app-gestao-grupos.feature)
- [Criação, edição e cancelamento de jogos](app-jogos.feature)
- [Meu plano, troca e cancelamento pelo app](app-assinaturas.feature)
- [Caixa, filtros e consulta do extrato](financeiro-consulta.feature)
- [Trial público, modos e cupons de trial](trial-cupons.feature)
- [Conversão e receita dos cupons](adm-conversao-cupons.feature)
- [Ligações do app: saída, perfil, mensalidades, mapa e comunicação](app-ligacoes.feature)
- [Regressão crítica: acesso, grupos, jogos e financeiro](app-regressao.feature)
- [Convites permanentes: criação, recarga, substituição e desativação](app-convites.feature)
- [Geração manual e encerramento do acerto](financeiro-final.feature)
- [Painel administrativo e checkout](adm.feature)
- [Recebimentos Asaas: jornadas integradas de homologação](../../docs/receivables/final-operations-runbook.md#jornada-completa-para-homologação-f1f7)
- [Evidências técnicas desta entrega](evidencias.md)
- [E2E Android instalado: cenários automatizados e execução local](../e2e/android/README.md)

## Bateria principal — recorte de 2026-09-14

Pedido: completar as descrições das features, priorizando jornadas críticas e falhas comuns.
Entram criação/consulta/edição/remoção do dia a dia, permissões de dono/admin/atleta,
dinheiro e assinatura, campos obrigatórios e recuperação de uma falha simples de rede.
Combinações raras de concorrência, respostas fora de ordem, limites extremos, relógio distante,
migrações legadas e falhas encadeadas não são exigidas nesta bateria. Os roteiros anteriores
que tratam desses casos continuam disponíveis como regressão complementar.

Os sete novos arquivos têm a tag `@principal`. `@p0` indica risco de impedir uma jornada,
conceder acesso indevido ou alterar dinheiro; `@p1` indica jornada frequente com contorno.
Ambos pertencem ao recorte solicitado. `@blocked_implementation` significa cenário descrito
cuja jornada ainda depende de implementação; não conta como aprovado nem como ausência de cenário.

| Área | Roteiros da bateria principal |
| --- | --- |
| Autenticação | J1.1–J1.12: caminhos principais; complementar com senha incorreta, falta de rede, código inválido/expirado e cancelar uma confirmação. As demais variações ficam para regressão complementar. |
| Primeiro acesso, Início e perfil | APP-OB01–02, APP-H01–03, APP-P04–07; APP-P01–02 para cadastro esportivo e privacidade do perfil alheio |
| Grupos e membros | APP-G01–07, APP-MB01–06; APP-R05/R07 para plano e edição básica; APP-L01–03 para saída voluntária |
| Convites | APP-I01/I02/I04/I05/I07 e APP-R06; caminho que depende de aprovação pela tela de Membros está bloqueado conforme tabela abaixo |
| Jogos e presença | APP-J01–05, APP-R08/R09 |
| Comunicação e notificações | APP-C01/C02/C04/C05, APP-N01/N03/N04 |
| Financeiro do grupo | APP-EF01–04, APP-MG01, APP-R10/R12, APP-AC01, APP-F01; APP-R11 depende das ações de isenção/cancelamento no app |
| Assinaturas e trial | APP-S01–06, TRIAL-01–06; ADM-X01–03 para contratação no checkout |
| Administrativo | ADM-A01/A02, ADM-O01, ADM-U01/U02, ADM-G01, ADM-S01, ADM-C01 e ADM-PG01 |
| Conversão de cupons | ADM-CA01–03 |
| Recebimentos Asaas | As 12 jornadas da seção F1–F7 do runbook: cadastro, gestão/delegação, Pix, cartão, renovação, carteira/comprovante, saque, recorrência, corte, retomada, operação e clientes |

Recebimentos já tem roteiro integrado: reutilizar as ações e provas do runbook, com ênfase
na jornada bem-sucedida, recusa comum de pagamento, saldo insuficiente e manutenção após corte.
Os testes de concorrência e falhas encadeadas do runbook são complementares a este recorte.
Para dinheiro, registrar os valores conhecidos de base, tarifas e total em centavos antes de
comparar a tela com o provedor; não aceitar apenas “pagamento apareceu”. A homologação real
Asaas continua pendente e não é realizada por esta entrega de documentação.

Critérios desta entrega documental:

- D1: cada lacuna levantada tem cenários principais ou referência a roteiro existente.
- D2: cada cenário define ator/massa, ação e resultado observável; tabelas têm exemplos concretos.
- D3: IDs são únicos, arquivos Gherkin são válidos e referências locais existem.
- D4: prioridades se limitam ao recorte acima, sem exigir uma matriz exaustiva de corner cases.
- D5: bloqueios de implementação e ausência de execução/automação ficam explícitos.

## Bloqueios conhecidos da bateria principal

Estes são resultados esperados para quando a jornada estiver integrada. Marcar `BLOQUEADO`
na execução atual; mudanças somente em memória não provam persistência.

| Cenário | Dependência identificada no código |
| --- | --- |
| APP-G06 — salvar/retomar rascunho | `GroupSetupViewModel.onSaveDraft` emite `DraftSaved` e `GroupSetupDestination` apenas fecha a rota; o `GroupDraftStorePort` não é usado nesse fluxo. |
| APP-G07 — salvar pela Agenda | `GroupScheduleViewModel.save` emite `Saved` sem escrita remota. APP-G04 cobre a alternativa pela edição do grupo. |
| APP-MB06 — aceitar/recusar entrada | `GroupMembersViewModel` inicia `requests` vazia; `accept`/`decline` alteram somente listas locais. A parte de aprovação de APP-R06 e APP-I03 não é homologada pela tela. |
| APP-R11 — isentar/cancelar cobrança pelo app | Ações ainda ausentes da UI conforme o [guia E2E Android](../e2e/android/README.md); testes da API não encerram essa jornada. |
| ADM-P01 — suporte/moderação | Seção demonstrativa já sinalizada no roteiro administrativo. |

## Preparação segura

Use backend e aplicativos da **mesma versão**, com migrações aplicadas em banco de teste. A comunicação interna exige a migration `V45__add_group_communications.sql`. Não a execute diretamente em produção para testar. Para checkout, use exclusivamente credenciais e pagamentos sandbox. Os scripts de seed existentes podem apontar para Firebase de desenvolvimento; confira o destino antes de executá-los.

Convites permanentes exigem `V46__allow_permanent_group_invites.sql` e app atualizado. Novos links não expiram; links antigos mantêm o prazo original até serem substituídos. A migração não reativa links expirados. A resposta da criação tem `expiresAt: null` e `revision`; a consulta de metadados omite `expiresAt` quando não há prazo. Aplicativos antigos podem rejeitar essa resposta: coordene a atualização do app e backend antes de usar novos convites.

O roteiro `APP-I01`–`APP-I07` tem **9 execuções**, considerando os exemplos. `APP-I03` usa relógio controlado do backend, não a data do celular. A cobertura automática desta mudança é por unidade, gateway, banco, HTTP, tela e adapters nativos; não foi acrescentado um E2E de app instalado para convites. Registre o teste físico do deeplink separadamente, inclusive no iOS.

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

Massa adicional dos novos roteiros:

| Alias / dado | Preparação |
| --- | --- |
| NOVO | Conta diferente por cenário, sem grupo anterior, trial ou pagamento; liberação de criação definida no próprio roteiro. Para APP-OB01/02, emitir link válido pelo fluxo de primeiro acesso do ambiente. |
| ASSINANTE | Assinatura sandbox independente por cenário; guardar plano, ciclo, limites, fim do período e valores de recibos. Para upgrade, registrar preços de origem/destino e instante de referência, calcular previamente o pró-rata positivo do período restante e conferir esse valor na cobrança. Para downgrade, preparar uso compatível ou acima do limite conforme o caso. |
| A e B | Contas exclusivas do banco de analytics. Em ADM-CA01, ambas usam QA30, somente A usa QA10, ambos os trials estão encerrados, e os dois pagamentos de A são posteriores aos usos. |
| QA30 / QA10 / QASEMUSO | Códigos de teste em base isolada; QA30 com 30 dias, QA10 de desconto e QASEMUSO sem uso. Restaurar a massa entre cenários; não presumir que executar TRIAL-02 prepara ADM-CA01. |
| Fotos e telefone | Imagens sintéticas distintas na galeria e celular fictício válido no formato aceito pelo app, sem depender de uma pessoa real. |
| Caixa e extrato | Substituir a massa padrão pela lista exata do cenário; não somar os movimentos de APP-EF01 aos de APP-EF02. |

Resultados esperados não são calculados a partir da própria resposta sob teste. Preparar e
registrar os fatos conhecidos antes da ação: valores, datas, contas, grupos e papéis. Operações
que exigem harness (geração automática, criação de fatos de analytics, provedor indisponível)
devem indicar como a massa foi preparada; ausência desse recurso é `BLOQUEADO`, nunca `PASSOU`.
Os novos casos de rede desligam a conexão **antes** do envio e não simulam perda de resposta
após persistência. Restaurar oferta de trial, rede, papéis e vínculos ao terminar cada cenário.

Crie também uma cobrança de PAR e uma cobrança por jogo de ATLETA: ambas devem ficar fora de “Minhas mensalidades”. Nos testes de lembrete, o elenco completo de G1 deve ser: DONO (remetente), PAR (ativo sem resposta), ATLETA e ADM (confirmados), um terceiro atleta recusado e um quarto inativo. Não acrescente outros membros ativos sem resposta. Deixe preferências habilitadas, exceto quando o cenário disser o contrário. O remetente não recebe notificação de si mesmo.

Cada cenário deve restaurar seu próprio estado inicial. Para os testes de saída, use uma cópia da conta/vínculo ou reinscreva por convite antes do próximo cenário. Não “limpe” histórico financeiro manualmente. Use IDs diferentes por execução e registre o mapeamento dos aliases para IDs reais em um arquivo privado de execução, sem tokens/senhas.

## Como executar e registrar

1. Registre commit, ambiente, data, executor, dispositivo/SO e tamanho de fonte; execute app em Android e iPhone. Inclua iPad se suportado pela distribuição.
2. Use a tabela da bateria principal para selecionar os roteiros; execute primeiro seus `@p0`, depois `@p1`. Os demais cenários são regressão complementar. Leia o Contexto e os Dados antes das ações.
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

Não substituir um E2E real por respostas mockadas e manter a etiqueta E2E. Os testes Compose de componentes usam gateways controlados; as integrações JDBC/HTTP usam Postgres, mas não dirigem o aplicativo. A suíte opt-in em `mobile/android-app/src/e2e` dirige o app instalado com Firebase Auth Emulator, API e Postgres locais reais; seus recortes e limites estão no guia acima.

Seletores novos estáveis: `GroupLeaveTags`, `MemberProfileTags`, `OwnMonthlyPaymentsTags`, `GroupThreadTags`, `NotificationCenterTags`, `MonthlyGenerationTags` e `GroupCashboxTags.GenerateMonthly`. Priorize papel/nome acessível no painel, com os controles sob `Paginação de usuários/grupos/assinaturas`. IDs de mensagem/grupo entram nas tags dinâmicas; não dependa da posição do item. Para iOS nativo, confirme a exposição dos seletores no driver escolhido antes de implementar o runner.

## Limites conhecidos

- Comunicação atual é texto persistido dentro do app, com Atualizar/Carregar anteriores. Não há WebSocket, push, anexos, edição/exclusão de mensagem ou nova integração WhatsApp.
- Desabilitar uma categoria afeta notificações futuras, não apaga as antigas nem impede consultar o conteúdo do grupo.
- O dono não pode sair do próprio grupo; saída não é exclusão de conta/grupo nem perdão de dívida.
- A prévia de convite ainda ignora o prazo legado e a configuração de aprovação no adapter JDBC; a entrada efetiva valida ambos. Pendência anterior a esta mudança, não homologada pelos novos cenários de convite permanente.
- O painel ainda usa dados demonstrativos em **Suporte e moderação** (`VUL-171`). Não aprovar esse fluxo como integrado; cenário correspondente está bloqueado por implementação.
- As três listas paginadas do painel exigem massa de pelo menos 51 registros e filtros com mais de 25 resultados para testar todas as transições; não use dados reais de clientes como fixture.
- Este catálogo é a primeira bateria crítica, não uma alegação de cobertura exaustiva de todas as combinações do produto.
