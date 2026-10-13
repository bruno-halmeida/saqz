// Bridge do Firebase Auth para o web app em Kotlin/Wasm. Quem chama é interop/AuthBridge.kt;
// quem decide o que cada resultado significa é access/WebAuthAdapter.kt (espelho do Android).
//
// Contrato: toda operação recebe um callback `done` e o chama UMA vez, sempre de forma
// assíncrona, com um JSON:
//   {"ok":true,"user":{"uid","email","emailVerified","displayName"}}   login, perfil, reload
//   {"ok":true,"token":"..."}                                          idToken
//   {"ok":true}                                                        sendVerification, signOut
//   {"ok":false,"code":"auth/..."}                                     falha (code é o error.code do SDK)
// O `code` segue cru: a tradução para NativeFailureCode mora no Kotlin, ao lado da dos outros apps.
//
// Firebase Auth JS 10.14.1, a mesma versão que adm-web/comecar usa em produção.

const FIREBASE_BASE = "https://www.gstatic.com/firebasejs/10.14.1/";

// Falta de usuário atual não é erro do SDK; o Kotlin a trata como o `missingUser()` do Android.
const MISSING_USER = "saqz/missing-user";

// Celular (iPhone, iPad, Android) ou app aberto pelo ícone da tela de início.
function prefersRedirect() {
  const touchMac = navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1;
  const mobile = /iPhone|iPad|iPod|Android/i.test(navigator.userAgent) || touchMac;
  const standalone = navigator.standalone === true ||
    (window.matchMedia && window.matchMedia("(display-mode: standalone)").matches);
  return mobile || standalone;
}

// Aviso simples por cima do app (o Compose desenha num canvas; isto é HTML à parte). Some sozinho.
function showNotice(text) {
  const show = () => {
    const box = document.createElement("div");
    box.setAttribute("role", "alert");
    box.textContent = text;
    box.style.cssText = "position:fixed;left:16px;right:16px;bottom:calc(24px + env(safe-area-inset-bottom));" +
      "z-index:10;margin:0 auto;max-width:448px;padding:14px 16px;border-radius:14px;background:#0b1223;" +
      "color:#fff;font:500 14px/1.4 Inter,system-ui,-apple-system,sans-serif;box-shadow:0 8px 24px rgba(0,0,0,.25)";
    document.body.appendChild(box);
    setTimeout(() => box.remove(), 8000);
  };
  if (document.body) show(); else addEventListener("DOMContentLoaded", show);
}

async function createBridge() {
  const config = (window.SAQZ_WEB_CONFIG || {}).firebase;
  if (!config) throw new Error("SAQZ_WEB_CONFIG.firebase ausente");

  // Import dinâmico de propósito: com o gstatic fora do ar o erro cai aqui e o app mostra a
  // falha. Com import estático o módulo nem executaria e a promessa do bridge ficaria pendente.
  const [{ initializeApp }, sdk] = await Promise.all([
    import(FIREBASE_BASE + "firebase-app.js"),
    import(FIREBASE_BASE + "firebase-auth.js"),
  ]);
  const {
    getAuth, connectAuthEmulator, onAuthStateChanged,
    createUserWithEmailAndPassword, signInWithEmailAndPassword, signInWithCustomToken,
    signInWithPopup, signInWithRedirect, getRedirectResult, GoogleAuthProvider, EmailAuthProvider,
    reauthenticateWithCredential, reauthenticateWithPopup,
    sendEmailVerification, updateProfile, reload, signOut,
  } = sdk;

  const app = initializeApp({
    apiKey: config.apiKey,
    authDomain: config.authDomain,
    projectId: config.projectId,
    appId: config.appId,
  });
  // Persistência padrão do getAuth (IndexedDB, com localStorage de reserva): sobrevive a recarregar.
  const auth = getAuth(app);
  // Tem de vir antes de qualquer outra chamada ao auth.
  if (config.authEmulatorUrl) connectAuthEmulator(auth, config.authEmulatorUrl, { disableWarnings: true });
  // E-mail de verificação e telas do Google em português, como no Android.
  auth.languageCode = "pt-BR";

  // O SDK marca no sessionStorage que um redirecionamento está em curso; lido antes de ele limpar.
  const hadPendingRedirect = (() => {
    try { return Object.keys(sessionStorage).some((key) => key.startsWith("firebase:pendingRedirect")); } catch (e) { return false; }
  })();

  // Retorno do login com Google por redirecionamento (ver signInWithGoogle). O SDK restaura o
  // usuário sozinho e ele chega pelo observe, como quem reabre o app já logado. A falha não tem
  // mais tela de login esperando por ela (a página recarregou): vira um aviso por cima do app.
  getRedirectResult(auth).catch((error) => {
    const code = (error && error.code) || String(error);
    console.warn("[saqz] retorno do login por redirecionamento falhou:", code);
    showNotice(code === "auth/account-exists-with-different-credential"
      ? "Este e-mail já tem conta no Saqz com outro jeito de entrar. Entre com e-mail e senha."
      : "Não foi possível entrar com o Google. Tente de novo ou entre com e-mail e senha.");
  });

  const toUser = (user) => (user ? {
    uid: user.uid,
    email: user.email,
    emailVerified: user.emailVerified,
    displayName: user.displayName,
  } : null);

  const missingUser = () => Object.assign(new Error("sem usuário autenticado"), { code: MISSING_USER });

  const requireUser = () => {
    const user = auth.currentUser;
    if (!user) throw missingUser();
    return user;
  };

  // Executa `work` e entrega o JSON por `done`. Nunca rejeita e nunca chama `done` de forma
  // síncrona, então o Kotlin vê sempre a mesma ordem, inclusive quando falta o usuário.
  function run(done, work) {
    Promise.resolve()
      .then(work)
      .then(
        (payload) => ({ ok: true, ...payload }),
        (error) => {
          const code = error && typeof error.code === "string" ? error.code : null;
          console.debug("[saqz] auth falhou:", code || error);
          return { ok: false, code };
        },
      )
      .then((reply) => done(JSON.stringify(reply)));
  }

  // Mesma conferência do Android depois de reautenticar: a conta não pode ter trocado no meio e
  // o token novo tem de existir, porque é ele que o backend exige para apagar a conta.
  async function finishReauthentication(user, subject) {
    if (auth.currentUser?.uid !== subject) throw missingUser();
    const token = await user.getIdToken(true);
    if (auth.currentUser?.uid !== subject || !token) throw missingUser();
    return { user: toUser(user) };
  }

  async function signInWithGoogle() {
    const provider = new GoogleAuthProvider();
    // Sempre o seletor de contas, como o Credential Manager do Android (filterByAuthorizedAccounts=false).
    provider.setCustomParameters({ prompt: "select_account" });
    // Celular e app na tela de início: redirecionamento. A janela do Google abre como outra aba (no
    // Chrome do iPhone e no ícone da tela de início, fora do app) e o resultado não voltava: o
    // Firebase dava a janela por fechada e o login terminava calado. O redirecionamento só funciona
    // com o authDomain no próprio saqz.app (proxy de /__/auth/ no nginx do web app); com o
    // firebaseapp.com o Safari e o Chrome isolam o armazenamento e a volta chega sem a sessão.
    if (prefersRedirect()) return redirectToGoogle(provider);
    try {
      const credential = await signInWithPopup(auth, provider);
      return { user: toUser(credential.user) };
    } catch (error) {
      if (!error || error.code !== "auth/popup-blocked") throw error;
    }
    // Computador com popup barrado: o mesmo redirecionamento.
    return redirectToGoogle(provider);
  }

  async function redirectToGoogle(provider) {
    await signInWithRedirect(auth, provider);
    // A página está indo embora: sem resposta, de propósito (o callback do Kotlin nunca roda).
    return new Promise(() => {});
  }

  async function reauthenticateWithPassword(subject, password) {
    const user = auth.currentUser;
    if (!user || user.uid !== subject || !user.email || !password.trim()) throw missingUser();
    await reauthenticateWithCredential(user, EmailAuthProvider.credential(user.email, password));
    return finishReauthentication(user, subject);
  }

  async function reauthenticateWithGoogle(subject) {
    const user = auth.currentUser;
    if (!user || user.uid !== subject) throw missingUser();
    const provider = new GoogleAuthProvider();
    // A conta da sessão já vem sugerida; escolher outra termina em auth/user-mismatch.
    // Sem fallback para redirect: ele tiraria a pessoa do fluxo de excluir conta.
    if (user.email) provider.setCustomParameters({ login_hint: user.email });
    await reauthenticateWithPopup(user, provider);
    return finishReauthentication(user, subject);
  }

  let lastObserverId = 0;
  const unsubscribers = new Map();

  return {
    // Emite o estado atual (depois que o SDK restaura a sessão) e então cada troca de conta.
    observe(done) {
      const id = ++lastObserverId;
      // Na volta de um login por redirecionamento o SDK só avisa o primeiro estado depois de fechar o
      // retorno. Se isso travar (ajuda do login em outro domínio, rede), o app ficaria na tela de
      // abertura para sempre: depois de 15 s ele recebe o estado atual e segue; se o retorno fechar
      // depois, o usuário chega pelo mesmo observe.
      let first = true;
      const guard = setTimeout(() => {
        if (!first) return;
        first = false;
        console.warn("[saqz] auth não terminou de iniciar em 15 s");
        if (hadPendingRedirect) showNotice("Não foi possível concluir o login com o Google. Tente de novo ou entre com e-mail e senha.");
        done(JSON.stringify({ ok: true, user: toUser(auth.currentUser) }));
      }, 15000);
      unsubscribers.set(id, onAuthStateChanged(
        auth,
        // O usuário é fotografado agora: o objeto do SDK muda por baixo (reload, updateProfile).
        (user) => {
          if (first) { first = false; clearTimeout(guard); }
          done(JSON.stringify({ ok: true, user: toUser(user) }));
        },
        (error) => console.warn("[saqz] observador de auth falhou:", error),
      ));
      return id;
    },
    unobserve(id) {
      const unsubscribe = unsubscribers.get(id);
      unsubscribers.delete(id);
      if (unsubscribe) unsubscribe();
    },
    currentSubject() {
      return auth.currentUser ? auth.currentUser.uid : null;
    },

    createAccount(name, email, password, done) {
      run(done, async () => {
        const credential = await createUserWithEmailAndPassword(auth, email, password);
        await updateProfile(credential.user, { displayName: name });
        return { user: toUser(credential.user) };
      });
    },
    signInWithPassword(email, password, done) {
      run(done, async () => ({ user: toUser((await signInWithEmailAndPassword(auth, email, password)).user) }));
    },
    signInWithCustomToken(customToken, done) {
      run(done, async () => ({ user: toUser((await signInWithCustomToken(auth, customToken)).user) }));
    },
    signInWithGoogle(done) {
      run(done, signInWithGoogle);
    },
    reauthenticateWithPassword(subject, password, done) {
      run(done, () => reauthenticateWithPassword(subject, password));
    },
    reauthenticateWithGoogle(subject, done) {
      run(done, () => reauthenticateWithGoogle(subject));
    },

    sendVerification(done) {
      run(done, async () => { await sendEmailVerification(requireUser()); });
    },
    reloadUser(done) {
      run(done, async () => {
        await reload(requireUser());
        return { user: toUser(requireUser()) };
      });
    },
    updateDisplayName(name, done) {
      run(done, async () => {
        const user = requireUser();
        await updateProfile(user, { displayName: name });
        return { user: toUser(user) };
      });
    },
    idToken(forceRefresh, done) {
      run(done, async () => {
        const token = await requireUser().getIdToken(forceRefresh);
        if (!token) throw missingUser();
        return { token };
      });
    },
    signOut(done) {
      run(done, async () => { await signOut(auth); });
    },
  };
}

createBridge().then(
  (bridge) => {
    window.saqzBridge = bridge;
    window.__saqzResolveBridge(bridge);
  },
  (error) => {
    console.error("[saqz] o Firebase Auth não carregou:", error);
    // Resolve (não rejeita) porque o index.html só expõe o resolve; o Kotlin lê `failed`.
    window.__saqzResolveBridge({ failed: true });
  },
);
