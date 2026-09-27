# Preparação das lojas — Brasil

Decisão do usuário em 2026-09-27: implementar os itens da auditoria; assinatura da
plataforma contratada na web; assinatura de grupo e Recebimentos desligados; retirar
chat do app. Não publicar nem ativar operações financeiras nesta entrega.

## Critérios de aceitação

- SR1: Android com Firebase real contém o identificador de build exigido pelo
  Crashlytics. Desenvolvimento sem credenciais continua utilizável com emulador;
  produção sem credenciais é recusada no build.
- SR2: Release iOS sem configuração Firebase válida falha no build; somente Debug
  pode usar emulador. Identidade do aplicativo e API de produção são verificadas.
- SR3: Login Apple no iOS usa nonce, Firebase e o mesmo bootstrap de sessão; trata
  cancelamento/falha sem autenticar, e mantém login Google e senha.
- SR4: Exclusão autenticada exige `auth_time` de no máximo 300 segundos, sem valor
  futuro, e verifica o ID da conta exibida no app; cancela assinatura ativa,
  remove identidade Firebase, dados pessoais e vínculos, e permite retomada segura
  quando um serviço externo falha. Dados financeiros sujeitos a retenção são
  preservados com acesso restrito, sem perfil público identificável.
- SR5: Perfil oferece exclusão com confirmação, reautenticação, erro recuperável e
  encerramento de sessão após sucesso. Página pública explica como solicitar exclusão.
- SR6: O app não oferece contratação, upgrade, checkout ou envio de link de compra;
  reconhece assinatura obtida na web. Chat não tem entrada nem rota funcional no
  app. Recebimentos e assinatura de grupos não abrem novas jornadas neste lançamento.
- SR7: Política e manifesto descrevem os dados efetivamente coletados, finalidade,
  exclusão e retenção; permissões publicitárias desnecessárias são removidas.
- SR8: Associação iOS usa identidade de produção. Android aceita certificado Play
  de produção validado, sem inventar fingerprint; ausência do certificado é
  pendência explícita de publicação. Links e orientações têm testes de configuração.

## Precisão operacional de SR4

O aceite da exclusão confirma a transação local e encerra o acesso; a remoção nos
provedores pode terminar depois. Sem billing configurado, uma assinatura ainda
não cancelada mantém o pedido pendente, sem excluir a identidade antes da cobrança.
Bootstrap e exclusão da mesma identidade devem ser serializados: nenhum login em
andamento pode recriar o perfil após a confirmação da exclusão.

| Classe de dado | Tratamento e momento | Acesso após exclusão |
|---|---|---|
| Perfil, foto, push, vínculos, mensagens e notificações pessoais | Remoção/redação na transação local | Perfil indisponível; nenhum novo bootstrap do mesmo UID |
| Grupos próprios, locais e snapshots de jogos/séries | Grupo encerrado; nome, descrição, Pix, endereço e notas pessoais redigidos na mesma transação | Grupo encerrado indisponível no app |
| Presença e cobranças em grupos de terceiros | Nome redigido; motivo pessoal de presença redigido sem mudar os fatos históricos | Autorizações existentes do grupo; sem acesso da conta excluída ou de terceiros sem vínculo |
| Identidade Firebase e credenciais de pagamento locais | Cancelamento, remoção/revogação Firebase e purge de token/últimos dígitos/bandeira; retry enquanto houver falha | Sem perfil ativo; segredo de pagamento nunca exposto ao app |
| Pedido de exclusão | UID necessário só até conclusão externa; depois, digest do UID, UUID interno, datas e tentativas | Operação interna; impede recriação da identidade excluída |
| Registros financeiros/fiscais e referências de conciliação | Valores, estados, eventos e IDs internos preservados; sem nome do perfil público | Acesso operacional restrito e acesso autorizado ao histórico do grupo |

Esta entrega não implementa expurgo por idade dos registros financeiros nem dos
registros mínimos de exclusão. A matriz de prazos legais/operacionais, backups e
configurações de retenção dos operadores precisa ser definida antes de produção;
não se infere um prazo legal dos testes nem se promete apagamento desses registros.

## Limites externos

Configuração Apple/Firebase nos consoles, credenciais reais, certificado público
Play, dados das fichas das lojas e homologação em binários assinados dependem de
acesso/insumos externos. Não são substituídos por testes com fakes. Fotos e textos
de grupos continuam sujeitos à análise de UGC das lojas, mesmo sem chat.
