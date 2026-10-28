# language: pt
@app @manual @automation_candidate @principal @blocked_implementation
Funcionalidade: Lista de grupos com foto, próximo jogo, vagas e Confirmar (GER-003, DEB-145)
  Contexto:
    Dado o ambiente isolado e as contas de tests/acceptance/README.md
    E cada cenário começa com sua própria massa e sessão

  @p0 @APP-LG01
  Cenário: A lista mostra cada grupo com foto, selo do jogo, local e vagas
    Dado que ATLETA participa de G1 com foto, cujo próximo jogo publicado é quinta 29/10 às 19h30 no "CERET", quadra "Quadra 2", com 14 confirmados de 18
    E ATLETA não respondeu o jogo de G1
    Quando abre a aba Grupos
    Então vê a foto de G1 encostada na borda esquerda, com o botão "Confirmar" na base
    E vê o selo "QUI", "29" e "19h30" no canto de cima
    E vê "CERET — Quadra 2" com o pino
    E vê "14 de 18 vão" e "4 vagas abertas"

  @p1 @APP-LG02
  Cenário: Grupo sem foto e grupo sem jogo
    Dado que ATLETA participa de G2 sem foto e de G3 sem jogo marcado, com a cidade "Vila Mariana"
    Quando abre a aba Grupos
    Então G2 mostra as iniciais do grupo em azul no lugar da foto
    E G3 mostra "Nenhum jogo marcado por enquanto." e o pino com "Vila Mariana"

  @p1 @APP-LG03
  Esquema do Cenário: A etiqueta da foto mostra a resposta de quem olha
    Dado que ATLETA respondeu "<resposta>" no próximo jogo de G1
    Quando abre a aba Grupos
    Então vê a etiqueta "<etiqueta>" na base da foto de G1

    Exemplos:
      | resposta              | etiqueta  |
      | Vou, com vaga         | Você vai  |
      | Vou, sem vaga         | Na espera |
      | Não vou               | Não vai   |

  @p1 @APP-LG04
  Cenário: Só dono e administradores veem a etiqueta Admin
    Dado que DONO administra G1 e ATLETA é atleta de G1
    Quando os dois abrem a aba Grupos
    Então DONO vê "Admin" no canto de cima da foto de G1
    E ATLETA não vê "Admin"

  @p0 @APP-LG05
  Cenário: Confirmar abre a folha e o Vou responde a presença
    Dado que ATLETA não respondeu o jogo publicado de G1 e há vaga
    Quando toca em "Confirmar" na foto de G1
    Então vê a folha com "Você vai nesse jogo?", "Vou" e "Não vou"
    Quando toca em "Vou"
    Então a folha fecha
    E a etiqueta de G1 passa a "Você vai"
    E ATLETA aparece entre os confirmados do jogo
    E o cabeçalho deixa de contar G1 entre os que esperam resposta

  @p1 @APP-LG06
  Cenário: Não vou pela folha e fechar sem responder
    Dado que ATLETA não respondeu o jogo publicado de G1
    Quando toca em "Confirmar" e toca fora da folha
    Então a folha fecha e "Confirmar" continua na foto
    Quando toca em "Confirmar" e depois em "Não vou"
    Então a etiqueta de G1 passa a "Não vai"

  @p1 @APP-LG07
  Cenário: Vou em jogo com vagas por posição abre a escolha da posição
    Dado que o próximo jogo de G1 tem vagas por posição e ATLETA não respondeu
    Quando toca em "Confirmar" e depois em "Vou"
    Então vê a escolha da posição, como no cartão da Início

  @p1 @APP-LG08
  Cenário: Falha de rede ao responder não muda a etiqueta
    Dado que ATLETA não respondeu o jogo publicado de G1
    E o aparelho está sem conexão
    Quando toca em "Confirmar" e depois em "Vou"
    Então vê "Sem conexão. Sua resposta não foi enviada."
    E "Confirmar" continua na foto de G1

  @p1 @APP-LG09
  Cenário: Confirmar vale depois do prazo, até o início do jogo
    Dado que o prazo de confirmação do jogo de G1 já passou, o jogo ainda não começou e ATLETA não respondeu
    Quando abre a aba Grupos
    Então vê "Confirmar" na foto de G1
    E o "Vou" segue a regra de depois do prazo

  @p1 @APP-LG10
  Cenário: A ordem põe primeiro quem pede resposta
    Dado que ATLETA participa de G1 (jogo no sábado, sem resposta), G2 (jogo na quinta, já respondeu) e G3 (sem jogo)
    Quando abre a aba Grupos
    Então a ordem é G1, G2, G3
    E o cabeçalho mostra "3 grupos · 1 esperando sua resposta"

  @p1 @APP-LG11
  Esquema do Cenário: As vagas mostram o que sobra ou que lotou
    Dado que o próximo jogo de G1 tem <confirmados> confirmados para <total> vagas
    Quando ATLETA abre a aba Grupos
    Então vê "<texto>" na seção de vagas de G1

    Exemplos:
      | confirmados | total | texto                          |
      | 14          | 18    | 4 vagas abertas                |
      | 18          | 18    | Completo                       |
      | 13          | 12    | 13 confirmados para 12 vagas   |

  @p2 @APP-LG16
  Cenário: Jogo grande tem a barra contínua
    Dado que o próximo jogo de G1 tem 30 confirmados para 40 vagas
    Quando ATLETA abre a aba Grupos
    Então a barra de vagas de G1 é contínua, cheia em três quartos e sem traços
    E vê "30 de 40 vão" e "10 vagas abertas"

  @p1 @APP-LG12
  Cenário: Jogo que começou dá lugar ao próximo
    Dado que o jogo de G1 começou há 30 minutos e G1 tem outro jogo publicado no sábado
    Quando ATLETA abre a aba Grupos
    Então G1 mostra o jogo de sábado

  @p1 @APP-LG13
  Cenário: Jogo em andamento sem outro marcado
    Dado que o jogo de G1 começou há 30 minutos, G1 não tem outro jogo e ATLETA não respondeu
    Quando ATLETA abre a aba Grupos
    Então o selo de G1 mostra "AGORA"
    E a etiqueta de G1 é "Não vai", sem "Confirmar"

  @p1 @APP-LG14
  Cenário: Rascunho não aparece na lista
    Dado que G2 tem só um rascunho criado por DONO
    Quando DONO abre a aba Grupos
    Então G2 mostra "Nenhum jogo marcado por enquanto."

  @p1 @APP-LG15
  Esquema do Cenário: Primeiro acesso sem grupo
    Dado que <quem> está autenticado e não participa de grupos
    Quando abre a aba Grupos
    Então vê o bloco azul "<titulo>" com o botão "<botao>"
    E vê a linha tracejada "<alternativa>"

    Exemplos:
      | quem                  | titulo                    | botao             | alternativa      |
      | ATLETA                | Entre no seu grupo        | Colar o convite   | Criar um grupo   |
      | DONO com plano ativo  | Crie o seu primeiro grupo | Criar grupo       | Tenho um convite |
