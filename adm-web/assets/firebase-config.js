// Config do adm-web por ambiente.
//
// Local (default): emulador de auth do Firebase (firebase/session-fixture sobe em
// 127.0.0.1:9099 com o projeto saqz-local) e backend do compose em 127.0.0.1:8080.
// O backend precisa liberar a origem desta página: SAQZ_ADMINWEB_ORIGINS=http://127.0.0.1:8123
//
// Produção: preencher apiKey/authDomain/projectId com os valores do console do
// Firebase, apontar apiBaseUrl para a API real e REMOVER authEmulatorUrl.
window.SAQZ_FIREBASE_CONFIG = {
  apiKey: "fake-saqz-local-api-key",
  authDomain: "saqz-local.firebaseapp.com",
  projectId: "saqz-local",
  authEmulatorUrl: "http://127.0.0.1:9099",
  apiBaseUrl: "http://127.0.0.1:8080",
};
