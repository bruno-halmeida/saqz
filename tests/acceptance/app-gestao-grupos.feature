# language: pt
@app @manual @automation_candidate @principal
Funcionalidade: Cadastro de grupos e gestão de membros
  Contexto:
    Dado o ambiente isolado e as contas de tests/acceptance/README.md
    E cada cenário começa com sua própria massa e sessão

  @p0 @APP-G01
  Cenário: Revisar e criar grupo salva os dados escolhidos uma única vez
    Dado que DONO tem capacidade no plano para mais um grupo
    Quando preenche um grupo "Grupo novo QA", modalidade quadra, público misto e categoria disponível
    E informa "Quadra QA" e seu endereço completo, capacidade 12 e antecedência de confirmação de 6 horas
    E configura terça-feira às 20h com duração de 120 minutos e avança para a revisão
    Então a revisão mostra esses dados e o grupo ainda não existe na lista
    Quando volta ao formulário, muda o nome para "Grupo revisado QA", revisa e confirma
    Então abre o detalhe de um único "Grupo revisado QA" com DONO como dono
    E os dados e o horário escolhidos permanecem ao reabrir a edição

  @p0 @APP-G02
  Cenário: Campos obrigatórios e falha de rede permitem corrigir a criação
    Dado que DONO iniciou Criar grupo
    Quando tenta avançar sem nome, modalidade e sem horário com recorrência ligada
    Então vejo indicação dos campos pendentes e não avanço para a revisão
    Quando preenche dados válidos, revisa e desliga a rede antes de confirmar
    Então vejo falha e os dados preenchidos continuam disponíveis para nova tentativa
    Quando restauro a rede, reviso os mesmos dados e confirmo
    Então um único grupo é criado com esses dados

  @p1 @APP-G03 @native
  Cenário: Trocar a foto do grupo persiste para os membros
    Dado que DONO abriu a edição de G1 com uma foto já salva
    Quando escolhe outra imagem de teste pela galeria, conclui o recorte e salva
    Então a nova foto aparece no detalhe e permanece após reabrir
    Quando ATLETA reabre G1 em outra sessão
    Então vê a mesma nova foto e o nome do grupo permanece inalterado

  @p0 @APP-G04
  Cenário: Atualizar os horários pela edição do grupo preserva a configuração
    Dado que G1 tem um horário semanal de terça-feira às 20h
    Quando DONO abre Editar grupo, substitui o horário por quinta-feira às 19h e salva
    Então ao reabrir Editar grupo vejo quinta-feira às 19h e não vejo o horário removido
    E a Agenda mostra a mesma configuração após reabrir
    E os horários de G2 permanecem inalterados
    # Este cenário verifica configuração; geração automática de jogos exige outro executor.

  @p0 @APP-G05
  Cenário: Excluir grupo exige confirmação do dono e revoga o acesso dos membros
    Dado que G1 é uma cópia descartável com DONO, ADM e ATLETA
    Quando DONO abre Excluir grupo e cancela a confirmação
    Então G1 continua acessível aos três
    Quando DONO confirma a exclusão e os três recarregam suas listas
    Então G1 deixa de aparecer e não pode ser reaberto pelo histórico de navegação
    E G2 continua acessível a quem já participava dele
    E ADM e ATLETA não dispõem da ação de excluir um grupo que não possuem

  @p1 @APP-G06 @blocked_implementation
  Cenário: Salvar rascunho permite retomar a criação depois de fechar o formulário
    Dado que DONO preencheu nome, modalidade e horário de um novo grupo
    E uma tentativa de criação falhou com a rede desligada antes do envio
    Quando toca em Salvar rascunho, fecha o app e reabre Criar grupo na mesma conta
    Então deve poder retomar nome, modalidade e horário sem ter criado um grupo
    E a conta PAR não deve receber esse rascunho ao entrar no mesmo aparelho
    # Bloqueado: onSaveDraft apenas emite DraftSaved; a navegação executa pop.
    # GroupDraftStorePort existe, mas não está ligado à gravação/retomada deste formulário.

  @p1 @APP-G07 @blocked_implementation
  Cenário: Salvar alterações pela própria Agenda mantém os horários ao reabrir
    Dado que DONO abriu a Agenda de G1 com terça-feira às 20h
    Quando muda o horário para quinta-feira às 19h e toca em Salvar
    Então deve ver quinta-feira às 19h ao fechar e reabrir a Agenda
    E deve ver o mesmo horário ao abrir Editar grupo
    # Bloqueado: GroupScheduleViewModel.save apenas emite Saved sem persistir.
    # A edição pelo formulário do grupo é verificada separadamente em APP-G04.

  @p1 @APP-MB01
  Cenário: Buscar membros e filtrar administradores mantém a pessoa selecionada
    Dado que DONO abriu Membros de G1 com DONO e ADM administradores e ATLETA e PAR atletas
    Quando filtra administradores
    Então a lista mostra DONO e ADM e não mostra ATLETA nem PAR como administradores
    Quando volta a Todos e busca o nome exclusivo de ATLETA
    Então encontra apenas ATLETA e abre o perfil dessa pessoa
    Quando busca um nome inexistente e depois limpa a busca
    Então primeiro vê nenhum resultado e depois recupera a lista de G1

  @p0 @APP-MB02
  Cenário: Promover e rebaixar administrador altera as permissões do membro
    Dado que PAR é atleta de G1 sem permissão administrativa
    Quando DONO promove PAR a administrador pela lista de membros
    Então PAR aparece como administrador após reabrir a lista
    E ao reabrir G1 PAR consegue acessar a gestão de atletas
    Quando DONO remove o papel de administrador de PAR
    Então PAR volta a atleta e não consegue mais gerir atletas após atualizar
    E PAR continua participando do grupo
    E ADM não pode promover outro atleta nem rebaixar DONO

  @p0 @APP-MB03
  Cenário: Editar atleta e sua mensalidade preserva cobranças já emitidas
    Dado que ATLETA é mensalista de G1 e M1 já foi emitida por R$80,00
    Quando DONO abre Editar jogador de ATLETA, muda o apelido para "Central QA" e a posição para Central e salva
    E reabre o editor, configura mensalidade de R$90,00 com vencimento no dia 10 e salva a configuração de cobrança
    Então ao reabrir o editor vejo apelido, posição, R$90,00 e dia 10
    E M1 mantém seu valor original de R$80,00 e seu vencimento original
    Quando o harness executa a geração automática para ATLETA em uma competência ainda sem cobrança, conforme APP-R10
    Então a nova cobrança é de R$90,00 com vencimento no dia 10 dessa competência
    E o cadastro e as cobranças de PAR permanecem inalterados

  @p0 @APP-MB04
  Cenário: Mudar mensalista para avulso afeta apenas as próximas gerações
    Dado que ATLETA é mensalista de G1 e M1 está pendente
    Quando DONO muda ATLETA para avulso no editor e salva
    Então ao reabrir o editor ATLETA está como avulso
    E ATLETA não aparece como destinatário elegível de uma nova geração manual de mensalidades
    E M1 continua pendente com o mesmo valor e vencimento

  @p0 @APP-MB05
  Cenário: Remover atleta revoga somente sua participação no grupo escolhido
    Dado que ATLETA participa de G1 e G2
    Quando DONO abre Editar jogador de ATLETA em G1, solicita remover e cancela
    Então ATLETA continua no elenco
    Quando solicita remover novamente e confirma
    Então ATLETA deixa de aparecer no elenco ativo de G1 após recarga
    E ATLETA perde acesso a G1 após atualizar sua sessão
    E continua participando de G2
    E o dono não pode ser removido por esse fluxo

  @p0 @APP-MB06 @blocked_implementation
  Esquema do Cenário: Decidir um pedido de entrada persiste a decisão
    Dado que G1 exige aprovação e FORA enviou um pedido de entrada pelo convite
    Quando DONO abre Pendentes na lista de membros e <acao> o pedido de FORA
    Então após reabrir a lista o pedido não deve continuar aguardando decisão
    E ao reabrir o convite FORA <resultado>
    Exemplos:
      | acao   | resultado                                  |
      | aceita | deve conseguir entrar em G1                 |
      | recusa | deve continuar sem vínculo nem acesso a G1  |
    # Bloqueado: a lista inicializa requests vazia; accept/decline só alteram memória.
    # O aceite pela API não comprova esta jornada pela tela de Membros.
