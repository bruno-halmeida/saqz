# Proveniência dos assets de marca mobile

Os drawables vetoriais da marca são derivados, sem redesenho, do SVG da landing.

## Fonte

- Arquivo: `landing-page/assets/saqz-logo.svg` (somente leitura; nunca alterado)
- SHA-256: `0c732546309e7143f60203472c368a3cebbb3a53721f142898724023aa33a473`
- Paths: `blue` (`#0638DF`), `white-details` (`#FFFFFF`), `green-accent` (`#C7F300`)

## Derivados

| Arquivo | viewBox | Conteúdo |
| --- | --- | --- |
| `mobile/core/design-system/src/commonMain/composeResources/drawable/saqz_wordmark.xml` | `0 0 1200 360` | Wordmark completo: os três paths do SVG, pathData e cores idênticos. |
| `mobile/core/design-system/src/commonMain/composeResources/drawable/saqz_symbol.xml` | `0 0 360 360` | Símbolo quadrado: mesmos paths, recortados pelo viewBox `360x360`. |

## Regras

- pathData e cores são cópia byte a byte dos paths do SVG; `fillType="evenOdd"`
  preserva o `fill-rule="evenodd"` original.
- Nenhum path é redesenhado, rasterizado ou recolorido, e nenhum asset é buscado
  em runtime.
- O verificador `tests/scripts/check-mobile-brand-assets.test.sh` reprova qualquer
  divergência de hash da fonte, viewBox, pathData, cor ou output ausente.
