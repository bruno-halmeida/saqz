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
| `metrics` — alturas de componente | `_ds_bundle.js` (CSS dos componentes, não é token CSS) |
| `motion` | `_ds_bundle.js` |

## Números que só existem no `_ds_bundle.js`

O bundle não está versionado (88 KB de JS compilado). As linhas citadas são do export acima.

| valor | linha do bundle |
|---|---|
| `buttonHeight` 52 | 20 — `.saqz-btn--md{min-height:52px}` |
| `iconButtonSize` 44 | 77 — `.saqz-iconbtn{width:44px;height:44px}` |
| switch 52×30, knob 24, inset 3 | 1384–1403 — `CreateGroupScreen.jsx`, `role="switch"` |
| `pressOffset` 1dp | 25 — `.saqz-btn--primary:active{transform:translateY(1px) …}` |
| `pressScale` .98 | 617 — `.saqz-attend__btn:active{transform:scale(.98)}` |
| `toastDwellMillis` 2600 | 1071 — `setTimeout(() => setToast(""), 2600)` |

O botão do bundle fecha em `scale(.995)`; o design system adota o `.98` do seletor de presença
para os dois, porque `.995` não é perceptível em tela de telefone e o mesmo press vale para todo
alvo primário.

<!-- ponytail: contrato transcrito à mão a partir destes CSS, não gerado. Um gerador precisaria
     de uma tabela de-para (--saqz-canvas → background, --m-body-* → body) do tamanho do próprio
     JSON, e ainda assim não cobriria as alturas, que vêm do bundle. Se o export virar rotina,
     o upgrade é um script que lê os CSS + o bundle e escreve o ui-contract.json inteiro. -->
