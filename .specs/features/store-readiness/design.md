# Desenho

Preservar as fronteiras KMP documentadas em mobile/AGENTS.md. Ports nativos de
autenticação em access:domain; produto em commonMain; composição no compose-app.
Reusar o bootstrap Firebase e a máquina de sessão, sem segunda sessão paralela.

Exclusão é coordenada pelo backend, com operações externas idempotentes e sem
permitir novo bootstrap da mesma identidade durante limpeza pendente. O app
confirma identidade antes de enviar a solicitação e encerra a sessão somente após
aceite. A exclusão deve alcançar fotos, tokens e conteúdo associado, além da linha
do usuário. Retenção financeira deve ser descrita sem prometer remoção impossível.

Escopo de lançamento é uma capacidade explícita do aplicativo: desabilitar tanto
entradas quanto rotas restauradas. O backend de assinatura SaaS permanece disponível
à web. O rollout existente de Recebimentos continua sendo uma segunda barreira.

Crashlytics usa os plugins oficiais, mantendo desenvolvimento sem configuração.
iOS valida o plist durante o build e não permite fallback local em Release.

Links de produção precisam de identidade verificável. O certificado Play ainda
foi solicitado ao usuário; não substituir pela chave debug/upload. Documentar
passos de console restantes ao lado de evidências reproduzíveis.
