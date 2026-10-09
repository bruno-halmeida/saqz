# Roteiro 01 · Dono cria o grupo, atletas entram, jogo e lista de espera

Papéis: **ORG** em `Saqz_QA_A`, **ATL** no iPhone 17, **ATL2** em `Saqz_QA_B`, **ATL3** no iPhone 17e.
Use `<data>` = dia de hoje (`DD/MM`) nos nomes e `<tag>` = `DDMM` (sem barra) nos e-mails. Contas:
`qa-org+<tag>@saqz.local`, `qa-atl+<tag>@saqz.local`, `qa-atl2+<tag>@saqz.local`, `qa-atl3+<tag>@saqz.local`,
senha `SenhaQA2026`, celulares `11900000001` a `11900000004`, nomes `QA Org <data>`, `QA Atl <data>`,
`QA Atl2 <data>`, `QA Atl3 <data>`, apelidos no cadastro de atleta `Atl`, `Atl2`, `Atl3`. As conferências de
banco pressupõem banco limpo: comece com `stack.sh reset` + `stack.sh up` (e apague os dados do app nos
aparelhos: `adb shell pm clear app.saqz`; no iOS `simctl uninstall` + `simctl keychain <udid> reset` + reinstalar).

| # | Jornada | Aparelho | Faça | Espere (texto exato na listagem) | Banco |
| --- | --- | --- | --- | --- | --- |
| 1 | AC01 | ORG | Abrir app.saqz; "Criar conta ›"; nome `QA Org <data>`, e-mail, celular, senha; BACK; "Criar conta" | `Fala, QA!` e `Confirme seu e-mail para não perder o acesso à sua conta.` | `select count(*) from access_users where display_name='QA Org <data>'` = 1 |
| 2 | AC02 | ORG | Abrir o `oobLink` de oobCodes com curl; mandar o app para o plano de fundo e trazer de volta (`adb shell input keyevent HOME` e depois `monkey -p app.saqz 1`) | A faixa `Confirme seu e-mail…` some ao voltar do plano de fundo (é aí que o app recarrega o usuário, VUL-91; trocar de aba não reconsulta, por desenho; criar o grupo também a derruba porque refaz a sessão) | — |
| 3 | GR01 | ORG | "Criar meu grupo" → "Entendi, quero experimentar" → nome `QA Vôlei <data>`, esporte "Vôlei de quadra", "Criar grupo" → revisão → "Criar grupo" | `Grupo criado!` e `Marcar primeiro jogo` | `select name from access_groups where name='QA Vôlei <data>'` |
| 4 | CV01 | ORG | Rolar até Gestão → "Convidar por link" → "Criar novo link" | `Link não expira` e um texto iniciado por `https://links.saqz.app/?saqz_invite=`; anote o link | — |
| 5 | CV02 | ATL | `xcrun simctl openurl <udid-17> "saqz://links.saqz.app/?saqz_invite=<código>"` → "Open" (se o diálogo aparecer) → na tela do convite, "Criar conta ›" | `Entrando no QA Vôlei <data>` e `Convite de QA Org <data> · entrada liberada`; após criar: `Cadastro de atleta` | `select count(*) from group_memberships` = 2 |
| 6 | CV02 | ATL | Apelido `Atl`, "Ponteiro", "Intermediário", rolar, "Salvar meu perfil" | Tela do grupo com `PRÓXIMO JOGO` | — |
| 7 | CV04 | ATL2 | Criar conta (como 1, nome `QA Atl2 <data>`); `adb -s emulator-5556 shell am start -a android.intent.action.VIEW -d '<link>' app.saqz`; perfil como 6 com apelido `Atl2` | `Você entrou no QA Vôlei <data>` | `select count(*) from group_memberships` = 3 |
| 8 | CV04 | ATL3 | Criar conta (como 1, nome `QA Atl3 <data>`); Início → "Tenho um convite" → colar o link → "Entrar no grupo"; perfil como 6 com apelido `Atl3` | `Você entrou no QA Vôlei <data>` | = 4 |
| 9 | JG01 | ORG | Reabrir Início (trocar de aba); "Marcar jogo"; Data: amanhã; Horário 20h00; Quadra `Quadra QA`; Endereço `Rua QA, 1`; vagas 2 (Diminuir até `2`); "Marcar jogo" | No grupo: `PRÓXIMO JOGO`, `0 confirmados`, `4 pendentes` (os 4 membros, dono incluído), placar com `Na espera` | `select capacity,status from games order by created_at desc limit 1` = `2|PUBLISHED` |
| 10 | JG01 | ATL2 | Trocar de aba Grupos → Início | Cartão `PRÓXIMO JOGO` com `Vou` e `Não vou`, sem reabrir o app | — |
| 11 | JG02 (prioridade ligada) | ATL2 | "Vou" | `Você entrou na lista de espera.` e `Os mensalistas entram primeiro.` com `Mensalistas têm prioridade até o fim do prazo de confirmação` | `select status,waitlist_sequence from game_attendance` → `WAITLISTED|1` |
| 12 | Ajuste | ORG | Grupo → "Editar" → rolar até "Como preencher as vagas" → desligar "Prioridade de mensalista" → "Salvar alterações" | Volta ao grupo | `select mensalista_priority from access_groups where name='QA Vôlei <data>'` = f |
| 13 | JG02 | ATL3 | "Vou"; listar no mesmo segundo | `Você está confirmado.` e `1 de 2 confirmados` (o toast `Presença confirmada. Bom jogo!` some em segundos: registrar se viu, sem contar como falha) | `CONFIRMED` para ATL3 |
| 14 | JG02 | ORG | "Vou" no grupo | `2 confirmados` · `0 não vão` · `1 na lista de espera` · `1 pendentes` (ATL ainda não respondeu) | — |
| 15 | JG05 | ATL | "Vou" | `Lista de espera · 2º` e `As vagas acabaram. Você entrou na lista de espera.` (prioridade desligada e jogo cheio: aqui "as vagas acabaram" é o texto certo) | `select member_display_name,status,waitlist_sequence from game_attendance order by waitlist_sequence nulls last` → ATL2 `1`, ATL `2` |
| 16 | JG04/JG05 | ATL3 | "Alterar" → "Não vou" | `Sua vaga foi liberada.` (toast) e `Tudo bem, na próxima. Sua vaga já foi liberada.` | ATL2 `CONFIRMED`; `select source::text,old_status,new_status from attendance_events where game_id='<jogo>' order by occurred_at desc limit 1` = `SYSTEM|WAITLISTED|CONFIRMED` |
| 17 | Recarga | ATL2 | Trocar de aba Grupos → Início, sem reabrir o app | Cartão passa a `Você está confirmado.` | — |
| 18 | Posição | ATL | Trocar de aba e voltar | `Lista de espera · 1º` (era `· 2º`) | — |
| 19 | Prazo (ordem de chegada) | banco + ATL | `update games set confirmation_deadline=now()-interval '1 minute', capacity=3 where id='<jogo>'`; esperar até 70 s; ATL troca de aba e volta | Cartão de ATL `Você está confirmado.` | `select status from game_attendance where member_display_name='QA Atl <data>'` = `CONFIRMED`; evento novo `SYSTEM|WAITLISTED|CONFIRMED` (mesmo select do passo 16) |
| 20 | Prazo (escolha manual) | banco + ORG | `update access_groups set promotion_mode='MANUAL' where name='QA Vôlei <data>'; update games set capacity=4 where id='<jogo>'; update game_attendance set status='WAITLISTED', waitlist_sequence=(select waitlist_sequence_allocator+1 from games where id='<jogo>'), updated_at=now(), version=version+1 where game_id='<jogo>' and member_display_name='QA Atl3 <data>' and guest_seq=0;` (ATL3 volta para a reserva com vaga aberta: 3 confirmados em 4; o banco exige a sequência seguinte ao alocador); esperar 70 s; ORG abre "Notificações" | Aviso `Prazo de confirmação encerrado: sábado, …` com `1 na lista de espera · 1 vaga aberta` e `O grupo escolhe quem entra` | `select recipient_count from group_messages where channel='WAITLIST_DEADLINE'` = `1` (o dono, mesmo já tendo respondido) |

Relate também o tempo total e qualquer passo em que precisou usar adb/simctl no lugar do mobile-mcp.
