# Financeiro do grupo

Elenco e regras em [README.md](README.md). Os textos entre aspas são os da tela. O financeiro do grupo é
só registro: nada aqui move dinheiro. O caixa fica em Gestão → "Caixa do grupo"; a aba "Financeiro"
("Caixa geral") só aparece para quem organiza.

## PROD-FI01 · Mensalidade automática no dia seguinte · P0

**Quem:** ATL, mensalista desde PROD-MB02 (R$ 10,00, com dia de vencimento já passado no mês); ORG confere.
**Quando:** no dia seguinte, depois das 03:10 (São Paulo).

1. ATL abre a Início.
2. ORG abre Gestão → "Caixa do grupo".

**Conferir**
- ATL vê "Minhas cobranças" com R$ 10,00, "Venceu em dd/mm" e o Pix do grupo, além da faixa "Você tem
  R$ 10,00 em aberto". Ver também PROD-IN04.
- ORG vê ATL em "Quem ainda não pagou", e "Mensalidades do mês" conta a cobrança.
- Existe uma cobrança só para ATL no mês, e ninguém recebe aviso pela geração.

## PROD-FI02 · Criar as cobranças do mês à mão · P0

**Quem:** ORG.

1. No caixa, tocar "Criar cobranças do mês".
2. Preencher:
   - "Mês": tocar "Próximo mês" até chegar ao mês **seguinte**, para não bater com PROD-FI01 (o seletor
     tem "Mês anterior" e "Próximo mês", nada de data digitada);
   - "Vencimento": tocar no campo, escolher na roda um dia desse mês e tocar "Salvar vencimento"
     (trocar o mês leva o vencimento ao dia do grupo, ou ao último dia do mês quando o dia não existe);
   - "Valor por pessoa (R$)": 10,00.
3. Em "Selecione os mensalistas ativos", ligar só ATL.
4. Tocar "Conferir cobranças", conferir o resumo e tocar "Criar cobranças".

**Conferir**
- Abrir a tela e conferir não cria nada; a cobrança só nasce no "Criar cobranças".
- O resumo diz "Mês <mês> · Vencimento <dd/mm/aaaa>" com o mês e o dia escolhidos.
- Volta ao caixa, e ATL vê a cobrança nova em Perfil → "Minhas mensalidades".
- Repetir a geração para o mesmo mês não cria uma segunda cobrança.

## PROD-FI03 · Registrar o recebimento · P0

**Quem:** ORG, com uma cobrança de ATL em aberto.

1. No caixa, em "Quem ainda não pagou", tocar "Recebi" na cobrança de ATL.
2. Em "Confirmar recebimento", conferir o valor, escolher Pix em "Como recebeu" e tocar
   "Recebi".

**Conferir**
- A cobrança sai de "Quem ainda não pagou", e o "SALDO DO GRUPO" sobe R$ 10,00.
- ATL vê a cobrança como paga em "Minhas mensalidades". Se era a única vencida, "Minhas cobranças"
  some da Início.
- O recebimento não pode ser desfeito pelo app: registrar só o que for de teste.

## PROD-FI04 · Lançar uma despesa · P1

**Quem:** ORG.

1. No caixa, tocar "Registrar".
2. Em "Novo lançamento": "Tipo" Saída, "Valor" R$ 80,00, "Descrição" `QA Bolas`, "Categoria" Material e
   a data de hoje.
3. Tocar "Salvar lançamento".

**Conferir**
- O lançamento aparece no extrato, e o saldo cai R$ 80,00.

## PROD-FI05 · Extrato e filtros · P1

**Quem:** ORG, depois de PROD-FI03 e PROD-FI04 (e PROD-JG12, se feito).

1. No caixa, tocar "Ver extrato completo".
2. Alternar os filtros "Tudo", "Entradas" e "Saídas".

**Conferir**
- "Entradas" mostra o recebimento de R$ 10,00; "Saídas" mostra `QA Bolas` (e o aluguel da quadra, se houver).
- "Tudo" mostra os dois, e o saldo do cabeçalho bate com o caixa.
- O extrato mostra só o mês atual: não há seletor de mês (PC-13).

## PROD-FI06 · Cobrar quem está devendo · P1

**Quem:** ORG, com uma cobrança de ATL em aberto; ATL confere no aparelho B.

1. No caixa, tocar "Cobrar" na cobrança de ATL.
2. Na folha, deixar só ATL selecionado e tocar "Enviar (1)".

**Conferir**
- A folha mostra "Avisos enviados: 1".
- ATL recebe o push "Você recebeu um lembrete de cobrança. Abra o app para conferir." e, na central, "Você
  tem uma cobrança de R$ 10,00 em aberto.". Tocar abre o grupo.

## PROD-FI07 · Caixa geral na aba Financeiro · P1

**Quem:** ORG.

1. Abrir a aba "Financeiro".
2. Alternar os períodos (mês atual, mês anterior, ano) e tocar no grupo QA em "Seus grupos".

**Conferir**
- "SALDO DOS SEUS GRUPOS" e "Entrou / Saiu / A receber" batem com o caixa do grupo no mesmo período.
- O grupo leva ao caixa dele, e "Últimos lançamentos" mostra os movimentos de teste.

## PROD-FI08 · Atleta vê só as próprias cobranças · P0

**Quem:** ATL2, que não é mensalista.

1. Abrir Perfil → "Minhas mensalidades" e o grupo QA.

**Conferir**
- ATL2 não vê as cobranças de ATL, nem "Caixa do grupo", nem a aba "Financeiro".

## PROD-FI09 · Saldo inicial do caixa · P0

_Só depois da entrega da DEB-148 (backend e app novos)._

**Quem:** ORG, num grupo QA novo, sem nada lançado.

1. Abrir Gestão → "Caixa do grupo".
2. Em "Quanto o grupo já tem em caixa?", digitar 250,00 e tocar "Salvar saldo inicial".
3. Abrir o extrato, tocar "Editar" no saldo inicial e trocar para 300,00.
4. No caixa, abrir o ⋯ e conferir os itens.

**Conferir**
- Depois do passo 2, a pergunta some e o saldo do caixa mostra R$ 250,00 ("Inclui o saldo inicial de R$ 250").
- O extrato mostra "Saldo inicial · Antes do Saqz · R$ 250" fixo no topo; depois do passo 3, R$ 300 e o saldo sobe R$ 50.
- O ⋯ do caixa tem "Saldo inicial" e "Criar cobranças do mês". O caixa não mostra mais o cartão do Pix.
- A aba "Financeiro" (caixa geral) soma o saldo inicial no saldo do grupo.
- Num segundo grupo QA, "Começar do zero" fecha a pergunta e o saldo fica R$ 0,00.

## PROD-FI10 · Entrada e saída em telas separadas · P1

_Só depois da entrega da DEB-148. Substitui o caminho do "Registrar" de PROD-FI04._

**Quem:** ORG.

1. No caixa, tocar "Saída": "Valor" R$ 80 pelo atalho, "Descrição" `QA Bolas`, "Categoria" Material; tocar na data, escolher
   ontem no calendário e tocar "Salvar saída".
2. Tocar "Entrada": "Valor" 50,00, "Descrição" `QA Patrocínio`, "Categoria" Patrocínio; tocar "Salvar entrada".
3. Abrir o extrato e alternar "Tudo", "Entradas" e "Saídas".

**Conferir**
- A Saída só oferece Quadra, Material, Árbitro e Outros, e a data veio com hoje antes da troca.
- A Entrada só oferece Patrocínio, Venda e Outros, sem os atalhos de valor.
- `QA Bolas` aparece em ontem, com o valor em vermelho; `QA Patrocínio` em hoje, em verde.
- No placar do caixa, "Saiu" sobe R$ 80 (em vermelho) e "Entrou" sobe R$ 50; tocar em cada número troca a lista de baixo.

## PROD-FI11 · Quem jogou sem cobrança · P0

_Só depois da entrega da DEB-146 e da DEB-147._

**Quem:** ORG; ATL e ATL2 como avulsos.

**Preparação:** um jogo QA marcado como "Sem diária", com ATL e ATL2 confirmados. Depois, ORG edita o jogo para "Cobrar
diária" de R$ 10,00.

1. ORG abre o acerto do jogo: a faixa "2 jogaram sem cobrança" aparece; tocar "Revisar".
2. Deixar ATL em "Cobrar" e trocar ATL2 para "Já pagou", com o meio trocado para Dinheiro.
3. Conferir o resumo e tocar "Confirmar".
4. ATL abre a Início.

**Conferir**
- A revisão veio com o valor do jogo (R$ 10,00) e os dois em Cobrar.
- O resumo diz "1 cobrança · 1 já pagou · 0 sem cobrança" antes de confirmar.
- Depois: ATL tem a diária de R$ 10,00 pendente, vencendo hoje; ATL2 aparece como recebido em dinheiro, e o caixa mostra a
  entrada na data do jogo; a faixa some do acerto e do caixa.
- Nem ATL nem ATL2 recebem push ou WhatsApp pela revisão; só depois, se ORG tocar "Cobrar".

## PROD-FI12 · Pix do grupo na página do grupo · P1

_Só depois da entrega da DEB-148 (app novo)._

**Quem:** ATL2, sem cobrança no grupo QA; ATL, com uma cobrança em aberto; ORG.

1. ATL2 abre a página do grupo e toca "Copiar" em "Pix do grupo".
2. ATL abre a página do grupo.
3. ORG abre a página do grupo.

**Conferir**
- ATL2 vê a seção "Pix do grupo" depois de "Minhas cobranças", com a chave e o tipo ("Chave Pix · E-mail"); o "Copiar" copia a
  chave.
- ATL vê a chave só no cartão da cobrança ("Copiar chave Pix"), sem a seção repetida.
- ORG, sem cobrança própria, vê a seção depois da "Galera".

