# Conversão de cupons

Abra **Cupons → Conversão de cupons** no administrativo. O painel acompanha **todos os cupons de trial e desconto**, inclusive sem usos, desativados, expirados e esgotados. Use os filtros de tipo e busca por código/campanha; **Atualizar indicadores** consulta novamente o histórico. A data exibida indica o snapshot atual. Os filtros afetam a tabela; os cartões mantêm o resumo global.

| Indicador | Definição |
|---|---|
| Usaram | Organizadores únicos com trial iniciado ou resgate de desconto registrado. Seleção de trial sem primeiro grupo não conta. |
| Pagantes confirmados | Organizadores com recibo de valor positivo confirmado após o uso registrado. Status ACTIVE sozinho não comprova receita. |
| Conversão | Pagantes ÷ organizadores que usaram. Sem usos, mostra —. |
| Receita confirmada bruta | Pagamentos e renovações confirmados depois do uso, em BRL, antes de taxas e estornos. Não é saldo disponível nem receita líquida. |
| Em andamento / encerrados | Trials cujo prazo ainda está aberto ou terminou no instante da consulta, independentemente de já terem convertido. |
| Conversão dos encerrados | Pagantes entre os organizadores cujo trial já encerrou ÷ trials encerrados. Não mistura campanhas recentes com prazos completos. |
| Confirmação incompleta | Há confirmação registrada sem recibo/valor utilizável. Não estimamos receita pelo preço do plano; o indicador sinaliza que os totais observados podem estar subestimados. |

## Atribuição e histórico

Cada cupom acompanha os pagamentos posteriores dos organizadores que o usaram. O vínculo não exige o código novamente ao assinar e não depende do cupom atual da assinatura. Uma pessoa que participou de duas campanhas pode aparecer em ambas. Isso mede participação, não causa exclusiva da venda. **O resumo deduplica pessoas e pagamentos e não soma as linhas.** Tipos diferentes com código igual continuam separados por tipo e ID.

A primeira confirmação **registrada no sistema** define o instante do pagamento. PAYMENT_CONFIRMED e PAYMENT_RECEIVED da mesma cobrança são consolidados antes de atribuir, para que uma entrega posterior não transforme pagamento anterior ao uso em conversão. Eventos não processados, futuros, sem dono explícito, sem ID de cobrança ou sem valor válido não geram receita fictícia. Recibos históricos ausentes não podem ser reconstruídos a partir do status da assinatura. Confirmação via recuperação sem recibo é sinalizada quando observável em first_confirmed_at; histórico sobrescrito sem evento não pode ser reconstruído. Zero não conta como pagante.

O histórico parte de coupon_redemptions, organizer_trials e subscription_events, com first_confirmed_at como sinal de confirmação incompleta. Eventos sem dono explícito não são atribuídos por inferência. Desativar o cupom ou alterar seu código não remove a associação por ID. Nenhuma cobrança, assinatura ou concessão é alterada pelo painel, e não há chamada ao provedor para abrir a página.

## API e verificação

`GET /admin/coupon-analytics` exige sessão administrativa. Resposta: `asOf`, `summary`, `coupons[]`; cada cupom inclui metadados, `metrics`, `ongoingTrials`, `endedTrials`, `endedConversionPercent`. Taxas sem denominador e métricas de trial em cupons de desconto são null. HTTP: 200 admin, 403 conta comum, 401 sem sessão. Dados são lidos em uma transação read-only repeatable-read, sem informações pessoais na resposta.

Testes, cobertura e revisão: `.specs/features/coupon-analytics/`. Capturas em `docs/coupons/evidence/` usam fixtures locais (não representam métricas reais de produção). Backend e painel precisam ser publicados para disponibilizar a funcionalidade; nenhuma migration nova é necessária.
