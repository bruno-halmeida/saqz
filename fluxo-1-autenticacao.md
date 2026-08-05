# Fluxo 1 · Autenticação — cenários de teste

Unidade do documento é a **jornada**: um caminho completo do usuário, com um caminho
principal e suas variações. Cada jornada vira um caso da planilha de QA manual; as
jornadas marcadas **crítico** são as candidatas a e2e automatizado.

**Crítico** = quebrou, o app fica inutilizável, e acontece com frequência ou não tem
contorno. Autenticação é quase toda crítica por construção: sem sessão não existe app.
O que sobra de fora é o que tem caminho alternativo (recuperar senha tem o suporte;
completar telefone depois pode esperar).

Marca de crítico vale para a jornada inteira; a variação herda, salvo marca própria.

Telas do export: `1a`/`1i` entrar · `1b` criar conta · `1c` completar identidade ·
`1d` esqueci a senha · `1e`/`1f`/`1k` código · `1g` nova senha · `1h` senha alterada ·
`Starting` e `Bootstrap` (sem desenho).

---

## Ambiente

Vale para todas as jornadas, e é pré-requisito de qualquer uma que envolva e-mail.

**E-mails são inventados.** O ambiente usa Mailpit — um SMTP que aceita tudo e não entrega
a ninguém. Remetente e destinatário não precisam existir: `teste-01@saqz.local` funciona.
Não há caixa de entrada real envolvida, não há limite de contas, não precisa de `+alias`.
E-mail de verdade (Gmail etc.) **não** ganha nada aqui — a mensagem é interceptada igual.

**Onde ler a mensagem:**

| Ambiente | Backend | Caixa |
|---|---|---|
| Local | `localhost:8080` | `http://localhost:8025`, sem senha |
| Server Dev | `saqz-api.brunoalmeida.dev` | `https://saqz-mail.brunoalmeida.dev`, usuário e senha de `SAQZ_MAILPIT_AUTH` |

O e2e lê o mesmo código pela API HTTP do Mailpit (`/api/v1/messages`) — sem porta dos
fundos no backend.

---

## J1.1 · Entrar com e-mail e senha · **crítico**

**Ator:** pessoa com conta ativa · **Telas:** 1a → Início
**Massa:** conta existente com senha conhecida.

**Caminho principal**
1. abrir o app deslogado (cai na 1a)
2. preencher e-mail e senha
3. tocar "Entrar"
4. → botão vira spinner → sessão criada → app abre na **Início**

**Variações**

| ID | Desvio | Esperado | Crít. |
|---|---|---|---|
| J1.1-a | passo 2 com `ana@exemplo` (domínio sem ponto) | erro "Digite um e-mail válido." sob o campo; **nenhuma chamada ao provedor**: sem spinner, sem alerta no topo, contador não aparece | ✅ |
| J1.1-b | idem com `@exemplo.com`, `ana @exemplo.com`, `ana@@exemplo.com`, `ana@.com`, `ana@exemplo.` | mesmo erro de formato nos cinco | |
| J1.1-c | senha errada | alerta com a **primeira frase em negrito** ("E-mail ou senha incorretos." + "Confira os dados e tente de novo."), erro sob o campo senha, e a frase "Errou 1 de 5 tentativas." abaixo do botão | ✅ |
| J1.1-d | errar a senha três vezes seguidas | a frase acompanha: 1, 2, 3 | |
| J1.1-e | errar a senha seis vezes sem o provedor bloquear | na sexta a frase **desaparece** — nunca escreve "Errou 6 de 5" | |
| J1.1-f | errar duas vezes → ir para a 1d → voltar para a 1a | contador zerado, frase ausente | |
| J1.1-g | errar até o provedor recusar por excesso | alerta de conta bloqueada ocupa o lugar do de credencial e a frase do contador some | |
| J1.1-h | sem rede | alerta de rede indisponível, nenhuma sessão, campos continuam preenchidos | ✅ |
| J1.1-i | provedor indisponível | alerta de provedor indisponível — uma frase só, **sem** negrito parcial | |
| J1.1-j | e-mail de conta criada via Google | alerta de conflito de método de autenticação | |
| J1.1-k | tocar tudo enquanto o passo 3 carrega | os dois campos, "Entrar" e "Continuar com o Google" desabilitados; segundo toque não dispara novo envio | ✅ |
| J1.1-l | errar a senha e depois acertar | alerta e erros por campo somem, sessão criada | |

> **J1.1-g não é automatizável.** O limiar é do Firebase, não é conhecido, não zera rápido
> e cada tentativa queima a conta. O mapeamento `TOO_MANY_REQUESTS` → alerta já é teste de
> unidade da ViewModel; em QA manual, verificar uma vez com conta descartável.

---

## J1.2 · Entrar com Google, conta existente · **crítico**

**Ator:** pessoa que criou a conta por provedor · **Telas:** 1a → Início
**Massa:** conta Google real no aparelho/emulador, já cadastrada no Saqz.

**Caminho principal**
1. na 1a, tocar "Continuar com o Google"
2. escolher a conta na folha do sistema
3. → sessão criada → app abre na **Início**

**Variações**

| ID | Desvio | Esperado | Crít. |
|---|---|---|---|
| J1.2-a | fechar a folha de contas sem escolher | volta para a 1a utilizável, **sem** alerta de erro | |
| J1.2-b | sem rede no passo 2 | alerta de rede indisponível, 1a segue utilizável | |

---

## J1.3 · Criar conta com e-mail e senha · **crítico**

**Ator:** pessoa sem conta · **Telas:** 1a → 1b → 1c → Início
**Massa:** e-mail livre; para J1.3-c, um e-mail já cadastrado.

**Caminho principal**
1. na 1a, tocar "Criar conta ›"
2. preencher nome, telefone, e-mail e senha (mínimo 8)
3. tocar criar
4. na 1c, conferir nome e telefone; opcionalmente escolher foto
5. confirmar
6. → sessão criada → app abre na **Início**, com o aviso de e-mail não verificado no topo

**Variações**

| ID | Desvio | Esperado | Crít. |
|---|---|---|---|
| J1.3-a | passo 3 com os quatro campos inválidos | alerta resume **quatro**, não três; cada campo com a sua frase | |
| J1.3-b | nome com 1 caractere, ou com 81 | recusa do campo nome | |
| J1.3-c | telefone fixo, ou celular sem o 9, ou DDD começando com 0 | recusa do campo telefone (o app só aceita celular BR: DDD + 9 + 8 dígitos) | |
| J1.3-d | e-mail já cadastrado | erro do campo e-mail é **clicável** e pergunta "Entrar?"; tocar leva para a 1a | ✅ |
| J1.3-e | senha com 7 caracteres | recusa local, frase do mínimo de 8, **sem** ida ao provedor | |
| J1.3-f | senha com 12 caracteres fracos | recusa do provedor; a frase **não** pode repetir "use no mínimo 8" | |
| J1.3-g | sem rede no passo 3 | alerta de rede no lugar do resumo de campos | |
| J1.3-h | passo 4 com foto que falha no envio | nome e telefone **gravam**; a foto vira aviso, não erro; o cadastro segue de pé e a foto sai do estado | ✅ |
| J1.3-i | repetir o envio depois de J1.3-h | não retenta o upload que acabou de falhar | |
| J1.3-j | foto sobe mas o perfil é recusado | a 1c volta com a imagem na tela e **não** reenvia o JPEG no próximo toque | |
| J1.3-k | matar o app entre os passos 3 e 4 | ao reabrir, retoma na 1c (a conta existe, a identidade não fechou) | ✅ |

---

## J1.4 · Entrar com Google sem cadastro · **crítico**

**Ator:** pessoa nova entrando por provedor · **Telas:** 1a → 1c → Início
**Massa:** conta Google real ainda não cadastrada no Saqz.

A 1c aqui aparece no **primeiro momento**: ainda não há sessão. O nome é pré-condição do
bootstrap no backend, então quem entrou por provedor que não deu nome utilizável passa por
aqui antes de ter sessão.

**Caminho principal**
1. na 1a, tocar "Continuar com o Google" e escolher conta sem cadastro
2. a 1c abre pedindo nome e telefone
3. preencher e confirmar
4. → sessão criada → app abre na **Início**

**Variações**

| ID | Desvio | Esperado | Crít. |
|---|---|---|---|
| J1.4-a | passo 3 com telefone inválido | recusa do campo, sem sessão criada | |
| J1.4-b | fechar o app na 1c e reabrir | volta para a 1c, não para a Início nem para a 1a | ✅ |
| J1.4-c | sem rede no passo 3 | alerta de rede, dados preenchidos preservados | |

---

## J1.5 · Recuperar a senha

**Ator:** pessoa que esqueceu a senha · **Telas:** 1a → 1d → 1e/1f/1k → 1g → 1h → 1a
**Massa:** conta existente; acesso à caixa do Mailpit.

**Caminho principal**
1. na 1a, tocar "Esqueci minha senha"
2. na 1d, informar o e-mail e enviar
3. abrir o Mailpit e ler o código de 4 dígitos
4. digitar o código na 1e
5. na 1g, digitar a nova senha e a confirmação
6. → 1h confirma a troca → voltar para a 1a e entrar com a senha nova

**Variações**

| ID | Desvio | Esperado | Crít. |
|---|---|---|---|
| J1.5-a | passo 2 com e-mail que **não existe** | avança para a 1e exatamente igual, e **nenhuma** mensagem chega ao Mailpit. O app não pode revelar quem tem conta no Saqz | ✅ |
| J1.5-b | SMTP fora do ar no passo 2 | mesma coisa: avança para a 1e, sem erro na tela | |
| J1.5-c | esperar o contador de reenvio | começa em 60s e libera o reenvio em 0 | |
| J1.5-d | tocar reenviar | 1f: alerta verde no ar, rodapé vira "Reenviar novamente em {m:ss}", o link "Entrar ›" some | |
| J1.5-e | código errado | 1k linha vermelha, com o número de tentativas restantes | |
| J1.5-f | esgotar as tentativas de verificação | o contador de **verificação** trava, mas o de **reenvio** continua livre — pedir código novo nunca fica bloqueado por errar código | ✅ |
| J1.5-g | usar um código expirado | 1k alerta âmbar; a única saída é pedir outro | |
| J1.5-h | passo 5 com senha e confirmação diferentes | erro na linha do campo de baixo | |
| J1.5-i | passo 5 com senha de 7 caracteres | frase do mínimo de 8, na linha do campo de baixo | |
| J1.5-j | passo 5 com o ticket já usado ou expirado | **não** trava num erro sem botão: a 1e reaparece pedindo código novo | ✅ |
| J1.5-k | senha nova recusada pelo provedor | alerta acima dos campos, distinto dos erros de linha | |
| J1.5-l | depois do passo 6, tentar entrar com a senha **antiga** | recusa de credencial | ✅ |

---

## J1.6 · Abrir o app com sessão salva · **crítico**

A jornada mais frequente do app inteiro: acontece toda vez que alguém abre o Saqz.

**Ator:** pessoa já logada · **Telas:** Starting → Bootstrap → Início
**Massa:** conta com sessão válida no aparelho.

**Caminho principal**
1. fechar o app completamente
2. abrir de novo
3. → passa por Starting e Bootstrap sem interação → abre na **Início**

**Variações**

| ID | Desvio | Esperado | Crít. |
|---|---|---|---|
| J1.6-a | abrir sem rede | não cai na 1a nem pede login de novo: Bootstrap acusa a falha com ação de tentar de novo | ✅ |
| J1.6-b | abrir com o token expirado | renova sozinho e abre na Início, sem pedir senha | ✅ |
| J1.6-c | abrir com a sessão revogada no servidor | cai na 1a limpa, sem dado da sessão anterior | ✅ |

---

## J1.7 · Bootstrap falha e tenta de novo · **crítico**

**Ator:** pessoa já logada, rede instável · **Telas:** Bootstrap
**Massa:** conta com sessão válida; forma de derrubar a rede entre app e backend.

**Caminho principal**
1. desligar a rede e abrir o app
2. → Bootstrap mostra a falha com a ação de tentar de novo
3. religar a rede e tocar tentar de novo
4. → app abre na **Início**

**Variações**

| ID | Desvio | Esperado | Crít. |
|---|---|---|---|
| J1.7-a | tocar tentar de novo ainda sem rede | volta ao mesmo estado de falha, sem travar e sem duplicar pedido | |
| J1.7-b | tocar tentar de novo várias vezes seguidas | um pedido por vez; a ação desabilita enquanto carrega | ✅ |
| J1.7-c | matar o app na tela de falha e reabrir com rede | abre normal na Início | |

---

## J1.8 · Completar o telefone depois do bootstrap

A 1c no **segundo momento**: já existe sessão e falta só o telefone. A assimetria é do
backend — o nome é pré-condição do bootstrap, o telefone é pós-condição.

**Ator:** pessoa com sessão e sem telefone · **Telas:** 1c → Início
**Massa:** conta com sessão válida e telefone vazio (precisa de seed).

**Caminho principal**
1. abrir o app com essa conta
2. a 1c pede o telefone
3. preencher e confirmar
4. → app abre na **Início**

**Variações**

| ID | Desvio | Esperado | Crít. |
|---|---|---|---|
| J1.8-a | telefone inválido | recusa do campo, permanece na 1c | |
| J1.8-b | sem rede ao confirmar | alerta de rede, o que foi digitado não se perde | |

---

## J1.9 · Sair da conta

Jornada do fluxo 7 (o botão mora no perfil); aqui ficam só as asserções de sessão.

**Ator:** pessoa logada · **Telas:** perfil → 1a
**Massa:** conta com sessão válida.

**Caminho principal**
1. abrir o perfil
2. sair da conta e confirmar
3. → volta para a **1a**
4. fechar e reabrir o app
5. → continua na 1a, **não** passa por Bootstrap direto para a Início

**Variações**

| ID | Desvio | Esperado | Crít. |
|---|---|---|---|
| J1.9-a | sair sem rede | a sessão local morre mesmo assim; não fica preso logado | ✅ |
| J1.9-b | voltar atrás na confirmação | continua logado, nada muda | |

---

## J1.10 · Aviso de e-mail não verificado

**Ator:** pessoa recém-cadastrada · **Telas:** Início (faixa do shell)
**Massa:** conta com e-mail não verificado (seed) e outra com verificado.

E-mail não verificado é **aviso, não trava**: o app inteiro continua utilizável.

**Caminho principal**
1. entrar com conta de e-mail não verificado
2. → faixa de aviso no topo da Início
3. verificar o e-mail pelo link do Mailpit
4. → a faixa some

**Variações**

| ID | Desvio | Esperado | Crít. |
|---|---|---|---|
| J1.10-a | navegar pelo app com a faixa no ar | nada bloqueado por causa dela | ✅ |
| J1.10-b | entrar com conta já verificada | faixa não aparece | |
| J1.10-c | girar a tela / matar e reabrir com a faixa no ar | a faixa se comporta igual, sem piscar nem sumir sozinha | |

---

## J1.11 · Sair e entrar com **outra** conta · **crítico**

A jornada que mais vale a pena automatizar no fluxo 1: ela testa vazamento de dados entre
sessões, que é o pior defeito possível num app multiusuário.

**Ator:** duas pessoas no mesmo aparelho · **Telas:** perfil → 1a → Início
**Massa:** duas contas com dados **visivelmente diferentes** (nome, foto, grupos distintos).

**Caminho principal**
1. entrar com a conta A e navegar até ver dados dela (Início, perfil, grupo)
2. sair da conta
3. entrar com a conta B
4. → nenhuma tela mostra dado da conta A: nem nome, nem foto, nem grupo, nem lista, nem
   estado de formulário

**Variações**

| ID | Desvio | Esperado | Crít. |
|---|---|---|---|
| J1.11-a | repetir A → B → A | idem nos dois sentidos | |
| J1.11-b | sair no meio de um formulário preenchido e entrar com B | o formulário de B nasce vazio | |

> **Deve falhar hoje.** O `NavDisplay` do `SaqzNavHost` é montado sem `entryDecorators`, o
> que faz toda ViewModel virar singleton de Activity e sobreviver ao logout (VUL-204). O
> conserto está na branch `vul-204-viewmodel-escopo`, ainda fora da main. Executar contra
> build da main é confirmar o bug, não achar cenário errado.

---

## J1.12 · O convite sobrevive ao desvio do login · **crítico**

Jornada do fluxo 3 (o convite é dele); aqui fica só o que a autenticação tem que garantir:
**o convite não se perde nos desvios do login.**

**Ator:** pessoa convidada · **Telas:** deeplink → 1b → (1a) → 1c → grupo
**Massa:** deeplink de convite válido, de um grupo com nome reconhecível.

**Caminho principal**
1. abrir o deeplink do convite
2. a 1b abre mostrando o nome do grupo e quem convidou
3. criar a conta
4. → termina **dentro do grupo**, não na Início genérica

**Variações**

| ID | Desvio | Esperado | Crít. |
|---|---|---|---|
| J1.12-a | passo 3 com e-mail já cadastrado → tocar "Entrar?" → entrar na 1a | ainda termina **dentro do grupo**: o convite fica guardado no coordinator, não na tela | ✅ |
| J1.12-b | preview do convite falha (sem rede no passo 2) | a 1b abre genérica, sem nome de grupo — e o convite **continua valendo** ao fim do cadastro | ✅ |
| J1.12-c | grupo que exige aprovação | termina em pedido pendente, não dentro do grupo | |
| J1.12-d | matar o app entre os passos 2 e 3 e reabrir | ou o convite sobrevive, ou a pessoa volta ao começo com mensagem clara — nunca cadastro concluído com convite perdido em silêncio | ✅ |
| J1.12-e | deeplink expirado / já usado | mensagem própria, não erro genérico | |

---

## Massa necessária

O que o fluxo 1 exige do ambiente, em três níveis de custo.

**O QA cria sozinho, pela UI** — conta nova (e-mail inventado), conta com senha conhecida,
e-mail já cadastrado (é a mesma conta anterior), conta com e-mail não verificado (toda
conta nova nasce assim).

**Precisa de seed** — conta com sessão válida e telefone vazio (J1.8); conta com e-mail
verificado (J1.10-b); conta com sessão revogada no servidor (J1.6-c); duas contas com dados
visivelmente distintos, cada uma no seu grupo (J1.11); e quatro deeplinks de convite
prontos: válido, de grupo que exige aprovação, expirado e já usado (J1.12).

**Depende de coisa fora do backend** — conta Google real logada no aparelho ou emulador
(J1.2, J1.4); forma de derrubar a rede entre app e backend (J1.1-h, J1.6-a, J1.7);
conta descartável para queimar no bloqueio do provedor (J1.1-g, que não será automatizado).

**Já resolvido, não precisa de nada** — leitura do código de reset e do link de verificação:
Mailpit, com e-mails inventados à vontade.

---

## Pendências que este documento levanta

1. **DNS e senha do Mailpit no Server Dev.** `saqz-mail.brunoalmeida.dev` precisa apontar
   para o servidor e `SAQZ_MAILPIT_AUTH` precisa existir no `.env` de lá, senão o `up`
   falha de propósito.
2. **Seeds.** A lista acima é o backlog. Sem ela, J1.6-c, J1.8, J1.10-b, J1.11 e J1.12 não
   rodam nem manualmente.
3. **VUL-204 fora da main.** Enquanto o merge não acontece, J1.11 é bug conhecido.
