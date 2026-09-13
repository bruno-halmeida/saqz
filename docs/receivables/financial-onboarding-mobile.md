# Cadastro financeiro no mobile

Base 24fd5bc4; continuação autorizada de T04/T14/T15/T17. Jornada própria de titular:
Perfil → Recebimentos → cadastro voluntário, documentos, situação e recuperação. Contas delegadas
continuam no seletor de configuração do grupo; cadastro não transfere identidade nem ativa grupos.

## Tarefas atômicas

1. Backend: termos vigentes e recuperação da conta própria. FinancialConditions/Jdbc/Controller,
   FinancialOnboarding/AccountsController; testes integração HTTP/JDBC e segurança/arquitetura.
2. Domain/data mobile: contrato de conta própria, formulário PF/PJ, documentos, gateway autenticado,
   upload com resultado tipado no network compartilhado. Gate gateways/network Android+iOS/detekt.
3. Ports de seleção de documentos Android/iOS, registro explícito no composition root e DI.
   Gate compilação nativa e testes de limites/cancelamento.
4. Presentation: estado/VM/telas/strings, Perfil/rotas/DI, recuperação sem PII persistida e consulta
   após navegador/envio. Gate VM/UI/rotas/DI Android+iOS, capturas inspecionadas.
5. Verificação independente após commits, mutações em cópia temporária e relatório por critério.

## Critérios e decisões

FO1 — GET /api/receivables/terms retorna a versão publicada e já vigente mais recente (effectiveAt,
publishedAt, version decrescentes como desempate determinístico); sem termos 404. Versões históricas
continuam acessíveis. Sem sessão 401. Nenhum termo, tarifa ou aceite é inventado.
FO2 — POST /accounts/me/recover {requestId} somente para titular da sessão, 404 sem conta,
200 com a mesma conta e requestId. Executa somente operação CREATE_ACCOUNT já registrada;
UNKNOWN sem credencial nunca repete criação. Conta existente permanece consultável após corte.
Respostas de cadastro/documentos/recuperação não são cacheáveis nem contêm chave/dados pessoais.
FO3 — Gateway valida conta/ator, versões/documentos/envelopes e requestId de writes. PF/PJ respeitam
campos backend; renda em centavos exatos sem Double. Cadastro exige termos não vazios e aceite.
Resposta inválida/perda de transporte preserva incerteza. Upload nunca tem retry de transporte
cego; sucesso de envio não significa documento aprovado. Dados sensíveis somente em memória.
FO4 — Consulta conta própria antes de oferecer formulário; entrada permanente em Perfil. Cadastro
é voluntário, independente da associação ao grupo; aprovação do cadastro não ativa recebimentos.
INCOMPLETE, UNDER_REVIEW, CORRECTION_REQUIRED, APPROVED e REJECTED têm rótulos/ações explícitos.
Falha na consulta não significa ausência de conta. Cadastro novo depende de disponibilidade backend/mobile.
FO5 — Persistir apenas ator/requestId/tipo/id/status do documento antes de mutações; bloquear novo
cadastro/edição/saída quando resultado incerto. Consulta vazia não autoriza outro cadastro. Em memória,
replay do cadastro mantém comando exato, sempre após consulta; restauração consulta conta sem PII.
Conta encontrada encerra tentativa de criação e permite recuperar provisionamento existente.
Logout/generation descartam respostas/callbacks/efeitos antigos e limpam PII/arquivo/marker.
FO6 — Documento com onboardingUrl usa navegador HTTPS de Asaas validado, sem interpretar retorno
como aprovação. Documento com envio API permite seleção explícita PDF/JPEG/PNG até5MiB,
confirmação antes do envio, bytes só em memória; cancelamento do picker não envia. Depois do envio
consulta situação remota. Resultado incerto preserva marker; PENDING antigo não prova novo resultado.
FO7 — UI shared commonMain com DS/strings/tags/previews; rotas escalares/serializáveis e Koin.
Exibir empty/loading/erro/termos/formulário/revisão/documentos/incerto/estados da conta. Voltar nativo
respeita pendência; retorno atualiza descoberta da conta. Testes isolam cada condição do aceite.

## Limites externos

Homologação real Asaas e termos/condições reais continuam em T20. Credencial perdida no provedor
não será recriada automaticamente; recuperação operacional desse caso requer identificação segura
no provedor e permanece na fila T18. Correção cadastral remota/delegações/carteira são tarefas
seguintes; não apresentar telas não implementadas como ações disponíveis.

## Gate tarefa 1

/ tmp sem espaço: `/tmp/saqz-onboarding-backend.log`, exit0. 10 onboarding +4 conditions +8 security
+20 architecture =42 execuções, sem falhas/erros/skips.

| AC | Asserções literais por teste (paths sob backend/features/receivables/src/integrationTest/kotlin/br/com/saqz/receivables) | Resultado |
|---|---|---|
| FO1 | FinancialConditionsIntegrationTest:103–105 404/no-store/null; :110 versão v2; :116–118 versão v4/conteúdo/hash exatos; :120 histórico v1; :122 count0 | ausente/futuro/não publicado/desempate/histórico/somente leitura |
| FO2 | FinancialOnboardingIntegrationTest:173 NOT_FOUND; :179/181 Success(account,request); :182 operação id igual; :183 1 POST/sem credencial; :185 foreign NOT_FOUND | UNKNOWN recupera sem nova subconta e sem trocar ator |
| FO2 HTTP | mesmo arquivo:205 404; :211–214 status200/no-store/requestId/conta/ator/UNDER_REVIEW/operationsfalse; :216 conjunto exato de campos; :217–218 retry1POST,400/404 | envelope e isolamento |
| FO6 descrição | mesmo arquivo:225 Success(documento CUSTOM com descrição literal) | instrução preservada |
| FO1/2 sessão | BearerSecurityIntegrationTest novo método `financial onboarding discovery and recovery require authentication`, helper assertUnauthorized401 | segurança real |

Todos os testes adicionados mapeiam FO1/FO2/FO6; nenhum existente foi removido, ignorado ou enfraquecido.

## Gate tarefa 2

Gateway autenticado e multipart com envelope tipado: dados/requestId exatos, erros e proteção contra
retry de upload. Extensão `uploadMediaDecoded` reutiliza o transporte limitado existente; o método
antigo de mídia conserva seu decoder Unit. Gate `/tmp/saqz-onboarding-data.log`, exit0: data39 Android
+39 iOS; network allTests e detekt domain/data/network aprovados.

FO3/6 evidências em KtorFinancialOnboardingGatewayTest (commonTest): :21 conta inteira; :22 ator
inválido; :23 ausência404 vs :24 rede; :31 termo completo; :33 vazio inválido; :45–50 corpo exato/retry;
:58–62 recuperação somente request/isolamento; :69 instruções; :72–81 links/duplicatas; :90–93 bytes,
MIME e parâmetros exatos; :102–107 pending/malformed/perda => UNCERTAIN e chamada única;
:115–123 comandos e arquivos inválidos não enviam; :130–133 erros400/401/403/404/409 tipados.
Cada um dos nove testes deriva FO3/FO6, sem casos removidos/ignorados nem asserções enfraquecidas.
