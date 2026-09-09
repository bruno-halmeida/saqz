# language: pt
@app @manual @automation_candidate
Funcionalidade: Regressão crítica do aplicativo
  Contexto:
    Dado o ambiente isolado e as contas de tests/acceptance/README.md
    E nenhuma operação usa usuários ou pagamentos de produção

  @p0 @APP-R01
  Cenário: Login e logout não misturam dados entre contas
    Dado que estou desconectado e ATLETA possui cobranças diferentes de PAR
    Quando entro com a credencial válida de ATLETA
    Então o app espera a sessão validada antes de mostrar telas protegidas
    E Início, Grupos e Perfil correspondem a ATLETA
    Quando saio da conta e entro como PAR
    Então não vejo nome, foto, saldo, notificações ou navegação guardada de ATLETA
    E o botão voltar não reabre uma tela autenticada da conta anterior

  @p0 @APP-R02
  Cenário: Erros de acesso não simulam autenticação bem-sucedida
    Dado que estou na tela de login
    Quando informo credencial inválida
    Então vejo erro sem entrar no app e sem revelar se o email pertence a terceiro
    Quando tento novamente com a rede indisponível
    Então vejo uma falha recuperável e o botão deixa de carregar
    Quando restauro a rede e uso uma credencial válida
    Então entro com a identidade correta sem criar duas sessões visuais

  @p0 @APP-R03
  Cenário: Recuperação de senha só aceita código válido da solicitação
    Dado que existe uma conta descartável cujo email de teste posso consultar
    Quando solicito recuperação e obtenho o código pelo canal de teste
    Então não existe código ou senha exposto em tela administrativa ou logs de execução
    Quando informo código incorreto ou expirado
    Então a senha permanece inalterada e vejo erro
    Quando solicito um novo código válido e concluo com uma nova senha
    Então consigo entrar com a nova senha e não com a antiga
    E o código consumido não pode ser usado novamente

  @p0 @APP-R04 @native
  Cenário: App permanece em retrato
    Dado que o aparelho está com rotação do sistema liberada
    Quando giro o aparelho durante login, Início, detalhe de grupo e um formulário aberto
    Então todas essas telas permanecem em retrato
    E não perco rascunho, confirmação aberta ou destino atual
    Quando volto de um aplicativo de mapas aberto em horizontal
    Então Saqz continua em retrato

  @p0 @APP-R05
  Cenário: Plano permite ou bloqueia criação de grupo com retorno correto
    Dado que DONO tem plano válido e capacidade disponível
    Quando inicio Criar grupo e salvo os dados obrigatórios
    Então existe um único novo grupo no backend e o detalhe mostra os dados enviados
    E DONO aparece como dono do grupo
    Quando repito a tentativa com uma conta sem autorização para criar grupo
    Então vejo a orientação de plano antes da criação
    E nenhum grupo é criado por uma chamada direta não autorizada

  @p0 @APP-R06
  Esquema do Cenário: Entrada por convite respeita a configuração do grupo
    Dado que FORA recebeu um convite válido de um grupo com aprovação <aprovacao>
    Quando abre o convite, autentica-se e solicita entrada
    Então <resultado>
    E tocar novamente ou reabrir o link não duplica participação ou solicitação
    E um convite inválido ou expirado não dá acesso aos dados do grupo
    Exemplos:
      | aprovacao | resultado                                                              |
      | desligada | obtém vínculo e pode preencher seu cadastro esportivo                   |
      | ligada    | aguarda revisão e só obtém acesso depois que um administrador aprova    |

  @p1 @APP-R07
  Cenário: Editar grupo atualiza os destinos anteriores
    Dado que DONO abriu G1 a partir da lista de grupos
    Quando altera nome e endereço do grupo e salva
    Então o detalhe mostra os novos dados após voltar
    E a lista mostra o novo nome sem logout
    E Ver no mapa usa o endereço atualizado
    E ATLETA não consegue realizar a mesma edição por uma chamada direta

  @p0 @APP-R08
  Cenário: Confirmação de presença respeita capacidade e lista de espera
    Dado que J1 tem capacidade 2 e nenhuma confirmação
    Quando dois atletas ativos confirmam presença
    Então ambos aparecem como confirmados e há zero vagas restantes
    Quando um terceiro confirma
    Então entra na lista de espera e não excede a capacidade de confirmados
    Quando um confirmado desiste com promoção FIFO habilitada
    Então o primeiro elegível da espera é promovido uma única vez
    E detalhe, lista de presença e Início concordam após atualizar

  @p0 @APP-R09
  Cenário: Prazo e encerramento impedem mudanças indevidas de presença
    Dado que o prazo de J1 está encerrado ou o jogo está congelado
    Quando ATLETA tenta alterar presença pela tela ou API
    Então a operação é recusada e a resposta anterior permanece
    E a interface informa indisponibilidade sem manter um sucesso otimista falso
    E repetir a tentativa não altera contagem, fila ou cobranças

  @p0 @APP-R10
  Cenário: Gerar mensalidades e registrar pagamento não duplica lançamentos
    Dado que o harness selecionou uma competência sem cobrança gerada em G1
    Quando o harness solicita geração pela API ou executa o job de teste para os mensalistas elegíveis
    Então o valor e vencimento de cada cobrança correspondem à configuração do atleta
    E repetir a mesma geração pelo harness não cria uma segunda cobrança da mesma competência
    Quando DONO abre as cobranças no app e registra o pagamento de M1
    Então M1 fica paga e sai do total em aberto
    E saldo e extrato refletem o pagamento uma única vez
    E ATLETA vê o mesmo estado em suas cobranças após atualizar

  @p0 @APP-R11
  Cenário: Isentar e cancelar preservam histórico sem criar receita
    Dado que há duas cobranças pendentes distintas de ATLETA em G1
    Quando DONO isenta uma e cancela a outra com a confirmação solicitada
    Então os estados ficam Isenta e Cancelada e as cobranças continuam no histórico
    E não surge entrada de dinheiro por essas operações
    E a pendência de ATLETA é reduzida conforme as cobranças alteradas
    E ATLETA não pode isentar ou cancelar sua própria cobrança pela API

  @p0 @APP-R12
  Cenário: Acerto do jogo e lançamento manual reconciliam o caixa
    Dado que o harness preparou J1 com participantes e cobranças por jogo já geradas por seu fluxo de backend
    E registrei IDs, participantes e valores das cobranças existentes
    Quando DONO abre o acerto de J1 e consulta o resumo
    Então o resumo corresponde às cobranças existentes, sem gerar novas cobranças apenas ao concluir o resumo
    Quando DONO registra uma despesa manual de quadra de R$120 pelo fluxo de lançamento
    Então o extrato contém uma única despesa de R$120 vinculada à operação
    E voltar ao caixa e ao detalhe mostra o saldo atualizado
    E falha de rede não anuncia conclusão antes da confirmação do servidor
    E uma segunda tentativa idempotente não duplica o lançamento

  @p0 @APP-R13
  Cenário: Sessão expirada interrompe operações protegidas
    Dado que ATLETA está em um formulário autenticado de G1
    Quando a API invalida a sessão durante uma consulta ou gravação
    Então o app deixa de mostrar a jornada protegida e oferece nova autenticação
    E não mantém indicador de gravação concluída para uma operação negada
    E após autenticar outra conta não reaproveita dados privados do formulário anterior
