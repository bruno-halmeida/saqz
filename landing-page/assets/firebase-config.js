// Produção: cadastro (/comecar/) e contratação (/assinar/) servidos em saqz.app pelo nginx da
// landing (nginx-landing.conf). App web "Saqz Web" do projeto Firebase saquz-app; a chave web é
// pública por natureza (vai para todo navegador) e o acesso real depende do token da conta.
window.SAQZ_FIREBASE_CONFIG = {
  apiKey: "AIzaSyC1wV54ox6S6Mp81STbUMLXBxtH0GCYH-A",
  authDomain: "saquz-app.firebaseapp.com",
  projectId: "saquz-app",
  appId: "1:641280788616:web:6da100b9e2a884304bb73d",
  apiBaseUrl: "https://api.saqz.app",
  // App Store: preencher com https://apps.apple.com/br/app/id6812743525 quando a Apple aprovar;
  // vazio mantém o selo "Em breve".
  iosAppStoreUrl: "",
  androidPlayStoreUrl: "https://play.google.com/store/apps/details?id=app.saqz",
};
