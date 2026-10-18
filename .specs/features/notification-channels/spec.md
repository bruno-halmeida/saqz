# Central de notificações multicanal

## Decisões confirmadas
- SDK de produção `com.github.thoughtbruno:uazapi-connector-sdk:0.1.0`, via JitPack.
- WhatsApp individual: avisos, lembretes de presença e cobranças; conversa excluída.
- Identidade confirmada pelo usuário: conta autenticada no Saqz, sem inferir telefone do clique.
- Link de confirmação abre o app autenticado e confirma presença respeitando regras do jogo.
- Credenciais em configuração externa; testes com servidor local, sem destinatários reais.

## Aceitação
- AC1: adaptador usa UazapiClient de instância, sendText, telefone internacional sem `+`, trackId estável; 429 respeita Retry-After; 408/5xx/rede transitórios, demais 4xx permanentes. Aceitação pelo provedor não significa leitura/entrega.
- AC2: a central mostra tudo (DEB-064): as chaves de "No app" não filtram mais nada. As preferências controlam só push e WhatsApp, independentes entre si; WhatsApp começa desligado. Configurações antigas continuam válidas (a coluna `in_app` fica no contrato para os apps antigos, mas não filtra a central). Cobranças continuam registradas na central.
- AC3: eventos criam filas duráveis atomicamente, uma por canal/destinatário/evento; replay não duplica; falha de um canal não impede outro. Revalidar associação, preferências e validade do lembrete antes de envio. Falhas têm limite e estado consultável no banco.
- AC4: push cobre avisos, conversas, presença e cobrança. WhatsApp exclui conversa, exige opt-in e telefone. Nenhum valor financeiro em push.
- AC8 (DEB-065): cancelamento de jogo e mudança de dia, hora ou local são identificados pela marca `event_kind` da mensagem (`GAME_CANCELLED`, `GAME_SCHEDULE_CHANGED`) e vão sempre por push, mesmo com "Avisos dos grupos" desligado. O push de cancelamento sai sem o jogo nos dados: nos apps 1.0.4 a 1.0.7 isso tira "Confirmar" e "Não vou" (categoria no iOS, botões no Android) e o toque abre a central, onde o aviso tem "Abrir".
- AC9 (DEB-071): a mudança de dia, hora ou local também vai ao grupo do WhatsApp vinculado (canal AUTO_CONFIRMATION, por `event_kind`), com a mesma copy da central e do push. Limite conhecido: a mensagem não tem jogo vinculado, então, numa falha passageira do provedor, uma nova tentativa pode sair depois de uma mudança mais nova.
- AC10 (DEB-070): denunciar uma mensagem ou aviso o esconde na hora de quem denunciou, na lista, na central e no push pendente, e continua escondido mesmo depois de desbloquear. O app só esconde depois que a denúncia foi enviada; denúncia de pessoa ou de grupo não esconde nada. Apps 1.0.4 a 1.0.7 só atualizam a lista na próxima carga.
- AC5: interface permite configurar canais e categorias; textos explicam telefone do perfil, permissão do sistema e escopo do WhatsApp; preserva carregamento/erro/salvar.
- AC7: link também encaminha ao cadastro de atleta do grupo antes de confirmar; novo membro entra pela política existente de convites (aprovação e limite do plano); cadastro concluído retoma o mesmo jogo/link. Cadastros históricos são preservados; novas associações só marcam conclusão no salvamento do próprio perfil.
- AC6: link de presença não muda estado via GET; app exige sessão, confirma só o próprio usuário, respeita prazo/capacidade/lista de espera e mostra resultado/erro. Duplicação não gera nova confirmação.

## Limites
Credenciais fornecidas não entram em git/logs. Homologação externa depende de instância conectada, configuração FCM/APNs e app links. Não enviar mensagens reais nesta implementação.
