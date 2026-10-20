# Assinatura iOS no CI

O job `iOS · gerar o IPA` (mobile-rc.yml, lane `ios archive` do `mobile/fastlane/Fastfile`) assina com **certificado e perfis fixos**,
sem `-allowProvisioningUpdates`. A assinatura automática na nuvem criava um certificado de desenvolvimento novo a cada execução
do runner e estourou o limite da conta (RC 1.0.10, 20/10/2026).

## Segredos (Environment `mobile`)
| Nome | O que é |
|------|---------|
| `IOS_DIST_CERT_P12` | certificado Apple Distribution + chave privada (.p12, base64) |
| `IOS_DIST_CERT_PASSWORD` | senha do .p12 |
| `IOS_PROFILE_APP` | perfil App Store de `app.saqz` (base64) |
| `IOS_PROFILE_LIVEACTIVITY` | perfil App Store de `app.saqz.LiveActivity` (base64) |

A lane cria um chaveiro temporário, importa o .p12, instala os perfis, troca os dois targets para assinatura manual (só no
checkout do runner), arquiva, exporta e apaga o chaveiro. O upload ao TestFlight continua usando a chave ASC.

## Renovar (certificado e perfis valem 1 ano; vencem em 20/10/2027)
1. Gerar chave e CSR com openssl; criar `IOS_DISTRIBUTION` em `POST /v1/certificates`; montar o .p12 com
   `-certpbe PBE-SHA1-3DES -keypbe PBE-SHA1-3DES -macalg sha1` (o `security import` do macOS recusa o padrão do OpenSSL 3+).
2. Criar os perfis `IOS_APP_STORE` em `POST /v1/profiles` (um por bundle id) ligados ao certificado novo.
3. Regravar os quatro segredos com `gh secret set --env mobile`.
Novo target com extensão: criar o bundle id, o perfil, o segredo e incluir o target no `update_code_signing_settings` da lane.
O backup do .p12 e da senha do certificado atual fica em `~/.config/saqz/ios-dist/` (fora do git).
