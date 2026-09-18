# S · Strings do detalhe do grupo novo

**Onda 1 · depende de: nada · bloqueia: V2, C1, C2, C3, C4, C5 · paralelo com: A, B, T, V1**

## Objetivo

Criar **todas** as chaves de texto novas do redesenho num arquivo próprio, de uma vez, para que nenhum ticket de tela toque em strings (arquivos de strings são ponto de conflito entre worktrees paralelas — foi o que o `strings_home.xml` resolveu na Início).

Depois deste PR as telas ficam **proibidas** de criar, renomear ou editar chave. Faltou texto num ticket seguinte? O worker para e avisa o orquestrador.

## Fora do escopo

- Não editar `strings.xml` nem nenhum outro `strings_*.xml` existente.
- Não remover chaves órfãs (é o D, no fecho).
- Não usar as chaves em código: este PR só cria o arquivo.

## Arquivos

| Ação | Arquivo |
|---|---|
| criar | `mobile/features/groups/presentation/src/commonMain/composeResources/values/strings_group_details.xml` |

**Tocar em arquivo fora desta lista = parar e avisar o orquestrador.**

## Passo a passo

### 1. Criar `strings_group_details.xml` (arquivo completo)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Detalhe do grupo novo · hero sem jogo -->
    <string name="group_details_hero_empty_admin">Marque o próximo e a galera confirma em um toque.</string>
    <string name="group_details_hero_first_title">Grupo criado!</string>
    <string name="group_details_hero_first_body">Vamos marcar o primeiro jogo? Defina data, local e vagas — depois é só chamar a galera.</string>
    <!-- Detalhe do grupo novo · minhas cobranças -->
    <string name="group_details_own_charges_history_show">Ver histórico (%1$d)</string>
    <string name="group_details_own_charges_history_hide">Ocultar histórico</string>
    <string name="group_details_own_charges_settled_title">Tudo em dia</string>
    <string name="group_details_own_charges_settled_meta">Nenhuma cobrança em aberto</string>
    <string name="group_details_own_charges_no_pix">O grupo ainda não cadastrou uma chave Pix. Combine o pagamento com o admin — a baixa acontece no caixa do grupo.</string>
    <!-- Detalhe do grupo novo · esperando você -->
    <string name="group_details_waiting_quorum">%1$d pessoas sem resposta</string>
    <string name="group_details_waiting_quorum_one">1 pessoa sem resposta</string>
    <string name="group_details_waiting_notify">Avisar</string>
    <string name="group_details_names_two">%1$s e %2$s</string>
    <string name="group_details_names_more">%1$s, %2$s e mais %3$d</string>
    <!-- Detalhe do grupo novo · próximos jogos -->
    <string name="group_details_agenda_meta">%1$d de %2$d confirmados</string>
    <string name="group_details_agenda_meta_venue">%1$d de %2$d · %3$s</string>
    <string name="group_details_agenda_meta_full">Lotado · %1$d de %2$d</string>
    <string name="group_details_agenda_meta_draft">Só você vê até publicar</string>
    <string name="group_details_agenda_status_draft">Rascunho</string>
    <string name="group_details_agenda_more">Ver mais %1$d jogos</string>
    <string name="group_details_agenda_more_one">Ver mais 1 jogo</string>
    <!-- Detalhe do grupo novo · mural -->
    <string name="group_details_mural_title">Mural</string>
    <string name="group_details_mural_notice_preview">%1$s: %2$s · %3$s</string>
    <string name="group_details_mural_notices_empty">Nenhum aviso por enquanto</string>
    <string name="group_details_mural_chat_meta">Fale com a galera do grupo</string>
    <!-- Detalhe do grupo novo · galera, gestão e quadra -->
    <string name="group_details_people_title">Galera</string>
    <string name="group_details_people_count">%1$d pessoas</string>
    <string name="group_details_people_count_one">1 pessoa</string>
    <string name="group_details_people_fallback">Membros</string>
    <string name="group_details_manage_title">Gestão</string>
    <string name="group_details_manage_invite_meta">Link, QR e pedidos de entrada</string>
    <string name="group_details_cash_balance">Saldo %1$s</string>
    <string name="group_details_home_court_title">Onde a gente joga</string>
</resources>
```

Pegadinha do compose-resources já registrada no projeto: `%%` **não** escapa como no Android. Nenhuma chave acima usa `%` literal — não acrescentar.

## O que é REUSO (não criar chave nova para isto)

Os tickets de tela usam estas chaves que já existem no módulo:

| Texto | Chave |
|---|---|
| "PRÓXIMO JOGO" | `home_game_next` |
| "Ver jogo" / "Ver no mapa" / "Editar" | `group_details_view_game` / `group_details_venue_map` / `group_details_venue_edit` |
| "Vou" / "Não vou" | `game_response_yes` / `game_response_no` |
| "Sua presença está confirmada." / "Você não vai jogar." (contrato do e2e) | `game_response_confirmed` / `game_response_declined` |
| "Alterar" / "Cancelar" | `home_attendance_change` / `home_attendance_cancel` |
| "Não foi possível salvar sua resposta. Tente novamente." | `game_response_request_failed` |
| "As confirmações estão encerradas." | `game_response_deadline_closed` |
| "Ao confirmar, a cobrança deste jogo será gerada." | `game_response_day_member_fee` |
| "A lista de presença pode estar desatualizada." / "Tentar novamente" | `game_response_roster_stale` / `game_response_retry_roster` |
| "%d de %d confirmados" / "Restam %d vagas" | `game_response_confirmed_summary` / `home_spots_left` |
| "Não foi possível abrir o mapa…" | `group_details_map_failure` |
| "Confirmar presença automaticamente" + falha | `game_response_auto_confirmation` / `game_response_auto_confirmation_failed` |
| "Sem jogo marcado" / "Quando a galera marcar…" | `home_no_game_hero_title` / `home_no_game_description` |
| "Marcar jogo" / "Convidar" / "Marcar primeiro jogo" | `home_admin_shortcuts_create_game` / `home_admin_shortcuts_invite` / `onboarding_create_action` |
| Placar "Vão" / "Não vão" / "Sem resposta" | `home_admin_score_going` / `home_admin_score_out` / `home_admin_score_pending` |
| Lista de espera (chip, caixa, ações, fila, sino, upsell) | `home_waitlist_*` |
| Toasts | `home_toast_confirmed` / `home_toast_declined` / `home_toast_waitlisted` / `home_toast_pix_copied` |
| "Minhas cobranças" / nota do pagamento manual / falha / "Tentar novamente" | `own_charges_title` / `own_charges_note` / `own_charges_failure` / `own_charges_retry` |
| Status da cobrança | `own_charges_status_pending` / `_paid` / `_waived` / `_cancelled` |
| "%d cobranças em aberto" / "Pix de %s" / "Chave copiada" | `home_own_charges_count` / `home_own_charge_pix_receiver` / `home_own_charge_copied` |
| "Esperando você" e as três linhas | `home_admin_waiting_title`, `home_admin_waiting_entry_requests`, `home_admin_waiting_monthly` (+ `_meta`), `home_admin_waiting_settle` (+ `_meta`), `home_admin_waiting_entry_chip` |
| Retorno do aviso (contrato do e2e) | `communication_reminded` / `communication_failure` |
| "Próximos jogos" / "%s · %s" / chips de resposta / CD da linha | `home_upcoming_title` / `home_upcoming_row_title` / `home_upcoming_status_going` / `_out` / `_waitlisted` / `home_upcoming_cd_row` |
| "Avisos" / "Conversa" | `group_details_notices` / `group_details_chat` |
| "Membros e permissões" / "Jogos e horários" / "Convidar por link" / "Caixa do grupo" / "Sair do grupo" | `group_details_manage_members` / `group_details_manage_schedule` / `group_details_invite_link` / `group_details_group_cash` / `group_details_leave` |
| Banner da foto | `group_details_created_photo_failed_title` / `group_details_created_photo_failed` |

## Testes

Não há teste novo: o gate é a compilação dos recursos (o `Res` gerado passa a expor as 32 chaves).

## Gates

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:generateResourceAccessorsForCommonMain
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:compileKotlinIosSimulatorArm64
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:detektAll
```

Depois do primeiro comando, confirmar que a chave saiu no `Res` gerado:

```sh
grep -rl "group_details_agenda_more_one" mobile/features/groups/presentation/build/generated/compose/resourceGenerator/kotlin/commonMainResourceAccessors | head -1
```

(tem de imprimir um caminho).

## Critérios de aceite

- [ ] Um único arquivo no diff, 32 chaves, todas com o prefixo `group_details_`.
- [ ] Nenhuma chave duplica nome que já existe em outro `strings*.xml` do módulo (`grep -rn 'name="group_details_' mobile/features/groups/presentation/src/commonMain/composeResources/values/ | awk -F'"' '{print $2}' | sort | uniq -d` não imprime nada).
- [ ] Os três gates verdes.

## Protocolo do worker

1. Branch a partir de `origin/main`: `git fetch origin && git switch -c vul-XXX-strings-detalhe-grupo origin/main` (XXX = número deste ticket).
2. Um commit em PT-BR: `chore(groups): strings do detalhe do grupo novo (VUL-XXX)`.
3. Rodar os gates antes de abrir o PR; colar a saída resumida no corpo.
4. PR contra `main`, aberto como ready (não draft), título `chore(groups): strings do detalhe do grupo novo (VUL-XXX)`.
5. `gh` desta máquina: prefixar `GH_TOKEN=$(gh auth token --user bruno-halmeida)` nos comandos `gh`.
6. Depois de abrir o PR: PARAR. Só executar mensagens do orquestrador que comecem com `CORRECAO:`.
