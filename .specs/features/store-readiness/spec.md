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
- SR4: Exclusão autenticada exige autenticação recente, cancela assinatura ativa,
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

## Limites externos

Configuração Apple/Firebase nos consoles, credenciais reais, certificado público
Play, dados das fichas das lojas e homologação em binários assinados dependem de
acesso/insumos externos. Não são substituídos por testes com fakes. Fotos e textos
de grupos continuam sujeitos à análise de UGC das lojas, mesmo sem chat.
