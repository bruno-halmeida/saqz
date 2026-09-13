# Validação histórica da base — 2026-09-12

Este registro descreve o gate de 2026-09-12. Para o estado atual e as entregas posteriores,
consultar `.specs/STATE.md` e `docs/receivables/evidence/`.

Resultado: base backend verificada; plano completo e piloto ainda não aprovados.
Verificação individual conforme autorização do usuário.

## Evidência executável

JDK 21: `backend/gradlew -p backend :features:receivables:check :architecture-tests:test :bootstrap:test`.
Resultado final: exit 0, 12 testes unitários de recebimentos, 24 de integração PostgreSQL/HTTP,
20 de arquitetura e 384 do bootstrap, sem falhas, erros ou testes ignorados.
Entre eles estão 51 testes novos específicos da entrega. O bootstrap valida também as
migrações combinadas e a composição com o trial incorporado de origin/main 6f86f8f6.
A branch core isolada passou 22 testes financeiros, 20 de arquitetura e a migração agregada.

## Discriminação dos testes

Em cópia temporária, sete alterações incorretas causaram falhas esperadas: corte comercial
ignorado, delegado revogado aceito, conta retirada do contexto criptográfico, reexecução de
operação incerta, consentimento ignorado, base errada da comissão e revogação de grupo desligada.
Sete detectadas, nenhuma sobrevivente nessa seleção. Isso não equivale a cobertura integral.

## Limites

Cobertos T01–T03 e partes de T04–T07. Pendentes correção cadastral, recuperação operacional,
ativação, emissão/conciliação, baixa manual, saques/reembolsos, recorrência/corte, mobile,
adm-web, notificações e homologação real. As permissões do domínio não substituem a aplicação
nas rotas financeiras futuras. Não houve teste iOS/Android, navegador do painel ou subconta real.

## Publicação

A criação de PR foi recusada pelo GitHub com `must be a collaborator (createPullRequest)`.
Branches separadas preservam entregas revisáveis; nenhuma alteração foi mesclada na main remota.

O gate amplo foi repetido após a simulação HTTP: 381 bootstrap, exit 0. A branch
feat/receivables-quotes contém a entrega dependente de catálogo e simulação.

Após publicação administrativa e correção de 404, gate amplo: 384 bootstrap e
20 arquitetura passaram. Receivables tem 12 unitários e 24 PostgreSQL/HTTP sem falhas.
V51 registra auditoria das condições. A interface visual administrativa ainda não foi implementada.
