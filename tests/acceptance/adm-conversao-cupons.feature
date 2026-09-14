# language: pt
@adm @manual @automation_candidate @principal
Funcionalidade: Consultar conversão de cupons
  Contexto:
    Dado o ambiente isolado e PAINEL de tests/acceptance/README.md
    E o banco de analytics usa exclusivamente a massa deste cenário, sem outras campanhas
    E os pagamentos são fatos de teste confirmados e processados, sem cobrança real

  @p0 @ADM-CA01
  Cenário: Conversão e receita usam pagamentos confirmados sem duplicar o resumo
    Dado que A e B iniciaram trial com QA30 e ambos os trials já terminaram
    E A também resgatou o cupom de desconto QA10 antes de pagar
    E depois dos usos A tem dois pagamentos distintos confirmados de R$100,00 e R$50,00
    E B não tem pagamento confirmado e QASEMUSO é um cupom sem usos
    Quando PAINEL abre Cupons e atualiza Conversão de cupons
    Então QA30 mostra 2 usos, 1 pagante, conversão de 50,00% e receita bruta de R$150,00
    E QA30 mostra 2 trials encerrados, zero em andamento e conversão dos encerrados de 50,00%
    E QA10 mostra 1 uso, 1 pagante, conversão de 100,00% e receita bruta de R$150,00
    E QASEMUSO mostra zero usos, zero pagantes, receita R$0,00 e conversão sem denominador como traço
    E o resumo global mostra 2 usos únicos, 1 pagante, conversão de 50,00% e receita bruta de R$150,00
    E o painel informa que a receita é bruta e o período é todo o histórico registrado

  @p1 @ADM-CA02
  Cenário: Busca e filtro alteram a tabela sem alterar o resumo global
    Dado que existem QA30 de trial na campanha "Quadra QA" e QA10 de desconto com métricas registradas
    E PAINEL registrou os valores do resumo global
    Quando filtra Trial e busca "Quadra QA"
    Então vejo QA30 e não vejo QA10 na tabela
    E os valores do resumo global permanecem iguais
    Quando busca um código inexistente
    Então vejo nenhum resultado da busca
    Quando limpa a busca e seleciona Todos
    Então os dois cupons reaparecem com os mesmos valores

  @p1 @ADM-CA03
  Cenário: Falha de consulta não vira conversão zero e Atualizar recupera os dados
    Dado que existe um cupom de teste com 1 uso e um pagamento confirmado de R$100,00
    Quando PAINEL abre Conversão de cupons com a API indisponível
    Então vejo falha com opção de tentar novamente, sem tratar os indicadores como zeros confirmados
    Quando restauro a API e tento novamente
    Então vejo 1 uso, 1 pagante, conversão de 100,00% e receita bruta de R$100,00
    Quando um segundo pagamento de R$50,00 da mesma pessoa é confirmado e PAINEL toca em Atualizar
    Então vejo receita bruta de R$150,00, mantendo 1 uso e 1 pagante
