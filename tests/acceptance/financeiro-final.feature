# language: pt
@app @manual @automation_candidate
Funcionalidade: Geração manual de mensalidades e conclusão do acerto
  Contexto:
    Dado que uso as contas e grupos descartáveis definidos em tests/acceptance/README.md
    E app e backend são da mesma versão em ambiente de teste

  @p0 @APP-MG01
  Esquema do Cenário: Gerar manualmente exige revisão e preserva cobranças existentes
    Dado que entrei como <gestor> e abri o caixa de G1
    E ATLETA e PAR são mensalistas ativos, outro membro é avulso e outro está inativo
    E a competência 2026-08 não tem cobrança para ATLETA nem PAR
    Quando toco Gerar mensalidades
    Então vejo somente os mensalistas ativos, sem seleção automática
    E abrir a tela não cria cobrança nem altera o saldo
    Quando informo mês 2026-08, vencimento 12/08/2026 e valor por pessoa 123,45
    E seleciono somente ATLETA e toco Revisar mensalidades
    Então a revisão mostra ATLETA, R$123,45, mês 2026-08 e vencimento 12/08/2026
    E PAR não aparece como destinatário e nenhuma requisição de geração foi enviada
    Quando volto e edito, depois reviso e confirmo
    Então POST /api/groups/G1/charges/monthly contém apenas o ID de ATLETA, 12345 centavos e vencimento 2026-08-12
    E o app volta ao caixa somente depois de sucesso e atualiza a pendência
    E a cobrança aparece para ATLETA em Minhas mensalidades após atualizar
    E saldo recebido não aumenta e não há mensagem enviada
    Quando repito a geração para ATLETA na mesma competência, agora com valor 200,00
    Então continua existindo uma única cobrança, com R$123,45 e vencimento original
    E não se altera a configuração automática ou o preço individual de ATLETA
    Exemplos:
      | gestor |
      | DONO   |
      | ADM    |

  @p0 @APP-MG02
  Esquema do Cenário: Dados inválidos não geram mensalidades
    Dado que DONO abriu a geração em G1
    Quando tenta revisar com <entrada>
    Então vejo a validação e não consigo confirmar uma geração
    E nenhuma cobrança é criada
    Exemplos:
      | entrada                                                 |
      | mês 2026-13                                              |
      | mês 2026-02 e vencimento 30/02/2026                       |
      | mês 2026-08 e vencimento 12/09/2026                       |
      | valor zero                                               |
      | valor negativo                                           |
      | valor acima de 999999,99                                 |
      | nenhum mensalista selecionado                            |

  @p0 @APP-MG03
  Cenário: Perda de resposta e duplo toque não duplicam geração
    Dado que DONO revisou uma mensalidade válida de ATLETA em G1
    Quando o harness permite o commit e descarta a resposta da primeira confirmação
    Então não vejo sucesso, os dados são mantidos e posso tentar novamente
    Quando tento novamente
    Então o requestId e o payload são os mesmos da tentativa anterior
    E uma única cobrança existe no banco para G1, ATLETA e competência
    E enquanto a chamada estiver em andamento Confirmar e Voltar e editar estão bloqueados
    E a seta da tela, o botão Voltar do Android e o gesto de retorno do iOS não saem da geração enquanto a resposta está pendente
    E duplo toque não envia requisição concorrente
    E após sucesso volto ao caixa atualizado; após falha posso tentar novamente ou voltar
    Quando uma tentativa falha e edito valor ou seleção antes de revisar
    Então a próxima confirmação usa nova chave para o novo conteúdo

  @p1 @APP-MG04
  Cenário: Falhas de carga e mudanças de vínculo não liberam operação indevida
    Dado que a consulta do grupo ou elenco falha ao abrir a geração
    Então vejo erro e Tentar novamente, sem botão de confirmar disponível
    Quando restauro a consulta e tento novamente
    Então vejo o formulário com os dados do grupo solicitado
    Quando um grupo não tem mensalistas ativos
    Então vejo estado vazio e Revisar desabilitado
    Quando ATLETA tenta abrir o destino de geração ou chamar a API diretamente
    Então o acesso administrativo é negado e nenhuma cobrança é criada
    Quando um selecionado sai do grupo depois da revisão e antes da confirmação
    Então a API não gera cobrança para o usuário desvinculado
    E o aplicativo apresenta a falha sem anunciar sucesso

  @p0 @APP-AC01
  Cenário: Encerrar acerto exige confirmação de todos os recebimentos
    Dado que DONO abriu o acerto de J1 com uma cobrança GAME pendente de R$70
    Então Encerrar acerto está desabilitado
    Quando inicia o registro de recebimento e o harness retém a resposta
    Então Encerrar continua desabilitado e o resumo não anuncia acerto encerrado
    Quando a resposta falha
    Então a cobrança volta a pendente, R$70 permanece em aberto e vejo erro
    Quando tento novamente e o backend confirma o recebimento
    Então o resumo fica sem pendência e Encerrar acerto fica habilitado
    Quando toco Encerrar acerto duas vezes
    Então volto uma única vez à tela anterior, sem nova escrita financeira nem recarga de encerramento
    E reabrir o acerto consulta o estado remoto atual

  @p1 @APP-AC02
  Cenário: Falha de consulta não é confundida com acerto sem pendências
    Dado que a consulta do acerto de J1 está indisponível
    Quando DONO abre o acerto
    Então vejo erro e retentativa, sem resumo encerrado e sem botão Encerrar disponível
    Quando restauro a consulta e ela retorna cobranças sem pendências
    Então posso encerrar e voltar ou usar Ver caixa para abrir o caixa de G1
    E Ver caixa nunca abre G2 ou cria um lançamento
