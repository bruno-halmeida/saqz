// Produção: cadastro (/comecar/) e contratação (/assinar/) servidos em saqz.app pelo nginx da
// landing (nginx-landing.conf). App web "Saqz Web" do projeto Firebase saquz-app; a chave web é
// pública por natureza (vai para todo navegador) e o acesso real depende do token da conta.
window.SAQZ_FIREBASE_CONFIG = {
  apiKey: "AIzaSyC1wV54ox6S6Mp81STbUMLXBxtH0GCYH-A",
  // O próprio saqz.app, como no web app: /__/auth/ e /__/firebase/ chegam ao Firebase pelo proxy do container
  // do web app (mobile/web-app/deploy/nginx.conf). Com o firebaseapp.com, o Safari e o Chrome isolam o
  // armazenamento de terceiros e o login com Google não volta para a página.
  authDomain: "saqz.app",
  projectId: "saquz-app",
  appId: "1:641280788616:web:6da100b9e2a884304bb73d",
  apiBaseUrl: "https://api.saqz.app",
  // App Store (1.0.6 liberada em 16/10/2026); vazio mantém o selo "Em breve".
  iosAppStoreUrl: "https://apps.apple.com/br/app/id6812743525",
  androidPlayStoreUrl: "https://play.google.com/store/apps/details?id=app.saqz",
};
