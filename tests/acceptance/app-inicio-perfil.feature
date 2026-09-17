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
  Cenário: Voltar ao app atualiza uma dívida vencida recebida em outro aparelho
    Dado que M1 é a única cobrança pendente de ATLETA em todos os seus grupos e já venceu
    E ATLETA vê no Início a seção "Minhas cobranças" com R$80,00 vencidos, a competência de M1 e a chave Pix de G1, além do aviso de cobranças
    Quando ATLETA deixa o app em segundo plano e DONO registra o recebimento de M1 em outro aparelho
    E ATLETA volta ao app com conexão e aguarda a atualização
    Então a seção "Minhas cobranças" some do Início e o aviso não mostra mais R$80,00 pendentes
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

  @p1 @APP-H04
  Cenário: Cobrança pendente dentro do prazo fica só no aviso, não na seção do Início
    Dado que M1 é a única cobrança pendente de ATLETA em todos os seus grupos e ainda não venceu
    Quando ATLETA abre Início
    Então vê o aviso de cobranças com R$80,00 pendentes
    E não vê a seção "Minhas cobranças" entre o próximo jogo e os grupos
    Quando o vencimento de M1 é antecipado para ontem na massa e ATLETA atualiza o Início
    Então a seção "Minhas cobranças" aparece com R$80,00 vencidos, a competência de M1 e a chave Pix de G1
    E "Copiar chave Pix" copia a chave, mostra "Chave copiada" por alguns segundos e avisa que a baixa é feita pelo gestor

  @p1 @APP-H05
  Cenário: Próximos jogos lista os jogos seguintes de todos os grupos depois do próximo jogo
    Dado que ATLETA participa de G1 e G2, J1 é seu próximo jogo e J2 e um jogo publicado de G2 vêm depois de J1
    Quando ATLETA abre Início
    Então o hero mostra J1 de G1
    E "Próximos jogos" lista J2 e o jogo de G2 em ordem de data, cada um com dia, mês, grupo, hora, confirmados e a própria resposta
    E J1 não aparece em "Próximos jogos"
    Quando toca no jogo de G2
    Então abre o detalhe desse jogo em G2, não o de J1
    Dado que ATLETA não tem nenhum jogo depois de J1
    Quando atualiza o Início
    Então "Próximos jogos" não aparece

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
