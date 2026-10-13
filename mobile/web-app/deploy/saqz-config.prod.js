// Produção: substitui o saqz-config.js de dev no build publicado (deploy-web.sh).
// Firebase saquz-app, app web "Saqz Web" (o mesmo de saqz.app/comecar); a chave web é pública por
// natureza e o acesso real depende do token da conta. A API passa pelo proxy do próprio /app/.
window.SAQZ_WEB_CONFIG = {
  environment: "prod",
  apiBaseUrl: window.location.origin + "/app/api",
  linksHost: "links.saqz.app",
  appVersion: "web",
  checkoutUrl: "/assinar/",
  firebase: {
    apiKey: "AIzaSyC1wV54ox6S6Mp81STbUMLXBxtH0GCYH-A",
    authDomain: "saquz-app.firebaseapp.com",
    projectId: "saquz-app",
    appId: "1:641280788616:web:6da100b9e2a884304bb73d",
  },
};
