# Saqz

Landing page de pré-lançamento do Saqz, localizada em `landing-page/`.

## Desenvolvimento local

Sirva a raiz do repositório com qualquer servidor HTTP estático. Exemplo:

```bash
python3 -m http.server 8080 --directory landing-page
```

Acesse `http://localhost:8080`.

## GitHub Pages

O workflow `.github/workflows/deploy-pages.yml` publica `landing-page/` após mudanças nessa pasta enviadas para `main`.
