// Configuração do hospedeiro web, lida por WebConfig.kt e por saqz-bridge.js.
// Este arquivo é o de DEV (API de staging atrás do proxy do dev server, Firebase saqz-dev).
// Em produção o deploy publica outro saqz-config.js com os valores de prod (saquz-app, api.saqz.app).
window.SAQZ_WEB_CONFIG = {
  environment: "dev",
  apiBaseUrl: window.location.origin,
  linksHost: "links.saqz.app",
  appVersion: "web (dev)",
  firebase: {
    apiKey: "AIzaSyC_7NhdA7NOnL0SXzzNlcI2nAbBmwVodB4",
    authDomain: "saqz-dev.firebaseapp.com",
    projectId: "saqz-dev",
  },
};
