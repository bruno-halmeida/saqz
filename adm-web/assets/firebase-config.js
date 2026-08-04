// Config do adm-web por ambiente.
//
// ATENÇÃO: o compose padrão (porta 8080) roda com o projeto Firebase saqz-dev SEM
// emulador — tokens do emulador saqz-local são rejeitados lá. Duas rotas locais:
//
// (a) Emulador ponta a ponta (default deste arquivo): suba o backend com o profile
//     local apontando para o emulador e ajuste apiBaseUrl para 8081:
//       npx --yes firebase-tools@15.23.0 emulators:start --only auth \
//         --project saqz-local --config firebase.json
//       cd backend && SPRING_PROFILES_ACTIVE=local SERVER_PORT=8081 \
//         SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/saqz \
//         SPRING_DATASOURCE_USERNAME=saqz SPRING_DATASOURCE_PASSWORD=... \
//         FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 \
//         SAQZ_ADMINWEB_ORIGINS=http://127.0.0.1:8123 \
//         SAQZ_PASSWORD_RESET_SECRET=dev ./gradlew :bootstrap:bootRun
//
// (b) Compose + Firebase dev real: preencha apiKey/authDomain/projectId com os
//     valores do projeto saqz-dev, REMOVA authEmulatorUrl e exporte
//     SAQZ_ADMINWEB_ORIGINS=http://127.0.0.1:8123 antes do docker compose up.
//
// Produção: valores do console do projeto real, apiBaseUrl da API publicada e
// SEM authEmulatorUrl.
window.SAQZ_FIREBASE_CONFIG = {
  apiKey: "fake-saqz-local-api-key",
  authDomain: "saqz-local.firebaseapp.com",
  projectId: "saqz-local",
  authEmulatorUrl: "http://127.0.0.1:9099",
  apiBaseUrl: "http://127.0.0.1:8080",
};
