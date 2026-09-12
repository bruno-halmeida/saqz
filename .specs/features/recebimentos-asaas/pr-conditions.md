Permite ao administrador da plataforma publicar versões de termos e tarifas com vigência e consultar um preview antes de publicar. Comandos usam requestId, atribuição ao ator autenticado e auditoria imutável na mesma transação da versão. Repetição concorrente não duplica publicação; conteúdo diferente conflita.

Novas condições entram em vigor na data informada, preservando versões aceitas. Taxas com precisão não representável e centavos fracionários são rejeitados. Não há preços padrão nem condições reais publicadas. Base de revisão: feat/receivables-quotes.

V51 adiciona a auditoria sem modificar migrações anteriores. Sete testes novos: quatro PostgreSQL e três HTTP com autenticação administrativa. Também corrige o mapeamento preexistente de rota inexistente, que respondia 500 em vez de 404; o cenário de segurança demonstrou o problema.

Painel visual, emissão de cobranças e liberação do piloto permanecem pendentes.
