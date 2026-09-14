# language: pt
@app @manual @automation_candidate @principal @sandbox
Funcionalidade: Consultar e gerenciar o próprio plano pelo aplicativo
  Contexto:
    Dado o ambiente isolado e as contas de tests/acceptance/README.md
    E ASSINANTE tem uma assinatura sandbox ativa com ciclo e fim do período conhecidos
    E preços, limites e valores de cobrança vêm do catálogo e da API do ambiente
    E cada cenário usa uma assinatura independente

  @p0 @APP-S01
  Cenário: Meu plano mostra assinatura, uso e recibos da conta autenticada
    Dado que ASSINANTE tem 2 recibos confirmados de valores e datas conhecidos
    Quando abre Perfil e Meu plano
    Então vejo seu plano, ciclo, situação e uso de grupos correspondentes à API
    Quando abre os recibos
    Então vejo os 2 recibos com seus valores e datas, sem recibos de outra conta
    Quando fecha e reabre Meu plano
    Então continua vendo a mesma assinatura

  @p0 @APP-S02
  Cenário: Upgrade pago por Pix só muda o plano depois da confirmação
    Dado que ASSINANTE tem plano Organizador e um upgrade para Ilimitado gera diferença positiva
    Quando abre Trocar plano, seleciona Ilimitado e cancela a confirmação
    Então permanece no plano Organizador e nenhuma cobrança é criada
    Quando seleciona Ilimitado novamente e confirma
    Então vejo Pix pendente e o valor exato retornado pela API para o upgrade
    Quando toca em Já paguei antes da confirmação do provedor
    Então continua aguardando e o plano Organizador permanece vigente
    Quando o pagamento sandbox é confirmado e consulta novamente
    Então Meu plano mostra Ilimitado e os limites correspondentes após reabrir

  @p0 @APP-S03
  Cenário: Downgrade fica agendado para o fim do período pago
    Dado que ASSINANTE tem plano Ilimitado e uso compatível com Organizador
    Quando seleciona Organizador em Trocar plano e confirma
    Então vejo a mudança agendada para o fim do período atual informado pela API
    E Ilimitado continua como plano vigente até essa data
    Quando reabre Trocar plano
    Então a mudança agendada permanece e não existe cobrança de upgrade por essa operação

  @p0 @APP-S04
  Cenário: Uso acima do limite impede reduzir o plano
    Dado que ASSINANTE usa mais grupos ou membros que o plano de destino permite
    Quando tenta confirmar esse plano em Trocar plano
    Então vejo a recusa por uso acima do limite
    E o plano vigente permanece sem mudança agendada após reabrir
    E nenhum grupo ou membro é removido automaticamente

  @p0 @APP-S05
  Cenário: Cancelar a assinatura interrompe renovação e informa o acesso restante
    Dado que ASSINANTE abriu Gerenciar em Meu plano
    Quando abre Cancelar assinatura e desiste na confirmação
    Então a assinatura permanece ativa
    Quando abre novamente e confirma com o provedor sandbox disponível
    Então após recarregar vejo a assinatura cancelada e a data de acesso até o fim do período pago
    E não vejo essa data como uma próxima cobrança
    E o provedor confirma o encerramento das cobranças recorrentes
    E grupos, membros e histórico financeiro não são apagados

  @p1 @APP-S06
  Cenário: Falhas de consulta e cancelamento permitem recuperação sem falso sucesso
    Dado que ASSINANTE está autenticado e a rede está desligada
    Quando abre Meu plano
    Então vejo falha de consulta com opção de tentar novamente
    Quando restauro a rede e tento novamente
    Então vejo a assinatura correta
    Quando abro Cancelar assinatura e desligo a rede antes de confirmar
    Então vejo falha de cancelamento e não vejo a assinatura como cancelada
    Quando restauro a rede e reabro Meu plano
    Então a assinatura permanece ativa
