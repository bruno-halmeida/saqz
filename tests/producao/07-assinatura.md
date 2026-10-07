# Assinatura e plano do organizador

Elenco e regras em [README.md](README.md). Os textos entre aspas são os da tela.

**Dinheiro.**
- **Android:** só com conta Google cadastrada como testador de licença. Use uma lista só com essa
  conta: Play Console → Configurações → Teste de licença. Marcar a lista "alfa-testers" daria compra
  grátis aos 35.
- **iPhone:** só pelo TestFlight, com conta sandbox.
- **Efeitos de uma compra de teste:** ela dá acesso real em produção e tira, para sempre, o teste grátis
  dessa conta Saqz. As renovações de teste são aceleradas (no Play, a mensal renova a cada 5 min) e
  param depois de algumas: a conta perde o acesso sozinha.

## PROD-AS01 · Teste grátis começa no primeiro grupo · P0

**Quem:** ORG, logo depois de PROD-GR01.

1. Perfil → "Meu plano".

**Conferir**
- Mostra "SEU TESTE GRATUITO", "Organizador", "Organizador em teste até <data>" (14 dias depois da
  criação do grupo), "Até 3 grupos" e "Atletas ilimitados", com "Continuar com o Organizador".
- Antes de criar o grupo, "Meu plano" nem aparecia no Perfil (PROD-PF07).

## PROD-AS02 · Assinar pelo Google Play · P0

**Quem:** ORG no Android, com a conta Google de testador de licença. **Deixa pronto:** assinatura do
**Titular**, usada em PROD-AS04.

1. Perfil → "Meu plano" → "Continuar com o Organizador".
2. Em "Assinar o Saqz", escolher "Mensal" e tocar "Assinar Titular".
3. Na folha do Google Play, confirmar com o cartão de teste.

**Conferir**
- A folha do Play mostra o preço da loja e o cartão de teste (sem cobrança).
- O app mostra "Assinatura confirmada. Finalizando…" e volta.
- "Meu plano" mostra "PLANO ATUAL", "Ativo", "Titular" e "Próxima cobrança em …".
- O pedido aparece no Play Console como pedido de teste.

## PROD-AS03 · Assinar pelo iPhone (TestFlight) · P0

**Quem:** ORG no iPhone com o build do TestFlight e uma conta sandbox da Apple. Use em vez de PROD-AS02
na rodada em que o organizador está no iPhone.

1. Perfil → "Meu plano" → "Continuar com o Organizador".
2. Em "Assinar o Saqz", tocar "Assinar Titular" e confirmar na folha da App Store (sandbox).

**Conferir**
- O app mostra "Assinatura confirmada. Finalizando…", e "Meu plano" fica "Ativo" no Titular.

## PROD-AS04 · Subir de plano no Android sem cobrar duas vezes · P0

**Quem:** ORG no Android, assinante do Titular (PROD-AS02), com 1 grupo, que é o limite do Titular.
Exige o app 0.0.4 (6) ou mais novo.

1. Na aba Grupos, tocar "+".
2. O app abre "Assinar o Saqz": tocar "Assinar Organizador" (mensal).
3. Na folha do Google Play, conferir a troca e confirmar.

**Conferir**
- A folha do Play mostra a troca de plano com a diferença proporcional, e não uma assinatura nova.
- "Meu plano" passa a "Organizador", "Ativo", e o "+" agora abre "Criar grupo".
- No app do Play Store (Pagamentos e assinaturas → Assinaturas), só a assinatura do Organizador está
  ativa; a do Titular foi substituída.

## PROD-AS05 · Cancelar pela loja · P1

**Quem:** ORG, assinante.

1. "Meu plano" → "Gerenciar" → "Trocar de plano ou cancelar".
2. Na tela de assinaturas da loja, cancelar a assinatura do Saqz.
3. Voltar ao app e reabrir "Meu plano" depois de 1 ou 2 minutos.

**Conferir**
- O botão abre a gestão de assinaturas da loja certa.
- "Meu plano" passa a mostrar a assinatura cancelada e "Acesso garantido até <data>". O acesso continua
  até essa data, sem apagar grupos nem histórico.

## PROD-AS06 · Recibos · P1

**Quem:** ORG, assinante.

1. "Meu plano" → "Gerenciar" → "Recibos".

**Conferir**
- A lista mostra a compra com a data. Recibos do Google Play aparecem com o valor "—": é o PC-14.

## PROD-AS07 · O plano vale no outro aparelho · P1

**Quem:** ORG, entrando no aparelho da outra plataforma.

1. Entrar com a conta ORG no outro aparelho e abrir "Meu plano".

**Conferir**
- O plano aparece ativo.
- A gestão pede para usar o aparelho da loja onde a compra foi feita ("use o iPhone…" ou "use um
  celular Android…").
