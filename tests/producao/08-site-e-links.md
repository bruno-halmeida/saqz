# Site e links

Elenco e regras em [README.md](README.md). Os textos entre aspas são os da tela. Desde 07/10/2026,
`saqz.app/comecar/` e `saqz.app/assinar/` rodam contra produção (Firebase `saquz-app`, `api.saqz.app`).

## PROD-WEB01 · saqz.app leva ao cadastro com os preços certos · P0

**Quem:** visitante, no computador e no celular.

1. Abrir `https://saqz.app`.
2. Tocar "Testar grátis por 14 dias"; voltar e tocar "Já decidiu? Assinar".
3. No rodapé, abrir "Termos de uso", "Política de privacidade" e "Excluir conta".

**Conferir**
- "Testar grátis" abre `saqz.app/comecar/`, e "Assinar" abre `saqz.app/assinar/`. Nada aponta para
  `brunoalmeida.dev`.
- Preços: Titular R$ 39,90/mês (R$ 359,10/ano), Organizador R$ 59,90 (R$ 539,10), Ilimitado R$ 89,90
  (R$ 809,10).
- As páginas do rodapé abrem. "Termos de recebimentos" ainda lê os termos de staging: é o PC-16.

## PROD-WEB02 · Criar conta pelo site e liberar o teste · P0

**Quem:** WEB, conta nova, no computador. **Deixa pronto:** a conta WEB para PROD-CV06.

1. Em `saqz.app/comecar/`, na aba "Criar conta", preencher Nome, E-mail (caixa real) e Senha e tocar
   "Criar minha conta". Também vale "Continuar com o Google".
2. Em "Falta só confirmar seu perfil.", preencher "Celular" e tocar "Salvar e continuar".
3. Em "Sua conta está pronta.", tocar "Experimentar o Organizador por 14 dias".
4. No celular, entrar no app com a mesma conta e tocar "Criar meu grupo".

**Conferir**
- O selo do Google Play leva à página do Saqz no Play; o da App Store mostra "Em breve" até a aprovação.
- O site diz que o teste está liberado e que o prazo começa no primeiro grupo.
- No app, a conta entra sem cadastro novo, e "Criar meu grupo" abre direto o formulário "Criar grupo",
  sem a tela do teste. Fechar sem criar: o prazo só começa no primeiro grupo.

## PROD-WEB03 · "Abrir o app neste celular" · P1

**Quem:** WEB, com `saqz.app/comecar/` aberto **no celular** que tem o app.

1. Em "Sua conta está pronta.", tocar "Abrir o app neste celular".
2. Tocar "Continuar no app".

**Conferir**
- Aparece "Link pronto…", e não "O link do app não está disponível neste ambiente."; essa falha foi
  corrigida em 07/10.
- O app abre com a conta WEB, sem pedir login.

## PROD-WEB04 · Assinar pela web · P0 (opcional, dinheiro real)

**Quem:** WEB, só com decisão prévia: o Asaas de produção cobra de verdade. Combinar o reembolso antes.

1. Em `saqz.app/assinar/`, entrar com a conta WEB.
2. Em "Escolha o plano", ficar no "Mensal" e escolher o Titular.
3. Em "Quase lá", preencher "CPF ou CNPJ do titular", escolher Pix e tocar "Gerar Pix".
4. Pagar o Pix e esperar na página.

**Conferir**
- O valor do Pix é o do plano escolhido (R$ 39,90).
- A página chega sozinha a "Assinatura ativa." e "Pagamento confirmado. Volte ao app Saqz e crie seu
  grupo.".
- No app, "Meu plano" mostra o Titular ativo.
- Para desfazer: "Meu plano" → "Gerenciar" → "Cancelar assinatura" e reembolso pelo painel do Asaas.
- Renovação e cancelamento só sincronizam com o webhook do Asaas configurado (pendente em 07/10).

## PROD-WEB05 · Link com o app instalado abre direto no app · P0

**Quem:** ATL no Android e no iPhone (TestFlight), com o app instalado e logado.

1. Num chat de teste do WhatsApp, tocar no link do convite do grupo QA.

**Conferir**
- O app abre direto no grupo, sem passar pela página do navegador.

## PROD-WEB06 · Link de convite no computador · P1

**Quem:** visitante, no navegador do computador.

1. Abrir o link do convite.

**Conferir**
- A página mostra "Entre no grupo pelo app Saqz" e "Abra este link no celular para continuar no app.",
  com o botão "Acessar site do Saqz", sem redirecionar sozinha.

## PROD-WEB07 · Página de exclusão de conta · P1

**Quem:** visitante. É a página que o Google Play exige para pedidos de exclusão.

1. Abrir `saqz.app/excluir-conta/`.

**Conferir**
- Explica como excluir pelo app (Perfil → "Excluir conta") e oferece o contato por e-mail
  (`contato@egysis.com`) para quem não tem mais o app.
