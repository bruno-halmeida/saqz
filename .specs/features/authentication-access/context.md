# Contexto de Autenticação e Acesso

**Coletado em:** 2026-07-15
**Spec:** `.specs/features/authentication-access/spec.md`
**Status:** Aprovado; pronto para Design

## Limite da Feature

Entregar a primeira jornada autenticada Android/iOS: conta Firebase, usuário
backend, grupos e papéis, onboarding do owner, convite compartilhável, seleção
de grupo, configuração geral, recuperação e logout. Não há produto web,
configuração de jogos/financeiro, gestão esportiva de atletas ou assinatura.

## Decisões de Implementação

### Plataformas e Fundação

- UI compartilhada em Compose Multiplatform para Android e iOS.
- SDKs Firebase nativos permanecem nos adapters de plataforma.
- Firebase por ambiente, bearer filter Spring e `/api/session` existentes são
  fundação a estender, não subtarefas a repetir.
- Landing estática não recebe login nem lógica de produto.

### Identidade

- Login/cadastro suportam email/senha e Google.
- Email/senha exige verificação antes de criar ou entrar em grupo.
- Firebase `uid` é a chave externa; email nunca faz merge de conta.
- Sessão persiste pelo Firebase; logout afeta somente o dispositivo atual.

### Papéis e Ownership

- Criador do grupo é o único `OWNER` e futuro responsável pela cobrança.
- Um grupo pode ter vários `ADMIN`s, sem limite artificial de produto.
- `ADMIN` administra grupo, convite e atletas, mas não ownership ou assinatura.
- Somente `OWNER` concede/remove `ADMIN`; convite sempre cria `ATHLETE`.
- Co-owner e transferência de ownership ficam adiados.

### Convite

- Cada grupo tem no máximo um convite ativo.
- Código é aleatório, opaco e reutilizável por vários atletas.
- Owner/admin pode expirar ou rotacionar; rotação invalida o código anterior.
- Não há expiração automática nesta fase.
- Deeplink pendente sobrevive a instalação, login, cadastro e verificação.
- Resgate repetido pelo mesmo usuário é idempotente e preserva papel superior.

### Grupo e Configurações

- Onboarding cria grupo e membership owner na mesma operação.
- Configuração geral contém somente nome e timezone IANA.
- Jogos e financeiro permanecem nos épicos 5 e 6.
- Qualquer usuário verificado pode criar grupo; atleta entra somente por convite.
- Um grupo é selecionado automaticamente; vários exigem seleção e permitem troca.

### Falhas e Segurança

- Indisponibilidade de backend/provedor mostra retry e nunca libera conteúdo
  protegido nem encerra uma sessão ainda válida.
- Token expirado recebe um refresh/retry; segunda rejeição volta ao login.
- Não membro recebe `404`; papel insuficiente recebe `403`.
- Mutações sensíveis são atômicas e idempotentes onde retry é esperado.
- Tokens, senhas, códigos de convite e emails completos não aparecem em logs.

### Discrição do Agente

- Composição visual exata das telas dentro do design system aprovado.
- Copy secundária e escolha de ícones, preservando os estados definidos na spec.
- Contratos HTTP, nomes internos, token alphabet e mecanismo transacional serão
  propostos no Design sem alterar os resultados observáveis.

### Áreas Não Discutidas Convertidas em Premissas

- Não há TTL automático do convite; expiração é manual.
- Somente owner altera quem é admin.
- Grupo geral possui nome e timezone; outros campos aguardam épico dono.
- Logout não revoga outros dispositivos.
- Seleção do último grupo é local e só restaura membership ainda válido.
- Resgate inválido tem limite de 10 tentativas por usuário em 10 minutos.

## Referências

- ClickUp Épico 03 `86ajh0q0x` e suas 13 subtarefas.
- ClickUp Épicos 04 `86ajh0q10`, 05 `86ajh0q1p` e 06 `86ajh0q2c` para os
  limites de atleta, jogos e financeiro.
- `AD-004`, `AD-005`, `AD-017` e `AD-018` em `.specs/STATE.md`.
- Identidade existente em `backend/features/identity/`.
- Bootstrap Firebase existente em `mobile/android-app/` e `mobile/ios-app/`.

## Ideias Adiadas

- Co-owner e transferência de ownership.
- Convite individual, por email, aprovação manual ou TTL automático.
- Apple Sign-In, biometria e MFA.
- Aplicativo web autenticado.
- Exclusão de conta/grupo e logout global.
- Configurações esportivas, financeiras e assinatura.
- Pipeline de observabilidade, métricas, agregação de logs, dashboards e alertas;
  acompanhar no épico ClickUp `86ajk0wmb` antes de criar uma nova spec.
