# language: pt
@adm @manual @automation_candidate
Funcionalidade: Administração da plataforma e checkout
  Contexto:
    Dado o painel apontando para a API de teste da mesma versão e as fixtures de tests/acceptance/README.md
    E PAINEL é administrador da plataforma, ADM é somente administrador de um grupo
    E há usuários, grupos, assinaturas e cupons descartáveis com valores conhecidos

  @p0 @ADM-A01
  Cenário: Somente administrador da plataforma acessa o painel
    Dado que abro o painel em uma sessão de navegador limpa
    Então os dados protegidos não aparecem antes da autenticação
    Quando entro como ADM
    Então a verificação /admin/me nega acesso e não exibe a visão administrativa
    Quando entro como PAINEL
    Então vejo o painel com dados reais da API
    E falha ou 403 nessa verificação não libera dados demonstrativos como fallback

  @p0 @ADM-A02
  Cenário: Expiração de sessão remove dados administrativos
    Dado que PAINEL abriu o detalhe de um usuário
    Quando uma consulta protegida responde 401 ou 403
    Então o painel limpa a sessão, os dados carregados e o detalhe/modal aberto
    E interrompe atualizações periódicas autenticadas
    E oferece autenticação sem permitir voltar ao detalhe com dados antigos

  @p1 @ADM-O01
  Cenário: Visão geral usa período e métricas da API
    Dado que registrei os valores retornados pela API de overview para 30 dias, 90 dias e todo o período
    Quando seleciono cada período na Visão geral
    Então receita, novos usuários, usuários ativos, grupos, jogos e churn correspondem à resposta desse período
    E unidades, moeda e porcentagens estão corretas
    E a resposta atrasada de um período anterior não substitui a seleção atual
    E indisponibilidade da API mostra erro sem apresentar valores fictícios como atuais

  @p0 @ADM-U01
  Cenário: Busca e detalhe do usuário apontam para a identidade selecionada
    Dado que há dois usuários com nomes semelhantes e planos diferentes
    Quando busco pelo email exato de um deles e uso filtros de plano e status
    Então os parâmetros enviados correspondem à busca e filtros escolhidos
    E o resultado não inclui usuários fora dos filtros
    Quando abro o usuário encontrado
    Então nome, email, grupos, assinatura e recibos pertencem ao mesmo ID selecionado
    E resultado vazio não conserva a lista da busca anterior

  @p0 @ADM-U02
  Cenário: Suspender e reativar usuário persistem após recarga
    Dado que PAINEL abriu uma conta descartável ativa
    Quando inicia suspensão e cancela a confirmação, se apresentada
    Então nenhuma suspensão é enviada
    Quando confirma a suspensão
    Então o painel aguarda a resposta e mostra Suspenso somente após sucesso
    E recarregar a página mantém Suspenso
    Quando reativa essa mesma conta
    Então o status volta a Ativo e permanece após recarga
    E nenhum outro usuário é alterado

  @p1 @ADM-U03
  Cenário: Falha de mutação não anuncia sucesso
    Dado que PAINEL abriu uma conta ativa
    Quando o harness faz a suspensão falhar com erro do servidor
    Então vejo erro, o controle deixa de carregar e o usuário não é mostrado como suspenso
    E nova consulta reflete o estado efetivo do backend
    Quando restauro o serviço e tento novamente
    Então o estado confirmado é apresentado sem duplicar ações

  @p1 @ADM-G01
  Cenário: Lista e detalhe de grupos são coerentes
    Dado que existem G1 ativo e outro grupo de teste em estado diferente
    Quando busco G1 e abro seu detalhe
    Então dono, membros, dados do grupo e status correspondem ao ID de G1
    E voltar mantém a pesquisa sem abrir o detalhe de outro grupo
    E falha da API não conserva um detalhe de grupo anteriormente selecionado
    E a paginação permite consultar registros além dos primeiros 25

  @p0 @ADM-S01
  Cenário: Cancelamento de assinatura reflete a confirmação do provedor
    Dado que PAINEL abriu uma assinatura ativa exclusivamente sandbox
    Quando inicia o cancelamento e desiste, se houver confirmação
    Então não ocorre cancelamento
    Quando confirma o cancelamento
    Então a requisição usa o ownerId da assinatura selecionada
    E após sucesso a consulta mostra o estado efetivo atualizado
    E o app desse dono mostra a autorização de plano coerente após recarga
    E a operação não cancela assinatura de outro dono

  @p0 @ADM-PG01
  Esquema do Cenário: Paginar além de 25 registros preservando filtros e detalhe
    Dado que a lista <lista> possui exatamente 51 registros da massa de teste em ordem conhecida
    E para usuários e grupos configurei filtros com 51 resultados conhecidos
    Quando abro a lista
    Então vejo os registros 1 a 25, Anterior desabilitado e Próxima habilitado
    Quando avanço duas vezes
    Então vejo somente o registro 51, página 3 de 3 e Próxima desabilitado
    Quando volto uma página e abro o registro 26, depois volto pelo controle do detalhe
    Então vejo a página 2 preservada e os mesmos filtros e registros
    Quando aciono Atualizar
    Então consulto a página 1 com os mesmos filtros
    E nenhum registro de outra página ou filtro é confundido com o selecionado
    Exemplos:
      | lista        |
      | usuários     |
      | grupos       |
      | assinaturas  |

  @p1 @ADM-PG02
  Esquema do Cenário: Paginação falha e responde fora de ordem sem dados falsos
    Dado que abri <lista> com mais de 25 resultados
    Quando o harness retém a resposta da página 2
    Então Anterior, Próxima e Atualizar ficam desabilitados durante a consulta
    Quando a resposta retida falha com 503
    Então vejo erro e Tentar novamente sem apresentar a página 1 como página 2
    Quando aciono Tentar novamente e a API retorna sucesso
    Então a consulta repete a página 2 e os filtros originais
    Quando inicio uma consulta, faço logout e libero a resposta antiga pelo harness
    Então nenhum dado antigo reaparece na tela nem após nova autenticação
    Exemplos:
      | lista        |
      | usuários     |
      | grupos       |
      | assinaturas  |

  @p1 @ADM-PG03
  Esquema do Cenário: Total reduzido e busca vazia não deixam página inválida
    Dado que <lista> tinha 51 resultados e estou na página 2
    Quando avanço para a página 3 e o harness retorna total 10 nessa resposta
    Então o painel consulta a página 1 e mostra página 1 de 1
    E Anterior e Próxima ficam desabilitados
    Quando uma nova consulta retorna total zero e lista vazia
    Então vejo estado vazio sem registros anteriores e sem avançar para página 2
    Exemplos:
      | lista        |
      | usuários     |
      | grupos       |
      | assinaturas  |

  @p0 @ADM-S02
  Esquema do Cenário: Cancelamento recusado não vira sucesso visual
    Dado que PAINEL abriu uma assinatura sandbox
    Quando a API de cancelamento responde <status>
    Então vejo erro ou conflito coerente e não vejo sucesso de cancelamento
    E posso consultar novamente o estado da assinatura
    E uma indisponibilidade do provedor não altera localmente a assinatura para cancelada
    Exemplos:
      | status |
      | 409    |
      | 503    |

  @p0 @ADM-C01
  Cenário: Criar e desativar cupom altera a elegibilidade
    Dado que o código QA10 ainda não existe
    Quando PAINEL cria o cupom " qa10 " com 10 por cento e validade futura
    Então o código persistido é QA10 e o desconto é 10 por cento
    E o cupom aparece após recarregar a página
    Quando valido QA10 em um checkout sandbox elegível
    Então o preço apresentado considera o desconto retornado pela API
    Quando PAINEL desativa QA10 e recarrego a lista
    Então o cupom aparece inativo e não é aceito em uma nova contratação
    E contratos anteriores não recebem alterações retroativas inventadas pelo painel

  @p0 @ADM-C02
  Esquema do Cenário: Cupom inválido não é persistido
    Dado que PAINEL abriu Novo cupom
    Quando tenta salvar <entrada>
    Então a interface ou API recusa com erro identificável
    E ao consultar a lista não existe um novo cupom inválido
    Exemplos:
      | entrada                              |
      | código vazio                         |
      | desconto de 0 por cento               |
      | desconto de 101 por cento             |
      | quantidade de ciclos igual a zero     |
      | código já cadastrado                 |

  @p0 @ADM-X01
  Cenário: Checkout Pix confirma contratação somente após pagamento
    Dado que uma conta de teste iniciou uma contratação via Pix sandbox
    Quando confirma plano e dados válidos
    Então vê preço, cobrança Pix e estado pendente correspondentes à API
    E o plano não é liberado apenas por abrir ou copiar o Pix
    Quando o provedor sandbox confirma o pagamento e a consulta recebe a atualização
    Então a assinatura/entitlement ficam liberados conforme o plano contratado
    E recarregar a página recupera a operação sem gerar outra cobrança

  @p0 @ADM-X02
  Cenário: Checkout com cartão lida com recusa e retry sem cobrança duplicada
    Dado que uma conta está no checkout com cartão de teste configurado para recusa
    Quando envia os dados
    Então recebe a recusa sem apresentar plano ativo
    E dados completos de cartão não aparecem em logs, captura de execução ou storage de teste
    Quando usa um cartão de teste aprovado
    Então a operação tem o resultado real do provedor sandbox
    E duplo clique ou retry do mesmo requestId não gera duas cobranças
    E mudança de plano ou cupom não reutiliza silenciosamente uma chave para payload diferente

  @p1 @ADM-X03
  Cenário: Checkout diferencia desconto expirado e erro de serviço
    Dado que tenho um cupom válido e outro expirado de teste
    Quando aplico o cupom expirado
    Então vejo que não é elegível e o total não assume desconto
    Quando a consulta do cupom válido falha por indisponibilidade
    Então vejo falha recuperável, não uma falsa confirmação de desconto
    E antes de concluir posso conferir o total efetivamente autorizado pela API

  @p1 @blocked_implementation @ADM-P01
  Cenário: Suporte e moderação ainda não é um fluxo integrado
    Dado que a versão atual do painel mantém Suporte e moderação demonstrativo no VUL-171
    Quando abro essa seção e suas ações
    Então registro este cenário como BLOQUEADO por implementação
    E não considero lista, resolver ou ver grupo evidência de atendimento persistido
    E não executo uma suposta resolução em produção para tentar comprovar integração
