# Retirada do Frontend Angular — Design

**Spec:** `.specs/features/retire-angular-frontend/spec.md`
**Status:** Aprovado
**Data:** 2026-07-15

## Abordagem

A retirada elimina o workspace Angular do Git e remove somente suas conexões
operacionais. Backend e mobile continuam com seus Gradle wrappers independentes;
Xcode continua dono do launcher iOS; a landing e o workflow Pages permanecem
inalterados. Node/npm ficam no ambiente raiz exclusivamente porque o Firebase
CLI e a fixture de sessão os utilizam.

Specs concluídas de `project-initialization` não são reescritas. A decisão
mobile-first é registrada em `STATE.md`, supersedendo as decisões ativas que
prescreviam Angular, enquanto a feature de design system ainda não executada é
reescrita integralmente.

## Superfícies Alteradas

| Superfície | Alteração |
| --- | --- |
| `frontend/` | Remoção integral dos arquivos rastreados. |
| Scope/arquitetura | Cliente ativo passa a significar somente mobile; `frontend/` vira path proibido. |
| Gate local | Remove as quatro etapas npm/Angular. |
| CI | Remove `angular-gate`; aggregate passa de quatro para três resultados. |
| README/metadados | Remove Angular; mantém Node/npm para Firebase tooling. |
| Specs ativas | Fundação de interface passa a Android/iOS somente. |

## Riscos e Mitigações

| Risco | Mitigação |
| --- | --- |
| Remover Node junto com Angular e quebrar Firebase Emulator | Preservar `.nvmrc`, Node/npm no README e testes da fixture. |
| Aggregate aceitar job omitido | Evaluator exige aridade 3 e testes mutam failure/cancelled de cada job. |
| Scope perder cobertura ao remover fixtures TypeScript | Migrar todas as mutações relevantes para fontes Kotlin mobile. |
| Reintrodução silenciosa de `frontend/` | Regra explícita de path + mutação mínima no teste de scope. |
| Alteração acidental da landing | Gate/hash existente e diff vazio obrigatório. |

## Sequência

1. Atualizar contrato e decisões locais.
2. Remover o workspace Angular.
3. Atualizar fronteiras e seus testes.
4. Atualizar gate local e CI com seus contratos.
5. Atualizar documentação/orquestração.
6. Executar gates completos e validação independente.
