# Mapa de achados e testes

Objetivo: para cada achado das rodadas de QA (09, 13 e 15/10/2026) e do `problemas-conhecidos.md`, dizer
qual teste o cobre em cada camada e o que falta. Este arquivo é o ponto de partida; as fases abaixo o
preenchem. Atualize-o no mesmo commit de cada teste novo.

## Camadas

| Camada | Onde vive | Executa em |
| --- | --- | --- |
| Unitário / integração do backend | `backend/**/src/test`, `integrationTest` (Postgres de teste) | Gradle, CI |
| Unitário do mobile (ViewModel, mapper, tela) | `mobile/**/commonTest`, `androidHostTest`, `iosTest` | Gradle, CI |
| E2E Android instalado | `tests/e2e/android` | local (`-Psaqz.e2e=true`) |
| Multi-aparelho / roteiros manuais | `tests/multi-aparelhos/roteiros`, `tests/producao` | manual, aparelhos reais |
| Aceitação (Gherkin) | `tests/acceptance` | sem executor (catálogo manual) |
| Carga / volume | `tests/load` | local |
| Mutação | **não existe** | ver Fase 4 |

## Situação em 17/10/2026

- 192 commits sem merge desde 09/10; 116 são `fix`/`feat`. **87 trazem teste no próprio commit; 29 não.**
  "Sem teste no commit" não prova que falta teste (pode estar em commit vizinho): é a fila de conferência da Fase 2.
- Nenhum arquivo de `tests/producao` ou `tests/acceptance` cita ID de achado (N-, P0–P3-, BE-, WA-); só os PC-xx
  têm página própria. Nenhum cenário tem tabela "Variações" (erro, rede, permissão), embora o README prometa.
- Mutação: sem PIT/Stryker em nenhum módulo.
- Conferido por amostra (com teste no commit): N-16, N-17, N-18, P1-12d, P2-4e, P2-4g, P2-2a, WA-2.

## Fase 2 · correções sem teste no próprio commit (29)

| Commit | Assunto |
| --- | --- |
| `0c00466dd` | fix(ci): tag da publicação sai mesmo com a esteira completa de testes desligada |
| `f93532be9` | fix(design-system): folhas sem conteúdo deixam de passar content vazio e perdem a faixa vazia |
| `35ca4a0c5` | fix(ios): porta de aviso usa async/await para não cruzar callbacks entre isolamentos |
| `7c2fe2939` | fix(perfil): dica da foto diz só onde ela aparece de fato |
| `fe40ba8f4` | fix(android): barras de status e navegação com ícones escuros no modo escuro do sistema |
| `67fefed91` | fix(site): termos de recebimentos leem a API de produção, não a de desenvolvimento |
| `457b21784` | feat(convite): app abre o cadastro de atleta logo ao pedir entrada, antes da aprovação |
| `8a48fdf3d` | feat(convite): cadastro de atleta guardado no pedido de entrada e aplicado na aprovação |
| `d41ed11cb` | fix(ci): wizard grava a memória em ~/.config/saqz, não no .env da pasta atual |
| `43101b8c2` | fix(whatsapp): imagem do lembrete com endereço novo |
| `42fb3f412` | fix(links): nova URL da imagem de prévia para o WhatsApp buscar a versão nova |
| `b018c4548` | fix(links): imagens do WhatsApp com poucos elementos e grandes |
| `b7540ac31` | feat(web-app): ícone e manifesto para a tela de início |
| `0df819d9e` | feat(links): imagens de marca para o WhatsApp |
| `77f9beea9` | fix(landing): logo correto na imagem de prévia do link |
| `2815c193a` | fix(sites): landing e links com Cache-Control no-cache |
| `9cac88ef5` | fix(web): login com Google no celular por redirecionamento first-party |
| `8204aa26f` | fix(web): scripts com versão na URL e .wasm anterior servido por mais uma publicação |
| `47f88e9cf` | fix(web): publicação do web app por tar/ssh (o servidor não tem rsync) |
| `b16357e7d` | fix(web): QR do convite em PNG no navegador |
| `94b18a9c6` | fix(web): banco de fusos IANA no navegador (grupo era recusado com fuso SYSTEM) |
| `86a5b3e2f` | feat(web): login pelo Firebase Auth JS e voltar do navegador ligado ao app |
| `933464d70` | fix(web): nginx sem sobrescrever os MIME types, Inter com gzip e pré-carregada |
| `1455fa886` | feat(web): foto do grupo e do perfil pelo navegador |
| `20440d9f8` | fix(web): Koin antes das portas, coluna de celular no desktop e ferramentas JS no settings |
| `114b21ac4` | feat(web): hospedeiro web (:web-app) com composition root e portas locais |
| `2c2770e18` | feat(landing): medição com GA4, Clarity e pixel da Meta sob consentimento |
| `baa2a898f` | feat(web): alvo wasmJs em todos os módulos KMP |
| `88f109067` | feat(grupos): placar do gestor mostra quem está na lista de espera |

Os de `feat(web)`/`fix(web)`/`fix(links)`/`fix(sites)`/`fix(landing)` são infraestrutura de publicação e
estáticos: precisam de teste do site (`site-verify`) ou de roteiro, não de unitário Kotlin. Os de `feat(convite)` e
`fix(grupos)` (cadastro guardado no pedido, abrir o cadastro ao pedir entrada, placar com a espera) são regra de
produto e vêm primeiro.

## Fase 2 · achados sem correção (nunca tiveram teste)

Tudo abaixo é comportamento atual; o teste que reprova hoje vira o critério de aceite da correção, ou fixa a
decisão "aceito" para o comportamento não mudar sem querer.

| Origem | Achados |
| --- | --- |
| Decididos "para depois" (15/10) | P2-1b, P2-2b, P2-2c, P2-7a, P2-7b, P2-6a, P2-6b, P2-13a, P2-13c, P2-13f, WA-4a, WA-4b, WA-5a, WA-5b, BE-4a, BE-4c, BE-7, BE-9, N-14, N-15, P3-2b, P3-2h, P2-11b, P3-4e, P3-5a, P3-5c |
| Deixados de lado pelo dono | P2-5/PC-08 (recado do jogo), WA-4c ("Avisar" sem trava), BE-4b (cobrar cobrança paga: `VERSION_CONFLICT`) |
| Abertos em `problemas-conhecidos.md` | PC-01 (aceito), PC-13 (extrato sem seletor de mês), PC-14 (recibo Play com "—") |
| Backend 13/10 (estado a conferir) | jogo em 2020 e prazo no passado aceitos; `startsAt` ≠ data/hora local aceito; corpo vazio em `POST /api/groups` → 500; rascunho não cancela (409); mensalidade já vencida; `autoConfirmEnabled`; dono contado como pendente; jobs sem log de execução |
| 15/10 | `GET /groups/{id}/charges/me` de grupo excluído devolve `GAME_NOT_FOUND`; `/subscriptions/me` com `autoRenew: null` na web; push só descarta `UNREGISTERED`/`SENDER_ID_MISMATCH` |
| Funil do WhatsApp | 11 combinações conferidas à mão; tela do link sem o jogo; presença de conta nova no Android que não registrou uma vez |

## Mudanças de regra já implementadas

| Regra | O que mudou | Commit |
| --- | --- | --- |
| PRES-006 (opção B) | Desistência de confirmado antes do prazo: sobe o mensalista da espera e, sem mensalista, o avulso mais antigo | `ef1e86040` |

## Fases

1. **Mapa** (este arquivo): achado → camada → teste existente. Fecha com a tabela completa.
2. **Lacunas**: conferir os 29 commits, escrever teste para o que faltar e para os achados sem correção,
   por prioridade P0 → P3. Commit a cada bloco, direto na `main`.
3. **Roteiros**: ligar cada achado a um passo de `tests/producao`/`multi-aparelhos`; criar as tabelas
   "Variações" (erro, rede, permissão); roteiro do funil do WhatsApp e das regressões N-16/N-17/N-18.
4. **Mutação**: PIT no backend (Gradle), módulos `groups`, `games`, `subscriptions`, `finance`; meta de
   mutation score definida depois da primeira medição. Mobile depois, só lógica comum.
