# Branch `screenshots`

Aqui moram os PNGs de evidência visual do Saqz. É uma branch **órfã**: não tem
ancestral comum com a `main`, e o histórico de binário daqui nunca entra no
histórico do código.

**Esta branch não se mergeia em `main`.** Nunca abra PR dela. Quem mexe em
componente ou tela empurra os prints direto para cá e embute o raw no corpo do
PR de código — assim PR de código não toca em binário, e dois PRs paralelos
deixam de conflitar em pixel.

## Padrão de caminho

```
<ticket>/<nome>.png
```

Minúsculas, kebab-case, o ticket em minúsculo (`vul-56/segmented-desabilitado.png`).
`catalogo/` é a exceção nomeada: guarda o catálogo canônico do design system,
regravado inteiro pelo `recordRoborazziDevDebug`.

## Como empurrar

Da sua branch de código, com os PNGs já gerados em
`mobile/android-app/screenshots/` (que é ignorado pelo git na `main`):

```sh
git fetch origin screenshots
git worktree add /tmp/shots screenshots
mkdir -p /tmp/shots/vul-NN
cp mobile/android-app/screenshots/*.png /tmp/shots/vul-NN/
git -C /tmp/shots add . && git -C /tmp/shots commit -m "screenshots: VUL-NN"
git -C /tmp/shots push origin screenshots
git worktree remove /tmp/shots
```

## Como embutir no PR

O repositório é público, então o raw renderiza inline:

```markdown
![segmented desabilitado](https://raw.githubusercontent.com/bruno-halmeida/saqz/screenshots/vul-NN/segmented-desabilitado.png)
```
