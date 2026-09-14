# language: pt
@app @adm @manual @automation_candidate @principal
Funcionalidade: Oferecer trial público ou por cupom
  Contexto:
    Dado o ambiente isolado e as contas de tests/acceptance/README.md
    E PAINEL administra a plataforma e NOVO nunca criou grupo, iniciou trial ou fez pagamento
    E cada cenário usa um NOVO distinto e restaura o modo de oferta ao terminar
    E cupons e campanhas são exclusivos da execução

  @p0 @TRIAL-01
  Cenário: Trial público começa ao criar o primeiro grupo
    Dado que PAINEL salvou a oferta de trial no modo Ligado
    Quando NOVO abre Criar grupo sem aplicar cupom
    Então vê a oferta pública de 14 dias e pode abrir o formulário
    E consultar a oferta não inicia nem consome o trial
    Quando cria seu primeiro grupo com dados válidos
    Então recebe um único trial de 14 dias contado da criação desse grupo
    E ao reabrir Meu plano vê o trial e a mesma data de término

  @p0 @TRIAL-02
  Cenário: Cupom libera o prazo da campanha e conta uso somente no primeiro grupo
    Dado que PAINEL salvou o modo Somente cupom
    Quando cria o cupom " qa30 " com 30 dias, campanha "Quadra QA", validade futura e limite 10
    Então ao recarregar a lista vejo QA30 com 30 dias e zero usos
    Quando NOVO abre Criar grupo sem cupom
    Então precisa aplicar um cupom para abrir o formulário
    Quando aplica QA30
    Então vê a oferta de 30 dias e pode continuar, ainda sem trial iniciado nem uso consumido
    Quando cria o primeiro grupo e PAINEL atualiza a lista
    Então o trial dura 30 dias a partir dessa criação e QA30 tem exatamente um uso
    E reabrir o grupo ou Meu plano não reinicia o prazo nem aumenta os usos

  @p0 @TRIAL-03
  Cenário: Desativar oferta impede novos trials e preserva os já concedidos
    Dado que uma conta tem trial ativo, outra tem assinatura ativa e NOVO ainda não tem benefício
    E registrei a data de término do trial e a assinatura existente
    Quando PAINEL salva o modo Desligado e recarrega o painel
    Então o modo permanece Desligado
    E NOVO não recebe oferta de trial nem libera criação aplicando cupom
    E a conta com trial mantém seu prazo e a conta assinante mantém seu plano

  @p0 @TRIAL-04
  Esquema do Cenário: Cupom indisponível não libera criação no modo Somente cupom
    Dado que a oferta está em Somente cupom e NOVO não tem cupom selecionado
    E o cupom informado está <estado>
    Quando NOVO tenta aplicar esse cupom
    Então vejo aviso de cupom não disponível e não abro o formulário de grupo
    E nenhum trial ou uso é registrado para NOVO
    Exemplos:
      | estado      |
      | desativado  |
      | expirado    |
      | esgotado    |

  @p1 @TRIAL-05
  Cenário: Corrigir cupom ou tentar novamente após falha de rede recupera a oferta
    Dado que a oferta está em Somente cupom e QA30 é válido com 30 dias
    Quando NOVO tenta aplicar um código inexistente
    Então vejo erro e posso editar o código
    Quando informa QA30, desliga a rede antes de aplicar e envia
    Então vejo falha recuperável e não vejo confirmação do benefício
    Quando restauro a rede e aplico QA30 novamente
    Então vejo a oferta de 30 dias e posso continuar para criar o grupo

  @p1 @TRIAL-06
  Cenário: Administração valida cupom e sua desativação persiste
    Dado que QA30 já existe com 2 usos e PAINEL abriu Criar cupom de trial
    Quando tenta criar outro cupom com o mesmo código
    Então vejo erro de duplicidade e a lista continua com apenas um QA30
    Quando tenta criar QAOUTRO com zero dias
    Então vejo erro no prazo e QAOUTRO não é salvo
    Quando desativa QA30 e recarrega a lista
    Então QA30 permanece desativado e os 2 usos anteriores permanecem registrados
