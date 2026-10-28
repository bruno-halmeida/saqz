# Roteiro 11 · Flags, atualização obrigatória e experimentos A/B

Regras do cofre cobertas: FLG-001 a FLG-009 (pasta "Flags e experimentos", decididas em 21/10). Os blocos dependem das entregas: **F1–F6** da DEB-119 (backend e adm) e da DEB-120 (app), **F7–F8** da DEB-121 (experimentos), **F9** da DEB-122 (recebimentos). Bloco cuja entrega ainda não está na `main` fica BLOQUEADO. Se o app e a nota divergirem, vale a nota; anote a divergência no relato.

**Rotas e rótulos marcados com ⟨⟩ são preenchidos pela entrega** de cada débito, com o nome exato da rota, da tela do adm e do texto do app. Até lá, o passo diz o que conferir.

Papéis: **ORG** (dono do grupo, em teste do Organizador), **ATL** (atleta do grupo), **TST** (atleta no grupo "testadores") e **ADMIN** (equipe do Saqz, admin do adm, sem aparelho: só API e adm). Contas: `qa-org+<tag>`, `qa-atl+<tag>`, `qa-tst+<tag>` e `qa-adm+<tag>`, todas `@saqz.local`, senha `SenhaQA2026`. Celulares `11900000001` (ORG), `11900000002` (ATL), `11900000008` (TST), `11900000009` (ADMIN). Grupo `QA Vôlei <data>`.

Etiquetas:
- **[CRÍTICO]**: entra na rodada segura.
- **[ao vivo: fora do stack]**: o Firebase não tem emulador de Remote Config, então o stack local não publica o aviso ao vivo. Aqui o passo confere a busca ao **voltar ao app**; a chegada em segundos se confere no ambiente dev (`saqz-dev`) ou em `tests/producao`.
- **[loja: só produção]**: o stack não tem App Store nem Google Play de verdade.
- **[Analytics: fora do stack]**: confere-se a linha de log do `AnalyticsSink` no app; o painel do Analytics fica para dev ou produção.

## Antes de começar

```sh
TAG=<tag>
PSQL() { docker exec saqz-multidev-db psql -U saqz -d saqz -Atc "$1"; }
tok() { curl -s "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=qa" -H 'Content-Type: application/json' -d "{\"email\":\"qa-$1+$TAG@saqz.local\",\"password\":\"SenhaQA2026\",\"returnSecureToken\":true}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["idToken"])'; }
# api <papel> <plataforma ANDROID|IOS> <versão N.N.N> <método> <caminho> [-d '<json>']
api() { w=$1; pl=$2; v=$3; m=$4; p=$5; shift 5; curl -s -w '\n%{http_code}\n' -X "$m" "http://127.0.0.1:18080$p" -H "Authorization: Bearer $(tok $w)" -H "X-Saqz-Platform: $pl" -H "X-Saqz-App-Version: $v" -H 'Content-Type: application/json' "$@"; }
anon() { pl=$1; v=$2; curl -s -w '\n%{http_code}\n' "http://127.0.0.1:18080/api/features" -H "X-Saqz-Platform: $pl" -H "X-Saqz-App-Version: $v"; }
uid() { PSQL "select id from access_users where email='qa-$1+$TAG@saqz.local'"; }
```

- **R-FLAGS** `<papel> <plataforma> <versão>`: `api <papel> <plataforma> <versão> GET /api/features` devolve só as flags e os valores daquela pessoa.
- **R-REGRA** `<chave> '<regra>'`: `api adm ANDROID 0.0.0 PUT /admin/features/<chave> -d '{"expectedVersion":<v>,"reason":"<motivo>","rule":<regra>}'`, com `<v>` = `version` de `api adm ANDROID 0.0.0 GET /admin/features/<chave>`. A regra **substitui a anterior inteira** (campo ausente volta ao padrão): `android`/`ios` (interruptores, padrão `true`), `state` (`OFF`, `AUDIENCE`, `ALL`), `alwaysOn`/`alwaysOff` (`{"users":[<uid>],"groups":["testadores"]}`), `audience` (`platforms` `ANDROID`/`IOS`, `minVersion`, `maxVersion`, `plans` `TITULAR`/`ORGANIZADOR`/`ILIMITADO`/`TRIAL`/`NONE`, `subscriptionStatuses` `ACTIVE`/`PAST_DUE`/`CANCELED`, `roles` `OWNER`/`ADMIN`/`ATHLETE`/`NONE`), `percentage` (0–100), `alertDisabled`. Ex. de F2.1: `{"state":"OFF","alwaysOn":{"groups":["testadores"]}}`. Chaves: `chat`, `purchases_asaas`, `purchases_app_store`, `purchases_google_play`, `whatsapp`. Sem motivo = 400; versão velha = 409.
- **R-VERSOES** `'<json>'`: `api adm ANDROID 0.0.0 PUT /admin/app-versions -d '{"expectedVersion":<v>,"reason":"<motivo>","android":{"minimum":"1.0.15","recommended":null},"ios":{"minimum":null,"recommended":null}}'`, com `<v>` = `version` de `GET /admin/app-versions`. Vazio ou `null` = sem versão. A versão chega em `appVersion` do `GET /api/features` da plataforma.
- **R-TELA-DEV**: no app de dev, ⟨caminho da tela escondida⟩ → forçar ou limpar o valor de uma flag ou variante.

## Preparação

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| P1 | terminal | `stack.sh reset`, `stack.sh up` (bootJar refeito), `builds.sh`, `aparelhos.sh instalar`; dados dos apps apagados | Backend em 18080 | — | ☐ | ☐ |
| P2 | ORG, ATL, TST | Criar as contas e o grupo `QA Vôlei <data>` como no roteiro 05 (P2–P4); ATL e TST entram pelo convite público | ORG dono, ATL e TST atletas | `select count(*) from group_memberships where group_id=(select id from access_groups where name='QA Vôlei <data>')` = 2 | ☐ | ☐ |
| P3 | terminal | Criar `qa-adm+<tag>` pelo emulador de Auth e `PSQL "update access_users set platform_admin=true where email='qa-adm+$TAG@saqz.local'"` | — | `select platform_admin from access_users where email='qa-adm+$TAG@saqz.local'` = `t` | ☐ | ☐ |
| P4 | ADMIN | No adm, aba **Flags** → "Grupos com nome": criar `testadores`; no editor de uma flag, buscar o celular de TST e tocar "No grupo aberto"; salvar com motivo. Conferir que `revisores` já existe (pela API: `PUT /admin/feature-groups/testadores` com `{"expectedVersion":0,"reason":"QA","members":["<uid tst>"]}`) | `testadores` com 1 pessoa | `select g.name, count(m.user_id) from feature_user_groups g left join feature_user_group_members m on m.group_name=g.name group by g.name` | ☐ | ☐ |

## F1 · O dia da troca: nada muda (FLG-008, FLG-001)

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| F1.1 [CRÍTICO] | terminal | R-FLAGS `org ANDROID <versão do build>` e `org IOS <versão>` | Chat e assinatura pelo Asaas **desligados**; compra no Google Play **ligada só no Android**; compra na App Store **ligada só no iOS**; WhatsApp igual à variável do stack | — | ☐ | ☐ |
| F1.2 | terminal | A mesma chamada como ATL | Mesmos valores; a resposta **não** traz regra, lista, porcentagem nem grupo | — | ☐ | ☐ |
| F1.3 [CRÍTICO] | ORG, ATL | Abrir o app e navegar: Início, grupo, Perfil → "Meu plano" | Igual à versão anterior: sem chat, sem "Pagar pelo app", com a compra da loja da plataforma | — | ☐ | ☐ |
| F1.4 | terminal | `api org ANDROID <versão> GET /api/whatsapp/availability` | Mesmo valor da flag do WhatsApp | — | ☐ | ☐ |
| F1.5 | terminal | `anon IOS <versão>` (sem login) | Só flags "ligada para todos" e a versão mínima e recomendada; flags de público aparecem desligadas | — | ☐ | ☐ |

## F2 · Quem recebe (FLG-002)

Use a flag do chat como cobaia. Volte ao estado de F1 no fim do bloco.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| F2.1 [CRÍTICO] | terminal | R-REGRA chat: estado desligada, grupo `testadores` em "sempre ligada" | R-FLAGS: chat ligado só para TST; ORG e ATL desligado | — | ☐ | ☐ |
| F2.2 [CRÍTICO] | terminal | Mesma regra, interruptor do iPhone desligado | TST: ligado no ANDROID, desligado no IOS | — | ☐ | ☐ |
| F2.3 | terminal | Estado ligada para o público, público = papel **dono**, 100% | ORG ligado; ATL desligado (TST continua ligado pela lista) | — | ☐ | ☐ |
| F2.4 | terminal | Público = papel dono **e** plano **Titular** | ORG (em teste do Organizador) desligado | — | ☐ | ☐ |
| F2.5 | terminal | Público = plano **em teste** | ORG ligado | — | ☐ | ☐ |
| F2.6 | terminal | Público = versão mínima `<versão do build + 1>` | R-FLAGS com a versão do build: desligado; com a versão +1: ligado | — | ☐ | ☐ |
| F2.7 | terminal | Ligada para todos, ATL em "sempre desligada" | ATL desligado; ORG e TST ligados | — | ☐ | ☐ |
| F2.8 | terminal | Tentar pôr a mesma pessoa nas duas listas | Recusado | — | ☐ | ☐ |
| F2.9 | terminal | Ligada para o público (público vazio), 50%; anotar quem ficou ligado entre ORG, ATL e TST (tirar TST da lista antes) | Para cada pessoa, o mesmo resultado em ANDROID e IOS | — | ☐ | ☐ |
| F2.10 | terminal | Subir para 80% | Quem estava ligado a 50% **continua** ligado | — | ☐ | ☐ |
| F2.11 | terminal | `anon ANDROID <versão>` com a regra de F2.9 | Chat desligado (antes do login, porcentagem não vale) | — | ☐ | ☐ |

## F3 · Mudar e desligar (FLG-003)

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| F3.1 [CRÍTICO] | ORG | Com a flag do WhatsApp ligada, abrir o grupo → aba do WhatsApp nas preferências | Vínculo do WhatsApp visível | — | ☐ | ☐ |
| F3.2 [CRÍTICO] [ao vivo: fora do stack] | ORG, terminal | Com o app aberto **fora** da tela do WhatsApp: desligar a flag (R-REGRA); mandar o app para o fundo e voltar | Na volta, a entrada do WhatsApp sumiu | — | ☐ | ☐ |
| F3.3 | ORG, terminal | Religar; entrar na tela do vínculo; desligar com a pessoa **dentro** da tela | Continua na tela; ao sair e voltar ao app, a entrada sumiu | — | ☐ | ☐ |
| F3.4 [CRÍTICO] | terminal | Flag do WhatsApp desligada: tentar o vínculo pela API como ORG | `PUT /api/groups/<id>/whatsapp-binding` responde 403 problem+json com `code: FEATURE_DISABLED`; o app mostraria `Essa função não está disponível no momento` | Nada gravado em `group_whatsapp_bindings` | ☐ | ☐ |
| F3.5 | ORG | Com o grupo já vinculado (religar, vincular, desligar): abrir o que existe | Consultar e desfazer o que existe continua possível; vincular de novo não | — | ☐ | ☐ |
| F3.6 | ORG (iOS) | Flag da compra na App Store desligada; Perfil → "Meu plano" | Sem botão de assinar pela App Store | — | ☐ | — |
| F3.7 [loja: só produção] | — | Compra da loja chega para validar com a flag desligada | Validada normalmente (conferir em `tests/producao`) | — | ☐ | ☐ |
| F3.8 | ATL | Modo avião; abrir o app | Vale o último valor recebido; sem erro | — | ☐ | ☐ |

## F4 · O adm (FLG-004)

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| F4.1 [CRÍTICO] | terminal | R-REGRA sem motivo | Recusado | — | ☐ | ☐ |
| F4.2 [CRÍTICO] | terminal | R-REGRA com o token de ORG (não é admin) | 403 | — | ☐ | ☐ |
| F4.3 | terminal | Duas R-REGRA seguidas com a **mesma** versão esperada | A segunda volta conflito; a regra é a da primeira | — | ☐ | ☐ |
| F4.4 | ADMIN | Abrir o histórico do chat | Uma linha por mudança dos blocos anteriores, com quem, quando, motivo, antes e depois | `select version, reason, actor_user_id, created_at from feature_history where entity_type='FLAG' and entity_key='chat' order by created_at`; `update feature_history set reason='x'` e `delete from feature_history` recusados (`feature_history is immutable`). No adm: aba Flags → editar `chat` → "Histórico da flag" | ☐ | ☐ |
| F4.5 | ADMIN | "Voltar para esta versão" numa linha antiga, com motivo | A regra antiga vale de novo; o histórico ganha uma linha nova | — | ☐ | ☐ |
| F4.6 | terminal | Pôr uma flag ligada para todos nas duas plataformas e recuar no banco a data em que ela ficou 100% para 31, 61 e 91 dias | Alerta amarelo, depois laranja, depois vermelho, no topo da lista do adm (`GET /admin/features` traz `alert` = `YELLOW`/`ORANGE`/`RED`) | `update feature_flags set full_rollout_since = now() - interval '31 days' where flag_key='<chave>'` (depois 61 e 91) | ☐ | ☐ |
| F4.7 | ADMIN | Desligar o alerta dessa flag com motivo | Sem alerta; o motivo no histórico | — | ☐ | ☐ |
| F4.8 | terminal | Desligar o interruptor do iPhone dessa flag | Sem alerta (não está 100% nas duas plataformas) | — | ☐ | ☐ |

## F5 · Atualização do app (FLG-007)

Vale só para build com o pop-up (1.0.15 em diante, DEB-117). Desde 28/10/2026 é um pop-up que não fecha, não mais tela cheia.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| F5.1 [CRÍTICO] | ATL | R-VERSOES: mínima da plataforma de ATL = versão do build + 1; mandar o app para o fundo e voltar | Pop-up que não fecha: `Atualize o Saqz`, `Tem versão nova do app. Para continuar usando, é só atualizar.` e um botão, `Atualizar no Google Play` (Android) ou `Atualizar na App Store` (iOS). Sem alça e sem X; tocar fora e o voltar do Android não fecham; o app por baixo não responde | — | ☐ | ☐ |
| F5.2 | ATL | Tocar no botão | Abre a loja da plataforma na página do Saqz (Play Store no emulador com Play; App Store no simulador pode não abrir: anotar). Voltar ao app sem atualizar: o pop-up continua | — | ☐ | ☐ |
| F5.3 | ORG | Na outra plataforma, sem mínima | Abre normal | — | ☐ | ☐ |
| F5.4 [CRÍTICO] | ORG | Mínima da plataforma de ORG acima, com ORG no meio de "Marcar jogo" | Nada na hora; termina a ação; o pop-up aparece na próxima volta ao app | — | ☐ | ☐ |
| F5.5 | ATL | Mínima de volta ao normal; recomendada = versão + 1; voltar ao app | Pop-up `Tem versão nova do Saqz` · `Atualize para ter as novidades e as correções.` com X, `Agora não` e `Atualizar`; tocar em `Agora não`; voltar de novo: não aparece. Recomendada = versão + 2 e voltar: aparece mais uma vez | — | ☐ | ☐ |
| F5.6 | ATL | "Sair da conta" com a mínima acima | O pop-up que não fecha aparece também na tela de entrada | — | ☐ | ☐ |
| F5.7 | TST | App recém-instalado, modo avião, primeira abertura com a mínima acima | Não bloqueia | — | ☐ | ☐ |
| F5.8 | terminal | Zerar mínima e recomendada | Todos abrem normal | — | ☐ | ☐ |
| F5.9 | ATL | Mínima e recomendada acima da versão do build; voltar ao app | Só o pop-up que não fecha | — | ☐ | ☐ |

## F6 · Tela de dev (FLG-009)

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| F6.1 [CRÍTICO] | ATL | R-TELA-DEV: forçar o chat ligado | O chat aparece só neste aparelho; ORG não vê | — | ☐ | ☐ |
| F6.2 | ATL | Limpar o valor forçado | Volta ao valor do backend | — | ☐ | ☐ |
| F6.3 [CRÍTICO] | terminal | Conferir o teste de build que garante que a build `prodRelease` (Android) e a `Release` (iOS) não têm a tela | Teste verde; a tela não existe | — | ☐ | ☐ |

## F7 · Experimentos (FLG-005) · depende da DEB-121

Use a flag de QA que a entrega da DEB-121 registrar (⟨chave⟩). Ela tem um parâmetro de escolha A/B e é mostrada numa tela própria; existe só fora de produção.

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| F7.1 [CRÍTICO] | ADMIN | Criar experimento: público vazio, 100%, A 50 / B 50, métrica principal "criou grupo", prazo 14 dias | Experimento rodando | ⟨tabela de experimentos⟩ | ☐ | ☐ |
| F7.2 | terminal | R-FLAGS para ORG e ATL **sem** abrir a tela | Nenhuma exposição gravada | ⟨tabela de exposições⟩ = 0 linhas | ☐ | ☐ |
| F7.3 [CRÍTICO] | ORG, ATL | Abrir a tela da flag de QA | Cada um vê A ou B; o log do app mostra `exp_<chave>` [Analytics: fora do stack] | 1 exposição por pessoa, com a variante | ☐ | ☐ |
| F7.4 | ORG | Abrir a tela de novo, fechar e reabrir o app | Mesma variante; nenhuma exposição nova | — | ☐ | ☐ |
| F7.5 | terminal | Tentar mudar os pesos | Recusado; só pausar ou encerrar | — | ☐ | ☐ |
| F7.6 | ADMIN, ORG | Pausar; ORG abre a tela | ORG vê o padrão (A) | — | ☐ | ☐ |
| F7.7 | ADMIN, ORG | Retomar | ORG volta à variante de F7.3 | — | ☐ | ☐ |
| F7.8 [CRÍTICO] | terminal, ATL | Desligar o interruptor da plataforma de ATL | Desligada para ATL; o período não conta; ao religar, ATL volta à variante dele | — | ☐ | ☐ |
| F7.9 | TST | TST na lista "sempre ligada"; abrir a tela | Vê o valor ligado; **não** grava exposição | — | ☐ | ☐ |
| F7.10 | ATL | R-TELA-DEV: forçar a variante B | Vê B; nada conta | — | ☐ | ☐ |
| F7.11 | terminal | `anon` com o experimento rodando | A flag de QA no padrão (antes do login não há experimento) | — | ☐ | ☐ |

## F8 · Medir e encerrar (FLG-006) · depende da DEB-121

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| F8.1 | ATL | Usar o app hoje duas vezes | 1 registro de uso de hoje | ⟨tabela de uso diário⟩ | ☐ | ☐ |
| F8.2 | ADMIN | Abrir o relatório de F7.1 | Por variante: quantos entraram e converteram; **inconclusivo** (menos de 2 semanas) | — | ☐ | ☐ |
| F8.3 | terminal | Recuar no banco o início do experimento e as exposições em 15 dias; fazer ORG criar um grupo dentro do prazo e ATL fora dele | Só a conversão de ORG conta | — | ☐ | ☐ |
| F8.4 | ADMIN | Encerrar escolhendo B | A flag passa a dar o valor de B para todos; relatório congelado; `exp_<chave>` limpa | — | ☐ | ☐ |

## F9 · Recebimentos (FLG-008) · depende da DEB-122

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| F9.1 [CRÍTICO] | terminal | Antes da migração: anotar o rollout (`select * from receivable_rollout`, overrides); depois: R-FLAGS de quem estava liberado e de quem estava bloqueado | Liberado → ligado ("sempre ligada"); bloqueado → desligado ("sempre desligada") | — | ☐ | ☐ |
| F9.2 [CRÍTICO] | ORG | Flag ligada para ORG; configurar o recebimento pelo app (⟨passos da entrega⟩) | Fluxo disponível | — | ☐ | ☐ |
| F9.3 [CRÍTICO] | terminal | Desligar a flag; tentar criar uma cobrança nova pelo app | Recusado com `Essa função não está disponível no momento` | Nada novo em `receivable_orders` | ☐ | ☐ |
| F9.4 | ORG | Abrir o que já existia | Consultar e manter continuam | — | ☐ | ☐ |
| F9.5 | terminal | Flag ligada, conta com `new_operations_enabled=false` | Nada novo para essa conta | — | ☐ | ☐ |
| F9.6 | terminal | `api org ANDROID <versão antiga> GET /api/receivables/availability` | Responde a partir da flag | — | ☐ | ☐ |

## Rodadas

Rodar o roteiro inteiro duas vezes, cada uma com `stack.sh reset` e apps limpos. Marcar o resultado na coluna da plataforma do aparelho do papel que age na linha. Linhas só de terminal marcam as duas colunas quando a chamada é feita com as duas plataformas.

| Papel | Rodada 1 | Rodada 2 |
| --- | --- | --- |
| ORG | `Saqz_QA_A` | iPhone 17 |
| ATL | iPhone 17 | `Saqz_QA_A` |
| TST | `Saqz_QA_B` | iPhone 17e |

Relate PASSOU, FALHOU ou BLOQUEADO por linha, com o que viu, o tempo total, as divergências entre nota e app e os passos em que precisou de adb ou simctl no lugar do mobile-mcp.
