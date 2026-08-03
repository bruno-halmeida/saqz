# adm-web — Dashboard administrativo do Saqz

Painel interno de administração da plataforma (back-office). Hoje é um protótipo
funcional com **dados mockados no cliente**: todo o visual e a navegação estão
prontos, nada está ligado ao backend ainda.

Origem: artifact do Claude Design
(https://claude.ai/code/artifact/df70dae6-72c1-4753-8ad7-e8ddc7676b0c),
extraído do bundle e convertido em página estática auto-contida.
Projeto no Linear: Adm-web · Dashboard administrativo (VUL-161..172).

## Como rodar

Sem build. Qualquer servidor estático serve:

```bash
cd adm-web && python3 -m http.server 8123
# http://127.0.0.1:8123
```

Stack: HTML + React 18 UMD + `dc-runtime.js` (runtime do Claude Design) +
`saqz-design-system.js` (componentes do DS calibrados pelo export oficial).
Tokens de cor/tipo/espaçamento no `<style>` do `index.html` — mesmos nomes do
design system do mobile (`--saqz-blue`, `--saqz-lime`, `--saqz-navy`…).

## Funcionalidades

Navegação por sidebar fixa com 6 seções. Ícone de Suporte ganha um ponto
vermelho quando há denúncia aberta.

### 1. Visão geral (`visao`)
- Seletor de período: últimos 30 dias · últimos 90 dias · desde o início.
- 6 KPIs com sparkline e delta vs período anterior: **Receita total, Novos
  usuários, Usuários ativos (30d), Grupos ativos, Jogos realizados, Churn de
  assinaturas**.
- Cohort semanal (5 semanas): cadastros → ativados → criou grupo → virou pagante.
- Split de receita: **planos** vs **taxa de 4% sobre mensalidades**, com volume
  transacionado no período.
- Receita por plano: Organizador, Quadra Cheia, taxa sobre mensalidades.
- Grupos por cidade.
- Card de pendências com atalho para a denúncia aberta.

### 2. Usuários (`usuarios` → `usuario`)
- Lista com busca (nome/e-mail) e filtros: plano (Amador · Organizador ·
  Quadra Cheia), status (ativo · inativo · suspenso), cidade.
- Colunas: iniciais/avatar, nome, e-mail, cidade, nº de grupos, chip de plano,
  chip de status, último acesso.
- **Detalhe do usuário**: dados cadastrais, plano e situação da assinatura
  (atalho para o detalhe da assinatura), grupos em que participa/organiza
  (navega para o grupo), histórico de pagamentos.
- Ações: **suspender** (modal de confirmação — perde acesso na hora, grupos
  ficam sem organizador) e **reativar**.

### 3. Grupos (`grupos` → `grupo`)
- Lista com busca (nome/organizador) e filtros: cidade, modalidade (quadra ·
  praia · futevôlei).
- Colunas: nome, modalidade, organizador, cidade, membros, mensalidades
  (R$/mês), **taxa Saqz de 4%**, chip de status.
- Estados especiais: **em análise** (suspeita de cobrança fora do app) e
  **sem plano** (organizador cancelou a assinatura).
- **Detalhe do grupo**: local, rotina, mensalidade por membro, presença média,
  jogos realizados, criado em, e lista dos últimos jogos com confirmados.

### 4. Receita (`receita` → `assinatura`)
Duas abas:
- **Assinaturas**: nome, plano, ciclo (mensal/anual), valor, cupom aplicado,
  status (ativa · atrasada · cancelada), assinante desde.
- **Cobranças**: data, nome, tipo (plano ou taxa de 4% do grupo), valor,
  status (paga · falhou · reembolsada). Ações por linha: **Tentar de novo**
  (falhou) e **Reembolsar** (paga).
- **Detalhe da assinatura**: forma de pagamento, próxima cobrança, histórico.
  Ações: **cancelar plano** (vale até o fim do período pago) e **reembolsar
  última cobrança** — ambas com modal de confirmação.

### 5. Cupons (`cupons`)
- Lista: código, desconto/duração, validade, usos, MRR impactado, status
  (ativo · vencido).
- **Criar cupom** (modal): código, % de desconto, duração em meses, validade.

### 6. Suporte (`suporte` → `denuncia`)
- Lista de denúncias: grupo, motivo, data, status (aberta · resolvida) e
  resumo do backlog.
- **Detalhe da denúncia**: grupo, organizador, quem denunciou, denúncias
  anteriores, descrição completa.
- Ações: **marcar como resolvida** e **ver grupo**.

### Padrões transversais
- Modais de confirmação para toda ação destrutiva (suspender, cancelar,
  reembolsar), com variante `danger`.
- Toast de feedback após cada ação.
- Chips de status com semântica de cor única em todo o app
  (verde/âmbar/vermelho/cinza).

## O que falta para virar produto (fora do protótipo)

1. **Autenticação de admin** — login restrito (e-mail interno / role `ADMIN`
   no backend). Nada disso existe hoje; a página é aberta.
2. **API administrativa no backend** — endpoints agregados (KPIs, cohort,
   split de receita) e CRUD/ações: listar usuários/grupos/assinaturas/
   cobranças, suspender usuário, cancelar assinatura, reembolsar, retry de
   cobrança, cupons, denúncias.
3. **Paginação e busca server-side** — o mock mostra 12 usuários / 8 grupos.
4. **Dados reais nos detalhes** — jogos do grupo e sparklines são gerados no
   cliente hoje.
5. **Deploy** — estático (Firebase Hosting cai bem com o resto do repo).
