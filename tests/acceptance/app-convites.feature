# language: pt
@app @convites
Funcionalidade: Convites de grupo sem prazo de validade
  Novos convites permanecem válidos até serem desativados ou substituídos.
  Não existe campo para escolher duração. Convites antigos mantêm seu prazo original.

  Contexto:
    Dado que uso app e backend atualizados com a migração V46 em ambiente de teste
    E existem DONO, ADM e ATLETA em G1 e contas FORA e FORA2 sem vínculo com G1
    E G1 tem plano válido e vagas disponíveis para entrada
    E cada cenário usa dados descartáveis e restaura seu estado inicial

  @p0 @APP-I01
  Esquema do Cenário: Criar um convite permanente sem configurar validade
    Dado que estou autenticado como <gestor> e G1 não tem convite ativo
    Quando abro G1 e a tela Convidar
    Então vejo "O convite não expira. Você pode desativá-lo quando quiser."
    E não existe controle para escolher data ou duração
    Quando toco em "Gerar novo link"
    Então vejo "Link não expira" e um link disponível para copiar
    E a resposta da criação contém inviteUrl, expiresAt nulo e uma revision não vazia
    E existe apenas um convite em G1 com expires_at nulo
    E somente o digest do código está persistido, nunca o link ou código original
    Exemplos:
      | gestor |
      | DONO   |
      | ADM    |

  @p0 @APP-I02
  Cenário: Reabrir e compartilhar mantém o mesmo link permanente
    Dado que DONO criou um convite permanente e guardou o link L1 em uma nota privada de teste
    Quando fecho e reabro o app sem limpar seus dados e volto à tela Convidar
    Então vejo "Link não expira" e o mesmo link L1
    Quando toco em "Copiar link" e colo em uma nota
    Então o conteúdo colado é exatamente L1
    Quando abro "Mostrar QR" e leio o código com outro aparelho
    Então o conteúdo lido é exatamente L1
    Quando compartilho o convite pelo compartilhamento nativo
    Então o texto compartilhado contém L1
    E nenhuma dessas ações gera outro código ou muda a revision do servidor

  @p0 @APP-I03 @relogio_controlado
  Esquema do Cenário: O tempo não invalida novos convites nem altera a regra de entrada
    Dado que DONO criou um convite permanente L1 em G1 com aprovação <aprovacao>
    E o relógio do backend descartável foi avançado para 2099 sem alterar token, plano ou vagas
    Quando FORA abre L1 e conclui a autenticação
    Então <resultado>
    E repetir a entrada não duplica vínculo ou solicitação
    E os metadados do convite continuam ativos sem expiresAt
    E o código não é contabilizado como tentativa de convite inválido
    Exemplos:
      | aprovacao | resultado                                                               |
      | desligada | obtém vínculo de atleta em G1 e segue para preencher o cadastro esportivo |
      | ligada    | fica com pedido pendente e sem vínculo até a aprovação de DONO ou ADM     |

  @p0 @APP-I04
  Cenário: Substituição em outro aparelho impede compartilhar o link antigo após recarga
    Dado que DONO gerou L1 no aparelho A e tem L1 salvo no cache
    Quando ADM gera outro link L2 no aparelho B
    Então L2 é diferente de L1 e a revision também mudou
    E L2 não tem prazo de validade
    Quando DONO sai da tela Convidar e a reabre no aparelho A com conexão
    Então L1 não aparece mais e não pode ser copiado nem compartilhado nessa tela
    E o cache antigo é removido
    E a tela permite gerar outro link, sem inventar ou recuperar o código de L2 do servidor
    Quando FORA tenta entrar usando L1
    Então a entrada é recusada como convite inválido ou expirado e nenhum vínculo é criado
    Quando FORA entra usando L2 antes de uma nova substituição
    Então a entrada respeita a configuração de aprovação de G1

  @p0 @APP-I05
  Cenário: Desativar um convite permanente é definitivo para esse link
    Dado que existe um convite permanente L1 de G1 e ATLETA já participa do grupo
    Quando DONO toca em "Desativar link"
    Então o link desaparece e a tela oferece "Gerar novo link"
    E os metadados ficam inativos, sem revision
    Quando fecho e reabro a tela
    Então L1 não é restaurado do cache
    Quando FORA tenta entrar usando L1
    Então nenhum vínculo ou solicitação é criado
    E ATLETA continua participando normalmente de G1
    Quando a desativação é repetida pela API autenticada como DONO
    Então a API responde 204 e o convite continua ausente

  @p0 @APP-I06 @legado
  Cenário: A migração não reativa convites antigos expirados
    Dado que antes da migração V46 havia um convite L0 com prazo já encerrado
    Quando aplico a migração no banco descartável e consulto G1
    Então a data de validade original de L0 está preservada e o convite está inativo
    Quando FORA tenta entrar usando L0
    Então a entrada é recusada e nenhum vínculo é criado
    Quando DONO gera um novo link L1
    Então L1 tem expiresAt nulo e é diferente de L0
    E L0 continua inválido após a substituição

  @p1 @APP-I07
  Cenário: Convites permanentes não concedem permissão de gerenciamento aos atletas
    Dado que existe um convite permanente L1 em G1
    Quando ATLETA tenta consultar, gerar ou desativar o convite pela API
    Então cada operação responde 403 com ACCESS_FORBIDDEN
    Quando FORA tenta as mesmas operações de gerenciamento em G1
    Então cada operação responde 404 com GROUP_NOT_FOUND
    E L1 e sua revision permanecem inalterados

# Execução manual: APP-I03 precisa de relógio injetado em backend/harness descartável;
# mudar somente a data do iPhone não testa o prazo do servidor. Os testes unitários
# GetInviteMetadataTest/RedeemInviteTest usam relógio controlado, sem esperar dias.
# iOS físico: use um link real emitido por esse backend, abra a partir do app Notas
# e registre instalação (Xcode/TestFlight/loja), domínio Branch e app aberto/fechado.
# O simulador e os testes dos adapters não homologam Universal Links de produção.
# Limite anterior à entrega: a prévia JDBC não considera o prazo legado nem a aprovação;
# a validação de entrada (redeem) os considera. Não aprovar a prévia como coberta aqui.
