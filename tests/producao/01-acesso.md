# Acesso

Elenco e regras em [README.md](README.md). Os textos entre aspas são os da tela.

## PROD-AC01 · Criar conta com e-mail e senha · P0

**Quem:** ORG, aparelho A, app recém-instalado. **Deixa pronto:** a conta ORG.

1. Abrir o app e, na tela de entrada, tocar "Criar conta ›".
2. Preencher nome `QA Org <data>`, celular de teste, o e-mail QA do organizador e uma senha de 8+ caracteres.
3. Conferir que "Termos de uso" e "Política de privacidade" no rodapé abrem as páginas de `saqz.app`.
4. Tocar "Criar conta".
5. Na tela seguinte, com nome e celular já preenchidos, tocar "Concluir cadastro".

**Conferir**
- Abre a Início com "Fala, QA!" e a faixa "Confirme seu e-mail para não perder o acesso à sua conta.".
- Em até 1 min chega na **caixa de entrada** (não no spam) o e-mail "Confirme seu e-mail no Saqz", de um
  remetente `@saqz.app`, com o botão "Confirmar e-mail".

## PROD-AC02 · Confirmar o e-mail · P1

**Quem:** ORG logado, com o e-mail de PROD-AC01.

1. No e-mail "Confirme seu e-mail no Saqz", tocar "Confirmar e-mail".
2. Voltar para o app.

**Conferir**
- A página do Firebase confirma o e-mail.
- Ao voltar ao app, a faixa de confirmação some. Se ela reaparecer depois de reabrir o app, registrar
  como falha, com o horário.

## PROD-AC03 · Entrar com e-mail e senha · P0

**Quem:** ORG, deslogado (depois de PROD-AC08 ou de "Sair da conta").

1. Na tela de entrada, preencher "E-mail" e "Senha".
2. Tocar "Entrar".

**Conferir**
- O botão vira carregamento e abre a Início da conta ORG, com o grupo QA se ele já existir.

## PROD-AC04 · Entrar com Google, conta nova · P0

**Quem:** ATL2, aparelho B, com uma conta Google de teste no aparelho e sem cadastro no Saqz.
**Deixa pronto:** a conta ATL2.

1. Na tela de entrada, tocar "Entrar com Google" (Android) ou "Google" (iPhone).
2. Escolher a conta Google de teste.
3. Na tela seguinte, conferir o nome, preencher o celular e tocar "Concluir cadastro".

**Conferir**
- Abre a Início da conta nova, **sem** a faixa de confirmar e-mail (o Google já confirma).
- No Android instalado pela loja, isto também prova que a assinatura do Play está cadastrada no Firebase.

## PROD-AC05 · Entrar com Apple · P1

**Quem:** conta descartável, iPhone. Só existe no iPhone.

1. Na tela de entrada, tocar "Apple" e confirmar com Face ID ou senha.
2. Se a Apple não devolver o nome, o campo vem como "Atleta": corrigir, preencher o celular e tocar
   "Concluir cadastro".

**Conferir**
- Abre a Início da conta nova.
- Testar também com "Ocultar meu e-mail": o cadastro funciona do mesmo jeito.

## PROD-AC06 · Recuperar a senha · P0

**Quem:** ORG, deslogado. Use este caminho, e não "Trocar senha" com a conta logada (ver PC-04).

1. Na tela de entrada, tocar "Esqueci minha senha".
2. Preencher o e-mail de ORG e tocar "Enviar código".
3. Abrir o e-mail "Seu código de acesso Saqz" e anotar os 4 dígitos.
4. Digitar o código e tocar "Verificar código".
5. Preencher "Nova senha" e "Confirmar nova senha" e tocar "Salvar nova senha".
6. Em "Senha alterada!", tocar "Entrar agora" e entrar com a senha nova.

**Conferir**
- O e-mail chega na caixa de entrada em até 1 min e diz que o código vale por 10 minutos.
- Entra com a senha nova; a senha antiga é recusada.

## PROD-AC07 · Abrir o app com a sessão salva · P0

**Quem:** qualquer conta logada.

1. Fechar o app por completo (tirar da lista de apps recentes).
2. Abrir de novo.

**Conferir**
- Passa pela marca do Saqz e abre direto na Início, sem pedir login.

## PROD-AC08 · Sair e entrar com outra conta sem misturar dados · P0

**Quem:** aparelho B, com ATL logado e ATL2 já criado.

1. Com ATL, abrir Início, Grupos e Perfil, e anotar o que aparece.
2. Perfil → "Sair da conta" → confirmar "Sair da conta".
3. Entrar com ATL2.

**Conferir**
- Depois de sair, o app fica na tela de entrada mesmo se for fechado e reaberto.
- Com ATL2, nenhuma tela mostra nome, foto, grupo, cobrança ou notificação de ATL.
- O botão voltar não reabre telas da conta anterior.
