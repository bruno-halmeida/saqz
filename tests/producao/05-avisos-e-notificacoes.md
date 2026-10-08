# Avisos e notificações

Elenco e regras em [README.md](README.md). Os textos entre aspas são os da tela. O chat do grupo e o
WhatsApp estão desligados em produção; só os avisos funcionam.

## PROD-NT01 · Permissão de push na primeira entrada · P0

**Quem:** ORG (aparelho A) e ATL (aparelho B), logo depois do primeiro login.

1. Quando o sistema pedir permissão de notificações, tocar "Permitir".

**Conferir**
- O pedido aparece logo na primeira vez que a Início abre.
- Nos ajustes do sistema, as notificações do Saqz ficam ligadas.
- Os pushes dos cenários seguintes chegam. No Android 13+ o app pede uma vez só por instalação: quem
  recusou precisa ligar nos ajustes (PC-12).

## PROD-NT02 · Publicar um aviso · P0

**Quem:** ORG publica; ATL e ATL2 recebem.

1. No grupo, em "Mural", tocar "Avisos".
2. Escrever `Aviso QA <hora>` em "Mensagem (até 2.000 caracteres)" e tocar "Enviar".

**Conferir**
- O aviso aparece na lista com autor e horário, e o "Mural" do grupo mostra a última mensagem.
- ATL e ATL2 recebem o push "Você recebeu um aviso do grupo. Abra o app para conferir." e o item na
  central; ORG não recebe o próprio aviso.
- Para ATL, a tela de avisos mostra "Somente administradores publicam avisos." e não tem campo de envio.
- Com o app fechado (fora dos recentes), tocar no push abre o app já na central. Até a 0.0.4 o app só
  passava a ouvir o toque depois da primeira tela, e o toque com o app fechado se perdia.

## PROD-NT03 · Central de notificações · P0

**Quem:** ATL, com o aviso de PROD-NT02 e o lembrete de PROD-JG07 ainda não lidos.

1. Na Início, tocar no sino ("Notificações").
2. No aviso, tocar "Abrir"; voltar e, no lembrete do jogo, tocar "Abrir".
3. Voltar e tocar "Atualizar".

**Conferir**
- Os itens não lidos mostram "Não lida".
- O aviso abre "Avisos do grupo"; o lembrete abre a tela do jogo.
- Depois de abertos, os dois aparecem como lidos.

## PROD-NT04 · Tocar no push abre o destino · P1

**Quem:** ATL, com um push de jogo e um de aviso na bandeja.

1. Com o app fechado, tocar no push de jogo.
2. Voltar à bandeja e tocar no push de aviso.

**Conferir**
- O push de jogo abre a tela do jogo, e o de aviso abre a central de notificações.

## PROD-NT05 · Preferências de notificação · P1

**Quem:** ATL; ORG publica um aviso no meio do teste.

1. Perfil → ícone de engrenagem → "Configurações de notificações" → aba "Push".
2. Desligar "Avisos dos grupos" e tocar "Salvar preferências".
3. ORG publica outro aviso.
4. ATL liga "Avisos dos grupos" de novo e salva.

**Conferir**
- Aparece "Preferências salvas.", e a escolha continua depois de reabrir o app.
- Com o push de avisos desligado, ATL não recebe push do aviso novo, mas o vê na central e em
  "Avisos" (a aba "No app" continua ligada).
- Só existem as abas "No app" e "Push"; não há aba de WhatsApp.

## PROD-NT06 · Denunciar um aviso · P0

**Quem:** ATL, no aviso de ORG. A equipe confere a caixa `contato@egysis.com`.

1. Em "Avisos", tocar no menu "⋮" do aviso e em "Denunciar aviso".
2. Em "Motivo", escolher "Spam ou propaganda". Em "Conte o que aconteceu (opcional)", escrever
   `Teste QA, desconsiderar`.
3. Tocar "Enviar denúncia".

**Conferir**
- Aparece "Denúncia enviada. Nossa equipe analisa em até 24 horas.".
- Em até 2 min chega em `contato@egysis.com` o e-mail "[Saqz] Denúncia: Spam ou propaganda · aviso em
  "QA Vôlei…"", com o texto do aviso e quem denunciou.

## PROD-NT07 · Bloquear e desbloquear alguém · P1

**Quem:** ATL2 bloqueia ORG; ORG publica um aviso no meio.

1. ATL2: Membros e permissões → ORG → "Bloquear" → confirmar.
2. ORG publica um aviso novo.
3. ATL2: Membros e permissões → ORG → "Desbloquear".

**Conferir**
- Aparece "Pessoa bloqueada.". Enquanto bloqueado, ATL2 não vê nem recebe os avisos de ORG, e ORG não
  é avisado.
- A equipe recebe o e-mail "[Saqz] Bloqueio: Pessoa bloqueada · pessoa em "QA Vôlei…"".
- Depois de "Pessoa desbloqueada.", os avisos de ORG voltam a aparecer.
