# Revisão coordenada de pagamentos — pontos para verificar no gate

Observações sobre código em construção; não são aceites nem defeitos finais. O implementador
recebeu C1–C5 durante o trabalho; C6–C8 serão conferidos com o diff final e a revisão independente.

- C1: emissão nova exige titular atual do grupo correspondente à conta; ordens antigas mantêm
  conta/pagador mesmo após transferência/exclusão. Não impor unicidade histórica de grupo que
  inviabilize vínculo voluntário futuro com outra conta após encerrar o vínculo anterior.
- C2: competência mensal vem da cobrança original, não é deduzida do vencimento. Reserva manual,
  ordem e snapshot commitam na mesma transação e respeitam grupo → cobrança → conta → instrumento.
- C3: resultado em análise não é instrumento ACTIVE cancelável; vencimento Pix é exposto. Valores
  CONFIRMED/SETTLED/AVAILABLE/split são fatos separados. Não marcar recebido por callback.
- C4: evento somente vira processado após consulta/aplicação bem-sucedida, não apenas porque já
  existe paymentId. Backoff/seleção justa impedem que eventos inválidos ou recebíveis antigos
  ocupem indefinidamente os primeiros 50 lugares da recuperação.
- C5: cancelamento solicitado por delegado deve poder ser recuperado pelo scheduler sem conflito
  entre approved_by e o ator original da operação persistida.
- C6: cancelamento incerto que a consulta encontra ainda pendente precisa de caminho seguro para
  repetir o DELETE da mesma cobrança após consulta autenticada, sem criar outro pagamento. Uma
  recuperação que apenas encontra ACTIVE para sempre não encerra o instrumento nem libera baixa.
- C7: movimentos usam fatos conhecidos. Divergência de tarifa/split gera ocorrência; não lançar
  comissão efetiva ou custo residual como se fossem fatos comprovados quando só há projeção.
  Reembolso integral/reversão não pode duplicar débito de taxas nem manter comissão devolvida
  como custo do gestor. Se faltarem fatos remotos, registrar pendência de conciliação explícita.
- C8: cliente/customer, webhook e credenciais têm jornada operacional configurável e recuperável;
  não exigir seed SQL secreto para o backend ser utilizável. Quatro classes HTTP/provedor podem
  passar em mocks sem provar configuração real; documentar exatamente os passos restantes.

Mobile: seleção vazia bloqueia preview/ativação; revisão/aceite são invalidados quando meios mudam;
GET estado independe de condições. Tentativa de escrita incerta trava rascunho e conserva requestId,
incluindo resposta 200 malformada. Replay conhecido não exige liberação de operação nova. Parsing de
percentuais BigDecimal reais do backend mantém representação decimal exata, sem Double.

Adm: revisão independente aprovada, incluindo probe HTTP real de strings decimais e JDBC. Rótulos
finais ajustados pelo coordenador e 45 testes + navegador mockado reexecutados.

Auditoria independente inicial (fontes em construção):

- C9: cancelamento de jogo também encerra ordem sem instrumento e libera a reserva de forma atômica.
- C10: disputa de chargeback e disputa vencida não podem virar reversão terminal que ignore o recebimento recuperado.
- C11: JSON inválido em webhook autenticado retorna 400; falha de persistência mantém 5xx.
- Mobile: recuperação persistida deve usar identidade estável do pagador/operador, sem perder a guarda de geração para respostas em voo. Reenvio após recriação recarrega conta e permissões.

O guard de bootstrap `payments-enabled` prepara o deployment inicial; não é a reversão operacional. Depois de emitir ordens, usar os controles administrativos de novos negócios, preservando os beans de manutenção e o scheduler.
