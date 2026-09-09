# language: pt
@app @manual @automation_candidate
Funcionalidade: Ligações completas do aplicativo
  Contexto:
    Dado o ambiente isolado com os aliases e dados de tests/acceptance/README.md
    E o aplicativo e o backend estão na versão sob teste

  @p0 @APP-L01
  Esquema do Cenário: Sair remove somente meu vínculo com o grupo selecionado
    Dado que entrei como <pessoa> e participo de G1 e G2
    E anotei as cobranças e presenças históricas dessa conta em G1
    Quando abro Grupos, G1, Sair do grupo
    Então vejo uma confirmação explicando perda de acesso e preservação do histórico
    E ainda consigo escolher Continuar no grupo sem enviar a exclusão
    Quando confirmo Sair do grupo
    Então ocorre DELETE /api/groups/G1/memberships/me sem ID de outra pessoa no corpo
    E após resposta 204 volto à lista de grupos sem G1
    E G2 permanece na lista e posso abri-lo normalmente
    E G1 não aparece em Perfil nem nos grupos do Início após recarga
    E voltar não reabre o detalhe anterior de G1
    E ao reabrir o app G1 continua ausente
    E como DONO ainda vejo o histórico financeiro e de participação da pessoa que saiu
    E a pessoa que saiu recebe recurso não encontrado ao consultar dados protegidos de G1
    Exemplos:
      | pessoa |
      | ATLETA |
      | ADM    |

  @p0 @APP-L02
  Cenário: Cancelar saída não altera participação
    Dado que entrei como ATLETA no detalhe de G1
    Quando abro Sair do grupo e toco Continuar no grupo
    Então a confirmação fecha e nenhuma requisição DELETE é enviada
    E continuo conseguindo abrir membros, agenda e comunicação de G1
    Quando reabro a confirmação e fecho pelo botão de fechar
    Então o vínculo continua existindo após reiniciar o app

  @p0 @APP-L03
  Cenário: O dono não sai por uma operação de desvinculação
    Dado que entrei como DONO no detalhe de G1
    Então não existe ação Sair do grupo
    Quando o harness chama DELETE /api/groups/G1/memberships/me com essa identidade
    Então a resposta é 403
    E G1, seu dono e seus membros permanecem inalterados

  @p0 @APP-L04
  Cenário: Saída lida com duplo toque e perda da resposta
    Dado que entrei como ATLETA e abri a confirmação de saída de G1
    E o harness segura a resposta da exclusão
    Quando toco duas vezes rapidamente em Sair do grupo
    Então existe apenas uma requisição em voo e os botões ficam indisponíveis
    E ainda não sou redirecionado para a lista
    Quando o backend confirma a exclusão mas o harness descarta a resposta
    Então vejo falha sem anúncio de saída concluída e posso tentar novamente
    Quando restauro a conexão e confirmo novamente
    Então a resposta é 204 mesmo sem vínculo restante e volto à lista sem G1
    E não há remoção de outro grupo nem alteração de histórico

  @p0 @APP-L05
  Cenário: Uma chamada anônima não pode desvincular ninguém
    Dado que registrei os vínculos atuais de G1
    Quando chamo DELETE /api/groups/G1/memberships/me sem credencial
    Então recebo 401 e todos os vínculos permanecem iguais

  @p1 @APP-P01
  Cenário: Editar meu cadastro esportivo volta ao perfil atualizado
    Dado que entrei como ATLETA com posição PONTA em G1
    Quando abro Perfil e toco no cadastro de G1
    Então vejo meus atributos existentes e não os de PAR
    Quando seleciono Central e salvo
    Então o botão fica ocupado até a resposta e volto ao Perfil
    E G1 mostra Central sem exigir logout ou reinício
    E não sou enviado ao detalhe do grupo como se fosse uma nova entrada
    Quando reabro o cadastro e depois reinicio o app
    Então Central permanece salvo e os atributos de G2 não foram alterados

  @p0 @APP-P02
  Cenário: Perfil de outro membro respeita a privacidade
    Dado que entrei como ATLETA e PAR ocultou o telefone de outros atletas
    Quando abro G1, Membros, PAR, Ver perfil
    Então vejo nome e atributos esportivos de PAR, não os de ATLETA
    E não vejo telefone, cobranças ou estatísticas que a API negou
    E uma resposta 403 somente nas estatísticas não substitui o perfil inteiro por erro
    Quando entro como DONO e abro o mesmo perfil
    Então estatísticas autorizadas correspondem aos valores da API
    E o telefone só aparece se a política de visibilidade de PAR permitir

  @p1 @APP-P03
  Cenário: Falha recuperável de estatísticas não apaga o perfil básico
    Dado que entrei como DONO e a lista de membros de G1 carregou
    E o harness faz a consulta de estatísticas de PAR falhar por timeout
    Quando abro o perfil de PAR
    Então vejo o perfil básico e Tentar carregar estatísticas novamente
    Quando restauro a consulta e tento novamente
    Então vejo os números reais e o aviso de erro desaparece

  @p0 @APP-F01
  Cenário: Minhas mensalidades não expõem cobranças alheias
    Dado que entrei como ATLETA com M1, M2, M3 e M4
    Quando abro Perfil, Minhas mensalidades
    Então vejo M1 em aberto com R$80 e vencimento correto
    E vejo M2 paga, M3 isenta e M4 cancelada no histórico
    E não vejo a cobrança de PAR nem a cobrança por jogo
    E o app usa /charges/me, não o caixa administrativo
    Quando toco Ver grupo e dados para pagamento em G1
    Então abro G1 com os dados de pagamento desse grupo, não de G2

  @p1 @APP-F02
  Cenário: Mensalidades cobrem histórico longo, vazio e falha por grupo
    Dado que ATLETA possui 12 mensalidades históricas em G1 e nenhuma em G2
    Quando abro Minhas mensalidades
    Então consigo consultar as 12 sem corte nas seis mais recentes
    E G2 informa ausência de mensalidades
    Quando o harness faz apenas a consulta de G2 falhar
    Então G1 continua visível e G2 oferece nova tentativa
    Quando restauro a conexão e tento novamente
    Então G2 volta ao estado vazio, sem cobranças fictícias

  @p1 @APP-M01 @native
  Cenário: Abrir mapa usa o endereço real e mostra recusa do sistema
    Dado que abri G1 com o endereço especial da fixture
    Quando toco Ver no mapa
    Então o sistema recebe a busca pelo endereço inteiro com acentos, espaços, &, / e # codificados
    E o local consultado corresponde a G1, não a coordenadas ou endereço fixo
    Quando o harness nativo recusa a abertura
    Então vejo falha no app e posso tentar novamente sem travamento

  @p0 @APP-C01
  Esquema do Cenário: Avisos têm leitura compartilhada e publicação restrita
    Dado que entrei como <pessoa> e abri G1, Avisos
    Então <editor>
    Quando DONO publica "Jogo na quadra 2 — execução QA"
    E abro ou atualizo Avisos como ATLETA
    Então vejo uma única mensagem com autor, texto e data corretos
    E ela permanece após fechar e reabrir o aplicativo
    E o último aviso também aparece no detalhe de G1 após voltar
    E FORA não consegue ler o aviso nem publicar usando o ID de G1
    Exemplos:
      | pessoa | editor                                      |
      | DONO   | vejo editor e botão Enviar                   |
      | ADM    | vejo editor e botão Enviar                   |
      | ATLETA | vejo conteúdo sem editor ou botão de envio   |

  @p0 @APP-C02
  Cenário: Conversa interna é persistida e lida por outra conta
    Dado que entrei como ATLETA em G1, Chat
    Quando escrevo "Estarei no treino QA" e toco Enviar
    Então o texto aparece somente após confirmação do envio
    E o campo é limpo e o autor corresponde à conta autenticada
    Quando entro como PAR e atualizo o Chat de G1
    Então vejo a mesma mensagem
    Quando volto à conta ATLETA e abro o Chat de G2
    Então a mensagem não aparece no Chat de G2
    E nenhuma chamada ou aplicativo WhatsApp é acionado

  @p0 @APP-C03
  Cenário: Reenviar mensagem após timeout não duplica
    Dado que escrevi um texto no Chat de G1
    E o harness deixa o servidor persistir e descarta somente a resposta
    Quando toco Enviar
    Então vejo falha com o texto preservado e não vejo sucesso antecipado
    Quando restauro a rede e envio novamente sem editar
    Então o requestId é o mesmo e existe uma única mensagem e notificação por destinatário
    Quando edito o texto para outra mensagem e envio
    Então o requestId é novo e a nova mensagem é persistida separadamente

  @p1 @APP-C04
  Cenário: Mensagens validam entrada e paginam sem perder conteúdo
    Dado que existem 53 mensagens distintas no Chat de G1
    Quando abro o Chat
    Então vejo as 50 mais recentes em ordem decrescente
    Quando toco Carregar anteriores
    Então as três restantes aparecem sem repetição e o botão deixa de aparecer
    E texto vazio ou só espaços não pode ser enviado
    E o editor limita a mensagem a 2000 caracteres
    E um POST direto maior que o limite recebe 422 sem gravação
    Quando toco Atualizar para voltar à primeira página de 50 mensagens
    E o harness faz a próxima consulta de Carregar anteriores falhar
    Então as 50 mensagens já carregadas permanecem e posso tentar Carregar anteriores novamente

  @p0 @APP-C05
  Cenário: Avisar pendentes alcança apenas destinatários elegíveis
    Dado que J1 está publicado com prazo de confirmação aberto
    E o elenco completo é DONO, PAR ativo sem resposta, ATLETA e ADM confirmados, um recusado e um inativo
    E registrei os IDs, estados e valores de presenças e cobranças de J1
    Quando DONO toca Avisar pendentes no detalhe de G1
    Então vejo o total efetivamente notificado após a resposta, não o total do grupo
    E somente PAR recebe um lembrete interno de J1
    E DONO não recebe cópia de seu próprio envio
    E ATLETA não consegue executar essa operação pela API
    E repetir a mesma requisição não duplica os lembretes
    E jogo encerrado, de outro grupo ou com prazo fechado não aceita lembrete
    E IDs, estados e valores das presenças e cobranças continuam idênticos ao registro anterior

  @p1 @APP-C06
  Cenário: Resultado atrasado de lembrete não aparece em outro jogo
    Dado que o harness segura a resposta de Avisar pendentes para J1
    Quando o próximo jogo muda para J2 e recarrego G1
    E libero a resposta antiga de J1
    Então o detalhe de J2 não mostra o total nem erro do envio de J1
    E consigo enviar um lembrete próprio para J2

  @p0 @APP-N01
  Cenário: Preferências são salvas e controlam notificações futuras
    Dado que entrei como PAR em Perfil, Configurações de notificações
    Quando desativo Mensagens do chat e salvo preferências
    Então só vejo Preferências salvas depois da confirmação da API
    Quando fecho e reabro o app
    Então Mensagens do chat continua desativada
    Quando DONO envia um novo chat e um novo aviso em G1
    Então recebo apenas o aviso na caixa de notificações
    E ainda posso ler a conversa ao abrir o Chat de G1
    E notificações antigas não são apagadas pela mudança
    E não há solicitação de push nem envio ao WhatsApp

  @p1 @APP-N02
  Cenário: Falha ao salvar preferências não anuncia persistência
    Dado que abri as preferências de PAR e alterei uma opção
    Quando a gravação falha por indisponibilidade da rede
    Então vejo erro, mantenho a seleção para tentar novamente e não vejo Preferências salvas
    Quando restauro a rede e salvo novamente
    Então o estado confirmado permanece ao reabrir as preferências

  @p0 @APP-N03
  Cenário: Abrir notificação marca somente a minha leitura e navega ao destino
    Dado que PAR tem um aviso e um lembrete não lidos de G1
    Quando PAR abre o aviso em Perfil, Notificações
    Então o aviso fica lido e abre Avisos de G1
    Quando PAR volta e abre o lembrete
    Então abre o detalhe de J1, não o Chat
    E o estado de leitura de outra conta não é alterado
    E usar o ID da notificação de PAR como FORA não permite lê-la nem alterá-la
    E se marcar como lida falhar, não há navegação falsa de sucesso e posso tentar novamente

  @p0 @APP-N04
  Cenário: Desvinculação revoga também a comunicação
    Dado que PAR possui notificações e acesso ao Chat de G1
    Quando PAR sai de G1 com sucesso
    Então notificações de G1 deixam de aparecer na caixa de PAR após recarga
    E PAR não consegue consultar ou publicar mensagens por uma rota antiga de G1
    E DONO continua vendo as mensagens históricas do grupo
