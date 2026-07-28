# design-tokens — cópia do export oficial

Os quatro `.css` desta pasta são **cópia byte a byte** de
`_ds/saqz-design-system-48df716a-8e90-4870-8746-6c7d11f3458a/tokens/` do export do cliente
(fluxo 10). Antes deles a fonte era uma pasta no Desktop de uma pessoa; agora é arquivo do
repositório. Export novo → substitua os arquivos e reconcilie o `ui-contract.json`.

`fonts.css` não veio junto de propósito: a Inter variável já está vendorizada como `.ttf` em
`src/androidMain/composeResources/font/`.

## O que deriva de quê

| bloco do `ui-contract.json` | origem |
|---|---|
| `colors` | `colors.css` |
| `typography` | `typography.css`, escala `--m-*` (a escala mobile; a `--text-*` é da web) |
| `metrics` — espaçamento e nav | `spacing.css` |
| `metrics` — raios | `radius.css` |
| `shadows` | `radius.css` — o bloco `--shadow-*` mora lá, junto dos raios |
| `metrics` — alturas de componente | `_ds_bundle.js` (CSS dos componentes, não é token CSS) |
| `motion` | `_ds_bundle.js`, menos o `thumbDurationMillis` (ver abaixo) |

**Antes de reconciliar um export novo, leia o bloco `_exceptions` do `ui-contract.json`.**
Nem todo valor do contrato sai do export: `minimumTouchTarget` é override de acessibilidade
(o export pede 44), `sectionVerticalPadding` não existe em CSS nenhum, e há mais dois casos.
Cada um está listado lá com o valor do export ao lado e o motivo. Sobrescrever em massa
rebaixa o piso de acessibilidade sem ninguém perceber — que é exatamente o acidente que a
lista existe para evitar. Fora dessa lista, o contrato pode ser reconciliado direto.

## Números que só existem no `_ds_bundle.js`

O bundle não está versionado (88 KB de JS compilado). As linhas citadas são do export acima.

| valor | linha do bundle |
|---|---|
| `buttonHeight` 52 | 20 — `.saqz-btn--md{min-height:52px}` |
| `iconButtonSize` 44 | 77 — `.saqz-iconbtn{width:44px;height:44px}` |
| switch 52×30, knob 24, inset 3 | 1384–1403 — `CreateGroupScreen.jsx`, `role="switch"` |
| `pressOffset` 1dp | 25 — `.saqz-btn--primary:active{transform:translateY(1px) …}` |
| `pressScale` .98 | 617 — `.saqz-attend__btn:active{transform:scale(.98)}` |
| `switchDurationMillis` 180 + `switchEasing` | 1395 e 1406 — `background .18s ease` e `left .18s ease` |
| `sheetDurationMillis` 320 | 438 — `.saqz-sheet{transition:transform .32s cubic-bezier(.22,1,.36,1)}` |
| `toastDwellMillis` 2600 | 1071 — `setTimeout(() => setToast(""), 2600)` |

O `thumbDurationMillis` (280 + curva enfática) é o único que não está no bundle: o segmented
só ganha transição na página renderizável, `Fluxo 10 Componentes.dc.html` l.523 —
`segThumb`, `transition: "left .28s cubic-bezier(.22,1,.36,1)"`.

**Switch e segmented não compartilham movimento.** O export dá `.18s ease` ao switch e `.28s`
enfático ao thumb do segmented; são componentes diferentes com movimentos diferentes, e por
isso são dois pares de token. Unificar num `thumbDuration` só parece limpeza e é regressão —
`SaqzMotionPolicyTest.switchAndSegmentedDoNotShareMovement` existe para barrar isso.

O botão do bundle fecha em `scale(.995)`; o design system adota o `.98` do seletor de presença
para os dois, porque `.995` não é perceptível em tela de telefone e o mesmo press vale para todo
alvo primário.

<!-- ponytail: contrato transcrito à mão a partir destes CSS, não gerado. Um gerador precisaria
     de uma tabela de-para (--saqz-canvas → background, --m-body-* → body) do tamanho do próprio
     JSON, e ainda assim não cobriria as alturas, que vêm do bundle. Se o export virar rotina,
     o upgrade é um script que lê os CSS + o bundle e escreve o ui-contract.json inteiro. -->
