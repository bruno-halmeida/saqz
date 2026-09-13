# Revisão independente ADM — condições comerciais

Decisão: **APROVADO para integração do diff ADM**, sem defeitos bloqueadores encontrados. O gate cruzado de strings decimais foi comprovado no HTTP real Spring/Tomcat com o controller do produto. Não é homologação financeira ou aprovação de produção.

Escopo: `git diff 3a9f6b6d -- adm-web`, confrontado com `docs/receivables/payment-wave.md`, `AdminReceivableConditionsController`, `PublishFinancialConditions` e `JdbcFinancialConditionsPublisher`. HEAD observado: `d5ea8368ff7dd0f934a862ed86333f67c1d3f999`. Nenhuma fonte do produto, staging, branch ou commit foi alterado por esta revisão; todos os artefatos próprios estão em `/tmp`.

## Evidência executada

- `node --test adm-web/tests/*.test.cjs`: **45/45**, zero falhas/skips. Log `/tmp/saqz-payment-adm-review-node.txt`.
- Em `/tmp/saqz-payment-adm-review-probe/backend`, `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :bootstrap:test --tests '*AdminReceivableConditionsEndpointIntegrationTest' --offline --console=plain`: **4/4**, zero falhas/skips, incluindo três testes existentes e um probe scratch. Log `/tmp/saqz-payment-adm-review-http-stable.txt`; XML `bootstrap/build/test-results/test/TEST-br.com.saqz.bootstrap.AdminReceivableConditionsEndpointIntegrationTest.xml` sob essa cópia.
- Na mesma cópia/JDK, `./gradlew :features:receivables:integrationTest --tests '*FinancialConditionsPublicationIntegrationTest' --offline --console=plain`: **4/4**, zero falhas/skips. Log `/tmp/saqz-payment-adm-review-jdbc.txt`; XML `features/receivables/build/test-results/integrationTest/TEST-br.com.saqz.receivables.FinancialConditionsPublicationIntegrationTest.xml`.
- Inspeção visual independente das cinco imagens fornecidas: `/tmp/saqz-payment-adm-desktop.png`, `tablet.png`, `simulation.png`, `retry.png`, `conflict.png` (todas com o prefixo `/tmp/saqz-payment-adm-`). Formulários, rótulos, datas, bloqueio/reenvio, conflito e seis valores da simulação legíveis. Desktop 1440×1100 e tablet 800×1000; não se inferiu validação de viewport menor dessas imagens.

## Gate HTTP: decimal textual → BigDecimal

Probe preservado em `/tmp/saqz-payment-adm-review-probe/ProbeTest.kt:83` e aplicado somente ao teste da cópia temporária. Enviou JSON textual real a `localhost` pela classe Java HttpClient e recebeu HTTP 200 de `/admin/receivables/fees`, com:

- `providerRate: "0.0123456789"` recebido exatamente como `BigDecimal.toPlainString() == "0.0123456789"`;
- `providerFixedCents: "9223372036854775807"` recebido exatamente como `Long.MAX_VALUE`;
- `commissionFixedCents: "29"` recebido como 29;
- data ISO `2099-01-01T15:00:00.000Z` aceita, retorno normalizado `2099-01-01T15:00:00Z`;
- simulação com `baseCents: "10001"` e custo fixo `"29"` retornou envelope correto, base 10001, total 10387, comissão 229, provedor 157, taxas 386 e líquido 10001, **sem incrementar chamadas ao publicador**.

Evidência: `ProbeTest.kt:87`, `:89`, `:90`, `:93`, `:95`; XML HTTP linha 45 contém os dois envelopes capturados. O controller usa campos BigDecimal e `longValueExact()` em `backend/bootstrap/src/main/kotlin/br/com/saqz/adminweb/http/AdminReceivableConditionsController.kt:24` e `:35`; o simulate chama diretamente `FeeCalculator.quote` em `:80`. O publicador do probe é gravador em memória; Spring, JSON binding, servidor HTTP, controller e calculador são reais. A persistência foi exercitada separadamente pelos quatro testes JDBC existentes.

## Resultado por requisito

| Requisito | Evidência / avaliação |
|---|---|
| Centavos e porcentagens exatos | `adm-web/index.html:792` usa regex + BigInt; `:812` converte percentual de oito casas para fração de dez sem float; `:817` converte reais para centavos até Long.MAX_VALUE. Rejeita vazio, negativo, expoente e precisão excedida. `1,23456789% → "0.0123456789"`; limites do domínio concordam. |
| Publicação explícita, sem defaults | Formulários vazios em `adm-web/index.html:781`; botões explícitos em `:368`; construir/editar rascunho não envia. PIX/CARD explícitos. Não há gross-up local ou listagem fictícia de tarifas. |
| Simulação não publica | `adm-web/index.html:832` usa `/fees/simulate`, `:804` omite vigência no preview e `:816` só inclui base na simulação. Controller `:70` não chama publisher. Comprovado em HTTP e Node. |
| Datas e vínculo | `adm-web/index.html:804` exige futura e envia ISO UTC a partir do horário local explicado na UI `:367`. JDBC `JdbcFinancialConditionsPublisher.kt:47` exige termos publicados com vigência até a tabela; `:81` rejeita vigência passada. Testes JDBC cobrem a fronteira e condições anteriores preservadas. |
| Envelope | `adm-web/index.html:844` exige `{value,requestId}` correspondente; `:846` valida campos de publicação; `:847` usa os seis valores do backend. Envelope HTTP real compatível. |
| Timeout/idempotência | `adm-web/index.html:831` guarda corpo JSON e caminho; `:840` timeout de 15 s; `:842` mantém identidade em 5xx/408; `:850` bloqueia rascunho no resultado incerto. Reenvio reutiliza corpo byte a byte sem revalidar data. JDBC `:67` serializa por request ID e `:75` recupera resultado antes de verificar data `:81`. |
| Conflito | `adm-web/index.html:843` mostra 409, desbloqueia revisão e limpa identidade para novo pedido explícito. JDBC `:76` verifica ator/digest/tipo; `:94` traduz colisão de integridade em conflito. |
| Logout/respostas atrasadas | `adm-web/index.html:739` incrementa época e `:741` limpa pendências/rascunhos; `:834` verifica época e sequência; `:963` descarta resposta antiga no transporte. Node cobre limpeza e resposta atrasada. |

## Observações não bloqueadoras e limites

1. **Regressão HTTP textual ainda não está permanente no repositório.** O teste existente `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/AdminReceivableConditionsEndpointIntegrationTest.kt:83` utiliza literais JSON numéricos; o Node prova apenas o cliente. O probe scratch fecha o gate desta revisão, mas convém incorporar um caso equivalente ao backend em etapa autorizada para proteger upgrades/configuração do Jackson. Nenhuma incompatibilidade atual foi encontrada.
2. **Limite de apresentação de montantes extremos já documentado pelo implementador.** O backend serializa Long como JSON numérico; `adm-web/index.html:824` recusa números acima de Number.MAX_SAFE_INTEGER. Portanto o formulário aceita montantes Long válidos que a simulação não consegue apresentar, embora a publicação seja exata. A falha é explícita e não mostra dinheiro arredondado. Para suportar visualização de toda a faixa Long seria necessário contrato textual de resposta ou parser lossless; não é bloqueador para esta integração.
3. **Título de um teste Node superestima a cobertura temporal.** `adm-web/tests/receivables-conditions.test.cjs:22` diz “after date passes”, mas usa 2099 e não avança Date.now. Ele prova timeout e identidade de reenvio; a ordem de validação no cliente foi revisada e o retry após vigência foi exercitado no teste JDBC `FinancialConditionsPublicationIntegrationTest.kt:35`. Não foi executado cenário browser com relógio avançando.
4. As imagens são capturas de navegador/API mockadas produzidas pelo implementador; esta revisão inspecionou os PNGs e não reexecutou o navegador. Seus números de simulação são dados de fixture, não uma conferência aritmética das taxas preenchidas. O cálculo real foi exercitado pelo HTTP local.
5. A primeira tentativa em cópia do working tree capturou trabalho backend em andamento e falhou em `JdbcPaymentStore.kt:37` (`String?` vs `String`), log `/tmp/saqz-payment-adm-review-http.txt`. Isso não é achado do ADM nem gate do backend ativo. A cópia foi restaurada de `git archive HEAD backend` (untracked src removidos apenas no scratch), mantendo só o probe; a execução estável passou. Foi confirmado que os três contratos alvo não tinham diff contra HEAD. Não se atesta o restante das edições concorrentes backend/mobile.
6. HTTP usou autenticação de teste e publicador gravador; JDBC usou PostgreSQL local de teste. Nenhum servidor remoto ou Asaas real foi acionado. Nenhuma alegação de homologação Firebase/Asaas, deploy ou prontidão de produção.

Nada precisa ser corrigido no ADM para este gate; incorporar o probe como regressão permanente e ampliar os cenários temporais/viewport são melhorias de cobertura futuras.
