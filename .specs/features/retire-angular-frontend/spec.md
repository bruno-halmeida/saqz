# Retirada do Frontend Angular

**Status:** ✅ Verificada
**Data:** 2026-07-15
**Origem:** decisão do usuário de operar somente um app mobile e a landing

## Problema

O repositório mantém um workspace Angular, gates, documentação e decisões de
arquitetura para uma superfície web de produto que deixou de fazer parte da
estratégia atual. Mantê-los ativos cria custo de CI, dependências e uma segunda
implementação de interface sem demanda validada.

## Objetivos

- Remover integralmente o workspace rastreado `frontend/`.
- Remover Angular dos gates locais e do aggregate de CI.
- Preservar backend, app Android/iOS e a landing estática.
- Impedir que o workspace retirado seja reintroduzido acidentalmente.
- Reescrever a fundação de interface ativa como mobile-only.

## Fora de Escopo

| Item | Motivo |
| --- | --- |
| Alterar conteúdo ou deploy da `landing-page/` | A landing permanece a superfície web pública. |
| Implementar Compose Multiplatform Web | Depende de demanda futura e de nova decisão arquitetural. |
| Alterar comportamento do backend ou do app mobile | Esta feature remove somente a superfície Angular e suas integrações. |
| Remover Node/npm | O Firebase CLI e `firebase/session-fixture` continuam usando `npx` e `node`. |
| Reescrever artefatos históricos de `project-initialization` | Specs e validações concluídas preservam o registro do que existia. |

## Decisões e Premissas

| Tema | Escolha | Justificativa |
| --- | --- | --- |
| Produto | Um app Compose Multiplatform para Android/iOS | Concentra esforço na superfície validada. |
| Web público | Landing estática existente | Preserva SEO, aquisição e deploy independente. |
| Angular | Remoção do código ativo, não arquivamento local | O Git já preserva o histórico sem manter dependências mortas. |
| Web futuro | Nova feature e nova avaliação tecnológica | Compose Web não é tratado como port automático. |
| Histórico | Specs finalizadas permanecem inalteradas | Mantém a trilha de auditoria. |

**Open questions:** nenhuma.

## Critérios de Aceite

1. **RET-01** - WHEN os arquivos rastreados forem enumerados após a retirada
   THEN nenhum path sob `frontend/` SHALL existir.
2. **RET-02** - WHEN o gate local completo executar THEN ele SHALL executar
   Gradle/Android, iOS e landing, nessa ordem, sem invocar Angular ou
   `npm --prefix frontend`.
3. **RET-03** - WHEN o workflow de inicialização executar THEN ele SHALL conter
   somente `gradle-gate`, `ios-gate`, `landing-gate` e o aggregate; o evaluator
   SHALL exigir exatamente os três resultados e rejeitar failure/cancelled de
   qualquer um.
4. **RET-04** - WHEN `scripts/check-scope` executar THEN o repositório limpo
   SHALL passar e qualquer arquivo rastreado sob `frontend/` SHALL falhar com
   diagnóstico específico; as proibições de auth UI, OpenAPI, navegação ainda
   não aprovada e domínio de backend SHALL continuar cobertas em mobile.
5. **RET-05** - WHEN README, `.gitignore` e contratos de scripts forem
   inspecionados THEN eles SHALL descrever backend, mobile e landing sem
   comandos, workspace, job ou dependência Angular; Node/npm SHALL permanecer
   documentado somente para o Firebase CLI.
6. **RET-06** - WHEN a fundação de interface ativa for lida THEN `spec.md`,
   `context.md` e `design.md` SHALL definir somente Android/iOS e SHALL colocar
   qualquer app web futuro fora de escopo.
7. **RET-07** - WHEN as decisões do projeto forem lidas THEN as decisões
   incompatíveis com Angular removido SHALL estar superseded por uma decisão
   mobile-first, sem apagar o histórico anterior.
8. **RET-08** - WHEN os gates finais executarem THEN credenciais, scope,
   contratos de scripts, backend/mobile, iOS e landing SHALL permanecer verdes;
   `landing-page/` e seu workflow de Pages SHALL não ser modificados.

## Casos Limite

- WHEN um arquivo isolado `frontend/README.md` for rastreado novamente THEN o
  scope gate SHALL falhar mesmo sem `package.json`.
- WHEN qualquer um dos três jobs do aggregate retornar `cancelled` THEN o
  evaluator SHALL falhar.
- WHEN Node/npm forem removidos da documentação THEN o contrato SHALL falhar,
  pois o Firebase CLI ainda depende deles.
- WHEN a palavra “web” pertencer ao backend HTTP ou ao web público da landing
  THEN ela SHALL não ser confundida com o workspace Angular retirado.

## Rastreabilidade

| Requisito | Evidência | Status |
| --- | --- | --- |
| `RET-01` | Ausência estrutural e `git ls-files frontend`. | ✅ Verified |
| `RET-02` | `tests/scripts/check-all.test.sh`. | ✅ Verified |
| `RET-03` | `tests/scripts/check-ci.test.sh`. | ✅ Verified |
| `RET-04` | `tests/scripts/check-scope.test.sh` e `ARCH-08`. | ✅ Verified |
| `RET-05` | `tests/scripts/check-readme.test.sh` e busca de referências. | ✅ Verified |
| `RET-06` | Validação estrutural dos três documentos ativos. | ✅ Verified |
| `RET-07` | `.specs/STATE.md` com supersessão preservada. | ✅ Verified |
| `RET-08` | Gates finais e diff da landing vazio. | ✅ Verified |

**Cobertura:** 8 requisitos, todos mapeados para tarefas.

## Backprop Log

| ID | Data | Causa | Decisão |
| --- | --- | --- | --- |
| `B1` | 2026-07-15 | Gate de arquitetura foi invocado com JDK 17 no shell, antes de alcançar os testes. | Nenhum novo invariant: o guard existente de JDK 21 falhou corretamente e README/check-all já configuram o ambiente exigido. |
| `B2` | 2026-07-15 | Build gate foi invocado sem Android emulator/device disponível e parou antes dos testes instrumentados. | Nenhum novo invariant: o guard existente falhou corretamente e o README já declara o device em execução como pré-requisito. |
