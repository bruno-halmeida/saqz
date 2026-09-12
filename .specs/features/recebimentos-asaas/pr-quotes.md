Adiciona simulação de cobrança em centavos com as tabelas vigentes de cada meio e consulta dos termos publicados. A simulação informa base, taxas agregadas, total, líquido previsto e versões aplicáveis; não cria conta, aceite, dívida ou instrumento.

Sem configuração publicada para algum meio solicitado, retorna CONFIGURATION_UNAVAILABLE. Rejeita frações de centavo, valores ausentes e overflow sem truncar. Consultas permanecem acessíveis com autenticação normal, antes do cadastro e após corte comercial.

Base de revisão: feat/receivables-foundation. Três novos testes PostgreSQL/HTTP cobrem vigência, publicação, histórico, ausência de configuração, efeitos colaterais e contrato monetário. Gates: 12 testes unitários financeiros, 20 integração, 20 arquitetura e compilação do bootstrap passaram.

Ainda pendentes publicação administrativa de tarifas/termos, página pública e emissão com snapshot aceito. Nenhuma configuração comercial real foi adicionada.
