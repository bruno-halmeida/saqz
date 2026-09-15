# Verificação em andamento

## T1
Gate: integração ChargeReminderIntegrationTest e GroupCommunicationIntegrationTest + bootstrap compileKotlin: PASS.
Cobertura: ChargeReminderIntegrationTest valida destinatários/privacidade/canal/valores/fila, replay/conflito, autorização/seleção inválida e membro inativo; assertions nos testes correspondem a AC1–AC3. Testes existentes de comunicação continuam passando.
AC4–AC6 ainda pendentes.

## T2
Gate integração + bootstrap: PASS. ChargeReminderIntegrationTest: seleção de tokens, retry sem reenviar sucesso, invalidação, transferência de instalação, remoção autorizada e preferência de push, mantendo inbox (AC4).
