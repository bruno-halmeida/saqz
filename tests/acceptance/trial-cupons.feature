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
    Então vê a oferta pública de 14 dias do Organizador, sem campo de cupom
    E vê que precisará assinar para continuar após o teste, sem cobrança automática
    Quando aceita a oferta
    Então pode abrir o formulário
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
    Então recebe a orientação para aplicar o cupom na página de contratação
    Quando aplica QA30 na página web de contratação
    Então sua conta fica com o teste de 30 dias liberado, ainda sem trial iniciado nem uso consumido
    E ao atualizar o app vai direto ao formulário de criação
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
    Quando NOVO tenta aplicar esse cupom na página web de contratação
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
    Quando NOVO tenta aplicar um código inexistente na página web de contratação
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

  @p0 @TRIAL-07
  Cenário: Teste liberado pela web fica vinculado à conta e dispensa oferta no app
    Dado que a oferta está Ligada e NOVO concluiu seu perfil na página de contratação
    Quando escolhe Experimentar o Organizador por 14 dias
    Então vê que o teste está liberado para esta conta e começa no primeiro grupo
    E o backend registra a liberação sem criar grupo, iniciar prazo ou gerar cobrança
    Quando repete a liberação e entra no app com a mesma conta
    Então ao abrir Criar grupo acessa diretamente o formulário, sem repetir o aceite
    Quando entra no app com outra conta elegível sem liberação
    Então a outra conta vê a oferta pública e precisa aceitar antes de abrir o formulário

  @p0 @TRIAL-08
  Cenário: Fim do teste exige assinatura para continuar sem cobrança automática
    Dado que NOVO criou seu primeiro grupo com teste de 14 dias e não contratou um plano
    E registrei o instante inicial, o término, os atletas e o histórico do grupo
    Quando o relógio controlado do backend alcança o término do teste
    Então Meu plano informa que o teste terminou e oferece contratação
    E as operações de escrita do grupo ficam bloqueadas até uma assinatura válida
    E os dados do grupo continuam disponíveis para consulta
    E não existe assinatura paga ou cobrança automática criada pelo término do teste
    Quando NOVO contrata um plano no checkout sandbox e o pagamento é confirmado
    Então o app recupera o acesso correspondente ao plano sem apagar o histórico

  @p0 @TRIAL-09
  Cenário: Aceitar e abandonar ou falhar a criação não consome o teste
    Dado que a oferta está Ligada e NOVO abriu a tela de 14 dias grátis
    Quando volta sem aceitar
    Então não abre o formulário e nenhum teste é iniciado
    Quando reabre a oferta, aceita e volta sem salvar o grupo
    Então nenhum grupo, teste ou cobrança é criado
    Quando aceita novamente e a criação do grupo falha antes do commit
    Então a criação e o teste são revertidos juntos
    Quando tenta novamente com sucesso e repete o mesmo envio
    Então existe um único grupo e um único teste contado do sucesso

  @p0 @TRIAL-10
  Cenário: Aceite reconsulta a oferta e não usa permissão desatualizada
    Dado que NOVO está na tela de 14 dias grátis
    Quando PAINEL desliga a oferta antes do aceite e NOVO aceita
    Então não abre o formulário e vê a orientação para consultar acesso na página de contratação
    E nenhum teste é iniciado
    Quando a oferta volta a ficar disponível e NOVO atualiza a tela
    Então vê a oferta atualizada antes de aceitar novamente

  @p1 @TRIAL-11
  Cenário: Falha de rede no aceite permite tentar novamente sem liberar criação
    Dado que NOVO está na tela de 14 dias grátis
    Quando desliga a rede e toca em Entendi, quero experimentar
    Então vê carregamento e o botão fica desabilitado enquanto a consulta está pendente
    E após a falha vê um erro recuperável sem abrir o formulário
    Quando restabelece a rede e atualiza a consulta
    Então vê a oferta atual e pode aceitar novamente
    E uma resposta atrasada de consulta anterior não substitui o estado mais recente

  @p1 @TRIAL-12
  Cenário: Tela do teste apresenta benefícios e condições com um único aceite
    Dado que NOVO é elegível ao teste público de 14 dias
    Quando abre Criar grupo em Android e iOS, inclusive com fonte ampliada e tela pequena
    Então vê 14 dias grátis, início no primeiro grupo, até 3 grupos e atletas ilimitados
    E vê os benefícios de jogos, presenças e financeiro
    E consegue ler que precisará contratar plano pago após o teste e não haverá cobrança automática
    E existe um único botão principal Entendi, quero experimentar, além da navegação de voltar
    E não existem campo de cupom, botão Aplicar cupom ou planos concorrendo com o aceite
    E o conteúdo pode ser rolado sem cortar textos e o aceite permanece acessível

  @p1 @TRIAL-13
  Cenário: Liberação web ignora resposta antiga e protege troca de conta
    Dado que NOVO está na página de contratação com consulta de teste pendente
    Quando libera seu teste e a consulta anterior responde depois da confirmação
    Então continua vendo a confirmação mais recente
    Quando aplica um cupom e sai da conta antes da resposta
    Então o código é limpo e a resposta atrasada não mostra benefício para outra conta
    E entrar com outra conta consulta exclusivamente o acesso dessa nova conta

  @p0 @TRIAL-14
  Cenário: Teste do Organizador permite três grupos com um único prazo
    Dado que NOVO aceitou o teste público do Organizador
    Quando cria o primeiro grupo e registra o início e o término do teste
    E cria o segundo e o terceiro grupo dois dias depois
    Então os três grupos usam o mesmo início e término, sem reiniciar a contagem
    Quando tenta criar um quarto grupo
    Então a criação é recusada pelo limite de três grupos
    E a API também impede exceder três grupos com requisições concorrentes
    Quando o prazo original termina
    Então todos os grupos ficam somente para consulta até uma assinatura válida
    E nenhum grupo, histórico ou atleta é excluído automaticamente

  @p0 @TRIAL-15
  Cenário: Teste do Organizador permite mais de vinte e cinco atletas
    Dado que NOVO tem teste ativo sem checkout pendente de plano menor
    E seu grupo tem 25 atletas ocupando vagas
    Quando um vigésimo sexto atleta aceita um convite válido
    Então o atleta entra sem bloqueio de limite do plano
    E o app e a página web descrevem o teste como até 3 grupos e atletas ilimitados
    E o teste não libera os recursos exclusivos do plano Ilimitado

  @p0 @TRIAL-16
  Cenário: Titular continua disponível quando o uso do teste cabe no primeiro plano
    Dado que NOVO usou apenas 1 grupo e tem até 25 atletas ocupando vagas
    Quando abre a contratação após o teste
    Então vê o Organizador em destaque com Continuar com o Organizador
    E pode escolher o Titular e conferir o preço do catálogo para o ciclo escolhido
    Quando escolhe o Titular e confirma o pagamento sandbox
    Então recebe os limites do Titular e mantém os dados existentes
    E nenhum pagamento é feito apenas por abrir ou selecionar o plano

  @p0 @TRIAL-17
  Esquema do Cenário: Plano menor incompatível é recusado antes da cobrança
    Dado que NOVO usou o Organizador com <grupos> grupos e <atletas> atletas ocupando vagas
    Quando tenta contratar o Titular no checkout
    Então vê que o uso atual não cabe no plano
    E a API recusa a contratação antes de criar assinatura ou cobrança
    E os grupos, atletas e históricos permanecem preservados
    E pode voltar para escolher o Organizador ou outro plano compatível
    E durante o teste ativo pode ajustar o uso, respeitando vagas ocupadas por atletas removidos nos últimos 30 dias
    E com teste expirado não recebe promessa de editar dados bloqueados antes de assinar
    Exemplos:
      | grupos | atletas |
      | 2      | 20      |
      | 1      | 26      |

  @p1 @TRIAL-18
  Cenário: Checkout pendente de plano menor impede aumentar uso além do plano escolhido
    Dado que NOVO tem teste ativo, 1 grupo e 25 atletas ocupando vagas
    Quando gera um Pix para contratar o Titular e ainda não paga
    Então continua sem assinatura paga confirmada
    E novas inclusões respeitam o limite do Titular para não excedê-lo antes da confirmação
    E a página de contratação explica essa condição antes de gerar o pagamento
    Quando o teste termina antes do pagamento
    Então o Pix pendente não libera acesso de escrita
    Quando o pagamento sandbox é confirmado
    Então passa a ter acesso conforme o Titular, sem apagar dados
