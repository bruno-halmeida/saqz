# Testes multi-aparelho: dono e atletas ao mesmo tempo

Quatro aparelhos na mesma máquina, todos contra um backend local descartável, para rodar as jornadas
de grupo de ponta a ponta: o organizador cria o grupo e marca o jogo num aparelho, e os atletas
entram pelo convite, respondem presença e conferem o resultado nos outros três. Serve para validar o
núcleo do app sem tocar em produção, sem e-mail de verdade e sem contas das lojas.

| Papel | Aparelho | Id no mobile-mcp |
| --- | --- | --- |
| ORG | iPhone 17 (simulador iOS 27) | udid do simulador (`xcrun simctl list`) |
| ATL | emulator-5554 (Saqz_QA_A, Android 36 com Play Store) | `Saqz_QA_A` |
| ATL2 | iPhone 17e (simulador iOS 27) | udid do simulador |
| ATL3 | emulator-5556 (Saqz_QA_B) | `Saqz_QA_B` |

Inverter os papéis cobre as duas plataformas como organizador, como pede o roteiro de produção.

## Subir tudo

```sh
tests/multi-aparelhos/stack.sh up        # Postgres, Mailpit, Firebase Auth Emulator e backend (127.0.0.1:18080)
tests/multi-aparelhos/aparelhos.sh criar # só na primeira vez: as duas AVDs
tests/multi-aparelhos/aparelhos.sh subir # 2 emuladores + 2 simuladores
tests/multi-aparelhos/builds.sh          # APK devDebug e SaqzIOS.app apontando para o stack local
tests/multi-aparelhos/aparelhos.sh instalar
```

Depois disso `mobile_list_available_devices` lista os quatro, e cada chamada do mobile-mcp recebe o
id do aparelho. O que não existe nesta máquina: `sdkmanager` e a imagem Android 36 (`criar` instala),
`firebase-tools` 15.25.1 (`stack.sh` instala em `~/.saqz-multidev/tools`) e o bootJar do backend
(`stack.sh` constrói com o JDK 21 de `~/.gradle/jdks`).

Pré-requisitos: Docker de pé, Xcode com runtime iOS 27, Android Studio (o JBR é o Java do Gradle
mobile) e nada ouvindo em 18080, 9099, 15432, 11025 e 18025. A porta 8080 fica livre para outras
coisas: por isso os builds apontam para 18080 em vez do default de `gradle.properties`.

## O que muda em relação à produção

- **Contas.** Firebase Auth Emulator, projeto `saqz-local`. Login e cadastro por e-mail e senha
  funcionam; Google e Apple não. Os links de confirmação de e-mail ficam em
  `http://127.0.0.1:9099/emulator/v1/projects/saqz-local/oobCodes`; abrir o `oobLink` confirma.
  As contas sobrevivem a `stack.sh down` (exportadas em `~/.saqz-multidev/auth-emulator`).
- **E-mail.** Tudo cai no Mailpit, em `http://127.0.0.1:18025`; nada sai da máquina.
- **Push.** Não há FCM nem APNs: os cartões e contadores atualizam ao reabrir a tela, mas o push
  "O jogo está liberado" (PROD-JG01) e os botões da notificação (PROD-JG03) não aparecem.
- **Links.** O backend gera `https://links.saqz.app/?saqz_invite=…`, mas os builds de dev não têm
  App Links verificados. Entregar o link ao outro aparelho direto no app:
  - Android: `adb -s emulator-5556 shell am start -a android.intent.action.VIEW -d '<link>' app.saqz`
  - iOS: `xcrun simctl openurl <udid> 'saqz://links.saqz.app/?saqz_invite=<codigo>'`
  - Ou, logado, "Tenho um convite" na Início aceita o link ou só o código.
- **WhatsApp, lojas e Asaas.** Desligados ou sem chave. Assinatura só pelo arquivo `.storekit` do
  scheme SaqzDev no iOS (o backend aceita `XCODE` neste stack).
- **Banco.** `psql -h 127.0.0.1 -p 15432 -U saqz saqz` (senha `saqz-multidev-local-only`) para
  conferir o que a tela mostra. `stack.sh reset` apaga banco e contas.

## Roteiro base

As jornadas são as de [`tests/producao`](../producao/README.md), executadas nesta ordem com os quatro
aparelhos: AC01 (ORG), GR01, CV01, CV02 (ATL, ATL2 e ATL3 pelo link), JG01, JG02 em cada atleta,
JG04/JG05 (vagas e reserva), MB01/MB05 (membros) e JG08/JG09 (editar e cancelar). O que depende de
push, loja ou WhatsApp fica registrado como `BLOQUEADO` aqui e vai para a rodada em produção.
