# language: pt
@app @manual @automation_candidate @principal
Funcionalidade: Primeiro acesso, Início e perfil pessoal
  Contexto:
    Dado o ambiente isolado e as contas de tests/acceptance/README.md
    E cada cenário começa com sua própria massa e sessão

  @p0 @APP-OB01
  Esquema do Cenário: Concluir a apresentação encaminha para criar o primeiro grupo
    Dado que NOVO é um organizador autenticado sem grupos e com criação permitida
    E abriu a apresentação por um link de primeiro acesso válido emitido pelo ambiente de teste
    Quando toca em "<acao>"
    Então a conclusão da apresentação fica registrada para NOVO
    E abre o formulário de criação do primeiro grupo
    E nenhum grupo ou cobrança é criado apenas por concluir a apresentação
    Quando fecha e reabre o app com a mesma conta
    Então a apresentação concluída não é exigida novamente
    Exemplos:
      | acao               |
      | Criar meu grupo    |
      | Pular apresentação |

  @p0 @APP-OB02
  Cenário: Falha ao concluir a apresentação permite tentar novamente
    Dado que NOVO tem criação de grupo permitida e abriu a apresentação ainda não concluída por um link válido
    Quando desligo a rede antes de tocar em "Criar meu grupo"
    Então vejo falha com ação de tentar novamente e não abro o formulário
    Quando restauro a rede, tento novamente e concluo a apresentação
    Então abro o formulário de grupo e a conclusão fica registrada

  @p0 @APP-H01
  Cenário: Início abre o próximo jogo correto e acompanha a presença
    Dado que ATLETA participa de G1 e G2 e J1 é seu próximo jogo
    E J1 tem vaga e ATLETA ainda não respondeu
    Quando ATLETA abre Início e confirma presença em J1
    Então o card mostra ATLETA confirmado em J1 de G1
    Quando abre o jogo pelo card
    Então o detalhe mostra o mesmo grupo, horário e confirmação
    E a participação nos jogos de G2 permanece inalterada

  @p0 @APP-H02
  Cenário: Voltar ao app atualiza uma dívida recebida em outro aparelho
    Dado que M1 é a única cobrança pendente de ATLETA em todos os seus grupos
    E ATLETA vê R$80,00 em aberto na seção e no aviso de cobranças do Início
    Quando ATLETA deixa o app em segundo plano e DONO registra o recebimento de M1 em outro aparelho
    E ATLETA volta ao app com conexão e aguarda a atualização
    Então a seção e o aviso não mostram mais R$80,00 pendentes
    E Minhas mensalidades mostra M1 paga após atualizar

  @p1 @APP-H03
  Cenário: Início distingue conta sem grupos de falha de carregamento
    Dado que NOVO não participa de grupos e está no Início
    Quando a consulta termina com sucesso
    Então vejo o estado sem grupos e uma ação para iniciar a jornada de grupos
    E não vejo jogo, cobrança ou grupo de demonstração
    Quando reabro Início sem rede e a consulta falha
    Então vejo falha recuperável, sem apresentar a falha como resultado vazio confirmado
    Quando restauro a rede e tento novamente
    Então volto ao estado sem grupos

  @p1 @APP-P04
  Cenário: Editar os dados pessoais persiste após novo login
    Dado que ATLETA abriu Perfil e Editar perfil
    Quando altera nome para "Pessoa QA", apelido para "QA", cidade para "Campinas" e um celular de teste válido
    E salva as alterações
    Então retorna ao Perfil com o nome e apelido atualizados
    Quando sai, entra novamente e abre Editar perfil
    Então nome, apelido, cidade e telefone correspondem aos valores salvos
    E o perfil de PAR permanece inalterado

  @p1 @APP-P05
  Cenário: Erro de preenchimento e falta de rede não descartam a edição do perfil
    Dado que ATLETA abriu Editar perfil e registrou os dados atuais
    Quando apaga o nome e tenta salvar
    Então vejo erro no nome e o perfil persistido permanece inalterado
    Quando corrige o nome, desliga a rede e tenta salvar
    Então vejo falha, continuo na edição e o nome digitado permanece preenchido
    Quando restauro a rede e salvo novamente
    Então retorno ao Perfil e a alteração permanece após reabrir

  @p0 @APP-P06
  Esquema do Cenário: A configuração de telefone controla quem pode consultá-lo
    Dado que ATLETA tem um celular de teste e abriu Editar perfil
    Quando seleciona a visibilidade "<visibilidade>" e salva
    E DONO e PAR reabrem o perfil de ATLETA em suas próprias sessões
    Então DONO <dono> o telefone e PAR <par> o telefone
    E ambos continuam vendo o nome e o perfil esportivo permitido
    Exemplos:
      | visibilidade          | dono    | par     |
      | A galera     | vê      | vê      |
      | Só admins    | vê      | não vê  |
      | Ninguém      | não vê  | não vê  |

  @p1 @APP-P07 @native
  Cenário: Alterar e remover foto do perfil mantém o resultado ao reabrir
    Dado que ATLETA abriu a seleção de foto do próprio Perfil
    E há duas imagens de teste visualmente distintas na galeria
    Quando escolhe uma imagem e conclui a seleção
    Então a nova foto aparece após o envio e permanece ao reabrir o Perfil
    Quando abre a galeria novamente e cancela a seleção
    Então a foto salva permanece
    Quando remove a foto pelo Perfil e reabre a tela
    Então vejo o avatar sem foto e os dados pessoais permanecem salvos
