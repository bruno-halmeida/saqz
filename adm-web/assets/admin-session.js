// Sessão do adm-web: login Firebase (e-mail/senha), validação do papel em /admin/me
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
    '#saqz-admin-login .erro{display:none;font-size:13px;font-weight:600;color:var(--saqz-error,#E5484D);margin-top:12px}' +
    '#saqz-admin-logout{position:fixed;left:12px;bottom:64px;z-index:60;font:600 12px/1 Inter,sans-serif;color:var(--saqz-muted,#667085);background:#fff;border:1px solid var(--saqz-border,#D8DDE8);border-radius:999px;padding:8px 12px;cursor:pointer}' +
    '</style>' +
    '<div class="card"><h1>Saqz Admin</h1><p>Entre com sua conta de administrador.</p>' +
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

  document.addEventListener("DOMContentLoaded", function () {
    document.body.appendChild(overlay);
    document.body.appendChild(logoutButton);
    overlay.querySelector("#saqz-admin-login-form").addEventListener("submit", function (event) {
      event.preventDefault();
      var button = overlay.querySelector("button");
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
          if (response.status === 401 || response.status === 403) {
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
