// Server deployment config (mounted over assets/firebase-config.js by
// compose.server.yaml): real saqz-dev project, no emulator, public API.
window.SAQZ_FIREBASE_CONFIG = {
  apiKey: "AIzaSyC_7NhdA7NOnL0SXzzNlcI2nAbBmwVodB4",
  authDomain: "saqz-dev.firebaseapp.com",
  projectId: "saqz-dev",
  apiBaseUrl: "https://saqz-api.brunoalmeida.dev",
  // Lojas: App Store e Google Play; vazio mantém o estado "Em breve".
  iosAppStoreUrl: "https://apps.apple.com/br/app/id6812743525",
  androidPlayStoreUrl: "",
};
