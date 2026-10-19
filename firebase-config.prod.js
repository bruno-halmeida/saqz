// Painel adm-web de produção em https://adm.saqz.app (montado sobre assets/firebase-config.js pelo serviço
// adm-web-prod do compose.server.yaml): projeto Firebase saquz-app, sem emulador, API de produção. Os valores
// web são os mesmos da landing e públicos por natureza; o acesso real depende do token da conta e do
// access_users.platform_admin.
window.SAQZ_FIREBASE_CONFIG = {
  apiKey: "AIzaSyC1wV54ox6S6Mp81STbUMLXBxtH0GCYH-A",
  authDomain: "saquz-app.firebaseapp.com",
  projectId: "saquz-app",
  appId: "1:641280788616:web:6da100b9e2a884304bb73d",
  apiBaseUrl: "https://api.saqz.app",
  iosAppStoreUrl: "https://apps.apple.com/br/app/id6812743525",
  androidPlayStoreUrl: "https://play.google.com/store/apps/details?id=app.saqz",
};
