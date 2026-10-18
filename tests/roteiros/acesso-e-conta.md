# Roteiro manual · Acesso e conta

Gerado das seções **QA** das regras do cofre (`Obsidian Vault/Saqz/Regras`). A regra manda: se o roteiro e o cofre divergirem, vale o cofre.

Etiquetas: **[CRÍTICO]** entra na rodada segura; **[MULTI]** depende de duas ou mais pessoas e tem roteiro em `tests/multi-aparelhos/roteiros/`; **[push: só produção]** o ambiente local não tem push.

Rode **em iOS e em Android**: nas colunas *iOS* e *Android* troque `☐` por `✔` (passou) ou `✘` (falhou, com anotação). Onde a regra depende de pessoas diferentes, vale o roteiro multi-aparelho, com os papéis invertidos entre as plataformas (organizador no iPhone e depois no Android).

## ACE-001 · Entrar e criar conta [CRÍTICO]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Cadastro completo e válido | Conta criada | ☐ | ☐ |
| E-mail já usado | "Esse e-mail já tem conta no Saqz. Entrar?" | ☐ | ☐ |
| Telefone com menos de 11 dígitos | "Telefone incompleto. Use DDD + 9 dígitos." | ☐ | ☐ |
| Senha com 7 caracteres | "Use no mínimo 8 caracteres." | ☐ | ☐ |
| Nome vazio ou com palavra proibida | Recusado | ☐ | ☐ |
| Várias senhas erradas | Bloqueio de alguns minutos | ☐ | ☐ |
| Google ou Apple, primeira vez | Cria a conta; pede nome e telefone | ☐ | ☐ |
| Sair | Aparelho deixa de receber push | ☐ | ☐ |

## ACE-002 · Confirmar e-mail e telefone [CRÍTICO] [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Conta por e-mail sem confirmar toca em "Vou" | Folha pedindo confirmação; "Vou" não vale | ☐ | ☐ |
| Confirma o e-mail | "Vou" liberado | ☐ | ☐ |
| Confirma só o telefone | "Vou" liberado; e-mail segue não confirmado | ☐ | ☐ |
| Conta Google ou Apple | "Vou" liberado | ☐ | ☐ |
| "Não vou" sem confirmar | Funciona | ☐ | ☐ |
| Reenviar antes de 60 s | Recusado, com o tempo restante | ☐ | ☐ |
| 9º pedido em 10 min | Recusado, com o tempo restante | ☐ | ☐ |
| Troca de telefone | Volta a "não confirmado" | ☐ | ☐ |
| Cobrança com telefone não confirmado | Push e central; sem WhatsApp | ☐ | ☐ |

## ACE-003 · Esqueci minha senha

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| E-mail com conta | Código por e-mail; resposta "aceito" | ☐ | ☐ |
| E-mail sem conta | Mesma resposta; nada enviado | ☐ | ☐ |
| Código certo em 10 min | Libera a senha nova por 5 min | ☐ | ☐ |
| Código errado 5 vezes | Descartado | ☐ | ☐ |
| Código depois de 10 min | "Expirou" | ☐ | ☐ |
| Pedir antes de 60 s | Recusado, com o tempo restante | ☐ | ☐ |
| Senha com 7 ou 129 caracteres | Recusada | ☐ | ☐ |
| Senha trocada com sucesso | E-mail de aviso (alvo) | ☐ | ☐ |

## ACE-004 · O perfil da pessoa

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Nome com palavra proibida | Recusado | ☐ | ☐ |
| Apelido com 1 caractere ou espaço na ponta | Recusado | ☐ | ☐ |
| Cidade com 81 caracteres | Recusada | ☐ | ☐ |
| Telefone com 10 dígitos | Recusado | ☐ | ☐ |
| Foto 3000 x 2000 | JPEG de até 512 pixels | ☐ | ☐ |
| Arquivo que não é imagem | Recusado | ☐ | ☐ |
| Remover foto | Avatar padrão | ☐ | ☐ |
| Trocar telefone | Volta a "não confirmado" | ☐ | ☐ |

## ACE-005 · Conta suspensa pela equipe [MULTI]

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Equipe suspende | Painel mostra "Suspenso" | ☐ | ☐ |
| Pessoa tenta entrar | Recusado, com a tela de conta suspensa (alvo) | ☐ | ☐ |
| Sessão aberta faz um pedido | Recusado | ☐ | ☐ |
| Muda o perfil | Recusado | ☐ | ☐ |
| Equipe reativa | Entra; grupos e histórico intactos | ☐ | ☐ |
| Dono suspenso | Grupo segue existindo | ☐ | ☐ |

## ACE-006 · Excluir a conta

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Pedido de exclusão | Conta sem acesso; apagamento em seguida | ☐ | ☐ |
| Tentar entrar depois | Recusado | ☐ | ☐ |

Os demais casos estão em SAI-009.
