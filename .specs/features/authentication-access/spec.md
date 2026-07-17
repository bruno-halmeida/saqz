# Especificação de Autenticação e Acesso

**Status:** Aprovada; pronta para Design
**Data:** 2026-07-15
**Origem:** ClickUp `86ajh0q0x`, reconciliado com `AD-004`, `AD-005`,
`AD-017` e `AD-018`

## Problema

O app já inicializa Firebase Auth em Android/iOS e o backend já valida bearer
tokens, mas ainda não oferece uma jornada de conta nem conhece usuários, grupos
ou papéis persistidos. Owners e atletas precisam entrar no produto, acessar
somente os grupos aos quais pertencem e alternar entre esses contextos sem
misturar identidade Firebase com autorização de negócio.

## Objetivos

- [ ] Entregar cadastro, login, verificação de email, recuperação de senha,
  Google Sign-In, sessão persistente e logout em Android/iOS.
- [ ] Sincronizar cada identidade Firebase verificada com um único usuário
  persistido no backend.
- [ ] Criar grupos com um `OWNER` único e permitir vários `ADMIN`s e `ATHLETE`s.
- [ ] Autorizar toda operação de grupo pelo vínculo e papel persistidos no
  backend, nunca por informação enviada pelo cliente.
- [ ] Permitir entrada como `ATHLETE` por um único convite opaco ativo por
  grupo, reutilizável até expiração ou rotação manual.
- [ ] Permitir seleção e troca de grupo para usuários com múltiplos vínculos.
- [ ] Permitir que `OWNER` e `ADMIN` editem somente as configurações gerais do
  grupo nesta fase.
- [ ] Manter os fluxos verificáveis sem credenciais ou serviços de produção.
- [ ] Manter coleção Bruno versionada para cada contrato HTTP backend entregue.

## Fora de Escopo

| Item | Motivo |
| --- | --- |
| Aplicativo web ou alteração da landing | `AD-017` mantém somente o app Android/iOS e a landing estática. |
| Apple Sign-In, biometria e MFA | Métodos futuros; esta fase cobre email/senha e Google. |
| Co-owner ou transferência de ownership | O criador permanece o único responsável pelo grupo e pela futura assinatura. |
| Assinatura, cobrança do owner e administração de plano | Pertencem ao Épico 08; esta fase apenas preserva o `OWNER` único. |
| Configurações de jogos | Pertencem ao Épico 05. |
| Configurações financeiras | Pertencem ao Épico 06. |
| Cadastro e perfil esportivo, tipo mensalista/avulso e remoção de atleta | Pertencem ao Épico 04; o vínculo `ATHLETE` desta fase representa somente acesso. |
| Convite individual, por email ou com aprovação manual | O produto terá um convite compartilhável por grupo. |
| Exclusão de conta, exclusão de grupo e saída voluntária | Exigem política própria de lifecycle e histórico. |
| Logout global/revogação de outras sessões | Logout desta fase afeta somente o dispositivo atual. |
| Uso offline de conteúdo protegido | Sem backend acessível, o app não expõe dados de grupo. |
| Permissões granulares/customizadas | Papéis fixos `OWNER`, `ADMIN` e `ATHLETE` cobrem esta fase. |

---

## Decisões e Premissas

| Tema | Escolha | Justificativa | Estado |
| --- | --- | --- | --- |
| Superfícies | Compose Multiplatform em Android/iOS; sem produto web | Decisão `AD-017`. | Confirmado |
| Fundação existente | Reusar ambientes Firebase, filtro Spring Security e `/api/session` | Já entregues no Épico 01; serão estendidos, não reimplementados. | Confirmado |
| Provedores | Email/senha e Google Sign-In | Escopo explícito do ClickUp. | Confirmado |
| Verificação | Email/senha exige email verificado antes de criar ou entrar em grupo; Google verificado segue direto | Evita vínculos de grupo com endereço não comprovado. | Confirmado pelo usuário |
| Identidade autoritativa | Firebase `uid` identifica conta; email não une nem substitui contas | Evita merge inseguro por dado mutável. | Premissa segura |
| Persistência | Usuário, grupo, membership e convite são duráveis no backend | Autorização não pode depender do cliente/Firebase claims. | Premissa arquitetural |
| Papéis | Um `OWNER`, vários `ADMIN`s e vários `ATHLETE`s por grupo | Owner único responde pela futura cobrança; admins delegam operação. | Confirmado pelo usuário |
| Administração | Somente `OWNER` concede/remove `ADMIN`; `ADMIN` não altera ownership nem assinatura | Impede escalada e preserva o responsável financeiro. | Premissa segura |
| Convite | Um código opaco ativo por grupo, reutilizável, sem expiração automática | Owner/admin controla expiração/rotação; atende compartilhamento do grupo. | Confirmado; TTL assumido |
| Entrada | Convite sempre cria `ATHLETE`; promoção posterior é exclusiva do `OWNER` | Convite compartilhável não concede privilégio. | Premissa segura |
| Configuração geral | Nome e timezone IANA do grupo | Jogos e financeiro permanecem nos épicos donos. | Recorte confirmado |
| Criação de grupo | Qualquer usuário verificado pode criar grupo; entrada como atleta exige convite | Permite ser athlete em um grupo e owner em outro sem descoberta pública. | Premissa de fluxo |
| Seleção | Um vínculo seleciona automaticamente; múltiplos mostram seletor e permitem troca | Reduz etapa sem esconder multiplicidade. | Premissa de UX |
| Logout | Encerra Firebase no dispositivo e limpa contexto local; não apaga dados nem revoga outros dispositivos | Limite explícito desta fase. | Premissa de lifecycle |
| Deeplink | Origem HTTPS própria configurável, com código opaco no path; URL do GitHub Pages não é contrato do app | App/Universal Links exigem origem controlada a definir no Design. | Premissa de implantação |
| Senha | Política e throttling de credenciais pertencem ao Firebase; o app não cria regra divergente | Mantém um único enforcement de credencial. | Premissa externa |
| Teste manual backend | Toda mudança HTTP observável atualiza request e assertions em `bruno/` | Impede contrato sem fluxo reproduzível. | Confirmado pelo usuário |

**Open questions:** nenhuma. O domínio exato do deeplink e os contratos HTTP
serão definidos no Design sem alterar os comportamentos desta spec.

## Regras de Domínio

- Cada grupo possui exatamente um membership `OWNER` ativo.
- Cada par usuário-grupo possui no máximo um membership e um papel.
- Um usuário pode ter papéis diferentes em grupos diferentes.
- `OWNER` possui todas as permissões de `ADMIN` e é o único que concede ou
  remove `ADMIN`.
- `ADMIN` pode editar configurações gerais, gerar/expirar convite e administrar
  atletas dentro do que os épicos posteriores implementarem.
- `ATHLETE` pode ler o grupo selecionado, mas não editar configuração, convite
  ou papéis.
- Não membro recebe `404` em recurso de grupo; membro sem papel suficiente
  recebe `403`. Isso evita confirmar a existência de grupos a terceiros.
- Nome de usuário e nome de grupo, após trim, aceitam de 2 a 80 caracteres e
  rejeitam string vazia ou caracteres de controle. Timezone deve ser um ID IANA
  válido.
- Código de convite contém ao menos 128 bits de entropia, não incorpora ID do
  grupo e nunca é persistido ou registrado em texto puro.

## Histórias e Critérios de Aceite

### P1: Criar e acessar uma conta

**User Story:** Como usuário, quero entrar com email/senha ou Google para
retomar minha conta com segurança em Android ou iOS.

**Why P1:** Nenhuma feature autenticada é alcançável sem esta jornada.

1. **AUTH-01** - WHEN usuário enviar nome, email válido e senha aceita pelo
   Firebase THEN o app SHALL criar a conta uma vez, enviar verificação e mostrar
   estado `Aguardando verificação`, sem liberar operação de grupo.
2. **AUTH-02** - WHEN usuário de email/senha abrir link válido de verificação
   ou tocar `Já verifiquei` após confirmar o endereço THEN o app SHALL atualizar
   o token e continuar do ponto pendente; conta não verificada SHALL continuar
   bloqueada e permitir reenvio com feedback de cooldown do provedor.
3. **AUTH-03** - WHEN Google Sign-In concluir com email verificado THEN o app
   SHALL autenticar a mesma conta Firebase e seguir ao bootstrap; cancelamento
   SHALL permanecer na tela atual sem criar conta backend parcial.
4. **AUTH-04** - WHEN credenciais forem inválidas, rede falhar ou o provedor
   estiver indisponível THEN o app SHALL preservar os campos não sensíveis,
   encerrar loading e mostrar erro acionável sem expor detalhe do provedor.
5. **AUTH-05** - WHEN recuperação receber qualquer email sintaticamente válido
   THEN o app SHALL solicitar o reset ao Firebase e sempre mostrar confirmação
   neutra, sem revelar se a conta existe.
6. **AUTH-06** - WHEN o processo reiniciar com sessão Firebase válida THEN o app
   SHALL pular login e executar bootstrap; ausência de sessão SHALL mostrar
   login.
7. **AUTH-07** - WHEN usuário confirmar logout THEN o app SHALL encerrar a
   sessão Firebase local, limpar grupo/invite selecionados e voltar ao login;
   dados persistidos no backend e sessões de outros dispositivos SHALL permanecer.
8. **AUTH-08** - WHEN um email já pertencer a outro método Firebase THEN o app
   SHALL orientar o método existente e SHALL não criar/mesclar usuário backend
   por igualdade de email.

**Independent Test:** cadastrar e verificar conta no Auth Emulator, reiniciar o
app, recuperar senha e sair; repetir entrada com fixture Google nos dois targets.

### P1: Sincronizar sessão com o backend

**User Story:** Como usuário autenticado, quero que o backend reconheça minha
conta e meus grupos sem duplicação para acessar o estado correto do produto.

1. **SESSION-01** - WHEN token Firebase válido com `email_verified=true` chegar
   ao bootstrap THEN o backend SHALL criar ou atualizar idempotentemente um
   único usuário pelo `uid` e retornar identidade, memberships e papéis atuais;
   token de conta não verificada SHALL receber `403` sem criar usuário.
2. **SESSION-02** - WHEN o mesmo `uid` executar bootstrap concorrentemente ou
   após retry THEN o backend SHALL retornar o mesmo usuário sem duplicar registro.
3. **SESSION-03** - WHEN uma request receber `401` por token expirado THEN o
   cliente SHALL forçar refresh e repetir a request uma vez; segundo `401` SHALL
   limpar a sessão local e exigir login.
4. **SESSION-04** - WHEN backend ou verificador estiver indisponível THEN o app
   SHALL mostrar estado de erro com retry, preservar a sessão Firebase e SHALL
   não renderizar conteúdo protegido nem redirecionar para login.
5. **SESSION-05** - WHEN email verificado ou nome mudar no Firebase THEN o
   próximo bootstrap SHALL atualizar os campos espelhados sem mudar o ID interno,
   papel ou membership.

**Independent Test:** repetir e concorrer bootstrap com token real do emulator,
simular refresh/indisponibilidade e provar uma linha de usuário e nenhum dado
protegido durante falha.

### P1: Criar grupo e delegar administração

**User Story:** Como organizador, quero criar meu grupo e delegar admins sem
perder a responsabilidade de owner.

1. **GROUP-01** - WHEN qualquer usuário verificado enviar nome válido e timezone
   IANA THEN o backend SHALL criar grupo e membership `OWNER` na mesma transação
   e o app SHALL selecionar o grupo criado, independentemente de outros vínculos.
2. **GROUP-02** - WHEN criação for repetida com a mesma chave idempotente por
   timeout, duplo toque ou retry THEN SHALL retornar o mesmo grupo e único owner.
3. **GROUP-03** - WHEN `OWNER` promover um `ATHLETE` THEN SHALL existir um único
   membership `ADMIN`; repetir a promoção SHALL ser idempotente.
4. **GROUP-04** - WHEN `OWNER` remover privilégio de `ADMIN` THEN o membership
   SHALL virar `ATHLETE`; último/único `OWNER` SHALL nunca ser rebaixado ou
   removido nesta fase.
5. **GROUP-05** - WHEN `ADMIN` ou `ATHLETE` tentar conceder/remover admin,
   transferir ownership ou acessar assinatura THEN backend SHALL responder
   `403` sem mutação.
6. **GROUP-06** - WHEN `OWNER` ou `ADMIN` alterar nome/timezone válidos THEN o
   backend SHALL persistir ambos atomicamente; `ATHLETE` SHALL receber `403`.
7. **GROUP-07** - WHEN usuário não membro requisitar grupo por ID conhecido ou
   aleatório THEN backend SHALL responder `404` com o mesmo contrato público.

**Independent Test:** criar um grupo, promover/rebaixar vários admins e executar
a matriz owner/admin/athlete/não membro sobre configuração e papéis.

### P1: Entrar por convite

**User Story:** Como atleta convidado, quero abrir um link e entrar no grupo
correto mesmo que ainda precise instalar o app ou criar minha conta.

1. **INVITE-01** - WHEN `OWNER` ou `ADMIN` gerar convite sem um ativo THEN o
   backend SHALL criar código aleatório opaco e retornar deeplink compartilhável;
   SHALL existir no máximo um convite ativo por grupo.
2. **INVITE-02** - WHEN gerar novo convite com um ativo THEN o backend SHALL
   invalidar o anterior e ativar o novo atomicamente; código anterior SHALL
   produzir o mesmo erro público de código inválido/expirado.
3. **INVITE-03** - WHEN `OWNER` ou `ADMIN` expirar o convite THEN nenhuma nova
   entrada SHALL usá-lo; `ATHLETE` SHALL receber `403` ao tentar gerenciar convite.
4. **INVITE-04** - WHEN pessoa não autenticada abrir deeplink válido THEN o app
   SHALL preservar o código por instalação/login/cadastro/verificação e somente
   tentar ingresso após sessão backend verificada.
5. **INVITE-05** - WHEN usuário verificado resgatar convite ativo THEN backend
   SHALL criar membership `ATHLETE` uma vez e selecionar o grupo; retries ou
   resgate por membro existente SHALL retornar sucesso sem duplicar ou trocar papel.
6. **INVITE-06** - WHEN código for ausente, malformado, expirado ou rotacionado
   THEN app SHALL mostrar `Convite inválido ou expirado`, sem revelar grupo nem
   criar membership.
7. **INVITE-07** - WHEN um usuário acumular 10 resgates inválidos em 10 minutos
   THEN tentativas adicionais nessa janela SHALL receber `429`; resgate válido e
   geração/expiração autorizada SHALL não consumir esse limite.
8. **INVITE-08** - WHEN dois usuários resgatarem o mesmo convite ativo em
   paralelo THEN ambos SHALL entrar como `ATHLETE`, sem consumir ou invalidar o
   convite compartilhável.

**Independent Test:** abrir link em instalação sem sessão, concluir cadastro e
verificação, entrar; provar rotação, expiração, reuso, retry e concorrência.

### P1: Selecionar o contexto do grupo

**User Story:** Como usuário com papéis diferentes, quero saber em qual grupo
estou e trocar de contexto sem vazar dados entre grupos.

1. **SELECT-01** - WHEN bootstrap retornar zero memberships THEN app SHALL
   mostrar estado sem grupo com ação `Criar grupo`; ingresso como atleta SHALL
   continuar disponível somente por deeplink.
2. **SELECT-02** - WHEN retornar um membership THEN app SHALL selecionar esse
   grupo e mostrar nome e papel atual.
3. **SELECT-03** - WHEN retornar dois ou mais memberships THEN app SHALL mostrar
   lista de todos os grupos com papel por grupo antes do conteúdo e permitir
   selecionar exatamente um; seletor SHALL oferecer ação `Criar grupo` para
   qualquer usuário verificado.
4. **SELECT-04** - WHEN usuário trocar de grupo THEN toda leitura/mutação
   subsequente SHALL usar o novo contexto e conteúdo do grupo anterior SHALL
   deixar a tela antes da nova carga.
5. **SELECT-05** - WHEN app reiniciar THEN SHALL restaurar a última seleção
   somente se o bootstrap ainda contiver o membership; caso contrário SHALL
   descartar a seleção e aplicar `SELECT-01..03`.
6. **SELECT-06** - WHEN papel mudar no backend THEN o próximo bootstrap/refresh
   SHALL atualizar ações disponíveis; UI escondida SHALL nunca substituir o
   enforcement backend.

**Independent Test:** usar uma conta owner em um grupo e athlete/admin em
outros, alternar e reiniciar, depois remover/alterar fixture de membership.

### P1: Segurança, atomicidade e diagnóstico

**User Story:** Como operador, quero falhas diagnosticáveis sem expor credencial
ou deixar autorização parcialmente aplicada.

1. **SEC-01** - WHEN autenticação, convite ou autorização produzir log/problem
   THEN SHALL incluir correlation ID e resultado estável, mas SHALL não incluir
   bearer token, código de convite, senha ou email completo.
2. **SEC-02** - WHEN criação de grupo, troca de papel, edição de configuração,
   rotação ou resgate falhar antes do commit THEN nenhuma parte da mutação SHALL
   permanecer; retry SHALL observar estado anterior ou resultado completo.
3. **SEC-03** - WHEN eventos forem medidos THEN SHALL distinguir bootstrap
   success/failure, `401`, `403`, `404`, `429`, convite gerado/expirado/resgatado
   e falha do provedor, sem usar dado pessoal como label.
4. **SEC-04** - WHEN gates locais/CI executarem THEN SHALL provar fluxos com
   Firebase Auth Emulator e banco descartável, sem credencial de produção, além
   das suites comuns, Android e iOS existentes.
5. **SEC-05** - WHEN contrato HTTP backend for adicionado, alterado ou removido
   THEN mesma mudança SHALL atualizar coleção Bruno versionada com request e
   assertions correspondentes; requests MAY ser organizadas em subdiretórios
   contextuais e o gate SHALL buscar recursivamente; rota explícita sem request
   SHALL falhar no gate.

**Independent Test:** injetar falhas antes/depois das fronteiras transacionais,
inspecionar logs/métricas e executar o fluxo completo em ambiente descartável.

## Casos Limite

- **EDGE-01** - WHEN Google retornar conta sem nome utilizável THEN app SHALL
  exigir nome válido antes do bootstrap criar/atualizar usuário.
- **EDGE-02** - WHEN o deeplink chegar mais de uma vez durante autenticação THEN
  app SHALL manter apenas o último código válido recebido e SHALL não resgatar
  antes da verificação.
- **EDGE-03** - WHEN convite pertencer ao grupo onde usuário já é `OWNER` ou
  `ADMIN` THEN resgate SHALL preservar o papel atual.
- **EDGE-04** - WHEN grupo tiver vários admins THEN rebaixar um SHALL não alterar
  os demais nem o owner.
- **EDGE-05** - WHEN nome/timezone forem inválidos THEN app SHALL associar erro
  ao campo e backend SHALL rejeitar com `400`, sem persistência parcial.
- **EDGE-06** - WHEN Google Sign-In for cancelado ou password reset voltar ao
  app THEN navegação SHALL ter um único destino e não duplicar telas no back stack.
- **EDGE-07** - WHEN rotação, background/foreground ou restauração ocorrer
  durante loading THEN submit/resgate SHALL permanecer single-flight e ações
  SHALL continuar alcançáveis com IME, font scale e Dynamic Type máximos.

## Dimensões Implícitas

| Dimensão | Resolução |
| --- | --- |
| Validação e limites | Nomes 2..80, timezone IANA, email sintático, senha pelo Firebase e convite opaco >=128 bits. |
| Falha/partial failure | `AUTH-04`, `SESSION-04`, `SEC-02` e mensagens estáveis sem conteúdo protegido. |
| Retry/duplicidade | `SESSION-02`, `GROUP-02`, `INVITE-05` e single-flight em `EDGE-07`. |
| Auth/rate limits | Backend deriva principal do token, matriz fixa de papéis e `INVITE-07`; credenciais são limitadas pelo Firebase. |
| Concorrência/ordem | Owner/grupo atômicos, rotação atômica e resgate paralelo em `INVITE-08`. |
| Lifecycle/expiração | Convite expira/rotaciona manualmente; logout é local; exclusões e transferência estão fora. |
| Observabilidade | `SEC-01` e `SEC-03`, com correlation ID e sem secrets/PII em labels. |
| Dependência externa | `AUTH-04`, `SESSION-03..04`; indisponibilidade não vira logout nem acesso offline. |
| Transições de estado | Rotas auth -> verificação -> bootstrap -> grupo e papéis OWNER/ADMIN/ATHLETE possuem guards explícitos. |

## Rastreabilidade

| Requisitos | História | Fase | Status |
| --- | --- | --- | --- |
| AUTH-01..08 | Criar e acessar conta | Design | Pending |
| SESSION-01..05 | Sincronizar sessão | Design | Pending |
| GROUP-01..07 | Criar grupo/delegar administração | Design | Pending |
| INVITE-01..08 | Entrar por convite | Design | Pending |
| SELECT-01..06 | Selecionar contexto | Design | Pending |
| SEC-01..05 | Segurança/diagnóstico | Design | Pending |
| EDGE-01..07 | Casos limite transversais | Design | Pending |

**Cobertura:** 46 requisitos aguardam mapeamento no Design e em Tasks.

## Backprop Log

| ID | Date | Root cause | Guard |
| --- | --- | --- | --- |
| B1 | 2026-07-16 | O gate KMP executava apenas testes do simulador iOS, permitindo que `commonMain` dependesse acidentalmente do classpath de teste e falhasse ao compilar para Android. | SEC-04; Quick access mobile compila Android antes de `allTests`. |
| B2 | 2026-07-16 | O gate Bruno buscava requests apenas na raiz da coleção e rejeitava organização por contexto. | SEC-05; busca recursiva coberta por fixture aninhada. |
| B3 | 2026-07-17 | Os XCUITests iOS legados continuaram exigindo Home/Catálogo após T45 substituir o shell pelo fluxo autenticado. | AUTH-06, EDGE-07 e o Full iOS já exigem cold start no Login, ausência de conteúdo protegido e ações alcançáveis em Dynamic Type máximo; nenhum novo invariante necessário. |
| B4 | 2026-07-17 | Protocolos Kotlin exportados via Objective-C não carregam isolamento `MainActor`, e Swift 6 rejeitou a conformidade actor-isolated do auth adapter. | O contrato T50 exige callbacks no `MainActor`; conformidade usa `@preconcurrency` e Full iOS compila a fronteira em Swift 6; nenhum novo invariante necessário. |
| B5 | 2026-07-17 | Labels de callbacks Kotlin colidiram após export Objective-C e chegaram ao Swift como `result_:`/`result__:` em vez do nome comum original. | O Full iOS compila consumidores Swift contra o header real do umbrella framework; nenhum novo invariante necessário. |
| B6 | 2026-07-17 | Um `Task @MainActor` tentou transferir o resultado não-`Sendable` do callback Google e Swift 6 rejeitou o possível data race. | O callback Google documentado na main queue permanece no executor com `MainActor.assumeIsolated`; Full iOS compila em concorrência estrita; nenhum novo invariante necessário. |
| B7 | 2026-07-17 | O script iOS exigia chaves Google em todo plist Firebase local e quebrou o build Prod quando elas ainda não estavam provisionadas. | Firebase continua empacotado por ambiente, enquanto `GIDClientID` e o URL scheme são injetados somente quando ambas as chaves Google existem; Full iOS cobre Dev e Prod. |
| B8 | 2026-07-17 | A referência direta ao método do router usou uma forma de `onOpenURL` sem o label `perform:` exigido pela API SwiftUI compilada. | O Full iOS compila o lifecycle bridge contra o SDK SwiftUI real em Dev e Prod; nenhum novo invariante necessário. |

## Critérios de Sucesso

- [ ] Conta de email verificada e conta Google concluem bootstrap em Android e iOS.
- [ ] Usuário cria grupo como único owner ou entra como athlete pelo convite.
- [ ] Vários admins operam o grupo sem adquirir ownership ou acesso à assinatura.
- [ ] Usuário alterna entre grupos com papéis diferentes sem vazamento de dados.
- [ ] Convite pode ser compartilhado, rotacionado e expirado sem expor group ID.
- [ ] Reinício preserva sessão e seleção válidas; logout limpa somente o dispositivo.
- [ ] Matriz de autorização, retries, concorrência e falhas externas passa em gates
  automatizados credencial-free.
- [ ] Cada contrato HTTP backend entregue possui request/assertions Bruno versionados.
