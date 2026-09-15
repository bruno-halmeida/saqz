(function () {
  "use strict";

  var config = window.SAQZ_FIREBASE_CONFIG || {};
  var auth = null;
  var currentUser = null;
  var observedUser = null;
  var generation = 0;
  var mode = "signup";
  var busy = false;
  var providerSettling = false;
  var activeAuthRequest = null;
  var signOutPending = false;
  var signOutFailed = false;
  var signOutPromise = null;
  var profileReady = false;
  var profileRetry = false;
  var session = null;
  var trialGeneration = 0;

  function $(id) { return document.getElementById(id); }
  function show(id) { $(id).hidden = false; }
  function hide(id) { $(id).hidden = true; }
  function setMessage(id, message, kind) {
    var element = $(id);
    element.textContent = message || "";
    element.classList.toggle("error", kind === "error");
    element.classList.toggle("success", kind === "success");
    element.hidden = !message;
  }
  function setBusy(value) {
    busy = value;
    var controlsDisabled = value || providerSettling;
    $("submit-auth").disabled = controlsDisabled;
    $("google-auth").disabled = controlsDisabled;
    $("save-profile").disabled = value;
    $("request-app-link").disabled = value;
    $("enroll-trial").disabled = value;
    $("apply-trial-coupon").disabled = value;
    $("trial-coupon").disabled = value;
    $("refresh-trial").disabled = value;
    $("signup-tab").disabled = controlsDisabled;
    $("login-tab").disabled = controlsDisabled;
    $("cancel-auth").hidden = !value && !providerSettling;
  }
  function validGeneration(user, expectedGeneration) {
    return Boolean(user && auth && auth.currentUser === user && generation === expectedGeneration);
  }
  function errorMessage(response, fallback) {
    return response.json().catch(function () { return {}; }).then(function (body) {
      var code = body && body.code;
      var messages = {
        AUTHENTICATION_REQUIRED: "Sua sessão expirou. Entre novamente para continuar.",
        ACCOUNT_NOT_FOUND: "Não encontramos seu perfil. Entre novamente ou tente outra conta.",
        ACCOUNT_SUSPENDED: "Esta conta está suspensa. Entre em contato com o suporte.",
        VALIDATION_FAILED: "Confira os dados informados e tente novamente.",
        IDENTITY_PROVIDER_UNAVAILABLE: "O serviço de identidade está indisponível. Tente novamente.",
      };
      return messages[code] || fallback;
    });
  }
  function safeError(message) {
    var error = new Error(message);
    error.safeMessage = message;
    return error;
  }
  function startSignOutCleanup() {
    if (signOutPending && !signOutFailed) return signOutPromise;
    signOutPending = true;
    signOutFailed = false;
    providerSettling = true;
    setMessage("auth-error", "Encerrando a sessão anterior. Aguarde um instante.");
    setBusy(true);
    signOutPromise = Promise.resolve().then(function () { return auth.signOut(); }).then(function () {
      signOutPending = false;
      signOutFailed = false;
      signOutPromise = null;
      providerSettling = false;
      $("cancel-auth").textContent = "Cancelar";
      setMessage("auth-error", "");
      setBusy(false);
    }, function () {
      signOutFailed = true;
      signOutPromise = null;
      providerSettling = true;
      $("cancel-auth").textContent = "Tentar novamente";
      setBusy(true);
      setMessage("auth-error", "Não foi possível encerrar a sessão. Toque em Tentar novamente.", "error");
    });
    return signOutPromise;
  }
  function invalidateSessionAndSignOut() {
    if (signOutPending && !signOutFailed) return signOutPromise;
    generation += 1;
    resetForLogout();
    return startSignOutCleanup();
  }
  function api(path, user, expectedGeneration, options) {
    if (!validGeneration(user, expectedGeneration)) return Promise.reject({ stale: true });
    return user.getIdToken().then(function (token) {
      if (!validGeneration(user, expectedGeneration)) return Promise.reject({ stale: true });
      var request = Object.assign({}, options || {});
      request.headers = Object.assign({ Authorization: "Bearer " + token }, request.headers || {});
      return fetch(String(config.apiBaseUrl || "").replace(/\/$/, "") + path, request).then(function (response) {
        if (response.status === 401 && validGeneration(user, expectedGeneration)) invalidateSessionAndSignOut();
        return response;
      });
    });
  }
  function jsonApi(path, user, expectedGeneration, method, body) {
    return api(path, user, expectedGeneration, {
      method: method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  }
  function validName(value) {
    return value.trim().length >= 2;
  }
  function resetReady() {
    trialGeneration += 1;
    hide("enroll-trial");
    hide("trial-coupon-form");
    $("trial-coupon").value = "";
    setMessage("trial-status", "Consultando as opções de teste…");
    session = null;
    profileReady = false;
    profileRetry = false;
    hide("ready-card");
    hide("profile-card");
    hide("app-link");
    $("app-link").removeAttribute("href");
    setMessage("app-link-error", "");
    setMessage("app-link-help", "O link é temporário. Se expirar, gere outro ou entre no app com esta mesma conta.");
    $("auth-form").reset();
    $("profile-form").reset();
  }
  function resetForLogout() {
    currentUser = null;
    resetReady();
    $("auth-form").reset();
    $("profile-form").reset();
    $("password").setAttribute("autocomplete", mode === "signup" ? "new-password" : "current-password");
    setMessage("auth-error", "");
    setMessage("profile-error", "");
    setBusy(false);
    show("auth-card");
  }
  function displayProfile(sessionResponse, requestedName) {
    var user = sessionResponse && sessionResponse.user;
    if (!user) throw new Error("resposta de sessão inválida");
    profileReady = user.phoneRequired === false;
    if (!profileReady) {
      if (requestedName && !user.displayName) {
        $("profile-name").value = requestedName;
        show("profile-name-field");
      } else {
        hide("profile-name-field");
      }
      show("profile-card");
      hide("ready-card");
      return;
    }
    show("ready-card");
    hide("profile-card");
    setMessage("ready-title", "Sua conta está pronta.");
    setMessage("ready-copy", "Baixe o app e entre com esta mesma conta. O teste começa quando você criar o primeiro grupo no celular.");
    bindStoreBadges();
    loadTrial(currentUser, generation);
  }
  function bootstrap(user, requestedName, expectedGeneration) {
    if (!validGeneration(user, expectedGeneration)) return Promise.reject({ stale: true });
    var name = (requestedName || user.displayName || "").trim();
    if (name && user.displayName !== name) {
      return user.updateProfile({ displayName: name }).then(function () {
        if (!validGeneration(user, expectedGeneration)) return Promise.reject({ stale: true });
        return user.getIdToken(true);
      }).then(function (token) {
        if (!validGeneration(user, expectedGeneration)) return Promise.reject({ stale: true });
        return token;
      });
    }
    return user.getIdToken(true).then(function (token) {
      if (!validGeneration(user, expectedGeneration)) return Promise.reject({ stale: true });
      return token;
    });
  }
  function finishAccount(user, requestedName, expectedGeneration) {
    currentUser = user;
    expectedGeneration = expectedGeneration == null ? generation : expectedGeneration;
    setBusy(true);
    setMessage("auth-error", "");
    return bootstrap(user, requestedName, expectedGeneration)
      .then(function () {
        return jsonApi("/api/session", user, expectedGeneration, "PUT", {});
      })
      .then(function (response) {
        if (!validGeneration(user, expectedGeneration)) return Promise.reject({ stale: true });
        if (!response.ok) return errorMessage(response, "Não foi possível preparar sua conta. Tente novamente.").then(function (message) { throw safeError(message); });
        return response.json();
      })
      .then(function (response) {
        if (!validGeneration(user, expectedGeneration)) return;
        session = response;
        profileRetry = false;
        hide("auth-card");
        displayProfile(response, requestedName);
      })
      .catch(function (error) {
        if (error && error.stale) return;
        if (!validGeneration(user, expectedGeneration)) return;
        profileRetry = true;
        if (mode === "signup") $("name").value = requestedName || user.displayName || $("name").value;
        $("submit-auth").textContent = "Tentar novamente";
        setMessage("auth-error", error.safeMessage || "Não foi possível preparar sua conta. Tente novamente.", "error");
        show("auth-card");
      })
      .finally(function () { if (validGeneration(user, expectedGeneration)) setBusy(false); });
  }
  function authenticateWithCredentials(event) {
    event.preventDefault();
    if (busy || providerSettling) return;
    var email = $("email").value.trim();
    var password = $("password").value;
    var name = $("name").value.trim();
    if (profileRetry && currentUser) {
      finishAccount(currentUser, mode === "signup" ? name : null);
      return;
    }
    if (!email || !password || (mode === "signup" && !validName(name))) {
      setMessage("auth-error", mode === "signup" ? "Informe seu nome, e-mail e uma senha válida." : "Informe seu e-mail e senha.", "error");
      return;
    }
    setMessage("auth-error", "");
    var intentMode = mode;
    var intentName = name;
    var intentGeneration = generation;
    var method = intentMode === "signup" ? "createUserWithEmailAndPassword" : "signInWithEmailAndPassword";
    var authRequest = { generation: intentGeneration, canceled: false };
    activeAuthRequest = authRequest;
    setBusy(true);
    auth[method](email, password)
      .then(function (credential) {
        if (generation !== intentGeneration || auth.currentUser !== credential.user) return staleProvider(credential, intentGeneration);
        return finishAccount(credential.user, intentMode === "signup" ? intentName : null, intentGeneration);
      })
      .catch(function (error) {
        if (error && error.stale) return;
        if (generation !== intentGeneration) return;
        var message = error && (error.code === "auth/email-already-in-use" || error.code === "auth/account-exists-with-different-credential")
          ? "Esta conta já existe. Troque para Já tenho conta e entre com ela."
          : "Não foi possível entrar com esses dados. Confira e tente novamente.";
        setMessage("auth-error", message, "error");
        setBusy(false);
      })
      .finally(function () {
        if (authRequest.canceled) {
          var cleanups = [authRequest.cancelCleanup, authRequest.staleCleanup].filter(Boolean);
          return Promise.all(cleanups).then(function () {
            if (activeAuthRequest === authRequest) activeAuthRequest = null;
            providerSettling = false;
            setBusy(false);
          }, function () {
            authRequest.cleanupFailed = true;
            setBusy(false);
            setMessage("auth-error", "Não foi possível encerrar a tentativa. Toque em Cancelar para tentar novamente.", "error");
          });
        }
        if (activeAuthRequest === authRequest) activeAuthRequest = null;
      });
  }
  function authenticateWithGoogle() {
    if (busy || providerSettling) return;
    setMessage("auth-error", "");
    var intentGeneration = generation;
    var provider = new firebase.auth.GoogleAuthProvider();
    var authRequest = { generation: intentGeneration, canceled: false };
    activeAuthRequest = authRequest;
    setBusy(true);
    auth.signInWithPopup(provider)
      .then(function (credential) {
        if (generation !== intentGeneration || auth.currentUser !== credential.user) return staleProvider(credential, intentGeneration);
        return finishAccount(credential.user, credential.user.displayName || null, intentGeneration);
      })
      .catch(function (error) {
        if (error && error.stale) return;
        if (generation !== intentGeneration) return;
        setMessage("auth-error", "Não foi possível entrar com Google. Tente novamente ou use e-mail e senha.", "error");
        setBusy(false);
      })
      .finally(function () {
        if (authRequest.canceled) {
          var cleanups = [authRequest.cancelCleanup, authRequest.staleCleanup].filter(Boolean);
          return Promise.all(cleanups).then(function () {
            if (activeAuthRequest === authRequest) activeAuthRequest = null;
            providerSettling = false;
            setBusy(false);
          }, function () {
            authRequest.cleanupFailed = true;
            setBusy(false);
            setMessage("auth-error", "Não foi possível encerrar a tentativa. Toque em Cancelar para tentar novamente.", "error");
          });
        }
        if (activeAuthRequest === authRequest) activeAuthRequest = null;
      });
  }
  function staleProvider(credential, expectedGeneration) {
    if (generation !== expectedGeneration && auth.currentUser === credential.user) {
      var cleanup = auth.signOut();
      if (activeAuthRequest) activeAuthRequest.staleCleanup = cleanup;
      return cleanup.then(function () { return Promise.reject({ stale: true }); });
    }
    return Promise.reject({ stale: true });
  }
  function saveProfile(event) {
    event.preventDefault();
    if (busy || !currentUser || !session) return;
    var phone = $("phone").value.trim();
    var name = $("profile-name").value.trim();
    if (!phone || (($("profile-name-field").hidden === false) && !validName(name))) {
      setMessage("profile-error", "Informe um nome e um celular válido.", "error");
      return;
    }
    var expectedGeneration = generation;
    setBusy(true);
    setMessage("profile-error", "");
    var body = { phone: phone };
    if (!$("profile-name-field").hidden) body.displayName = name;
    jsonApi("/api/session/profile", currentUser, expectedGeneration, "PATCH", body)
      .then(function (response) {
        if (!validGeneration(currentUser, expectedGeneration)) return Promise.reject({ stale: true });
        if (!response.ok) return errorMessage(response, "Não foi possível salvar o perfil. Tente novamente.").then(function (message) { throw safeError(message); });
        return response.json();
      })
      .then(function (response) {
        if (!validGeneration(currentUser, expectedGeneration) || !response) return;
        session = response;
        if (!response.user) throw safeError("Não foi possível confirmar o perfil. Tente novamente.");
        profileReady = response.user.phoneRequired === false;
        if (profileReady) {
          hide("profile-card");
          show("ready-card");
          loadTrial(currentUser, expectedGeneration);
        }
      })
      .catch(function (error) {
        if (error && error.stale) return;
        if (validGeneration(currentUser, expectedGeneration)) setMessage("profile-error", error.safeMessage || "Não foi possível salvar o perfil. Tente novamente.", "error");
      })
      .finally(function () { if (validGeneration(currentUser, expectedGeneration)) setBusy(false); });
  }
  function displayTrial(access) {
    $("enroll-trial").hidden = access.offerMode !== "ON" || !access.canRedeemCoupon || access.preauthorized;
    $("trial-coupon-form").hidden = !access.canRedeemCoupon;
    $("enroll-trial").textContent = "Experimentar o Organizador por " + access.trialDays + " dias";
    var message = access.status === "AVAILABLE" && access.preauthorized
      ? "Organizador: teste de " + access.trialDays + " dias liberado para esta conta. O prazo começa ao criar seu primeiro grupo no app."
      : access.status === "ACTIVE" ? "Seu teste do Organizador já está em andamento. Continue no app."
      : access.status === "SUBSCRIBED" ? "Sua conta já tem uma assinatura. Continue no app."
      : access.offerMode === "ON" && access.canRedeemCoupon ? "Você pode liberar seu teste de " + access.trialDays + " dias ou aplicar um cupom de campanha."
      : access.canRedeemCoupon ? "Aplique um cupom válido para liberar seu teste."
      : "Não há um novo teste disponível para esta conta. Confira os planos pagos.";
    setMessage("trial-status", message, access.canCreateGroup ? "success" : null);
  }
  function loadTrial(user, expectedGeneration) {
    var request = ++trialGeneration;
    return api("/subscriptions/trial", user, expectedGeneration, { method: "GET" })
      .then(function (response) {
        if (!response.ok) throw safeError("Não foi possível consultar seu teste. Atualize as opções.");
        return response.json();
      }).then(function (access) {
        if (request === trialGeneration && validGeneration(user, expectedGeneration)) displayTrial(access);
      }).catch(function (error) {
        if (request === trialGeneration && validGeneration(user, expectedGeneration)) setMessage("trial-status", error.safeMessage || "Não foi possível consultar seu teste. Atualize as opções.", "error");
      });
  }
  function selectTrial(coupon) {
    if (busy || !currentUser || !profileReady) return;
    var user = currentUser;
    var expectedGeneration = generation;
    var code = $("trial-coupon").value.trim().toUpperCase();
    if (coupon && !/^[A-Z0-9]{1,32}$/.test(code)) {
      setMessage("trial-status", "Informe um cupom válido, com letras e números.", "error");
      return;
    }
    var request = ++trialGeneration;
    setBusy(true);
    jsonApi(coupon ? "/subscriptions/trial/coupon" : "/subscriptions/trial/enrollment", user, expectedGeneration, "POST", coupon ? { code: code } : {})
      .then(function (response) {
        if (!response.ok) throw safeError(response.status === 400
          ? "Cupom inválido, expirado ou sem usos disponíveis. Confira o código."
          : "Não foi possível liberar este teste. Atualize as opções e tente novamente.");
        return response.json();
      }).then(function (access) {
        if (request === trialGeneration && validGeneration(user, expectedGeneration)) displayTrial(access);
      }).catch(function (error) {
        if (request === trialGeneration && validGeneration(user, expectedGeneration)) setMessage("trial-status", error.safeMessage || "Não foi possível liberar seu teste. Tente novamente.", "error");
      }).finally(function () { if (request === trialGeneration && validGeneration(user, expectedGeneration)) setBusy(false); });
  }
  function branchOriginFromAppUrl(raw) {
    try {
      var url = new URL(raw);
      if (url.protocol !== "https:" || !url.hostname || url.username || url.password || url.port) return null;
      if ((url.pathname !== "/" && url.pathname !== "") || url.hash) return null;
      return url.origin;
    } catch (ignored) { return null; }
  }
  function validAppUrl(raw, expectedOrigin) {
    try {
      var url = new URL(raw);
      if (url.protocol !== "https:" || !url.hostname || url.username || url.password || url.port) return null;
      if ((url.pathname !== "/" && url.pathname !== "") || url.hash) return null;
      if ((expectedOrigin || config.branchOrigin) && url.origin !== (expectedOrigin || config.branchOrigin)) return null;
      var parameters = new URLSearchParams(url.search);
      if (parameters.size !== 3 || parameters.get("$deeplink_path") !== "onboarding" || parameters.get("$ios_nativelink") !== "true") return null;
      if (!/^[A-Za-z0-9_-]{43}$/.test(parameters.get("saqz_onboarding") || "")) return null;
      return url.toString();
    } catch (ignored) { return null; }
  }
  function storeUrl(value) {
    return typeof value === "string" && /^https:\/\//.test(value.trim()) ? value.trim() : "";
  }
  function bindStoreBadge(id, url) {
    var element = $(id);
    var href = storeUrl(url);
    var label = element.getAttribute("data-label") || "";
    var soon = !href;
    element.classList.toggle("is-soon", soon);
    if (soon) {
      element.removeAttribute("href");
      element.removeAttribute("target");
      element.removeAttribute("rel");
      element.setAttribute("aria-disabled", "true");
      element.setAttribute("aria-label", label + " — em breve");
      return;
    }
    element.href = href;
    element.target = "_blank";
    element.rel = "noopener noreferrer";
    element.removeAttribute("aria-disabled");
    element.setAttribute("aria-label", label);
  }
  function bindStoreBadges() {
    bindStoreBadge("ios-store", config.iosAppStoreUrl);
    bindStoreBadge("android-store", config.androidPlayStoreUrl);
    var waiting = $("ios-store").classList.contains("is-soon") && $("android-store").classList.contains("is-soon");
    $("store-soon-note").hidden = !waiting;
  }
  function requestAppLink() {
    if (busy || !currentUser || !profileReady) return;
    var user = currentUser;
    var expectedGeneration = generation;
    setBusy(true);
    setMessage("app-link-error", "");
    hide("app-link");
    api("/subscriptions/trial", user, expectedGeneration, { method: "GET" })
      .then(function (response) {
        if (!validGeneration(user, expectedGeneration)) return Promise.reject({ stale: true });
        if (!response.ok) return Promise.reject(safeError("Não foi possível confirmar o ambiente do app. Tente novamente."));
        return response.json().then(function (body) {
          var origin = branchOriginFromAppUrl(body && body.appUrl);
          if (!origin) throw safeError("O app não está configurado neste ambiente. Entre manualmente com a mesma conta.");
          return origin;
        });
      })
      .then(function (expectedOrigin) {
        return api("/api/session/app-link", user, expectedGeneration, { method: "POST" }).then(function (response) {
          return { response: response, expectedOrigin: expectedOrigin };
        });
      })
      .then(function (packet) {
        var response = packet.response;
        if (!validGeneration(user, expectedGeneration)) return Promise.reject({ stale: true });
        if (!response.ok) return errorMessage(response, "Não foi possível gerar o link agora. Tente novamente.").then(function (message) { throw safeError(message); });
        return response.json().then(function (body) {
          return { body: body, expectedOrigin: packet.expectedOrigin };
        });
      })
      .then(function (packet) {
        if (!validGeneration(user, expectedGeneration)) return;
        var url = validAppUrl(packet.body && packet.body.url, packet.expectedOrigin);
        if (!url) throw safeError("O link do app não está disponível neste ambiente. Entre manualmente com a mesma conta.");
        $("app-link").href = url;
        show("app-link");
        setMessage("app-link-help", "Link pronto. Toque em Continuar no app para abrir o Saqz; se expirar, gere outro link.");
      })
      .catch(function (error) {
        if (error && error.stale) return;
        if (validGeneration(user, expectedGeneration)) setMessage("app-link-error", error.safeMessage || "Não foi possível gerar o link agora. Tente novamente.", "error");
      })
      .finally(function () { if (validGeneration(user, expectedGeneration)) setBusy(false); });
  }
  function resumeConnectedSession(user) {
    setMode("login");
    $("form-title").textContent = "Preparando sua conta";
    $("form-subtitle").textContent = "Você já está conectado. Seguimos sem criar outra conta.";
    finishAccount(user, user.displayName || null);
  }
  function setMode(nextMode) {
    mode = nextMode;
    $("signup-tab").setAttribute("aria-pressed", String(mode === "signup"));
    $("login-tab").setAttribute("aria-pressed", String(mode === "login"));
    $("form-title").textContent = mode === "signup" ? "Crie sua conta" : "Entre na sua conta";
    $("form-subtitle").textContent = mode === "signup" ? "Sem cartão, CPF ou escolha de plano para começar." : "Use a mesma conta que você vai usar no app.";
    $("submit-auth").textContent = mode === "signup" ? "Criar minha conta" : "Entrar";
    $("name-field").hidden = mode !== "signup";
    $("password").setAttribute("autocomplete", mode === "signup" ? "new-password" : "current-password");
    setMessage("auth-error", "");
  }
  function bind() {
    $("enroll-trial").addEventListener("click", function () { selectTrial(false); });
    $("trial-coupon-form").addEventListener("submit", function (event) { event.preventDefault(); selectTrial(true); });
    $("refresh-trial").addEventListener("click", function () {
      if (!busy && currentUser && profileReady) loadTrial(currentUser, generation);
    });
    $("signup-tab").addEventListener("click", function () { setMode("signup"); });
    $("login-tab").addEventListener("click", function () { setMode("login"); });
    $("auth-form").addEventListener("submit", authenticateWithCredentials);
    $("google-auth").addEventListener("click", authenticateWithGoogle);
    $("profile-form").addEventListener("submit", saveProfile);
    $("request-app-link").addEventListener("click", requestAppLink);
    $("cancel-auth").addEventListener("click", function () {
      if (signOutPending && signOutFailed) {
        setMessage("auth-error", "Tentando encerrar a sessão anterior…");
        startSignOutCleanup();
        return;
      }
      if (providerSettling && activeAuthRequest && activeAuthRequest.cleanupFailed) {
        activeAuthRequest.cleanupFailed = false;
        setMessage("auth-error", "Tentando encerrar a tentativa anterior…");
        Promise.resolve().then(function () { return auth.signOut(); }).then(function () {
          if (!activeAuthRequest || !activeAuthRequest.cleanupFailed) {
            if (activeAuthRequest) activeAuthRequest = null;
            providerSettling = false;
            setBusy(false);
          }
        }).catch(function () {
          if (activeAuthRequest) activeAuthRequest.cleanupFailed = true;
          setMessage("auth-error", "Não foi possível encerrar a tentativa. Toque em Cancelar para tentar novamente.", "error");
        });
        return;
      }
      if (!busy || providerSettling) return;
      if (!activeAuthRequest) {
        generation += 1;
        resetReady();
        profileRetry = Boolean(currentUser);
        setBusy(false);
        setMessage("auth-error", currentUser ? "A preparação foi cancelada. Clique em continuar para retomar sua conta." : "A preparação foi cancelada.");
        if (currentUser) $("submit-auth").textContent = "Continuar com esta conta";
        show("auth-card");
        return;
      }
      if (activeAuthRequest) activeAuthRequest.canceled = true;
      providerSettling = true;
      generation += 1;
      resetForLogout();
      setBusy(false);
      setMessage("auth-error", "Encerrando a tentativa anterior. Aguarde um instante para tentar novamente.");
      activeAuthRequest.cancelCleanup = Promise.resolve().then(function () { return auth.signOut(); });
    });
    $("logout").addEventListener("click", function () { invalidateSessionAndSignOut(); });
    auth.onAuthStateChanged(function (user) {
      if (observedUser && (!user || observedUser.uid !== user.uid)) generation += 1;
      if (observedUser && user && observedUser.uid !== user.uid) {
        resetReady();
        setBusy(false);
      }
      observedUser = user;
      currentUser = user;
      if (!user) { resetForLogout(); return; }
      if (!busy && !profileReady && !session) resumeConnectedSession(user);
    });
  }
  function start() {
    try {
      firebase.initializeApp({ apiKey: config.apiKey, authDomain: config.authDomain, projectId: config.projectId });
      auth = firebase.auth();
      if (config.authEmulatorUrl) auth.useEmulator(config.authEmulatorUrl, { disableWarnings: true });
      bind();
      bindStoreBadges();
    } catch (error) {
      setMessage("auth-error", "Não foi possível carregar o acesso agora. Tente novamente mais tarde.", "error");
    }
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start); else start();
})();
