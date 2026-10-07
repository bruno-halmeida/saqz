# Início e perfil

Elenco e regras em [README.md](README.md). Os textos entre aspas são os da tela.

## PROD-IN01 · Conta sem grupo vê o guia de primeiro acesso · P0

**Quem:** ORG logo depois de PROD-AC01, ainda sem grupo.

1. Abrir a Início.
2. Tocar "Tenho um convite" e fechar a folha sem colar nada.

**Conferir**
- Aparecem "COMECE POR AQUI", "Seu grupo em 1 minuto" e os botões "Criar meu grupo" e "Tenho um convite".
- "Como funciona" mostra 3 passos, com "Crie o grupo" marcado como "Agora".
- A folha "Recebeu um convite?" tem o campo "Link do convite" e o botão "Entrar no grupo".
- Nada de jogo, cobrança ou grupo de exemplo.

## PROD-IN02 · Próximo jogo na Início com resposta em um toque · P0

**Quem:** ATL, depois de PROD-JG01 (jogo de amanhã publicado no grupo QA).

1. Abrir a Início.
2. Tocar no cartão do próximo jogo.

**Conferir**
- O cartão mostra "PRÓXIMO JOGO", dia e hora, o local, o nome do grupo, "Vou" e "Não vou", e a contagem
  "N de M confirmados".
- Tocar no cartão abre a tela do mesmo jogo.

## PROD-IN03 · "Próximos jogos" lista os jogos seguintes · P1

**Quem:** ATL, com pelo menos dois jogos futuros publicados (PROD-JG01 e o publicado em PROD-JG08).

1. Abrir a Início e rolar até "Próximos jogos".
2. Tocar num jogo da lista.

**Conferir**
- O jogo do cartão principal não se repete na lista; os outros aparecem por data, com a resposta da
  pessoa ("Sem resposta", "Você vai"…).
- O toque abre o jogo certo.

## PROD-IN04 · Cobrança vencida aparece na Início com o Pix · P0

**Quem:** ATL, com uma mensalidade vencida (PROD-FI01 ou PROD-FI03 com vencimento no passado) e o Pix
do grupo cadastrado em PROD-GR03.

1. Abrir a Início.
2. Em "Minhas cobranças", tocar "Copiar chave Pix".
3. Colar a chave numa nota.

**Conferir**
- A faixa "Você tem R$ X em aberto" aparece em todas as abas.
- O cartão mostra o valor, "Venceu em dd/mm" e "Pix de …"; o botão vira "Chave copiada" e aparece
  "Chave copiada. Depois de pagar, o admin dá baixa.".
- A chave colada é a mesma cadastrada no grupo.

## PROD-PF01 · Editar os dados pessoais · P1

**Quem:** ATL.

1. Perfil → "Editar dados".
2. Mudar "Apelido" para `QA Ponta` e "Cidade" para `Campinas`.
3. Tocar "Salvar alterações".

**Conferir**
- Volta ao Perfil com o apelido e a cidade novos, e eles continuam lá depois de fechar e abrir o app.
- No grupo QA, a lista de membros mostra `QA Ponta`.

## PROD-PF02 · "Quem vê seu celular" controla quem vê o telefone · P0

**Quem:** ATL muda a opção; ORG e ATL2 conferem em Membros e permissões → ATL → "Ver perfil".

| Opção em "Editar dados" | ORG (admin) vê o celular? | ATL2 (atleta) vê? |
| --- | --- | --- |
| "A galera" | sim | sim |
| "Só administradores" | sim | não |
| "Ninguém" | não | não |

1. Para cada linha: ATL escolhe a opção em "Quem vê seu celular", toca "Salvar alterações", e ORG e
   ATL2 reabrem o perfil de ATL.

**Conferir**
- O telefone aparece só para quem a tabela diz; nome e dados de jogo aparecem para todos.

## PROD-PF03 · Trocar e remover a foto do perfil · P1

**Quem:** ATL, com uma imagem de teste na galeria.

1. No Perfil, tocar no selo da câmera → "Escolher da galeria" e escolher a imagem.
2. Fechar e abrir o app.
3. Tocar de novo no selo → "Remover foto".

**Conferir**
- A foto aparece no Perfil e continua depois de reabrir; depois de remover, voltam as iniciais.
- Os outros membros não veem a foto: é o PC-03, não reprova o cenário.

## PROD-PF04 · Cadastro de atleta por grupo · P1

**Quem:** ATL, membro do grupo QA.

1. Perfil → "Como você joga" → linha do grupo QA.
2. Em "Cadastro de atleta", mudar "Posição" e tocar "Salvar meu perfil".

**Conferir**
- Volta ao Perfil com a posição nova, que continua depois de reabrir.
- ORG vê a posição nova na lista de membros.

## PROD-PF05 · Minhas mensalidades · P1

**Quem:** ATL, com mensalidades no grupo QA (PROD-FI01 ou PROD-FI03).

1. Perfil → "Minhas mensalidades".
2. Tocar "Ver grupo e dados para pagamento".

**Conferir**
- A lista mostra as mensalidades do grupo QA com valor, vencimento e situação ("Em aberto", "Paga").
- O botão abre o grupo QA, com a chave Pix.

## PROD-PF06 · Ajuda, termos e privacidade · P1

**Quem:** qualquer conta.

1. Perfil → "Ajuda e contato".
2. Voltar e tocar "Termos de uso" e depois "Política de privacidade".

**Conferir**
- "Ajuda e contato" abre um e-mail para `contato@egysis.com`.
- Os outros dois abrem `saqz.app/termos/` e `saqz.app/privacidade/`.

## PROD-PF07 · "Meu plano" só para quem organiza · P1

**Quem:** ORG (com o teste grátis de PROD-AS01) e ATL.

1. Abrir o Perfil com cada conta.

**Conferir**
- ORG vê a seção "Organização" com "Meu plano"; ATL não vê "Meu plano" nem a aba "Financeiro".

## PROD-PF08 · Excluir a conta · P0 (limpeza)

**Quem:** uma conta descartável da rodada (por exemplo ATL2), no fim.

1. Perfil → "Excluir conta".
2. Marcar "Entendo as consequências e quero excluir minha conta.".
3. Confirmar a identidade: "Confirmar senha e excluir" com a "Senha atual", "Confirmar com Google e
   excluir" ou, no iPhone, "Confirmar com Apple e excluir".

**Conferir**
- O app volta para a tela de entrada.
- Entrar de novo com a mesma senha falha. Com o Google, entrar cria uma conta nova e vazia.
- No grupo QA, o histórico de presença da pessoa aparece como "Conta excluída".
- Se a conta tinha assinatura da loja, a tela de exclusão avisou que a cobrança continua e ofereceu
  "Gerenciar assinatura".
