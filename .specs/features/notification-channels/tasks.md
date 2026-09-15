# Execução

- [x] T1 Adaptador Uazapi e testes HTTP locais (AC1). Gate: `:features:groups:test --tests '*UazapiNotificationSenderTest'`.
- [x] T2 Preferências, filas transacionais, workers e configuração (AC2–4). Gate: testes JDBC de comunicação e bootstrap HTTP/configuração.
- [x] T3 Configurações mobile por canal e categorias (AC5). Gate: testes gateway/ViewModel/iOS, compilação Android, detekt e capturas.
- [ ] T4 Confirmação de presença pelo link no app (AC6). Gate: testes de sessão, link, API e confirmação.
- [ ] T5 Documentação operacional e validação independente de AC1–6. Gate: regressões afetadas, arquitetura, build e sensor de mutação.

## Cobertura
SDK: payload/autenticação/erros/atraso. JDBC: isolamento, opt-in, seleção, dedup, cancelamento, retentativa. HTTP: serialização e compatibilidade. Mobile: roundtrip preferências, estados e fluxo autenticado. Links: replay/prazo/identidade/capacidade.
