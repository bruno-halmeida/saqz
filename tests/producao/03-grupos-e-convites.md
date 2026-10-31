# Grupos, convites e membros

Elenco e regras em [README.md](README.md). Os textos entre aspas são os da tela.

## Grupos

### PROD-GR01 · Criar o primeiro grupo com o teste grátis · P0

**Quem:** ORG, sem grupo. **Deixa pronto:** o grupo `QA Vôlei <data>` e o teste grátis (PROD-AS01).

1. Na Início, tocar "Criar meu grupo". Não há tela de oferta antes: abre direto "Criar grupo", com a nota
   "Teste o Organizador com até 3 grupos e atletas ilimitados. O prazo começa no primeiro grupo…".
2. Preencher:
   - "Nome do grupo": `QA Vôlei <data>`;
   - "Esporte": Vôlei de quadra;
   - "Público": Misto.
3. Tocar "Outras configurações (opcional)" e preencher:
   - "Limite de jogadores por jogo": 12;
   - "Duração do jogo": 2h;
   - "Confirmar presença até": 6h antes;
   - "Nome da quadra" e "Endereço da quadra (opcional)": um endereço público real (o mapa usa); as
     sugestões vêm do Google ("powered by Google").
4. Tocar "Criar grupo". Não há tela de revisão: o grupo é criado nesse toque.

**Conferir**
- Abre o grupo com "Grupo criado!", "Seus 14 dias grátis começaram. Vamos marcar o primeiro jogo?…" e
  "Marcar primeiro jogo".
- A aba Grupos mostra um único `QA Vôlei <data>`, com a etiqueta "Admin" no canto de cima da foto (a partir da lista nova, GER-003; antes, o chip "Administrador").

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

1. No grupo, tocar "Editar". "Editar grupo" é uma lista só, sem revisão e sem "Outras configurações" separada:
   "Como preencher as vagas", "Pix do grupo" e o resto estão na mesma rolagem.
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
- Em "Próximos jogos", aparece já publicado o próximo jogo do novo horário; os seguintes da série não
  aparecem para ninguém: cada um é liberado sozinho 4 dias antes (rotina de hora em hora), em nome do dono
  do grupo, com o aviso de jogo liberado.
- Os rascunhos da série são invisíveis, também para ORG (desenho de 09/10): não reprova a agenda futura
  não aparecer.

### PROD-GR06 · Excluir o grupo · P0 (limpeza)

**Quem:** ORG, no fim da rodada.

1. No grupo, tocar "Editar" e depois o ícone de lixeira ("Excluir grupo").
2. Confirmar "Excluir grupo".

**Conferir**
- O grupo some para ORG. Depois de atualizar, some também para ATL e ATL2.
- O link de convite do grupo passa a dar "Este link foi substituído" (“Peça um novo ao organizador.”).

## Convites e entrada

### PROD-CV01 · Ver e compartilhar o link de convite · P0

**Quem:** ORG, ou qualquer admin. **Deixa pronto:** o link do convite, enviado para o aparelho B (WhatsApp de teste ou nota).

1. Gestão → "Convidar por link". O convite já nasce criado: não há "Criar novo link".
2. Conferir o "Código do grupo" (8 caracteres, tipo `44DF-8SA8`) e tocar "Copiar código".
3. Tocar "Copiar link" e colar numa nota.
4. Tocar "Compartilhar no WhatsApp", conferir a mensagem (ela traz "Já tem o app? Use o código … em
   “Tenho um convite”.") e voltar sem enviar ("Voltar ao convite").
5. Tocar "Compartilhar convite" → "Mostrar QR Code" e ler o código com o outro aparelho.

**Conferir**
- Aparecem o código, "Link do convite" com "Link não expira" e um endereço
  `https://links.saqz.app/?saqz_invite=…`; os toasts dizem "Código copiado." e "Link copiado.".
- Outro admin do grupo vê o mesmo código e o mesmo link.
- O link colado, o compartilhado e o do QR são o mesmo; o QR diz "O convite vale até você trocar o código".

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
- O convite guardado some depois de usado: sair da conta ou pedir entrada também o apaga, então uma conta
  que entrar depois no mesmo aparelho não vê "Entrando no QA Vôlei…" (ver PROD-AC08).

### PROD-CV03 · Convite sem o app: loja e volta ao convite · P0

**Quem:** pessoa sem o app (desinstalar no aparelho B), usando um link de convite válido.

1. Tocar no link do convite, de preferência dentro do WhatsApp, que é por onde o atleta chega.
2. **Android:**
   - a página vai sozinha ao Google Play (ou abre o app, se ele estiver instalado);
   - antes de instalar, conferir em "Sobre este app" → "Versão" que é a mais nova (em 08/10 o Play
     entregou a um aparelho a versão anterior, que ainda não lia o convite);
   - instalar e abrir o app.
3. **iPhone:**
   - sem o app, a página vai sozinha à App Store (apps.apple.com/br/app/id6812743525), sem passar pelo web app;
   - com o app instalado, o link universal abre o app direto.

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
2. WEB: logado no app, tocar no link do convite e em "Pedir pra entrar".
3. WEB: preencher o "Cadastro de atleta" ("Pedido enviado para o QA Vôlei…") e tocar "Salvar meu perfil".
4. ORG: tocar no aviso na central (ou Gestão → "Convidar por link") e, em "Pedidos para entrar", tocar "Aprovar".
5. WEB: reabrir a aba Grupos.
6. ORG: desligar "Aprovar pedidos para entrar".

**Conferir**
- Pedir entrada abre o "Cadastro de atleta" antes da aprovação, e depois a Início de WEB diz "Pedido enviado ·
  aguardando o organizador". WEB fica sem acesso ao grupo até a aprovação.
- ORG recebe o aviso "<Nome> pediu para entrar no <grupo>." (central e push), um por pedido; tocar abre
  "Convidar", em "Pedidos para entrar".
- Os dados do cadastro entram com WEB na aprovação, sem novo "Cadastro de atleta"; o grupo aparece em Grupos.
  Ninguém é avisado da aprovação (decisão do dono: nenhum texto promete aviso individual).
- Pedir entrada apaga o convite guardado no aparelho de WEB.

### PROD-CV07 · Trocar o código do convite · P1 (limpeza)

**Quem:** ORG.

1. Em "Convidar por link", anotar o código e tocar "Trocar código".
2. Em "Trocar o código?", tocar "Trocar código".
3. Uma conta de fora do grupo toca no link antigo.

**Conferir**
- A tela mostra um código novo e um link novo; o código e o link antigos deixam de funcionar.
- O link antigo dá "Este link foi substituído" (“Peça um novo ao organizador.”), e quem já é membro continua no grupo.
- Não existe "Desativar link": o convite fica sempre ativo, e a troca do código é o jeito de derrubar o
  que já foi enviado.

### PROD-CV08 · Cadastrar mensalista e enviar o convite pelo WhatsApp · P0

**Quem:** ORG, no grupo QA, e o aparelho B com um WhatsApp de teste (o número fica no mapa privado, fora do git). **Só depois da entrega do DEB-106 em produção.** **Deixa pronto:** a mensagem com o link de mensalistas no WhatsApp do aparelho B.

1. No grupo, tocar "Editar", ir ao cartão "Mensalidade", preencher "Valor por mês" com `80,00` e "Dia do vencimento" 10, e tocar "Salvar".
2. Abrir "Membros e permissões". Sem convidados, aparece o cartão "Mensalistas pelo WhatsApp"; tocar "Convidar mensalista".
3. Tocar "Adicionar pessoa" e preencher "Nome" com `QA Mensalista` e "WhatsApp" com o número de teste do aparelho B; tocar "Adicionar".
4. Tocar "Ver a mensagem que vai sair" e depois "Enviar 1 convite".

**Conferir**
- Em Membros, o chip "Convidados · 1" aparece ao lado de "Pendentes", e a seção "Convidados" mostra a linha em "Na fila" e logo depois "Aguardando". O número de "Todos" não muda.
- No WhatsApp do aparelho B, em até 1 minuto, chega a mensagem do número do Saqz: "E aí, QA Mensalista! 🏐 O … te colocou na lista de mensalistas do QA Vôlei … aqui no Saqz.", o link `https://links.saqz.app/?saqz_invite=…` e "Se não era pra você, é só ignorar.".
- Cada execução manda uma mensagem de verdade pelo número do Saqz, o mesmo das cobranças: rodar uma vez por rodada e só para o número de teste.

### PROD-CV09 · Erros do cadastro e promover sem mensagem · P1

**Quem:** ORG, no grupo QA, com ATL como avulso (PROD-CV02). **Só depois da entrega do DEB-106 em produção.**

1. "Convidar mensalista" → "Adicionar pessoa". Testar, um de cada vez: nome vazio; "WhatsApp" `(11) 9000`; "WhatsApp" `(11) 88888-7777`; o celular de ATL.
2. No caso de ATL, tocar "Promover a mensalista". Voltar a "Adicionar pessoa" e digitar o celular de ATL de novo.
3. Adicionar `(11) 90000-0090` (fictício) com o nome `QA Sem Zap` e tocar "Enviar 1 convite".

**Conferir**
- Mensagens da folha "Nova pessoa": "Digite o nome da pessoa.", "Faltam dígitos. Use o DDD e os 9 dígitos, por exemplo (11) 98765-4321.", "Esse número não parece um celular do Brasil. Confira o DDD e o 9 do começo." e, para ATL, "… já está no grupo como avulso. Se promover, vira mensalista agora e não recebe mensagem.".
- Promover mostra "… agora é mensalista. Nenhuma mensagem foi enviada." e ATL passa a mensalista em Membros. Digitar o celular de ATL de novo mostra "… já é mensalista do grupo. Não precisa de convite.".
- O número fictício fica "Sem WhatsApp"; tocar na linha oferece "Copiar link" e "Remover da lista", e nada sai. Remover a linha ao final.

### PROD-CV10 · Invalidar o link e reenviar para todos · P1

**Quem:** ORG e o aparelho B, depois de PROD-CV08 (a pessoa de teste ainda não entrou). **Só depois da entrega do DEB-106 em produção.** **Deixa pronto:** uma segunda mensagem, com link novo, no WhatsApp do aparelho B.

1. ORG: em Membros → "Convidados", tocar "Invalidar link". Na folha "Invalidar o link?", tocar "Invalidar link".
2. Aparelho B: tocar no link da primeira mensagem (a de PROD-CV08).
3. ORG: tocar "Reenviar para todos".
4. Aparelho B: esperar a segunda mensagem e comparar o link com o da primeira.
5. ORG: abrir "Convidar por link" e conferir o código público.

**Conferir**
- O link velho dá "Este link foi substituído" ("Peça um novo ao organizador.").
- A linha de `QA Mensalista` volta a "Aguardando", e a segunda mensagem chega em até 1 minuto, com um código diferente do da primeira.
- O convite público de PROD-CV01 e PROD-CV07 segue o mesmo: invalidar o link de mensalistas não mexe nele.

### PROD-CV11 · Entrar pelo link de mensalistas e o aviso ao organizador · P0

**Quem:** MENS (conta nova, aparelho B, app instalado e deslogado) e ORG (aparelho A), depois de PROD-CV10. **Só depois da entrega do DEB-106 em produção.** **Deixa pronto:** MENS como mensalista no grupo QA.

1. Aparelho B: no WhatsApp, tocar no link da **segunda** mensagem (a de PROD-CV10).
2. Conferir a prévia ("como mensalista", sem "precisa de aprovação") e tocar em "Criar conta ›"; criar a conta com o e-mail QA de MENS e o **mesmo celular** usado em PROD-CV08.
3. Em "Cadastro de atleta", tocar "Salvar meu perfil".
4. ORG: abrir a central de avisos e "Membros e permissões".

**Conferir**
- MENS termina dentro do grupo QA, e não na Início genérica.
- ORG vê MENS em Membros como mensalista, com R$ 80,00 e vencimento no dia 10; a linha sumiu de "Convidados" e o chip "Convidados" também (era a única).
- ORG recebe o aviso "… entrou no QA Vôlei … como mensalista." na central e por push; MENS não recebe aviso. O aviso chega também por WhatsApp, no número cadastrado de ORG (exceção à confirmação do número, DEB-081), no aparelho A.
- O link vale para mais gente: a mesma mensagem, repassada, entraria outra conta como mensalista (proteção do desenho: circula só no privado, vale 7 dias e se invalida). **Não** testar com conta de terceiro.

**Fora da produção.** Validade de 7 dias, retenção de 30 dias, falhas de envio, pausa e retomada da fila, fila entre grupos, reinício do backend e limite do plano precisam de relógio, banco ou número que falha, o que a regra 5 deste roteiro não permite. Estão no roteiro multi-aparelho `tests/multi-aparelhos/roteiros/10-convite-de-mensalistas.md`. Os grupos QA apagados em PROD-GR06 levam os convidados junto.

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
3. Tocar "Salvar".

**Conferir**
- O cartão mostra "R$ 10,00 / mês · vence dia N", e o valor continua lá ao reabrir o editor.
- A lista de membros mostra "Mensalista" ao voltar para ela, sem sair do grupo (a tela recarrega ao reaparecer).

### PROD-MB03 · Promover e rebaixar administrador · P1

**Quem:** ORG, no teste grátis ou com plano pago. Durante o teste a promoção e o rebaixamento são
liberados; só plano vencido ou pausado abre a folha de assinatura.

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
- Antes de remover, o app pergunta "Remover … do grupo?" e só remove ao confirmar "Remover".
- ATL2 some da lista e, depois de atualizar, perde o acesso ao grupo.
