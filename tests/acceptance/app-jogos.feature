# language: pt
@app @manual @automation_candidate @principal
Funcionalidade: Criar, editar e cancelar jogos
  Contexto:
    Dado o ambiente isolado e as contas de tests/acceptance/README.md
    E G1 permite novos jogos e cada cenário restaura seu estado inicial

  @p0 @APP-J01
  Cenário: Criar jogo fora da recorrência publica os dados escolhidos
    Dado que DONO abriu Criar jogo em G1
    Quando escolhe uma data futura sem conflito, 20h, duração de 120 minutos e capacidade 12
    E confirma a quadra de teste, o prazo de confirmação e salva
    Então abre o detalhe de um único jogo publicado com esses dados no fuso de G1
    E o jogo aparece na Agenda de G1 após reabrir
    E ATLETA consegue abrir esse jogo e responder presença
    E G2 não recebe um jogo novo

  @p0 @APP-J02
  Cenário: Editar um jogo altera somente o evento selecionado
    Dado que J1 e J2 são jogos futuros distintos e J1 ainda não tem confirmações
    Quando DONO edita J1 para 21h, capacidade 8 e observação "Levar bola" e salva
    Então detalhe e Agenda mostram J1 às 21h após atualizar
    E ao reabrir a edição vejo capacidade 8 e observação "Levar bola"
    E J2 e os horários regulares de G1 permanecem inalterados

  @p0 @APP-J03
  Cenário: Cancelar um jogo mantém os demais jogos disponíveis
    Dado que J1 está publicado com ATLETA confirmado e sem cobranças emitidas
    Quando DONO solicita cancelar J1 e desiste na confirmação
    Então J1 continua publicado com a mesma presença
    Quando DONO confirma o cancelamento e reabre o detalhe
    Então J1 aparece cancelado e ATLETA não pode alterar presença
    E J1 não aparece como próximo jogo disponível no Início
    E J2 continua publicado e acessível

  @p0 @APP-J04
  Cenário: Horário ocupado é informado sem criar um segundo jogo
    Dado que J1 ocupa uma data e horário futuros de G1
    Quando DONO tenta criar outro jogo de G1 no mesmo intervalo
    Então vejo conflito de horário e posso abrir o jogo existente
    E não existe um segundo jogo publicado para a tentativa recusada
    Quando volta ao formulário, escolhe um horário livre e salva
    Então o novo jogo é publicado no horário corrigido

  @p1 @APP-J05
  Cenário: Falta de rede preserva o formulário do jogo para nova tentativa
    Dado que DONO preencheu um novo jogo com data e horário livres
    Quando desliga a rede antes de salvar
    Então vejo falha, continuo com os campos preenchidos e não vejo confirmação de publicação
    Quando restauro a rede e salvo novamente
    Então um único jogo com os dados escolhidos aparece na Agenda
