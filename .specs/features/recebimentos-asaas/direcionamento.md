# Direcionamento financeiro — recebimentos pelo Saqz

Data: 12/09/2026. Status: direcionamento de produto para o piloto, com decisões comerciais pendentes.

Este documento orienta produto, desenvolvimento e comunicação. Não habilita cobranças,
não contrata serviços e não define taxas comerciais finais. O contrato de acesso após
expiração está em [contexto de recebimentos](context.md).

## 1. Objetivo e decisões confirmadas

Permitir que o gestor receba pagamentos dos atletas pelo aplicativo, acompanhe os
recebimentos e retire os valores disponíveis, usando Asaas no piloto.

- Recebimentos são uma funcionalidade opcional, ativada expressamente pelo gestor.
- Abrir conta no Saqz, criar grupo, iniciar o período gratuito ou visualizar uma
  campanha não cria subconta financeira nem autoriza cobranças.
- A oferta contempla Pix, cartão e recorrência, com split para a eventual comissão
  do Saqz. Valores comerciais e elegibilidade ainda serão definidos.
- As tarifas são explícitas para o gestor, que pode gerar a cobrança já com os
  acréscimos para os membros. Incluir acréscimos é uma escolha expressa, não automática.
- Para o gestor, usar o rótulo único “Taxas de serviço e pagamento” e mostrar seu
  valor agregado. A composição entre processamento Asaas e remuneração Saqz fica
  detalhada nos termos de aceite, acessíveis antes da ativação.
- O cadastro financeiro começa quando o gestor decide habilitar os recebimentos.
- O fim do período gratuito ou da assinatura Saqz nunca condiciona o acesso ao dinheiro
  a uma renovação, nem autoriza descontar a assinatura dos valores do gestor.
- O onboarding geral e campanhas podem apresentar a possibilidade; não são requisitos
  para utilizar o restante do produto nem substituem a ativação financeira.

## 2. Jornada de descoberta e ativação

### Apresentar no momento em que o gestor precisa cobrar

Proposta: manter uma entrada permanente na área financeira e uma apresentação breve,
dispensável, no onboarding do gestor. Priorizar a apresentação contextual ao configurar
o valor de um jogo ou uma mensalidade. O resultado esperado é o primeiro recebimento
real; abrir uma subconta, isoladamente, não comprova que o recurso foi útil.

| Momento | Conteúdo | Próxima ação |
| --- | --- | --- |
| Onboarding geral | Mostrar que é possível receber e acompanhar pagamentos pelo app | Conhecer recebimentos / Agora não |
| Área financeira sem ativação | Explicar benefício, meios disponíveis e necessidade de cadastro | Ver como funciona |
| Configuração de valor do jogo ou mensalidade | Mostrar exemplo do valor pago e do líquido recebido | Configurar recebimentos |
| Cadastro interrompido | Informar a etapa realmente pendente | Continuar cadastro |
| Aprovação concluída | Informar que o gestor está apto a começar | Criar primeira cobrança |

Conteúdo mínimo antes do cadastro: quem processa o pagamento, “Taxas de serviço e
pagamento” aplicáveis, prazo estimado de disponibilidade por meio, condições de saque,
documentos necessários e regra de acesso financeiro após expiração do plano. Oferecer
acesso aos termos de aceite com a composição das taxas, incluindo a remuneração Saqz.
Mostrar progresso real, sem prometer aprovação instantânea ou recebimento garantido.

### Campanhas de descoberta

Proposta para o piloto: apresentação dentro do app para gestores que ainda não ativaram,
com demonstração curta de uma cobrança. E-mail, push ou WhatsApp são canais futuros,
conforme preferências de comunicação e disponibilidade operacional.

- Direcionar a campanha à página explicativa, nunca a uma ativação automática.
- Distinguir quem desconhece o recurso de quem iniciou cadastro, aguarda análise ou já ativou.
- Permitir dispensar a apresentação e limitar sua repetição; frequência será definida no piloto.
- Não enviar campanha de ativação a quem já concluiu o fluxo.
- Separar divulgação promocional de avisos operacionais de cadastro/pagamento.
- Exibir preços, simulações e condições reais. Não anunciar gratuidade de transações
  porque o usuário está no período gratuito do software.

Contraponto: apresentar pagamentos cedo pode aumentar a percepção de utilidade, mas
pedir documentos financeiros no primeiro acesso pode atrapalhar a criação do grupo.
Por isso, apresentar é opcional e ativar é uma jornada separada.

## 3. Cadastro do recebedor

Fluxo proposto:

1. Gestor escolhe ativar e revisa condições e titular recebedor.
2. Saqz coleta os dados necessários e cria ou recupera a subconta correspondente.
3. Gestor conclui verificação de identidade/documentação solicitada.
4. Saqz acompanha pendências e aprovação; permite retomar sem duplicar subconta.
5. Após aprovação e elegibilidade, disponibiliza os meios de pagamento habilitados.
6. Gestor configura a primeira cobrança e a confirma antes de disponibilizá-la ao atleta.

Criar a subconta não equivale à aprovação. O Asaas exige habilitação prévia do modelo
BaaS, conta principal PJ e aprovação cadastral; a integração atual das assinaturas Saqz
não comprova essa habilitação. A jornada deve identificar o Asaas segundo o modelo
acordado com o provedor. [Documentação BaaS](https://docs.asaas.com/docs/cria%C3%A7%C3%A3o-de-subcontas-baas).

Proposta: uma subconta por titular financeiro CPF/CNPJ, reutilizada em grupos desse
mesmo titular. Cadastro duplicado, conta Asaas preexistente e vinculação de pessoa
jurídica precisam de tratamento explícito; possuir um CPF/CNPJ não comprova autorização.

A troca do administrador de um grupo não transfere a titularidade da conta nem o
direito aos saldos anteriores. O recebedor de futuras cobranças deve ser configurado
separadamente, preservando o vínculo financeiro das já emitidas.

## 4. Estados independentes

| Dimensão | Exemplos | Responsabilidade |
| --- | --- | --- |
| Descoberta | Não apresentado, apresentado, dispensado | Comunicação |
| Cadastro financeiro | Não iniciado, incompleto, em análise, aprovado, pendente, recusado | Habilitação junto ao provedor |
| Opção do gestor | Recebimentos ativados ou desativados | Intenção de operar novas cobranças |
| Direito comercial Saqz | Trial/plano elegível, direito encerrado | Recursos comerciais contratados |
| Situação financeira | A receber, disponível, saque pendente, restrição do provedor | Movimentação efetivamente possível |

Os nomes são conceituais; não prescrevem enums ou contratos de API. Um gestor pode ter
plano expirado, recebimentos desativados e saldo disponível ao mesmo tempo.
Não usar um único indicador de assinatura ativa para decidir todas essas dimensões.

## 5. Quem paga as tarifas

### Possibilidade e limites

É possível estruturar preços diferentes conforme o meio/prazo de pagamento: a
Lei 13.455/2017 autoriza essa diferenciação. O comércio eletrônico também exige
informação clara sobre despesas adicionais e condições da oferta. Isso não transforma
qualquer sobretaxa em cobrança automaticamente válida; a comissão própria do Saqz e
seus termos precisam de definição específica.
[Lei 13.455/2017](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2017/lei/l13455.htm),
[Decreto 7.962/2013](https://www.planalto.gov.br/ccivil_03/_ato2011-2014/2013/decreto/d7962.htm).

O Asaas documenta repasse automático de taxas no fluxo de criação de cobranças do
painel quando cartão é o único meio selecionado. Isso não comprova um parâmetro
equivalente para nossa API, Pix ou todas as modalidades de assinatura. Na referência
de criação de cobrança consultada, não identificamos um campo de repasse automático.
O desenho da integração deve validar cada modalidade antes de prometer o recurso.
[Repasse no Asaas](https://central.ajuda.asaas.com/hc/pt-br/articles/31691238010139-As-taxas-cobradas-pelo-Asaas-podem-ser-repassadas-automaticamente-para-meus-clientes-ao-criar-cobran%C3%A7as),
[API de cobrança](https://docs.asaas.com/reference/criar-nova-cobranca).

### Decisão: gestor revisa as tarifas e pode incluir os acréscimos

Antes de gerar a cobrança, mostrar ao gestor o preço base, o valor agregado de
“Taxas de serviço e pagamento”, o total que o membro pagará e o líquido previsto
para o gestor. A tela não exige linhas separadas para processamento Asaas e comissão
Saqz; essa composição fica detalhada nos termos de aceite.
Ele escolhe absorver os custos ou incluir os acréscimos permitidos no total da cobrança.
Registrar quais custos foram incluídos e quais continuam a cargo do gestor.

A composição detalhada pode ser consultada nos termos antes do aceite, não apenas
depois da ativação. Os termos explicam os componentes, as regras de cálculo e as
condições aplicáveis, incluindo cancelamento, reembolso e recorrência. Registrar a
versão aceita pelo gestor. O agrupamento visual não elimina o registro separado dos
custos do provedor e da comissão Saqz para cálculo, conciliação e auditoria.

A cobrança é gerada com os valores revisados: o membro recebe o preço base, os acréscimos e o total
correspondentes ao meio de pagamento, sem acréscimo surpresa no final. O gestor não
precisa calcular percentuais manualmente. Na recorrência, o resumo explicita o valor
por ciclo e depende do aceite do pagador.

Proposta de interface ainda a detalhar: permitir uma preferência por meio de pagamento,
com revisão na emissão. Começar sem divisão parcial customizável, para manter a
configuração e a conciliação simples. A escolha de repassar não ocorre ao abrir a conta.

| Política | Atleta | Gestor | Contraponto |
| --- | --- | --- | --- |
| Gestor absorve | Paga o preço base informado | Recebe o líquido após custos | Facilita entendimento do preço, reduz o líquido |
| Atleta assume processamento | Vê e aceita o total com custo do meio escolhido | Recebe o base, descontada eventual comissão que continue a seu cargo | Preserva receita, pode aumentar abandono ou pagamento por fora |

Definir quais componentes comerciais serão oferecidos para inclusão, inclusive a
eventual comissão Saqz. A possibilidade de gerar a cobrança com acréscimos está
confirmada; valores e componentes finais dependem da política comercial. Tarifa do
provedor, comissão da plataforma, antecipação e saque não são a mesma coisa. Não apresentar receita Saqz
como se fosse tarifa bancária. Custo de antecipação escolhida depois pelo gestor
não deve ser acrescentado retroativamente à cobrança paga pelo atleta.

### Exemplo ilustrativo de processamento

Valores hipotéticos, sem comissão Saqz: preço base R$ 25,00, tarifa de cartão de 3%
mais R$ 0,50. Esses números não são a proposta contratada do Asaas.

| Política | Atleta paga | Processamento | Gestor recebe |
| --- | --- | --- | --- |
| Gestor absorve | R$ 25,00 | R$ 1,25 | R$ 23,75 |
| Atleta assume | R$ 26,29 | R$ 1,29 | R$ 25,00 |

Quando o percentual incide sobre o total cobrado, somar a tarifa calculada apenas
sobre o preço base não preserva o líquido. No exemplo, o cálculo de partida é
`total = (base + tarifa_fixa) / (1 - percentual)`, seguido de validação em centavos
com a regra real de arredondamento do provedor. Com comissão, a fórmula depende de
ela ser fixa ou percentual e de sua base de cálculo; não somar percentuais de bases diferentes.

### Informação e consentimento do pagador

- Informar os totais por meio disponível antes de o atleta assumir o compromisso,
  inclusive na apresentação do jogo/mensalidade quando houver custo obrigatório.
- Ao escolher o meio, exibir base, acréscimos aplicáveis e total final; o botão confirma
  esse valor. Evitar anunciar apenas o valor base e revelar taxa obrigatória no último passo.
- A configuração do gestor não substitui o aceite do atleta para cobrança recorrente.
- Informar total por ciclo, periodicidade, primeiro vencimento e cancelamento.
- Alteração de política de taxas não muda cobranças aceitas/pagas. Para novos ciclos,
  definir aviso e consentimento aplicável antes de alterar o valor autorizado.
- Trocar Pix por cartão antes do pagamento exige nova simulação e confirmação;
  impedir que instrumentos antigos continuem gerando uma segunda cobrança liquidável
  sem tratamento de duplicidade.
- Definir devolução do preço, comissão e tarifa na política de cancelamento, respeitando
  direitos aplicáveis; não presumir que o provedor devolve suas taxas nem declarar
  genericamente todos os acréscimos como não reembolsáveis.

## 6. Cobrança, recorrência e split

Proposta: criar a cobrança na subconta do titular que recebe pelo jogo/mensalidade.
O split destina a comissão acordada ao Saqz; a parcela restante pertence ao gestor.
Não usar cobrança em conta principal seguida de repasse manual como substituto implícito.

O percentual de split do Asaas incide no valor líquido (`netValue`), depois das
tarifas. Se a comissão comercial usar outra base, a integração precisa convertê-la
explicitamente. O total do split não pode superar o líquido disponível.
[Regras de split](https://docs.asaas.com/docs/split-de-pagamentos).

Manter conceitos diferentes de assinatura do gestor no Saqz e mensalidade recorrente
do atleta. Nunca reutilizar a mesma identidade para cancelar ambas.

Pix comum pode ser emitido periodicamente, mas exige pagamento a cada ciclo.
Pix Automático envolve autorização bancária e elegibilidade própria; no Asaas,
os requisitos incluem conta PJ aprovada e CNPJ ativo há pelo menos seis meses.
Não prometer débito automático Pix a todo gestor CPF. O escopo detalhado dessa
modalidade deve ser validado à parte. [FAQ Pix Automático](https://docs.asaas.com/docs/faq-2).

O primeiro uso não cobra automaticamente pendências antigas. O gestor revisa quais
cobranças existentes devem receber um instrumento de pagamento; não duplicar débitos.

## 7. Acesso após período gratuito e expiração

Regra confirmada: expiração comercial não bloqueia acesso ou retirada de dinheiro.

| Operação | Tratamento após expiração |
| --- | --- |
| Saldo, recebíveis, extrato e comprovantes | Preservar acesso do titular |
| Saque e acompanhamento de transferências | Preservar, conforme saldo e regras do provedor |
| Cadastro necessário ao saque, devoluções e contestações | Preservar ações aplicáveis |
| Cancelar cobrança ou recorrência existente | Preservar |
| Pagamentos tardios e liquidação de cartão | Continuar recebimento e conciliação |
| Área do atleta com suas cobranças e comprovantes | Preservar |
| Novas cobranças/recorrências | Pendente; proposta: exigir elegibilidade comercial |
| Próximos ciclos de recorrências existentes | Pendente; proposta: manter com condições aceitas |

Manter recorrências antigas reduz interrupção do grupo, mas mantém custo operacional
para um gestor sem plano. Se a decisão for interrompê-las, é necessário definir data
de corte, avisos, cobranças já emitidas, retentativas e comandos no provedor; parar
apenas um job local ou ocultar botões não basta.

Não exigir assinatura para acessar recebimentos por novo login ou link direto.
Não encerrar subconta, apagar credenciais, apropriar saldo ou transferir titularidade
em resposta à expiração. Não prometer saque imediato de valores ainda a receber:
o Asaas permite transferir o saldo disponível. [Transferências](https://docs.asaas.com/docs/transferencias).

Desativação voluntária de novas cobranças, exclusão do grupo, troca de gestor e
encerramento da conta Saqz são eventos distintos. Preservar tratamento de saldos,
recebíveis e obrigações existentes e acordar caminho de saída com o Asaas caso o
próprio serviço BaaS do Saqz seja encerrado.

## 8. Orientação técnica

- Separar integrações financeiras dos bloqueios comerciais de trial/assinatura.
- Criar permissões por ação e titularidade: leitura, saque e cancelamento não podem
  depender genericamente de `entitled` ou `readOnly` do plano.
- Credenciais de subconta ficam protegidas no backend. Autenticar e deduplicar eventos
  do provedor; não confiar em retorno de checkout ou comprovante enviado como baixa automática.
- Registrar por cobrança: titular, grupo, atleta, base, total, meio, política de taxas,
  versão da tabela, aceite aplicável, tarifa estimada/efetiva, comissão, líquido e IDs externos.
- Preservar histórico da política usada; não recalcular transações antigas com a tabela atual.
- Conciliar bruto, tarifas, split e saldo. Confirmado, recebido, disponível e transferido
  são estados diferentes; uma transferência solicitada ainda pode falhar.
- Não interromper webhooks e recuperação de eventos porque o plano expirou.
- Proteger criação de subconta, emissão, saque e devolução contra repetição de requisição,
  falhas após resposta do provedor e cliques simultâneos.
- Calcular valores em centavos/decimal exato, com regra de arredondamento explícita.
- Recuperar o estado do provedor quando necessário; indisponibilidade não equivale a saldo zero.
- Automatização de baixa não altera por si só regras de vaga, presença ou prioridade.

## 9. Critérios para liberar o piloto

1. Onboarding/campanha não cria subconta nem emite cobrança sem ação explícita.
2. Cadastro interrompido pode ser retomado; repetição não cria outra subconta.
3. Recebimentos só aparecem habilitados após aprovação e elegibilidade necessárias.
4. Atleta vê o total correto antes de confirmar e antes de autorizar recorrência.
5. Gestor revisa base, valor agregado de “Taxas de serviço e pagamento”, total e
   líquido previsto antes da emissão. Os termos com a composição entre processamento
   Asaas e remuneração Saqz estão acessíveis antes do aceite, cuja versão é registrada.
   Incluir acréscimos exige sua escolha expressa. A cobrança gerada reproduz os valores
   revisados, e a política de tarifas não muda transações passadas.
6. Cobrança é conciliada uma vez, inclusive com webhook repetido ou fora de ordem.
7. Com plano/trial expirado, titular autorizado consulta e saca saldo disponível,
   acompanha liquidação posterior, cancela recorrência e trata devolução elegível.
8. Usuário sem autorização, inclusive novo administrador do grupo, não movimenta saldo alheio.
9. Saque e estorno repetidos não duplicam movimentação; falhas permitem recuperação.
10. Recursos podem parar de aceitar novas operações no piloto sem parar a manutenção
    de dinheiro e obrigações existentes.

Validar em sandbox e liberar para gestores selecionados após habilitação comercial.
Não usar só a existência de subconta como critério de sucesso.

## 10. Métricas e decisões restantes

Medir por canal de descoberta: gestores expostos, início/conclusão do cadastro,
aprovação, primeira cobrança paga, tempo até primeiro recebimento e primeiro saque
bem-sucedido. Acompanhar abandono após exibir taxas, falhas de pagamento, dúvidas de
saque, devoluções e custo operacional por gestor ativo. Não enviar dados bancários,
documentos ou detalhes de cartões às ferramentas de campanhas/analytics.

Decisões comerciais que permanecem abertas:

- Em quais planos e condições de trial se pode ativar e criar novas cobranças?
- Quais componentes podem integrar os acréscimos escolhidos pelo gestor, incluindo
  eventual comissão Saqz? Como salvar a preferência por meio de pagamento e revisá-la
  em cada emissão? A possibilidade de incluir acréscimos já está confirmada.
- Qual o valor/base da comissão e quem arca com subconta, saque e eventuais perdas?
- Recorrências existentes continuam após o término do direito comercial?
- Política de cancelamento, devolução das taxas e mudança de preço em novos ciclos.
- Pix Automático entra no piloto ou inicialmente oferecemos cartão recorrente e Pix
  com pagamento pelo atleta a cada vencimento?
- Frequência das campanhas, disponibilidade de canais e métricas de sucesso do piloto.

Os custos e condições finais devem vir do contrato da operação BaaS e das subcontas;
não fixar em código ou material promocional a tabela pública de uma conta comum.

## Referência de produto

Aplicação do princípio de medir ativação real: acompanhar o primeiro recebimento
útil, além da conclusão do onboarding, inspirado em
[Measure activation, not signups — Richard](https://x.com/richardrx/status/2059616501544468624).
Não foram adotadas metas numéricas de mercado como promessa do Saqz.
