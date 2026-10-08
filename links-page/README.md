# Links de produção

Identidade: `app.saqz` nas duas plataformas; Apple Team ID `8JG4JP8VMT`;
domínio `https://links.saqz.app`. iOS Debug e Release usam a associação pública.
Os certificados Android do Play foram configurados em 28/09/2026: chave atual
clássica (`hybrid_classical_cert.der`), pós-quântica (`hybrid_pqc_cert.der`) e
certificado anterior (`deployment_cert.der`). Os dois fingerprints atuais foram
confirmados pelo titular no Play Console. O certificado de debug foi removido.
Ainda é necessário publicar o arquivo e homologar a associação no app instalado
pelo Play.

## Obter e aplicar o certificado do Play

1. Abra o [Google Play Console](https://play.google.com/console/) e selecione Saqz.
2. No menu atual, abra **Protegido com o Google Play → Distribuição na Google
   Play Store → Acessar a Assinatura de Apps do Google Play**. A navegação antiga
   pode mostrar **Testar e lançar → Configuração → Integridade do app →
   Assinatura do app**.
3. Na seção **Chave de assinatura do app** (ou **Certificado da chave de assinatura
   do app**), copie a impressão digital **SHA-256**. Se o console mostrar várias
   chaves que assinam as versões distribuídas, copie o SHA-256 de cada uma.
   Não copie o certificado da chave de upload.
4. Se a assinatura ainda não foi configurada, conclua Play App Signing ao preparar
   o primeiro app bundle na faixa interna. Não substitua por uma chave inventada
   nem pelo `debug.keystore`. O fingerprint é público; nenhuma chave privada é necessária.
5. Na raiz do repositório, execute:

```sh
node links-page/scripts/configure-app-links.cjs --sha256 'COLE_AQUI_O_SHA256_DO_PLAY'
node links-page/scripts/configure-app-links.cjs --check
node --test links-page/tests/*.test.cjs mobile/ios-app/tests/signing-config.test.cjs
```

O script aceita 64 caracteres hexadecimais ou 32 pares separados por `:`.
Para rotação de chave no Play, repita `--sha256` para os certificados que assinam
versões distribuídas. A validação verifica formato, identidade e rejeita o debug
conhecido; a procedência do certificado precisa ser conferida no console.
O script altera somente o arquivo local e não faz deploy.

Cadastre também os fingerprints **SHA-1 e SHA-256 da assinatura do Play** no app
Android `app.saqz` do projeto Firebase de produção. Habilite Google Login e baixe
novamente `google-services.json` para `mobile/android-app/src/prod/`.

Fontes: [assinatura de apps](https://support.google.com/googleplay/android-developer/answer/9842756?hl=pt-BR),
[certificado para App Links](https://developer.android.com/training/app-links/faq),
[Google Sign-in e certificados](https://developers.google.com/android/guides/client-auth).
Navegação atual conferida na ajuda oficial em 27/09/2026.

## Publicação e homologação

O host é servido pelo serviço `links-page` em `compose.server.yaml`, com
`nginx-links.conf`. A landing principal usa outro serviço/workflow. Antes do deploy,
o comando `--check` acima deve terminar com código zero. Copie os arquivos com a
pasta oculta `.well-known`. `scripts` e `tests` são ferramentas locais de validação.

Confirme resposta HTTPS 200, `Content-Type: application/json` e ausência de
redirecionamento em `/.well-known/assetlinks.json` e
`/.well-known/apple-app-site-association`. A associação iOS já contém o ID de
produção; confirme que o prefixo do perfil de distribuição coincide com o Team ID.

Com o app instalado **pela faixa interna do Play**, execute:

```sh
adb shell pm verify-app-links --re-verify app.saqz
adb shell pm get-app-links app.saqz
```

Espere a verificação e confirme `links.saqz.app: verified`. Teste convite,
onboarding e presença a partir de um link real recebido fora do navegador desse
domínio. Repita no iOS pelo TestFlight, com Associated Domains e Sign in with Apple
habilitados no App ID e no perfil. O fallback Android oferece um intent explícito
para `app.saqz`; não substitui a verificação do domínio.

## Sem o app instalado

No celular a página redireciona sozinha, sem clique; os botões ficam de reserva.

- **Android:** tenta abrir o app pelo `intent://` com o mesmo link. Sem o app, o Chrome
  segue para o Google Play; se o navegador recusar o intent sem toque, a página vai
  para a loja depois de 1,5 s.
- **iPhone:** vai direto para a App Store, também no link de presença. Nenhum link tenta
  o esquema `saqz://` sozinho: com o app, o link universal já abriu direto; quem chega à
  página costuma não ter o app, e o Safari responderia com o alerta de endereço inválido.
  O esquema fica no botão "Já instalei, abrir o app".
- **Computador:** fica na página, com o site e o pedido para abrir no celular.

- **Google Play**, no ar desde 07/10/2026. Convite, onboarding e presença seguem no
  `referrer` do Play (`saqz_invite=…`, `saqz_onboarding=…`, `saqz_attendance=…`, com
  `&saqz_intent=decline` no "não vou") e o app os retoma na primeira abertura: convite
  desde a 0.0.4 (6), presença desde a 0.0.5 (7). Quem não é do grupo e chega pela presença
  entra no grupo e já responde. A página ainda pede para tocar no link de novo, para
  quem instalar por fora do Play.
- **App Store**: quando a Apple aprovar, preencha `appStore` em `index.html` com
  `https://apps.apple.com/br/app/id6812743525`. Até lá o iPhone mostra o site e
  avisa que o app chega em breve.
