# language: pt
@app @manual @automation_candidate @principal
Funcionalidade: Consultar caixa e extrato do grupo
  Contexto:
    Dado o ambiente isolado e as contas de tests/acceptance/README.md
    E cada cenário usa um caixa descartável com movimentos conhecidos e sem saldo anterior

  @p0 @APP-EF01
  Cenário: Caixa e filtros do extrato mantêm os valores conciliados
    Dado que G1 tem apenas entradas recebidas de R$80,00 e R$90,00 e uma despesa de R$120,00
    E existe uma cobrança pendente de R$70,00 que ainda não é receita
    Quando DONO abre o caixa e o extrato de G1
    Então o saldo é R$50,00, com R$170,00 de entradas e R$120,00 de saídas
    E a cobrança pendente não aparece como recebimento no extrato
    Quando filtra Entradas
    Então vejo somente os movimentos de R$80,00 e R$90,00
    Quando filtra Saídas
    Então vejo somente a despesa de R$120,00
    E o resumo continua com R$170,00 de entradas, R$120,00 de saídas e saldo R$50,00
    Quando volta a Todos e reabre o extrato
    Então vejo os três movimentos uma vez cada e nenhum movimento de G2

  @p1 @APP-EF02
  Cenário: Carregar mais movimentos mantém a lista completa
    Dado que G1 tem 21 entradas de teste com descrições distintas, de R$1,00 cada
    Quando DONO abre o extrato e carrega os movimentos seguintes
    Então consegue consultar as 21 entradas sem repetição e o saldo é R$21,00
    E não há mais movimentos para carregar

  @p1 @APP-EF03
  Cenário: Caixa vazio e consulta indisponível têm resultados diferentes
    Dado que G1 não tem movimentos nem cobranças
    Quando DONO abre caixa e extrato com conexão
    Então vejo saldo R$0,00 e extrato sem movimentos
    Quando reabre o extrato sem rede
    Então vejo falha de consulta com ação de tentar novamente
    Quando restauro a rede e tento novamente
    Então vejo o extrato vazio confirmado, sem lançamentos de demonstração

  @p0 @APP-EF04
  Cenário: Trocar o período da visão financeira mostra os movimentos daquele mês
    Dado que DONO administra somente G1 e não há outros movimentos na massa
    E G1 tem entrada de R$100,00 e saída de R$40,00 no mês anterior
    E G1 tem entrada de R$80,00 e saída de R$20,00 no mês atual
    Quando DONO abre a aba Financeiro no mês atual
    Então vejo R$80,00 de entradas e R$20,00 de saídas
    Quando seleciona o mês anterior
    Então vejo R$100,00 de entradas e R$40,00 de saídas
    E os movimentos recentes mostrados pertencem ao mês anterior
    Quando volta ao mês atual
    Então vejo novamente R$80,00 de entradas e R$20,00 de saídas
