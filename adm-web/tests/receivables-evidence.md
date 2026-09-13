# Gate ADM Recebimentos — 2026-09-13

- Node: `node --test adm-web/tests/*.test.cjs` — 35 testes aprovados (27 anteriores, 8 de recebimentos). Saída local: `/tmp/receivables-adm-node.txt`.
- Browser: Chromium visível via Playwright, HTML/DC real em `http://127.0.0.1:8123`, sessão e API **simuladas explicitamente** por interceptação; chamadas externas bloqueadas. Nenhuma configuração, chave ou dado real usado.
- Roteiro executado: entrada pela navegação, motivo obrigatório, 409 e recarga explícita, perda de rede com reenvio de corpo idêntico, seleção pela listagem existente/detalhe, overrides, flag operacional, estado efetivo, histórico página 2 e antes/depois, logout sem dados anteriores, ausência de erros JavaScript.
- Script reproduzível local: `/tmp/playwright-test-receivables.js`; executor `/Users/bruno_almeida/.agents/skills/playwright-skill/run.js`. URL configurável por `TARGET_URL`.
- Evidência visual local: `/tmp/receivables-adm-overview.png`, `/tmp/receivables-adm-desktop.png` (1440×1100), `/tmp/receivables-adm-tablet.png` (800×1000).
- Node cobre timeout de 15 segundos com relógio controlado, versão/requestId, motivo, 409, flag null/boolean, resposta antiga de usuário/histórico, redução de total e limpeza no logout. Browser simula falha de rede, não aguarda timeout real.

Limitação: não é E2E com backend/Firebase real e não valida persistência, autorização ou enforcement financeiro do servidor; cabe ao gate de integração verificar isso. Nenhum deploy, habilitação de produção, branch, staging, commit ou push realizado. Mudanças restritas a `adm-web/**`.
