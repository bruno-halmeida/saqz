# Roteiro manual · Geral

Gerado das seções **QA** das regras do cofre (`Obsidian Vault/Saqz/Regras`). A regra manda: se o roteiro e o cofre divergirem, vale o cofre.

Etiquetas: **[CRÍTICO]** entra na rodada segura; **[MULTI]** depende de duas ou mais pessoas e tem roteiro em `tests/multi-aparelhos/roteiros/`; **[push: só produção]** o ambiente local não tem push.

Rode **em iOS e em Android**: nas colunas *iOS* e *Android* troque `☐` por `✔` (passou) ou `✘` (falhou, com anotação). Onde a regra depende de pessoas diferentes, vale o roteiro multi-aparelho, com os papéis invertidos entre as plataformas (organizador no iPhone e depois no Android).

## GER-001 · Toda ação com efeito ignora toque duplo

Para cada ação: tocar duas e três vezes rápido e conferir que acontece um só efeito (uma linha, um aviso, uma cobrança). Repetir
com rede lenta. Casos já conhecidos que a regra reabre: P1-15 ("Cobrar" manda dois avisos), WA-4c ("Avisar" sem trava) e BE-4b
(`VERSION_CONFLICT` ao cobrar cobrança paga).

## GER-002 · Puxar para atualizar

_Decidida em 22/10/2026; vale depois da entrega do DEB-130 (app 1.0.12 onde houver tela)._

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Puxar na Início, Grupos, detalhe do grupo, jogo, Caixa, Jogos anteriores e Perfil | Bola gira, dados novos, bola some | ☐ | ☐ |
| Puxar sem internet | Dados antigos ficam; aviso de erro | ☐ | ☐ |
| Puxar duas vezes rápido | Uma busca só | ☐ | ☐ |
| Leitor de tela | Ação de atualizar anunciada | ☐ | ☐ |

## GER-003 · Lista de grupos: foto, próximo jogo, vagas e Confirmar [CRÍTICO]

_Decidida em 28/10/2026; vale depois da entrega do DEB-145. Desenho: canvas da lista de grupos, linha H e primeiro acesso._

Massa: um atleta em três grupos (G1 com foto e jogo publicado sem resposta, 14 de 18; G2 sem foto, já respondeu "Vou"; G3 sem
jogo) e um dono com plano, admin de G1. Para os casos de espera, um jogo lotado; para posições, um jogo 5-1.

| Situação | Resultado | iOS | Android |
| --- | --- | --- | --- |
| Abrir a aba Grupos (atleta) | G1 primeiro (pede Confirmar), depois G2, G3 no fim; cabeçalho "3 grupos · 1 esperando sua resposta" | ☐ | ☐ |
| G1, com foto | Foto sangrada à esquerda; Confirmar azul na base; selo "QUI · 29 · 19h30"; pino "CERET — Quadra 2"; vagas "14 de 18 vão" e "4 vagas abertas" | ☐ | ☐ |
| G2, sem foto | Fundo claro com as iniciais em azul e a bola; etiqueta "Você vai" (ponto verde) | ☐ | ☐ |
| G3, sem jogo | "Nenhum jogo marcado por enquanto."; pino com o bairro do grupo; sem selo e sem vagas | ☐ | ☐ |
| Dono abre a aba Grupos | Etiqueta "Admin" no canto de cima da foto de G1; o atleta não vê | ☐ | ☐ |
| G1 → Confirmar | Folha com foto, nome, local, selo, vagas, "Você vai nesse jogo?", Vou e Não vou | ☐ | ☐ |
| Tocar fora da folha | Fecha sem responder; Confirmar continua | ☐ | ☐ |
| Confirmar → Vou (com vaga) | Folha fecha; etiqueta "Você vai"; cabeçalho "3 grupos" | ☐ | ☐ |
| Confirmar → Vou em jogo lotado | Etiqueta "Na espera" (ponto laranja) | ☐ | ☐ |
| Confirmar → Não vou | Etiqueta "Não vai" (ponto cinza) | ☐ | ☐ |
| Confirmar → Vou em jogo 5-1 | Abre a escolha da posição, como na Início | ☐ | ☐ |
| Confirmar → Vou sem internet | "Sem conexão. Sua resposta não foi enviada."; Confirmar continua | ☐ | ☐ |
| Confirmar em grupo congelado | Mesmo aviso da Início; nada muda | ☐ | ☐ |
| Tocar Vou duas vezes rápido | Uma resposta só (conferir no jogo) | ☐ | ☐ |
| Depois do prazo, antes do início, sem resposta | Confirmar continua; Vou segue a regra de depois do prazo | ☐ | ☐ |
| Jogo em andamento | Selo com "AGORA"; sem Confirmar | ☐ | ☐ |
| Jogo lotado | "Completo" no lugar das vagas abertas | ☐ | ☐ |
| Acima da lotação (entrada forçada) | Só "N confirmados para M vagas" | ☐ | ☐ |
| Rascunho (dono) | Selo e vagas; etiqueta "Rascunho"; sem Confirmar; o atleta não vê | ☐ | ☐ |
| Responder no jogo e voltar à aba | Etiqueta atualizada sem reabrir o app | ☐ | ☐ |
| Celular de 360 e fonte grande | Nada cortado; nome do local quebra só no travessão; botões em uma linha | ☐ | ☐ |
| Primeiro acesso, atleta | "Entre no seu grupo" e "Colar o convite"; "Criar um grupo" tracejado | ☐ | ☐ |
| Primeiro acesso, dono com plano | "Crie o seu primeiro grupo" e "Criar grupo"; "Tenho um convite" tracejado | ☐ | ☐ |
| Leitor de tela | Cada grupo: nome, dia e hora, local, vagas e a sua resposta; Confirmar anunciado como botão | ☐ | ☐ |
