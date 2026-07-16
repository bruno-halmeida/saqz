# Especificação da Fundação Mobile de Interface e Design System

**Status:** Verified
**Data:** 2026-07-15
**Origem:** ClickUp `86ajh0q0n`, revisado pela decisão mobile-first

## Problema

O app Android/iOS possui apenas um shell mínimo. Antes das features de produto,
ele precisa de tema, componentes, navegação, estados, localização e
acessibilidade compartilhados por Compose Multiplatform. O plano anterior
duplicava essa fundação em Angular sem demanda validada; essa superfície foi
retirada e a landing estática permanece como única presença web.

## Objetivos

- [x] Entregar uma fundação Compose compartilhada por Android e iOS.
- [x] Criar componentes reutilizáveis com estados, acessibilidade e motion
  definidos antes das features de produto.
- [x] Entregar shells navegáveis de Home e Componentes em Android e iOS.
- [x] Disponibilizar um catálogo de componentes pela Home.
- [x] Padronizar estados assíncronos e formatação pt-BR de data, hora e BRL.
- [x] Substituir placeholders sem introduzir autenticação ou dados fictícios.
- [x] Preservar a landing e a independência entre backend e mobile.

## Fora de Escopo

| Item | Motivo |
| --- | --- |
| Aplicativo web, Angular, Compose Web, Kotlin/JS ou Kotlin/Wasm | Web de produto será reavaliado somente após demanda dos usuários. |
| Alteração ou migração de `landing-page/` | A landing permanece estática, independente e protegida. |
| Autenticação, logout, avatar e papel real | Pertencem ao Épico 03. |
| Grupos, jogos, atletas, financeiro e dashboards | São features de negócio posteriores. |
| Integração com backend, cliente OpenAPI, cache e offline | Esta feature é apresentação e navegação local. |
| Dark mode | A direção desta fase é clara. |
| SF empacotada ou fontes por rede | iOS usa SF do sistema; Android usa Inter local. |
| Retry e validação de negócio | O design system oferece slots/callbacks; a feature dona define regras. |

## Decisões e Premissas

| Tema | Escolha | Justificativa |
| --- | --- | --- |
| Produto | Um app Compose Multiplatform para Android e iOS | Concentra esforço em uma superfície. |
| Landing | Permanece HTML/CSS independente | Preserva aquisição, SEO e deploy. |
| Web futuro | Nova spec e nova avaliação | Não assume port automático para Compose Web. |
| Módulos | `:core:common`, `:core:design-system` e umbrella `:compose-app` | Primitivas estáveis sem múltiplos frameworks iOS. |
| Nomenclatura | Prefixo `Saqz*` | Remove referências herdadas a AIS Group. |
| Direção visual | Apple-inspired, mas identidade Saqz | Baixa densidade, chrome discreto e cores próprias. |
| Tipografia | SF do sistema no iOS; Inter estática local no Android | Determinismo desde API 23 sem rede. |
| Implementação mobile | Compose-first em Android/iOS; nativo apenas onde a plataforma exigir | Maximiza compartilhamento sem substituir launchers e SDKs nativos. |
| Catálogo | Disponível pela Home nesta fase | Torna a fundação inspecionável sem app paralelo. |
| Splash | Launch screen nativa, sem timer | Usa o ciclo de vida do sistema. |
| Localização | pt-BR, 24 horas e fuso local | Público inicial e resultados determinísticos. |
| Dinheiro | Centavos inteiros | Evita ponto flutuante. |
| Motion | Rápido e funcional | Mantém feedback abaixo de 250 ms. |
| Verificação manual | Recomendada e não bloqueante | Evidência automatizada e Verifier determinam `Verified`; achados manuais geram follow-up. |

**Open questions:** nenhuma.

## Contrato Visual

| Token | Valor | Uso |
| --- | --- | --- |
| `background` | `#F5F5F7` | Fundo principal |
| `surface` | `#FFFFFF` | Cards, dialogs e campos |
| `surface-subtle` | `#F4F8FB` | Região secundária |
| `surface-pearl` | `#FAFAFC` | Controle secundário |
| `surface-dark` | `#071025` | Demonstração escura, não tema global |
| `on-dark` | `#FFFFFF` | Conteúdo principal em superfície escura |
| `text-muted-on-dark` | `#CCCCCC` | Conteúdo secundário em superfície escura |
| `primary` | `#0638DF` | Ação, seleção e foco |
| `on-primary` | `#FFFFFF` | Conteúdo sobre primary |
| `accent` | `#C7F300` | Destaque esportivo não clicável |
| `on-accent` | `#1D1D1F` | Conteúdo sobre accent |
| `text-primary` | `#1D1D1F` | Texto principal |
| `text-secondary` | `#6E6E73` | Texto secundário significativo |
| `text-muted` | `#707075` | Conteúdo suplementar com contraste AA em fundos claros aprovados |
| `control-border` | `#85858A` | Limite de controle com contraste não textual `>=3:1` |
| `border` | `#D2D2D7` | Divisor estrutural não interativo |
| `hairline` | `#E0E0E0` | Separação decorativa de 1dp |
| `divider-soft` | `#F0F0F0` | Divisor decorativo secundário |
| `info-surface` | `#EAF0FF` | Informação |
| `info-foreground` | `#0638DF` | Informação |
| `success-surface` | `#E8F5EC` | Sucesso |
| `success-foreground` | `#1D6B35` | Sucesso |
| `warning-surface` | `#FFF4D6` | Alerta |
| `warning-foreground` | `#7A4B00` | Alerta |
| `error-surface` | `#FDEBEC` | Erro |
| `error-foreground` | `#B42318` | Erro/destructive |
| `disabled-surface` | `#E8E8ED` | Controle desabilitado |
| `disabled-foreground` | `#6E6E73` | Conteúdo desabilitado |

### Métricas

| Métrica | Valor mobile |
| --- | --- |
| Grid | `8dp`, sub-grid `4dp` |
| Padding horizontal | `16dp` mais safe areas |
| Padding vertical de seção | `48dp` |
| Padding de utility card | `16dp` |
| Botão primário | raio `12dp` |
| Controle compacto | raio `8dp` |
| Card | raio `16dp` |
| Bottom nav | `56dp` mais inset inferior |
| Alvo interativo mínimo | `48x48dp` |

### Tipografia

- Android empacota Inter estática 300/400/600/700; iOS usa SF do sistema.
- Nenhuma fonte é carregada por rede.
- Texto respeita font scale Android e Dynamic Type iOS sem clamp.
- Compose aplica a escala tipográfica uma única vez nos dois targets; Swift não
  recalcula tamanhos por `UIFont.TextStyle`.

| Token | Tamanho | Weight | Line height | Tracking |
| --- | --- | --- | --- | --- |
| `hero-display` | `34sp` | `600` | `1.07` | `-0.005em` |
| `display-lg` | `32sp` | `600` | `1.10` | `-0.009em` |
| `display-md` | `28sp` | `600` | `1.20` | `-0.011em` |
| `lead` | `22sp` | `400` | `1.14` | `0.007em` |
| `body` | `17sp` | `400` | `1.47` | `-0.022em` |
| `body-strong` | `17sp` | `600` | `1.24` | `-0.022em` |
| `caption` | `14sp` | `400` | `1.43` | `-0.016em` |
| `nav-link` | `12sp` | `400` | `1.00` | `-0.010em` |

### Composição e Motion

- Home e catálogo usam baixa densidade, superfícies e whitespace.
- Cards, botões e texto não usam drop shadow ou gradiente decorativo.
- `primary` sinaliza ação; `accent` nunca sinaliza clicabilidade.
- Press usa escala `0.95` por `100-160 ms`.
- Foco/cor usa `150-200 ms`; rota/estado usa `180-250 ms`, opacity e
  translate de no máximo `8dp`.
- Reduced Motion remove translate/scale; Reduce Transparency usa chrome opaco.

## Matriz de Evidência Obrigatória

| Superfície | Evidência automatizada | Evidência manual |
| --- | --- | --- |
| Core comum | `:core:common:allTests` cobre estado e formatadores. | N/A |
| Design system | `:core:design-system:allTests` cobre tokens, componentes, recursos, semantics e motion. | N/A |
| Compose app | `:compose-app:allTests` cobre shell, strings, menus e navegação comum. | N/A |
| Android | Unit/instrumented devDebug em API 30/35 cobrem fonte, navegação, rotação, font scale e cold start. | Checklist recomendado e não bloqueante: TalkBack, IME/landscape e cold start. |
| iOS | XCTest/XCUITest `SaqzDev` e build/unit Release `SaqzProd`. | Checklist recomendado e não bloqueante: VoiceOver, Dynamic Type, motion/transparency, rotação e cold start. |
| Isolamento | Mobile executa sem backend ou workspace web. | N/A |
| Landing/escopo | Landing, credenciais e scope passam com mutações discriminatórias. | N/A |

## Histórias e Critérios de Aceite

### P1: Contrato visual mobile

1. **VIS-01** - WHEN o build mobile for inspecionado THEN SHALL suportar
   Android e iOS por Compose Multiplatform, sem target de UI web.
2. **VIS-02** - WHEN o tema for avaliado THEN SHALL expor todos os tokens com
   os valores exatos desta spec.
3. **VIS-03** - WHEN `mobile/` for copiado isoladamente THEN recursos e tema
   SHALL resolver sem backend ou artefato externo.
4. **VIS-04** - WHEN texto for renderizado THEN Android SHALL usar Inter local
   estática e iOS SHALL usar fonte de sistema, sem request de fonte externa.
5. **VIS-05** - WHEN Home/catálogo forem renderizados THEN o wordmark local
   SHALL derivar de `landing-page/assets/saqz-logo.svg`; launch screens SHALL
   usar o símbolo quadrado derivado sem modificar o original.
6. **VIS-06** - WHEN tema envolver o app THEN cores SHALL ser aplicadas segundo
   seus usos semânticos.
7. **VIS-07** - WHEN orientação, safe area ou teclado mudar THEN controles
   SHALL permanecer visíveis e alcançáveis.
8. **VIS-08** - WHEN métricas e tipografia forem inspecionadas THEN SHALL
   corresponder exatamente às tabelas e todos os tokens SHALL aparecer no
   catálogo Android/iOS.

### P1: Componentes reutilizáveis

Inventário: `SaqzButton`, `SaqzInput`, `SaqzCard`, `SaqzListItem`, `SaqzBadge`,
`SaqzDialog`, `SaqzBottomSheet`, `SaqzLoadingState`, `SaqzErrorState`,
`SaqzEmptyState` e `SaqzBottomNav`.

1. **CMP-01** - WHEN o catálogo abrir THEN SHALL apresentar todas as variantes
   e estados aplicáveis do inventário.
2. **CMP-02** - WHEN `SaqzButton` receber focus THEN SHALL usar indicador
   `primary >=3:1`; WHEN pressed THEN escala `0.95` por `100-160 ms`; WHEN
   disabled THEN tokens/semântica disabled; WHEN loading THEN progresso visível,
   largura/nome preservados e busy; disabled/loading SHALL não disparar ação.
3. **CMP-03** - WHEN card/list item for interativo THEN SHALL expor ação,
   feedback imediato e alvo `48x48dp`; variante estática SHALL não simular ação.
4. **CMP-04** - WHEN badge usar `accent` THEN foreground SHALL ser `on-accent`;
   tons semânticos SHALL atender WCAG 2.2 AA.
5. **CMP-05** - WHEN overlay abrir THEN fundo SHALL ficar indisponível para
   interação/acessibilidade; título, ação e close SHALL ser acessíveis quando
   `dismissible=true`.
6. **CMP-06** - WHEN API pública for inspecionada THEN SHALL usar `Saqz*` e
   SHALL não introduzir `Ais*`.
7. **CMP-07** - WHEN input mostrar erro THEN mensagem SHALL estar associada;
   password toggle SHALL preservar foco, seleção e valor.
8. **CMP-08** - WHEN dialog/sheet abrir THEN back SHALL fechar somente se
   dismissible, conteúdo longo SHALL rolar sem esconder ações e foco de
   acessibilidade SHALL anunciar título e ação principal.

### P1: Shell e navegação

1. **NAV-01** - WHEN app iniciar THEN SHALL mostrar Home com logo, heading
   `Saqz` e ação `Explorar componentes`.
2. **NAV-02** - WHEN shell renderizar THEN bottom nav SHALL ter `56dp` mais
   inset, destinos `Início`/`Componentes` e estado selected acessível.
3. **NAV-03** - WHEN fixtures owner/atleta forem testadas THEN labels SHALL ser
   configuráveis, mas produção SHALL não expor perfil, avatar ou destinos falsos.
4. **NAV-04** - WHEN Home -> Componentes -> back executar THEN SHALL retornar
   à Home sem duplicar destinos; reselection SHALL ser idempotente.
5. **NAV-05** - WHEN shell for inspecionado THEN SHALL conter apenas Home e
   Componentes, sem login/logout ou placeholders de negócio.

### P1: Launch screen nativa

1. **LAUNCH-01** - WHEN cold start ocorrer THEN Android/iOS SHALL mostrar
   símbolo Saqz em `#F5F5F7` por recurso nativo.
2. **LAUNCH-02** - WHEN launch terminar THEN SHALL ir diretamente ao shell, sem
   timer, segunda splash Compose ou retenção artificial.
3. **LAUNCH-03** - WHEN startup real estiver Loading/Error THEN o shell SHALL
   renderizar `SaqzUiState`, não prolongar a launch screen.
4. **LAUNCH-04** - WHEN targets forem inspecionados THEN Android SHALL cobrir
   API 23-30 e 31+, e iOS SHALL usar `UILaunchScreen` estática em iOS 15+.

### P1: Estado assíncrono

1. **STATE-01** - WHEN estado for modelado THEN SHALL oferecer Loading,
   Content, Empty e Error como sealed contract exaustivo.
2. **STATE-02** - WHEN state host renderizar THEN SHALL receber slots próprios
   para os quatro estados.
3. **STATE-03** - WHEN retry for ativado THEN SHALL chamar uma vez o callback,
   sem rede ou política embutida.
4. **STATE-04** - WHEN Loading/Empty/Error ocupar tela THEN SHALL centralizar
   no content slot respeitando nav, insets e IME.
5. **STATE-05** - WHEN estado mudar THEN transição SHALL seguir motion/reduced
   motion sem esconder feedback.

### P1: Formatação pt-BR

1. **FMT-01** - WHEN formatter receber `Instant` THEN SHALL converter por
   timezone IANA de provider injetável.
2. **FMT-02** - WHEN `2025-05-15T23:00:00Z` usar `America/Sao_Paulo` THEN data
   SHALL ser `15/05/2025`.
3. **FMT-03** - WHEN o mesmo instante for formatado como hora THEN SHALL ser
   `20:00`.
4. **FMT-04** - WHEN formatado como data/hora THEN SHALL ser
   `15/05/2025 20:00`.
5. **FMT-05** - WHEN centavos forem formatados THEN `0` SHALL ser `R$ 0,00`,
   `123456` SHALL ser `R$ 1.234,56`, `-123456` SHALL ser `-R$ 1.234,56` e `-0`
   SHALL normalizar para zero.
6. **FMT-06** - WHEN offline THEN formatters SHALL produzir os mesmos outputs.
7. **FMT-07** - WHEN timezone for inválido ou centavos exceder safe integer JS
   THEN SHALL falhar explicitamente sem output parcial/arredondamento.

### P1: Localização

1. **L10N-01** - WHEN UI renderizar THEN textos visíveis SHALL estar em pt-BR.
2. **L10N-02** - WHEN strings forem inspecionadas THEN SHALL vir de Compose
   resources pertencentes ao workspace mobile.
3. **L10N-03** - WHEN locale do device mudar THEN esta fase SHALL permanecer
   pt-BR e manter outputs exatos.
4. **L10N-04** - WHEN leitor anunciar ação THEN nome acessível SHALL vir do
   mesmo catálogo pt-BR do label visível.

### P1: Acessibilidade

1. **A11Y-01** - WHEN texto/controle for medido THEN contraste SHALL atender
   WCAG 2.2 AA; `text-muted` SHALL ter `>=4.5:1` nos fundos claros aprovados;
   foco e limites necessários para identificar controles SHALL ter `>=3:1`
   contra adjacentes; hairlines/divisores decorativos SHALL não ser a única
   indicação de controle, estado ou agrupamento essencial.
2. **A11Y-02** - WHEN `accent` for fundo THEN SHALL usar `on-accent`;
   `text-muted` SHALL não carregar informação essencial sozinho.
3. **A11Y-03** - WHEN controle for usado por touch/teclado/leitor THEN nome,
   role, state, ordem e alvo `48x48dp` SHALL estar disponíveis.
4. **A11Y-04** - WHEN interação iniciar THEN feedback SHALL aparecer no
   press/focus antes do release e ação SHALL ocorrer uma vez no commit.
5. **A11Y-05** - WHEN Reduce Motion ativo THEN movimento espacial SHALL sumir
   sem remover feedback; WHEN Reduce Transparency ativo no iOS THEN chrome
   SHALL ficar opaco e manter hairline; preferências sem API Compose comum SHALL
   entrar por adapter nativo mínimo.
6. **A11Y-06** - WHEN Android usar font scale `2.0` ou iOS a maior categoria
   Dynamic Type THEN Compose SHALL aplicar a escala uma única vez e o conteúdo
   SHALL reflow sem cortar controle/ação.
7. **A11Y-07** - WHEN VoiceOver/TalkBack navegar THEN overlays, navegação,
   estado e input SHALL manter leitura/ordem coerentes.

### P1: Gates

1. **GATE-01** - WHEN `scripts/check-scope` executar THEN SHALL permitir apenas
   fundação mobile aprovada e rejeitar workspace web, auth UI, persistência,
   negócio, OpenAPI, backend domain no cliente e acoplamento entre workspaces.
2. **GATE-02** - WHEN scratch contiver somente `mobile/` THEN SHALL passar
   `:core:common:allTests`, `:core:design-system:allTests`,
   `:compose-app:allTests`, Android dev unit/instrumented, `SaqzDev` e
   `SaqzProd` sem backend ou credencial de produção.
3. **GATE-03** - WHEN gates mobile executarem THEN nenhuma suite SHALL ser
   omitida, skipped ou aceitar zero tests/device indisponível silenciosamente.
4. **GATE-04** - WHEN aggregate CI executar THEN landing, credenciais, scope e
   matriz automatizada SHALL passar; WHEN a feature mudar para `Verified` THEN
   o relatório local do Verifier SHALL estar PASS; checklist manual ausente ou
   pendente SHALL não bloquear o status, e qualquer achado manual registrado
   SHALL gerar follow-up explícito.

## Casos Limite

- **EDGE-01** - WHEN não houver perfil THEN navegação SHALL usar somente
  `Início` e `Componentes`.
- **EDGE-02** - WHEN timezone converter para dia anterior/seguinte THEN data
  local resultante SHALL ser usada.
- **EDGE-03** - WHEN centavos forem negativos THEN sinal SHALL preceder `R$`.
- **EDGE-04** - WHEN recurso obrigatório faltar THEN build SHALL falhar sem
  buscar cópia remota.
- **EDGE-05** - WHEN Android rotacionar THEN destino e overlay fechado SHALL ser
  preservados, e Firebase nomeado SHALL não reinicializar.
- **EDGE-06** - WHEN destino selecionado for tocado novamente THEN back stack
  SHALL manter uma única entrada.
- **EDGE-07** - WHEN overlay non-dismissible estiver aberto THEN toque externo
  e back SHALL ser ignorados; close explícito SHALL continuar acessível.
- **EDGE-08** - WHEN conteúdo rolar sob bottom nav THEN divisor `hairline`
  contínuo de `1dp` SHALL permanecer em chrome translúcido ou opaco.

## Dimensões Implícitas

| Dimensão | Resolução |
| --- | --- |
| Validação/limites | Inputs só exibem erro; regras de negócio fora. Formatters validam IANA/safe integer. |
| Falha parcial | Recursos obrigatórios falham build; startup real usa Error. |
| Idempotência | Retry chama uma vez; reselection e bootstrap Firebase são idempotentes. |
| Auth/rate limit | N/A: auth e rede fora de escopo. |
| Concorrência/ordem | N/A: estado local determinístico, sem I/O concorrente. |
| Ciclo de dados | N/A: sem persistência. |
| Observabilidade | Gates contam suites/tests; checklist manual não bloqueante registra achados no `validation.md`; sem telemetria de produto nesta fase. |
| Dependência externa | Fontes/formatters offline; Firebase fica no launcher existente. |
| Transição de estado | Sealed state, back stack e overlay possuem transições definidas. |

## Rastreabilidade

| Requisitos | História | Tasks | Status |
| --- | --- | --- | --- |
| `VIS-01..08` | Contrato visual mobile | T01–T06, T11–T16, T18, T30, T33–T37, T40 | Verified |
| `CMP-01..08` | Componentes | T01, T16, T20–T28, T30 | Verified |
| `NAV-01..05` | Shell/navegação | T07, T28–T30, T32–T33 | Verified |
| `LAUNCH-01..04` | Launch screen | T18, T27, T31, T34, T36, T41 | Verified |
| `STATE-01..05` | Estado | T08, T13, T19, T27, T31 | Verified |
| `FMT-01..07` | Formatação | T09–T10 | Verified |
| `L10N-01..04` | Localização | T17, T29 | Verified |
| `A11Y-01..07` | Acessibilidade | T11–T15, T19–T28, T30–T31, T33, T35–T37 | Verified |
| `GATE-01..04` | Gates | T01, T05–T06, T38–T43 e Verifier | Verified |
| `EDGE-01..08` | Limites | T03–T07, T09–T10, T15, T18–T19, T25–T26, T28, T32–T33, T35 | Verified |

**Cobertura:** 60 requisitos identificados; 60 mapeados em tasks.md.

## Critérios de Sucesso

- [x] Home e catálogo funcionam em Android/iOS sem backend ou produção.
- [x] Inventário completo, tokens, tipografia e formatação são demonstrados.
- [x] Navegação/back, launch nativa e estados funcionam nos dois targets.
- [x] TalkBack, VoiceOver, texto ampliado e preferências passam.
- [x] Workspace mobile funciona isolado; landing permanece inalterada.
