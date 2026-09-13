# Gestão financeira final — especificação

## Pressupostos fechados

- O ator autenticado é resolvido no servidor; IDs enviados pelo cliente nunca substituem a sessão.
- Manutenção ignora gate comercial, mas nunca ignora titularidade/delegação/administração atuais.
- Campos legais são imutáveis neste fluxo; somente os dez campos do contrato são corrigíveis.
- Request incerto é recuperado por leitura, sem repetição automática; nenhum reembolso existe.

## Requisitos e ACs

| ID | WHEN | THEN SHALL |
|---|---|---|
| MGT-01 | titular/delegado consulta contas | retornar apenas contas com autorização atual |
| MGT-02 | titular concede/revoga | exigir administrador atual e requestId idempotente |
| MGT-03 | delegado tenta conceder/revogar | retornar NOT_FOUND sem efeito |
| MGT-04 | administrador é removido | revogar acesso na requisição seguinte, inclusive mesma sessão |
| MGT-05 | ator autorizado corrige cadastro | aceitar só contato/endereço/renda e preservar identidade legal remota |
| MGT-06 | POST remoto fica incerto | retornar RESULT_PENDING e recuperar por GET sem segundo POST |
| MGT-07 | requestId é reutilizado com outro conteúdo | retornar CONFLICT |
| MGT-08 | sessão/generation muda no mobile | descartar callback/efeito e limpar dados em memória |
| MGT-09 | estado incerto é restaurado | persistir ator/accountId/requestId/kind e IDs do alvo/versão dos termos quando necessários; oferecer recuperação explícita |
| MGT-10 | qualquer tela/rota é exibida | não oferecer reembolso nem transferência de titularidade |

## Dimensões implícitas

- Validação: limites/formato por campo e centavos inteiros; payload desconhecido rejeitado pelo mapper.
- Falha parcial/idempotência/concorrrência: operação e payload cifrado antes de IO; digest conflitante;
  autorização fresca; recovery read-before-write; callback tardio descartado.
- Ciclo de dados: PII apenas em memória/payload cifrado; estado terminal retido para auditoria.
- Observabilidade: nenhum PII/chave em URL, `toString`, erro ou log; requestId é a correlação.
- Rate limit: GET só por entrada/recovery explícito, sem polling.
- N/A: reembolso, cobrança, saque e transferência de titularidade estão fora do escopo.

## Rastreabilidade

MGT-01..10 estão em implementação; evidência final será registrada em
`docs/receivables/evidence/final-management-author.md`.

## Complementos da integração

- MGT-11: o titular escolhe administradores atuais por nome e grupos; não digita UUID nem vê IDs de sessão/pedido.
  O endpoint `/delegations/candidates` exige titular atual e consulta os grupos não excluídos.
- MGT-12: antes da atualização remota, armazenar snapshot cifrado dos seis campos de identidade legal.
  Resposta imediata e recuperação só confirmam se identidade preservada e correção solicitada coincidirem.
- MGT-13: revogação ou conta removida limpa cadastro, candidatos e permissões na UI; pedido incerto continua
  vinculado à conta original e nunca muda para outra conta por fallback.
