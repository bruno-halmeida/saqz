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
   - "Mês (AAAA-MM)": o mês **seguinte**, para não bater com PROD-FI01;
   - "Vencimento (DD/MM/AAAA)": um dia desse mês;
   - "Valor por pessoa (R$)": 10,00.
3. Em "Selecione os mensalistas ativos", ligar só ATL.
4. Tocar "Conferir cobranças", conferir o resumo e tocar "Criar cobranças".

**Conferir**
- Abrir a tela e conferir não cria nada; a cobrança só nasce no "Criar cobranças".
- Volta ao caixa, e ATL vê a cobrança nova em Perfil → "Minhas mensalidades".
- Repetir a geração para o mesmo mês não cria uma segunda cobrança.

## PROD-FI03 · Registrar o recebimento · P0

**Quem:** ORG, com uma cobrança de ATL em aberto.

1. No caixa, em "Quem ainda não pagou", tocar "Recebi" na cobrança de ATL.
2. Em "Confirmar recebimento", conferir o valor, escolher Pix em "Como recebeu" e tocar
   "Confirmar recebimento".

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
