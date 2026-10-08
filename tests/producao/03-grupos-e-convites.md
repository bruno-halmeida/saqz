# Grupos, convites e membros

Elenco e regras em [README.md](README.md). Os textos entre aspas são os da tela.

## Grupos

### PROD-GR01 · Criar o primeiro grupo com o teste grátis · P0

**Quem:** ORG, sem grupo. **Deixa pronto:** o grupo `QA Vôlei <data>` e o teste grátis (PROD-AS01).

1. Na Início, tocar "Criar meu grupo".
2. Na tela "EXPERIMENTE O ORGANIZADOR" ("14 dias grátis", "O prazo começa ao criar seu primeiro grupo."),
   tocar "Entendi, quero experimentar".
3. Em "Criar grupo", preencher:
   - "Nome do grupo": `QA Vôlei <data>`;
   - "Esporte": Vôlei de quadra;
   - "Público": Misto.
4. Abrir "Outras configurações (opcional)" e preencher:
   - "Limite de jogadores por jogo": 12;
   - "Duração do jogo": 2h;
   - "Confirmar presença até": 6h antes;
   - "Nome da quadra" e "Endereço da quadra": um endereço público real (o mapa usa).
5. Avançar para a revisão, conferir os dados e tocar "Criar grupo".

**Conferir**
- Abre o grupo com "Grupo criado!" e "Marcar primeiro jogo".
- A aba Grupos mostra um único `QA Vôlei <data>`, com o chip "Administrador".

### PROD-GR02 · Detalhe do grupo para quem organiza · P1

**Quem:** ORG, no grupo QA.

1. Rolar o grupo de cima a baixo.
2. Em "Onde a gente joga" (ou no cartão do próximo jogo), tocar "Ver no mapa".

**Conferir**
- "Gestão" tem "Membros e permissões", "Jogos e horários", "Convidar por link" e "Caixa do grupo",
  esta com "Saldo R$ 0,00".
- O mapa abre no endereço da quadra.

### PROD-GR03 · Editar o grupo: Pix e regras das vagas · P0

**Quem:** ORG. Este cenário também aplica o contorno do PC-01 antes dos testes de presença.

1. No grupo, tocar "Editar".
2. Em "Pix do grupo", preencher "Chave Pix" com uma chave de teste e "O que é" com `QA Pix`.
3. Em "Como preencher as vagas", desligar "Prioridade de mensalista".
4. Tocar "Salvar alterações".

**Conferir**
- Volta ao grupo; ao reabrir "Editar", a chave, o rótulo e a prioridade desligada estão salvos.

### PROD-GR04 · Lista "Deixe o grupo redondo" · P1

**Quem:** ORG, com o grupo QA já com um jogo e mais de um membro (depois de PROD-JG01 e PROD-CV02).

1. Abrir o grupo e tocar em cada item da lista "Deixe o grupo redondo".

**Conferir**
- Cada item leva à tela da ação:
  - "Mensalistas e mensalidade" → Membros;
  - "Pix do grupo" e "Regras da lista de espera" → Editar grupo;
  - "Repetir toda semana" → Jogos e horários.
- Itens já feitos aparecem como feitos (o Pix de PROD-GR03, por exemplo).
- A lista some quando tudo está feito.

### PROD-GR05 · Horário fixo em "Jogos e horários" · P1

**Quem:** ORG, com a quadra do grupo cadastrada (PROD-GR01).

1. Gestão → "Jogos e horários".
2. Ligar "Repetir toda semana", tocar "Adicionar dia e horário" e escolher um dia diferente do de
   PROD-JG08.
3. Tocar "Salvar alterações".

**Conferir**
- Em "Próximos jogos", ORG vê os jogos novos com "Rascunho" e "Só você vê até publicar".
- ATL não vê os rascunhos.

### PROD-GR06 · Excluir o grupo · P0 (limpeza)

**Quem:** ORG, no fim da rodada.

1. No grupo, tocar "Editar" e depois o ícone de lixeira ("Excluir grupo").
2. Confirmar "Excluir grupo".

**Conferir**
- O grupo some para ORG. Depois de atualizar, some também para ATL e ATL2.
- O link de convite do grupo passa a dar "Convite inválido".

## Convites e entrada

### PROD-CV01 · Criar e compartilhar o link de convite · P0

**Quem:** ORG. **Deixa pronto:** o link do convite, enviado para o aparelho B (WhatsApp de teste ou nota).

1. Gestão → "Convidar por link" → "Criar novo link".
2. Tocar "Copiar link" e colar numa nota.
3. Tocar "Compartilhar no WhatsApp" e cancelar o envio na folha do sistema.
4. Em "Compartilhar convite", abrir "Mostrar QR Code" e ler o código com o outro aparelho.

**Conferir**
- Aparece "Link não expira" e um endereço `https://links.saqz.app/?saqz_invite=…`; o toast diz "Link copiado.".
- O link colado, o compartilhado e o do QR são o mesmo.
- O QR diz "O convite vale por 7 dias", mas o link não expira: é o PC-07, não reprova.

### PROD-CV02 · Entrar pelo convite sem conta, com o app instalado · P0

**Quem:** ATL, aparelho B, app instalado e deslogado. **Deixa pronto:** a conta ATL no grupo QA.

1. Tocar no link do convite (no WhatsApp ou na nota).
2. O app abre na tela de entrada; tocar "Criar conta ›".
3. Conferir que a tela mostra "Entrando no QA Vôlei…" e o convite de QA Org.
4. Criar a conta com o e-mail QA do atleta (nome, celular, e-mail e senha) e tocar "Criar conta".
5. Em "Cadastro de atleta", preencher "Seu apelido neste grupo", "Posição" e "Nível" e tocar
   "Salvar meu perfil".

**Conferir**
- Termina dentro do grupo QA, e não na Início genérica.
- ORG vê ATL em Membros e permissões.

### PROD-CV03 · Convite sem o app: loja e volta ao convite · P0

**Quem:** pessoa sem o app (desinstalar no aparelho B), usando um link de convite válido.

1. Tocar no link do convite, de preferência dentro do WhatsApp, que é por onde o atleta chega.
2. **Android:**
   - a página vai sozinha ao Google Play (ou abre o app, se ele estiver instalado);
   - antes de instalar, conferir em "Sobre este app" → "Versão" que é a mais nova (em 08/10 o Play
     entregou a um aparelho a versão anterior, que ainda não lia o convite);
   - instalar e abrir o app.
3. **iPhone:**
   - enquanto a Apple não aprovar, a página diz "O app para iPhone chega em breve na App Store.";
   - depois da aprovação, ela vai sozinha à App Store.

**Conferir**
- Nenhum clique é preciso para chegar à loja.
- Com o app 0.0.4 (6) ou mais novo no Android, a primeira abertura já vai ao login com o convite: a tela
  de cadastro mostra "Entrando no …". Com versões anteriores é preciso tocar no link de novo.

### PROD-CV04 · Entrar colando o link em "Tenho um convite" · P0

**Quem:** ATL2, conta nova do Google sem grupo (logo depois de PROD-AC04).

1. Na Início, tocar "Tenho um convite".
2. Colar o link em "Link do convite" e tocar "Entrar no grupo".
3. Em "Cadastro de atleta", tocar "Salvar meu perfil".

**Conferir**
- ATL2 entra no grupo QA, e ORG o vê em Membros e permissões.

### PROD-CV05 · Conta logada toca o link e entra direto · P1

**Quem:** ATL2, depois de removido em PROD-MB05, logado no app.

1. Tocar no link do convite.

**Conferir**
- O app abre e entra no grupo sem outra confirmação; aparece "Cadastro de atleta" e, depois de
  "Salvar meu perfil", o grupo.

### PROD-CV06 · Grupo com aprovação: pedido e aprovação · P1

**Quem:** ORG, mais a conta WEB (PROD-WEB02), que ainda não é do grupo.

1. ORG: em "Convidar por link", ligar "Aprovar pedidos para entrar".
2. WEB: logado no app, tocar no link do convite.
3. ORG: em "Pedidos para entrar", tocar "Aprovar".
4. WEB: reabrir a aba Grupos.
5. ORG: desligar "Aprovar pedidos para entrar".

**Conferir**
- WEB vê "Pedido enviado" e fica sem acesso até a aprovação.
- Depois de aprovado, o grupo aparece em Grupos. Ninguém é avisado e não aparece "Cadastro de atleta":
  é o PC-11, não reprova.

### PROD-CV07 · Desativar o link · P1 (limpeza)

**Quem:** ORG.

1. Em "Convidar por link", tocar "Desativar link".
2. Uma conta de fora do grupo toca no link antigo.

**Conferir**
- A tela volta a oferecer "Criar novo link".
- O link antigo dá "Convite inválido", e quem já é membro continua no grupo.

## Membros

### PROD-MB01 · Lista de membros: busca e filtros · P1

**Quem:** ORG, com ATL e ATL2 no grupo.

1. Gestão → "Membros e permissões".
2. Usar "Buscar membro" com o apelido de ATL, limpar e filtrar "Administradores".
3. Tocar em ATL → "Ver perfil".

**Conferir**
- A busca acha só ATL; o filtro mostra só ORG.
- O perfil aberto é o de ATL.

### PROD-MB02 · Tornar mensalista com valor e vencimento · P0

**Quem:** ORG. **Deixa pronto:** ATL mensalista para PROD-FI01.

1. Membros e permissões → ATL → "Editar jogador".
2. Tocar "Tornar mensalista", preencher "Valor por mês" com R$ 10,00 e escolher em "Dia do vencimento"
   um dia que já passou no mês (5, 10, 15 ou 20).
3. Tocar "Confirmar mensalidade".

**Conferir**
- O cartão mostra "R$ 10,00 / mês · vence dia N", e o valor continua lá ao reabrir o editor.
- A lista de membros só mostra "Mensalista" depois de sair e entrar de novo: é o PC-10.

### PROD-MB03 · Promover e rebaixar administrador · P1

**Quem:** ORG **com plano pago** (depois de PROD-AS02 ou PROD-AS03). Durante o teste grátis a promoção é
bloqueada: é o PC-09.

1. Membros e permissões → ATL → "Tornar administrador".
2. ATL reabre o grupo.
3. ORG: ATL → "Retirar acesso de administrador".

**Conferir**
- Como admin, ATL vê a seção "Gestão" no grupo; depois de rebaixado, deixa de ver.

### PROD-MB04 · Atleta sai do grupo · P0

**Quem:** ATL2, no fim da rodada.

1. No grupo, tocar "Sair do grupo" e confirmar "Sair do grupo".

**Conferir**
- Volta à aba Grupos sem o grupo QA, e ORG deixa de ver ATL2 em Membros.
- ORG não tem a opção "Sair do grupo" no próprio grupo.

### PROD-MB05 · Organizador remove um atleta · P1

**Quem:** ORG, removendo ATL2 (que volta em PROD-CV05).

1. Membros e permissões → ATL2 → "Remover do grupo".

**Conferir**
- ATL2 some da lista e, depois de atualizar, perde o acesso ao grupo.
- A remoção acontece sem pedir confirmação: é o PC-15.
