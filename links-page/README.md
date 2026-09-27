# Links de produção

Identidade: `app.saqz` nas duas plataformas; Apple Team ID `8JG4JP8VMT`;
domínio `https://links.saqz.app`. iOS Debug e Release usam a associação pública.
O certificado Android atual é de desenvolvimento: **não está pronto para Play**.

## Obter e aplicar o certificado do Play

1. Abra o [Google Play Console](https://play.google.com/console/) e selecione Saqz.
2. Abra **Testar e lançar → Configuração → Assinatura do app**. Dependendo da
   navegação da conta, essa opção também aparece em **Integridade do app →
   Assinatura do app**.
3. Na seção **Certificado da chave de assinatura do app**, copie a impressão
   digital **SHA-256**. Não copie o certificado da chave de upload.
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

Quando as fichas das lojas existirem, substitua o botão do site em `index.html`
pelos destinos reais de cada loja. Não há App Store ID informado nesta entrega.
