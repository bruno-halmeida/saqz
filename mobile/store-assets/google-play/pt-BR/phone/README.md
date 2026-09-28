# Saqz — imagens para smartphones no Google Play

Quatro peças em **1080 × 1920 pixels**, proporção 9:16, PNG RGB de 24 bits sem transparência.
Os arquivos para upload estão em `png/`, na ordem indicada pelo nome.

| Ordem | Arquivo | Recurso apresentado |
| --- | --- | --- |
| 1 | [01-grupos.png](png/01-grupos.png) | Grupos, próximos jogos e convites |
| 2 | [02-presenca.png](png/02-presenca.png) | Detalhes do jogo e confirmação de presença |
| 3 | [03-membros.png](png/03-membros.png) | Participantes, pedidos de entrada e permissões |
| 4 | [04-financeiro.png](png/04-financeiro.png) | Controle financeiro manual dos grupos |

## Visualizar e editar

Abra [index.html](index.html) diretamente no navegador. A galeria mostra as quatro peças e permite ajustar o zoom da prévia. Os links “Baixar PNG” apontam para a última exportação.

- **Textos, ordem e screenshots:** edite [content.js](content.js).
- **Cores, tipografia, posições e elementos gráficos:** edite [styles.css](styles.css).
- **Estrutura do layout:** edite [index.html](index.html).
- **Celular:** `device.width` define a largura da moldura; `device.top` define sua posição vertical. A captura inteira mantém a proporção original, sem recorte vertical. A moldura, os botões laterais, a câmera e o alto-falante são elementos de HTML/CSS editáveis em `styles.css`.
- **Temas:** `blue`, `ice` e `navy` em `content.js`.
- **Texto alternativo para o Play Console:** campo `alt` de cada peça, também exportado em [png/manifest.json](png/manifest.json).

Cada título tem duas linhas explícitas no campo `title`. O subtítulo fica em `description`.
Depois de editar, recarregue a galeria para visualizar e execute a exportação para atualizar os PNGs.

## Exportar novamente

No terminal, a partir desta pasta:

```sh
npm install
npx playwright install chromium
npm run export
```

As duas primeiras etapas são necessárias apenas na instalação inicial. A exportação abre uma janela do Chromium e fecha ao concluir. Para escolher a execução sem janela, use `npm run export -- --headless`.

O script usa arquivos locais e espera o carregamento das fontes e imagens. Confere resolução, PNG sem transparência, proporção e altura integral das capturas, moldura dentro da arte, separação entre textos e celular, altura do slogan e texto alternativo. Os resultados e tamanhos ficam em `png/manifest.json`.

É possível reutilizar uma instalação existente de Playwright informando o caminho do módulo em `PLAYWRIGHT_MODULE`, em vez de instalar uma segunda cópia. A exportação inicial foi validada com Playwright 1.61.1.

## Origem das capturas e da marca

As capturas em `assets/screenshots/` foram **geradas novamente em 28/09/2026**, usando os componentes Compose atuais desta branch (`60a3bb4d89b8e08dfc314a57013661791af4a748`) e os cenários nativos existentes no projeto. A tela de presença usa a Home nova, com marca e sino, cartão azul do próximo jogo e próximos jogos. Os nomes, datas e valores são dados de demonstração.

O commit, o cenário utilizado e o SHA-256 de cada PNG original estão em [assets/screenshots/source.json](assets/screenshots/source.json). As capturas são copiadas sem alterações; a escala e a moldura são aplicadas apenas no HTML/CSS. A moldura é genérica, sem marca de fabricante.

| Cópia local | Captura original em `mobile/features/groups/presentation/screenshots/` |
| --- | --- |
| `grupos.png` | `vul-67/group-list-2n-lista-cheia.png` |
| `presenca.png` | `vul-222/flow6b-01-atleta-sem-resposta.png` |
| `membros.png` | `vul-70/group-members-list.png` |
| `financeiro.png` | `vul-181/finance-overview-filled.png` |

As fontes Inter vêm do design system do projeto. O SVG vem de `landing-page/assets/saqz-logo.svg`, preservando os vetores e as cores originais em todas as peças. Uma base branca garante a leitura da marca sobre os fundos. Os arquivos necessários à visualização estão incluídos nesta pasta.

Para atualizar as capturas após mudanças no app, rode a partir da raiz do repositório, com JDK 21:

```sh
mobile/gradlew -p mobile :features:groups:presentation:testAndroidHostTest \
  -Proborazzi.test.record=true \
  --tests 'br.com.saqz.groups.presentation.ui.list.GroupListScreenshotTest.filled' \
  --tests 'br.com.saqz.groups.presentation.ui.home.HomeJourneyScreenshotTest.s01AthleteWithoutResponse' \
  --tests 'br.com.saqz.groups.presentation.ui.members.GroupMembersScreenshotTest.list' \
  --tests 'br.com.saqz.groups.presentation.ui.finance.overview.FinanceOverviewScreenshotTest.filled'
```

Copie os quatro PNGs conforme a tabela, atualize `source.json` com o commit e os hashes correspondentes e exporte novamente as peças. Reutilizar capturas antigas não incorpora mudanças feitas no app.

Referência de resolução e formato: [orientações do Google Play para capturas de tela](https://support.google.com/googleplay/android-developer/answer/9866151?hl=pt-BR). As peças descrevem os recursos disponíveis nesta versão; a quarta mostra registros financeiros manuais.
