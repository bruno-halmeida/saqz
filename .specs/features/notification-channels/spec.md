# Central de notificações multicanal

## Decisões confirmadas
- SDK de produção `io.github.uazapi:uazapi-connector-sdk:0.1.0-SNAPSHOT`, Maven local.
- WhatsApp individual: avisos, lembretes de presença e cobranças; conversa excluída.
- Identidade confirmada pelo usuário: conta autenticada no Saqz, sem inferir telefone do clique.
- Link de confirmação abre o app autenticado e confirma presença respeitando regras do jogo.
- Credenciais em configuração externa; testes com servidor local, sem destinatários reais.

## Aceitação
- AC1: adaptador usa UazapiClient de instância, sendText, telefone internacional sem `+`, trackId estável; 429 respeita Retry-After; 408/5xx/rede transitórios, demais 4xx permanentes. Aceitação pelo provedor não significa leitura/entrega.
- AC2: preferências de app, push e WhatsApp independentes; WhatsApp começa desligado. Configurações antigas continuam válidas. Cobranças continuam registradas na central.
- AC3: eventos criam filas duráveis atomicamente, uma por canal/destinatário/evento; replay não duplica; falha de um canal não impede outro. Revalidar associação, preferências e validade do lembrete antes de envio. Falhas têm limite e estado consultável no banco.
- AC4: push cobre avisos, conversas, presença e cobrança. WhatsApp exclui conversa, exige opt-in e telefone. Nenhum valor financeiro em push.
- AC5: interface permite configurar canais e categorias; textos explicam telefone do perfil, permissão do sistema e escopo do WhatsApp; preserva carregamento/erro/salvar.
- AC7: link também encaminha ao cadastro de atleta do grupo antes de confirmar; novo membro entra pela política existente de convites (aprovação e limite do plano); cadastro concluído retoma o mesmo jogo/link. Cadastros históricos são preservados; novas associações só marcam conclusão no salvamento do próprio perfil.
- AC6: link de presença não muda estado via GET; app exige sessão, confirma só o próprio usuário, respeita prazo/capacidade/lista de espera e mostra resultado/erro. Duplicação não gera nova confirmação.

## Limites
Credenciais fornecidas não entram em git/logs. Homologação externa depende de instância conectada, configuração FCM/APNs e app links. Não enviar mensagens reais nesta implementação.
