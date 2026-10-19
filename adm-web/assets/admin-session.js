// Sessão do adm-web: login Firebase (Google ou e-mail/senha), validação do papel em /admin/me
// e camada de fetch autenticado que as telas usam. Expõe window.saqzAdmin.
(function () {
  "use strict";

  var config = window.SAQZ_FIREBASE_CONFIG;
  firebase.initializeApp({
    apiKey: config.apiKey,
    authDomain: config.authDomain,
    projectId: config.projectId,
  });
  var auth = firebase.auth();
  if (config.authEmulatorUrl) {
    auth.useEmulator(config.authEmulatorUrl, { disableWarnings: true });
  }

  var me = null;
  var listeners = [];
  var pendingError = null;

  // ---- Overlay de login (bloqueia o painel até validar o papel de admin) ----
  var overlay = document.createElement("div");
  overlay.id = "saqz-admin-login";
  overlay.innerHTML =
    '<style>' +
    '#saqz-admin-login{position:fixed;inset:0;z-index:100;display:flex;align-items:center;justify-content:center;background:var(--saqz-canvas,#F5F5F7);font-family:var(--font-ui,Inter,sans-serif)}' +
    '#saqz-admin-login .card{width:360px;background:#fff;border:1px solid var(--saqz-border,#D8DDE8);border-radius:16px;padding:32px;box-shadow:0 12px 40px rgba(14,23,56,.08)}' +
    '#saqz-admin-login h1{font-size:21px;font-weight:700;color:var(--saqz-navy,#0E1738);margin:0 0 4px}' +
    '#saqz-admin-login p{font-size:14px;color:var(--saqz-muted,#667085);margin:0 0 20px}' +
    '#saqz-admin-login label{display:block;font-size:13px;font-weight:600;color:var(--saqz-navy,#0E1738);margin:12px 0 6px}' +
    '#saqz-admin-login input{width:100%;box-sizing:border-box;font:inherit;font-size:15px;padding:10px 12px;border:1px solid var(--saqz-border,#D8DDE8);border-radius:10px;color:var(--saqz-navy,#0E1738)}' +
    '#saqz-admin-login input:focus{outline:2px solid rgba(6,56,223,.35);border-color:var(--saqz-blue,#0638DF)}' +
    '#saqz-admin-login button{width:100%;margin-top:20px;font:inherit;font-size:15px;font-weight:700;color:#fff;background:var(--saqz-blue,#0638DF);border:0;border-radius:999px;padding:12px;cursor:pointer}' +
    '#saqz-admin-login button[disabled]{background:var(--saqz-disabled-bg,#C9CED8);color:var(--saqz-disabled-fg,#7A8291);cursor:default}' +
    '#saqz-admin-login button.google{display:flex;align-items:center;justify-content:center;gap:10px;margin-top:0;background:#fff;color:#1F1F1F;border:1px solid #747775}' +
    '#saqz-admin-login button.google svg{width:18px;height:18px;flex:0 0 auto}' +
    '#saqz-admin-login button.google[disabled]{background:#fff;color:var(--saqz-disabled-fg,#7A8291);border-color:var(--saqz-border,#D8DDE8)}' +
    '#saqz-admin-login .ou{display:grid;grid-template-columns:1fr auto 1fr;align-items:center;gap:12px;margin:20px 0 4px;font-size:12px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:var(--saqz-muted,#667085)}' +
    '#saqz-admin-login .ou::before,#saqz-admin-login .ou::after{content:"";height:1px;background:var(--saqz-border,#D8DDE8)}' +
    '#saqz-admin-login .erro{display:none;font-size:13px;font-weight:600;color:var(--saqz-error,#E5484D);margin-top:12px}' +
    '#saqz-admin-logout{position:fixed;left:12px;bottom:64px;z-index:60;font:600 12px/1 Inter,sans-serif;color:var(--saqz-muted,#667085);background:#fff;border:1px solid var(--saqz-border,#D8DDE8);border-radius:999px;padding:8px 12px;cursor:pointer}' +
    '</style>' +
    '<div class="card"><h1>Saqz Admin</h1><p>Entre com sua conta de administrador.</p>' +
    '<button type="button" class="google" id="saqz-login-google">' +
    '<svg viewBox="0 0 48 48" aria-hidden="true" focusable="false">' +
    '<path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>' +
    '<path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>' +
    '<path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>' +
    '<path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>' +
    '</svg><span>Entrar com o Google</span></button>' +
    '<div class="ou">ou com e-mail</div>' +
    '<form id="saqz-admin-login-form">' +
    '<label for="saqz-login-email">E-mail</label><input id="saqz-login-email" type="email" autocomplete="username" required>' +
    '<label for="saqz-login-senha">Senha</label><input id="saqz-login-senha" type="password" autocomplete="current-password" required>' +
    '<button type="submit">Entrar</button><div class="erro" id="saqz-login-erro"></div>' +
    "</form></div>";

  var logoutButton = document.createElement("button");
  logoutButton.id = "saqz-admin-logout";
  logoutButton.type = "button";
  logoutButton.textContent = "Sair";
  logoutButton.style.display = "none";
  logoutButton.addEventListener("click", function () {
    auth.signOut();
  });

  function showOverlay(errorMessage) {
    overlay.style.display = "flex";
    logoutButton.style.display = "none";
    var erro = overlay.querySelector("#saqz-login-erro");
    erro.textContent = errorMessage || "";
    erro.style.display = errorMessage ? "block" : "none";
  }

  function hideOverlay() {
    overlay.style.display = "none";
    logoutButton.style.display = "block";
  }

  // Fechar o pop-up ou desistir no Google não é erro; o bloqueio de pop-up tem saída própria.
  function googleErrorMessage(code) {
    if (code === "auth/popup-closed-by-user" || code === "auth/cancelled-popup-request" || code === "auth/user-cancelled") {
      return null;
    }
    if (code === "auth/popup-blocked") {
      return "O navegador bloqueou a janela do Google. Libere pop-ups deste site e tente de novo.";
    }
    return "Não deu para entrar com o Google. Tente de novo.";
  }

  document.addEventListener("DOMContentLoaded", function () {
    document.body.appendChild(overlay);
    document.body.appendChild(logoutButton);
    // Depois do pop-up o fluxo é o mesmo do e-mail: onAuthStateChanged valida o papel em /admin/me.
    var google = overlay.querySelector("#saqz-login-google");
    google.addEventListener("click", function () {
      google.disabled = true;
      showOverlay();
      var provider = new firebase.auth.GoogleAuthProvider();
      // Quem tem mais de uma conta Google escolhe qual usar (e troca de conta depois de uma recusa).
      provider.setCustomParameters({ prompt: "select_account" });
      auth
        .signInWithPopup(provider)
        .catch(function (error) {
          // O código do Firebase vai para o console, para saber o que falhou; a tela mostra só o essencial.
          var code = (error && error.code) || "unknown";
          if (window.console) console.warn("Login com Google falhou:", code, error && error.message);
          var message = googleErrorMessage(code);
          if (message) showOverlay(message);
        })
        .finally(function () {
          google.disabled = false;
        });
    });
    overlay.querySelector("#saqz-admin-login-form").addEventListener("submit", function (event) {
      event.preventDefault();
      var button = overlay.querySelector("#saqz-admin-login-form button");
      button.disabled = true;
      var email = overlay.querySelector("#saqz-login-email").value.trim();
      var senha = overlay.querySelector("#saqz-login-senha").value;
      auth
        .signInWithEmailAndPassword(email, senha)
        .catch(function () {
          showOverlay("E-mail ou senha incorretos.");
        })
        .finally(function () {
          button.disabled = false;
        });
    });
  });

  // ---- Validação do papel e sessão ----
  function verifyAdmin(user) {
    return user.getIdToken().then(function (token) {
      return fetch(config.apiBaseUrl + "/admin/me", {
        headers: { Authorization: "Bearer " + token },
      }).then(function (response) {
        if (response.ok) return response.json();
        var error = new Error("admin check failed");
        error.status = response.status;
        throw error;
      });
    });
  }

  auth.onAuthStateChanged(function (user) {
    if (!user) {
      me = null;
      // signOut disparado por falha de validação: a mensagem sobrevive a este callback.
      showOverlay(pendingError);
      pendingError = null;
      notify();
      return;
    }
    verifyAdmin(user)
      .then(function (profile) {
        me = profile;
        hideOverlay();
        notify();
      })
      .catch(function (error) {
        me = null;
        pendingError = error.status === 403
          ? "Esta conta não tem acesso ao painel."
          : "Não deu para validar o acesso agora. Tente de novo.";
        showOverlay(pendingError);
        auth.signOut();
      });
  });

  function notify() {
    listeners.forEach(function (listener) {
      try {
        listener(me);
      } catch (ignored) {
        /* listener da tela não pode derrubar a sessão */
      }
    });
  }

  window.saqzAdmin = {
    get me() {
      return me;
    },
    /** Registra callback de mudança de sessão (login validado → objeto; logout → null). */
    onSession: function (listener) {
      listeners.push(listener);
      if (me) listener(me);
    },
    /** fetch autenticado contra a API; 401/403 derruba a sessão. */
    fetchAdmin: function (path, options) {
      var user = auth.currentUser;
      if (!user) return Promise.reject(new Error("sem sessão"));
      return user.getIdToken().then(function (token) {
        var merged = Object.assign({}, options || {});
        merged.headers = Object.assign({}, (options || {}).headers || {}, {
          Authorization: "Bearer " + token,
        });
        return fetch(config.apiBaseUrl + path, merged).then(function (response) {
          // Resposta atrasada de uma sessão anterior não pode derrubar a sessão
          // atual: o signOut só vale se o usuário desta chamada ainda é o logado.
          if ((response.status === 401 || response.status === 403) && auth.currentUser === user) {
            auth.signOut();
          }
          return response;
        });
      });
    },
    signOut: function () {
      return auth.signOut();
    },
  };
})();
