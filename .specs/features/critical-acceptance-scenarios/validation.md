# Validação das descrições de aceitação críticas

Data: 2026-09-14. Verificador independente, distinto do autor.

Resultado final: **PASS documental — D1–D5 atendidos**. Os dois ajustes pontuais encontrados na primeira revisão foram corrigidos e verificados novamente em `5eac2b29`. Este resultado avalia os roteiros; não é um resultado de execução do produto.

Referência dos critérios: `tests/acceptance/README.md:61`. Diff final verificado: `fd22416ed34eff6b8eaa3daa083301738278c71a..5eac2b29`, restrito ao README e sete arquivos `.feature` novos, com 617 adições e 3 remoções. A revisão inicial cobriu `fd22416ed34eff6b8eaa3daa083301738278c71a..ccd36be8`; a segunda leitura conferiu o delta `ccd36be8..5eac2b29` e o entorno dos ajustes. Não houve alteração de código, executor ou testes automatizados de produto nesse diff.

## Critérios e evidências

| Critério | Resultado esperado | Evidência documental e expressão verificada | Resultado |
| --- | --- | --- | --- |
| D1 — cobrir as lacunas principais | Primeiro acesso/Início/perfil, grupos/membros, jogos, assinaturas, financeiro, trial e analytics têm roteiros ou referência existente | `tests/acceptance/README.md:36` mapeia áreas e IDs; `app-inicio-perfil.feature:9`/`:32`/`:62`, `app-gestao-grupos.feature:9`/`:86`, `app-jogos.feature:9`, `app-assinaturas.feature:11`, `financeiro-consulta.feature:9`, `trial-cupons.feature:11` e `adm-conversao-cupons.feature:10` contêm jornadas correspondentes. Os caminhos são relativos a `tests/acceptance/`. | PASS |
| D2 — ator, massa, ação e resultado observável | Os passos permitem preparar e executar a jornada e comparar com um resultado conhecido, sem inventar navegação nem usar a própria resposta como oráculo monetário | `tests/acceptance/README.md:104` define massa adicional e `:113` exige resultados independentes. `financeiro-consulta.feature:10` implica `17000 - 12000 = 5000` centavos em `:13`; `adm-conversao-cupons.feature:11` implica 2 pessoas, 1 pagante e R$150,00 em `:16`/`:20`. Após a correção, `app-jogos.feature:14` abre o jogo explicitamente antes da verificação e `app-assinaturas.feature:23` registra o pró-rata esperado antes da ação, comparado com API/Pix/UI em `:27`/`:28`. | PASS — F1/F2 resolvidos |
| D3 — Gherkin, IDs e referências válidos | Todos os arquivos compilam em português; cada cenário tem ID único e prioridade; exemplos resolvem parâmetros; referências locais e âncora existem | `tests/acceptance/app-inicio-perfil.feature:1`, `:8`, `:12` e `:18` exemplificam idioma, ID e expansão. Parser oficial aplicado aos 12 arquivos encontrou 111 IDs únicos e 141 casos expandidos; os 46 cenários novos possuem Dado/Quando/Então. As 17 referências locais do README, inclusive `README.md:18`, existem; a âncora aponta para `docs/receivables/final-operations-runbook.md:101`. | PASS |
| D4 — priorizar o frequente | Jornada principal, validação de entrada, cancelamento de confirmação, autorização e recuperação simples; sem impor matriz rara de concorrência | `tests/acceptance/README.md:24` delimita o recorte, `:27` exclui combinações raras e `:31` define prioridades; `app-inicio-perfil.feature:74`, `app-gestao-grupos.feature:49`, `app-jogos.feature:49` e `trial-cupons.feature:49` cobrem falhas comuns. Paginação usa somente 21 movimentos em `financeiro-consulta.feature:25`. | PASS |
| D5 — bloqueios e não execução explícitos | Documento não implica homologação; UI sem integração e harness ausente impedem aprovar uma execução | `tests/acceptance/README.md:3` declara ausência de executor Cucumber; `:33` define bloqueio; `:69` relaciona rascunho, Agenda, pedidos, isenção/cancelamento e moderação; `:115` exige registrar a preparação por harness ou marcar BLOQUEADO; `:128` inclui NÃO EXECUTADO. `app-gestao-grupos.feature:56`, `:66` e `:126` têm `@blocked_implementation`. | PASS |

## Achados iniciais resolvidos

Os locais e descrições originais abaixo referem-se ao snapshot `ccd36be8`. Ambos foram resolvidos pelo autor em `5eac2b29`; não há achados pendentes.

### F1 — resultado monetário circular no upgrade (Major, D2)

Local: `tests/acceptance/app-assinaturas.feature:26`.

O cenário exige apenas “o valor exato retornado pela API para o upgrade”. Assim, qualquer valor incorreto da API continua satisfazendo a comparação com o Pix/UI. Isso contraria a exigência de `tests/acceptance/README.md:113` de preparar resultados conhecidos antes da ação. O contexto sabe apenas que a diferença é positiva (`app-assinaturas.feature:22`); não registra o resultado esperado em centavos.

Correção: preparar preços/ciclo/período e um instante de teste que permitam registrar antecipadamente o pró-rata esperado; comparar esse mesmo valor com a API, o Pix e a tela. A regra existente está em `backend/features/subscriptions/src/main/kotlin/br/com/saqz/subscriptions/application/SubscriptionPricing.kt:42`: diferença dos preços multiplicada pela fração restante do ciclo, em centavos inteiros. Uma fixture simples com resultado conhecido basta; não é necessária uma matriz de datas.

Resolução verificada: `tests/acceptance/app-assinaturas.feature:23` exige registrar preços, ciclo, período e pró-rata esperado antes da ação; `:27` e `:28` comparam esse valor com a cobrança, API, Pix e tela. `tests/acceptance/README.md:107` exige o instante de referência. A regra geral de `README.md:115` mantém BLOQUEADO quando falta recurso de preparação. **PASS na segunda revisão.**

### F2 — detalhe do jogo sem ação de abertura (Minor, D2)

Local: `tests/acceptance/app-jogos.feature:13`.

Depois de salvar, o roteiro espera “abre o detalhe de um único jogo publicado”. O fluxo atual retorna à tela anterior: `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/gameeditor/GameEditorRoot.kt:34` executa `onSave()` e `onBack()`, e `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHost.kt:810` liga esses callbacks a `pop` e atualização do detalhe do grupo. A rota de detalhe é aberta por uma ação específica.

Correção: descrever o retorno após salvar, verificar que o jogo único aparece na Agenda e acrescentar uma ação explícita de abrir esse jogo antes de conferir grupo/data/horário/capacidade. Não alterar a navegação do produto para satisfazer o roteiro.

Resolução verificada: `tests/acceptance/app-jogos.feature:13` exige retorno à tela anterior e um único jogo publicado; `:14` abre Agenda e seleciona o jogo; `:15` confere seus dados. A abertura e a confirmação de ATLETA e o isolamento de G2 permanecem em `:16`/`:17`. **PASS na segunda revisão.**

## Conferências adicionais de coerência

- Os bloqueios foram conferidos no código, sem executar o produto: `GroupSetupViewModel.kt:277` emite `DraftSaved` e `SaqzNavHost.kt:1014` apenas fecha a rota; `GroupScheduleViewModel.kt:124` emite `Saved` sem escrita; `GroupMembersViewModel.kt:120` zera pedidos e `:190`/`:198` alteram listas em memória. Esses três ViewModels estão em `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/`, nos diretórios `setup`, `schedule` e `members`.
- APP-MB02 acompanha os papéis do código: gestão de atletas para dono/admin e de papéis somente para dono (`GroupMembersViewModel.kt:107`). APP-MB03 separa edição e configuração de cobrança e declara a geração por harness (`tests/acceptance/app-gestao-grupos.feature:99`).
- Os rótulos de telefone de APP-P06 existem em `mobile/features/profile/presentation/src/commonMain/composeResources/values/strings_perfil_editar.xml:14`. O onboarding chama conclusão remota antes de abrir o formulário em `mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/presentation/appaccess/AppOnboardingViewModel.kt:53`.
- A paginação de 21 entradas atravessa a página de 20 itens configurada em `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/statement/StatementViewModel.kt:123`.
- A massa isolada de analytics representa a atribuição documentada: a mesma pessoa pode constar em dois cupons, mas pessoas/pagamentos são deduplicados no resumo (`docs/coupons/analytics.md:17`). Os pagamentos são posteriores aos usos e os trials estão encerrados no cenário principal. Os modos, prazo público e consumo de trial correspondem a `docs/trials/operation.md:5` e `:13`.
- Os 12 roteiros F1–F7 reaproveitados existem em `docs/receivables/final-operations-runbook.md:108`. Sua execução Asaas permanece pendente; a revisão não iniciou pagamentos, serviços, dispositivos ou alterações de ambiente.

## Gate documental e contagem

Comandos finais executados: `node /tmp/saqz-acceptance-review.0WeSg4/check.cjs` e `git diff --check fd22416ed34eff6b8eaa3daa083301738278c71a..5eac2b29`, ambos com saída 0. Os mesmos checks passaram no snapshot inicial. O script temporário foi inspecionado e usa `@cucumber/gherkin` com `@cucumber/messages`; ele valida estrutura e referências, não o significado dos resultados esperados.

| Superfície | Antes | Depois | Delta |
| --- | ---: | ---: | ---: |
| Arquivos `.feature` | 5 | 12 | +7 |
| Cenários/IDs | 65 | 111 | +46 |
| Casos ao expandir Exemplos | 89 | 141 | +52 |

Nos sete arquivos novos: 9/12 (cenários/casos) de primeiro acesso e perfil, 13/14 de grupos e membros, 5/5 de jogos, 6/6 de assinaturas, 4/4 de financeiro, 6/8 de trial e 3/3 de analytics. Três cenários novos, quatro casos expandidos, têm bloqueio de implementação. Nenhum cenário anterior foi removido ou enfraquecido pelo diff.

Verificações estruturais: 12 arquivos válidos, 111 IDs únicos, 17 referências locais válidas, zero falhas de sintaxe/ID/link e zero erros de whitespace. A revisão semântica encontrou dois ajustes, ambos resolvidos após um ciclo de correção e nova verificação.

## Sensor e limites

Sensor comportamental: **N/A**, pois o diff é exclusivamente documental. Nenhum código de comportamento foi introduzido e nenhuma jornada E2E foi executada; injetar falhas no produto não verificaria esta entrega.

Foram executadas duas provas de corrupção documental somente em memória: duplicar APP-OB01 e substituir um parâmetro por uma coluna inexistente. Ambas foram detectadas (2/2); nenhum arquivo do repositório foi mutado. Essas provas conferem o validador estrutural e não demonstram cobertura de regressão do produto.

Build de aplicativo, testes de domínio, Android/iOS, UAT e homologação Asaas: não executados, por estarem fora do diff e do pedido documental. Ator, preparação e resultados foram avaliados por leitura dos roteiros e amostragem dirigida de regras/código existentes; isso não garante que as jornadas passarão quando forem executadas.

A única escrita deste verificador é este relatório. Os arquivos locais não rastreados `.specs/features/recebimentos-asaas/context.md` e `direcionamento.md` foram preservados. Não houve commit nem push. Não foram criadas lições em outros arquivos porque o mandato do verificador limita a escrita ao relatório; F1 fundamenta a orientação reutilizável de registrar um oráculo monetário independente, e F2 a de explicitar ações de navegação antes das verificações de tela.

Trabalho documental aprovado no diff final. Execução futura dos roteiros deve usar os estados e limites do catálogo; não está incluída neste PASS.

## Registro de fechamento pelo coordenador

Após a aprovação independente, o coordenador registrou F1 e F2 como lições candidatas pelo
script da skill em `.specs/lessons.json` e `.specs/LESSONS.md`. Esse registro não altera o
parecer do verificador nem representa execução dos cenários do produto.
