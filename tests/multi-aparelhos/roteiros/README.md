# Roteiros executáveis por agente

Cada arquivo desta pasta é um roteiro que um agente executor (Sonnet; Haiku para o puramente
mecânico) roda pelo mobile-mcp contra o stack local, sem julgar nada: ele registra o que viu e devolve
uma tabela. Quem analisa o relato e retesta o que discordar é quem coordena.

## Regras para quem executa

1. Só os aparelhos do roteiro. Ids no mobile-mcp: `Saqz_QA_A` e `Saqz_QA_B` (Android),
   `BAB08A28-4E8C-4AA3-BF6A-54904566BF0F` (iPhone 17) e `F22B2FF2-494E-4CF3-BD18-FE48ECE5A85B`
   (iPhone 17e). Ler a tela com `mobile_list_elements_on_screen`; toque por `ref` da última listagem.
2. Cada passo tem **Faça** e **Espere**. Registre PASSOU se o texto esperado apareceu, FALHOU com o que
   apareceu no lugar (cole a linha da listagem), BLOQUEADO se não conseguiu chegar ao passo. Nunca
   "parece ok".
3. Conferência de banco quando o passo pede: `docker exec saqz-multidev-db psql -U saqz -d saqz -Atc "<sql>"`.
4. Dicas de operação (ver ../README.md): no Android, se a listagem falhar com "no XML content", use
   `adb -s emulator-5554 exec-out screencap -p > /tmp/a.png` e descreva; BACK fecha folhas inteiras.
   No iOS, `xcrun simctl launch <udid> app.saqz` abre o app; preencha um campo por vez e reliste; use
   "next"/"done" do teclado; para alcançar um botão coberto, role o conteúdo.
5. Sem improviso: se um rótulo não existe, é FALHOU (ou BLOQUEADO) com a listagem, não "tentei outro".
6. Relato final: a tabela do roteiro preenchida, mais a lista de prints salvos em
   `~/.saqz-multidev/evidencias/<data>-<id>.png` (salvar com `mobile_save_screenshot` no scratchpad e
   copiar).

## Antes de começar

```sh
tests/multi-aparelhos/stack.sh status        # backend em 18080, Auth Emulator, Mailpit, Postgres
tests/multi-aparelhos/aparelhos.sh listar    # 2 emuladores + 2 simuladores online
```

Contas descartáveis desta rodada e senha ficam no roteiro; e-mails caem no Mailpit
(`http://127.0.0.1:18025`) e o link de confirmação em
`http://127.0.0.1:9099/emulator/v1/projects/saqz-local/oobCodes`.

## Roteiros

| Arquivo | O que cobre | Aparelhos |
| --- | --- | --- |
| [01-dono-e-atletas.md](01-dono-e-atletas.md) | Conta, grupo, convites, jogo, lista de espera, prazo (20 passos) | 4 |
| [02-recorrencia.md](02-recorrencia.md) | Horário fixo pelo editor e por "Jogos e horários": próximo jogo publicado e vinculado à série (8 passos) | 2 Android |

