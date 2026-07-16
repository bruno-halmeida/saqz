# Contexto da Fundação Mobile de Interface e Design System

**Coletado em:** 2026-07-15
**Spec:** `.specs/features/frontend-design-system-foundation/spec.md`
**Status:** Decisões de revisão confirmadas; design mobile-only em atualização

## Limite da Feature

Substituir os placeholders Compose por uma fundação visual clara, navegável e
acessível para Android e iOS. A entrega contém tema, componentes, Home,
catálogo, navegação neutra, launch screens, estados assíncronos e formatadores
locais. A landing continua independente. Não há aplicativo web, autenticação,
backend no cliente, perfis reais, telas de negócio ou dados mockados.

## Decisões de Implementação

### Plataformas

- Um único app Compose Multiplatform compartilha UI entre Android e iOS.
- Compose Multiplatform é o caminho padrão também no iOS; Swift/UIKit ficam
  restritos a launch screen, SDKs de plataforma e preferências sem API comum.
- `:compose-app` continua sendo o único framework `SaqzMobile` exportado ao iOS.
- `:core:common` e `:core:design-system` concentram primitivas compartilhadas.
- Não criar target Compose Web/Kotlin Wasm nesta fase.
- Qualquer produto web futuro exige nova spec e avaliação tecnológica.
- A landing HTML/CSS não será portada para Compose.

### Direção visual

- Linguagem Apple-inspired adaptada à identidade Saqz, não cópia comercial.
- Visual claro com `#F5F5F7`, branco, `#1D1D1F`, `#0638DF` e `#C7F300`.
- Azul sinaliza ação; accent verde é destaque/status não clicável; vermelho é
  erro/destructive.
- Grid 8dp, padding 16dp, cards 16dp e alvos 48x48dp.
- Superfícies/hairlines substituem sombras; gradientes decorativos são proibidos.
- `text-muted` usa `#707075` para manter contraste AA sobre branco e
  `#F5F5F7`.
- Hairlines/divisores claros são decorativos; limites de controles usam um
  token próprio com contraste mínimo de `3:1` contra fundos adjacentes.
- SF do sistema somente no iOS; Inter estática 300/400/600/700 no Android.
- Logo deriva do SVG da landing sem modificar o arquivo original.

### Componentes e catálogo

- Entregar todo o inventário da spec.
- Dialog é confirmação bloqueante; bottom sheet é escolha/ação contextual.
- Catálogo fica acessível pela Home nesta fase.
- `SaqzServiceCard` e padrões de marketing não pertencem ao app.

### Navegação e shell

- Produção mostra apenas `Início` e `Componentes` na bottom nav.
- Fixtures owner/atleta testam configuração, mas não criam sessão falsa.
- Avatar, logout e destinos de negócio ficam para o Épico 03.
- Home mostra logo, `Saqz` e `Explorar componentes`.

### Launch mobile

- Android API 23-30/31+ e iOS 15+ usam símbolo Saqz sobre `#F5F5F7`.
- Não existe segunda splash Compose nem atraso fixo.
- Loading/Error real aparece no shell via `SaqzUiState`.
- Bootstrap Firebase Android continua idempotente após rotação.

### Estado, formatação e idioma

- `commonMain` oferece Loading, Content, Empty e Error com slots.
- Retry é só callback; rede/política pertencem à feature.
- Datas usam pt-BR, timezone local injetável e formato numérico.
- Dinheiro recebe centavos inteiros e usa NBSP depois de `R$`.
- UI e nomes acessíveis permanecem pt-BR nesta fase.

### Motion e acessibilidade

- Interações comuns ficam abaixo de 250 ms; pressed usa `0.95`.
- Reduced Motion e Reduce Transparency são preferências de primeira classe.
- Compose é o único responsável por font scale e Dynamic Type; não existe
  multiplicador tipográfico calculado novamente em Swift.
- Adapters nativos observam somente preferências que Compose não exponha de
  forma comum, sem assumir ownership da tipografia ou da árvore semântica.
- WCAG 2.2 AA, TalkBack, VoiceOver, font scale 2.0, maior Dynamic Type,
  rotação, IME e alvos mínimos fazem parte da evidência.

### Gates

- `scripts/check-scope` permite a fundação mobile e rejeita qualquer workspace
  web de produto, auth, persistência, negócio, OpenAPI ou coupling.
- Testes comuns, Android instrumented e XCUITest são bloqueantes.
- Somente evidências automatizadas e o Verifier bloqueiam `Verified`.
- TalkBack, VoiceOver, Dynamic Type, motion/transparency, cold start e layouts
  extremos permanecem em checklist manual recomendado e não bloqueante no
  `validation.md`; achados viram follow-up explícito.
- `mobile/` funciona em scratch sem backend.
- Landing/credenciais continuam gates independentes.

## Referências

- Épico ClickUp `86ajh0q0n` e subtasks originais como histórico de produto.
- `AD-001`, `AD-013`, `AD-015`, `AD-016`, `AD-017` e `AD-018`.
- Fundação atual em `mobile/compose-app/`, `mobile/android-app/` e
  `mobile/ios-app/`.
- Logo em `landing-page/assets/saqz-logo.svg`.

## Ideias Adiadas

- App web e eventual target Compose Multiplatform Web.
- Dark mode.
- Perfil, avatar, logout e navegação autorizada.
- Features de negócio, backend client, cache/offline.
- Site dedicado de documentação do design system.
- Localização configurável por locale/moeda.
