// Página de assinatura (VUL-209): login Firebase (mesma conta do app), planos,
// checkout Pix ou cartão (VUL-210) e poll de recibos. Config e API base vêm de
// /assets/firebase-config.js, como no painel; sem o gate de admin — qualquer usuário
// autenticado passa.
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

  var loginPorLink = new URLSearchParams(window.location.search).get("t");
  var redimindoLink = Boolean(loginPorLink);

  function limparTokenDaUrl() {
    if (!loginPorLink) return;
    history.replaceState({}, "", window.location.pathname + window.location.hash);
    loginPorLink = null;
  }

  function entrarComLink(raw) {
    mostrar("carregando");
    fetch(config.apiBaseUrl + "/subscriptions/checkout-login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token: raw }),
    })
      .then(function (response) {
        if (!response.ok) throw new Error("status " + response.status);
        return response.json();
      })
      .then(function (body) {
        if (!body || !body.customToken) throw new Error("sem sessao");
        limparTokenDaUrl();
        redimindoLink = false;
        return auth.signInWithCustomToken(body.customToken);
      })
      .catch(function () {
        redimindoLink = false;
        limparTokenDaUrl();
        mostrar("login");
        mostrarErro("login-erro", "Este link expirou. Entre com e-mail e senha.");
      });
  }
  var CHECKOUT_KEY = "saqz-assinar-checkout";
  var POLL_MS = 5000;

  var cartao = window.SaqzCartao;
  var planos = [];
  var cicloEscolhido = "MONTHLY";
  var metodoEscolhido = "PIX";
  var planoEscolhido = null;
  var cupomAplicado = null;
  var pollTimer = null;
  var checkoutAtual = null;

  function $(id) { return document.getElementById(id); }

  function mostrar(estado) {
    ["carregando", "login", "assinante", "planos", "dados", "pix", "cartao", "sucesso", "erro"].forEach(function (nome) {
      $("estado-" + nome).hidden = nome !== estado;
    });
    var user = auth.currentUser;
    var rodape = $("rodape-sessao");
    if (user && estado !== "carregando") {
      rodape.hidden = false;
      rodape.innerHTML = "";
      rodape.append("Conectado como " + (user.email || "") + " · ");
      var sair = document.createElement("button");
      sair.type = "button";
      sair.textContent = "Sair";
      sair.addEventListener("click", function () { auth.signOut(); });
      rodape.append(sair);
    } else {
      rodape.hidden = true;
    }
  }

  function mostrarErro(id, mensagem) {
    var alvo = $(id);
    alvo.textContent = mensagem || "";
    alvo.classList.toggle("visivel", Boolean(mensagem));
  }

  function reais(cents) {
    return (cents / 100).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
  }

  function api(path, options) {
    var user = auth.currentUser;
    if (!user) return Promise.reject(new Error("sem sessão"));
    return user.getIdToken().then(function (token) {
      var merged = Object.assign({}, options || {});
      merged.headers = Object.assign(
        { Authorization: "Bearer " + token },
        (options || {}).headers || {}
      );
      return fetch(config.apiBaseUrl + path, merged).then(function (response) {
        // 401 com token recém-renovado = sessão inválida de verdade: derruba pro login.
        // Mesma guarda do admin-session: resposta atrasada de sessão anterior não conta.
        if (response.status === 401 && auth.currentUser === user) auth.signOut();
        return response;
      });
    });
  }

  function apiJson(path, body) {
    return api(path, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  }

  // ---- Checkout persistido: requestId reusado é o que impede cobrança dupla. ----
  function checkoutSalvo() {
    try {
      return JSON.parse(localStorage.getItem(CHECKOUT_KEY));
    } catch (ignored) {
      return null;
    }
  }

  function checkoutPara(planId, cycle) {
    var salvo = checkoutSalvo();
    if (salvo && salvo.requestId && salvo.planId === planId && salvo.cycle === cycle) return salvo;
    var novo = {
      requestId: crypto.randomUUID(),
      planId: planId,
      cycle: cycle,
      startedAt: new Date().toISOString(),
    };
    localStorage.setItem(CHECKOUT_KEY, JSON.stringify(novo));
    return novo;
  }

  // ---- Fluxo ----
  function iniciar() {
    mostrar("carregando");
    api("/subscriptions/me")
      .then(function (response) {
        if (response.status === 404) return carregarPlanos();
        if (!response.ok) throw new Error("status " + response.status);
        return response.json().then(function (me) {
          if (me.entitled) return mostrar("assinante");
          if (me.status === "PAST_DUE") return retomarCheckout(me);
          return carregarPlanos();
        });
      })
      .catch(function () {
        $("erro-detalhe").textContent = "Não deu para carregar seus dados agora.";
        mostrar("erro");
      });
  }

  function carregarPlanos() {
    return api("/plans")
      .then(function (response) {
        if (!response.ok) throw new Error("status " + response.status);
        return response.json();
      })
      .then(function (lista) {
        planos = lista;
        renderizarPlanos();
        mostrar("planos");
      });
  }

  function renderizarPlanos() {
    var alvo = $("lista-planos");
    alvo.innerHTML = "";
    planos.forEach(function (plano) {
      var card = document.createElement("div");
      card.className = plano.maxGroups == null ? "card plano plano--destaque" : "card plano";
      var preco = cicloEscolhido === "ANNUAL"
        ? reais(plano.annualPriceCents) + "<small>/ano</small>"
        : reais(plano.monthlyPriceCents) + "<small>/mês</small>";
      var beneficios = [];
      beneficios.push(plano.maxGroups == null ? "Grupos ilimitados" : plano.maxGroups + (plano.maxGroups === 1 ? " grupo" : " grupos"));
      beneficios.push(plano.maxAthletes == null ? "Atletas ilimitados" : "Até " + plano.maxAthletes + " atletas por grupo");
      if (plano.multiAdmin) beneficios.push("Vários administradores");
      if (plano.reports) beneficios.push("Relatórios");
      if (plano.whatsappSla) beneficios.push("Suporte prioritário no WhatsApp");
      card.innerHTML =
        '<div class="topo"><h2></h2><span class="preco">' + preco + "</span></div>" +
        "<ul>" + beneficios.map(function (b) { return "<li>" + b + "</li>"; }).join("") + "</ul>" +
        '<button type="button" class="escolher">Assinar este plano</button>';
      card.querySelector("h2").textContent = plano.name;
      card.querySelector(".escolher").addEventListener("click", function () {
        abrirDados(plano, cicloEscolhido, "Quase lá");
      });
      alvo.appendChild(card);
    });
  }

  function abrirDados(plano, ciclo, titulo) {
    planoEscolhido = { plano: plano, ciclo: ciclo };
    cupomAplicado = null;
    $("dados-cupom").value = "";
    $("cupom-ok").classList.remove("visivel");
    mostrarErro("cupom-erro", "");
    mostrarErro("dados-erro", "");
    // Cada entrada na tela de dados começa no Pix e sem cartão nenhum em memória:
    // trocar de plano não pode carregar dado de cartão da tentativa anterior.
    limparFormCartao();
    selecionarMetodo("PIX");
    $("dados-titulo").textContent = titulo;
    $("dados-resumo-plano").textContent =
      "Plano " + plano.name + " · " + (ciclo === "ANNUAL" ? "anual" : "mensal");
    atualizarTotal();
    mostrar("dados");
  }

  function precoLista() {
    return planoEscolhido.ciclo === "ANNUAL"
      ? planoEscolhido.plano.annualPriceCents
      : planoEscolhido.plano.monthlyPriceCents;
  }

  function atualizarTotal() {
    var total = cupomAplicado ? cupomAplicado.finalPriceCents : precoLista();
    $("dados-total").textContent = reais(total) + (planoEscolhido.ciclo === "ANNUAL" ? "/ano" : "/mês");
  }

  // Retomada de checkout pendente: mesma tela de dados, plano travado no que está
  // pendente no backend (criar com o mesmo plano re-emite a cobrança, não duplica).
  function retomarCheckout(me) {
    return api("/plans")
      .then(function (response) { return response.ok ? response.json() : []; })
      .then(function (lista) {
        planos = lista;
        var plano = lista.find(function (p) { return p.id === me.plan; });
        if (!plano) return carregarPlanos();
        cicloEscolhido = me.cycle;
        abrirDados(plano, me.cycle, "Retomar pagamento");
      });
  }

  function validarCupom() {
    var codigo = $("dados-cupom").value.trim().toUpperCase();
    mostrarErro("cupom-erro", "");
    $("cupom-ok").classList.remove("visivel");
    cupomAplicado = null;
    atualizarTotal();
    if (!codigo) return;
    apiJson("/coupons/validate", {
      code: codigo,
      planId: planoEscolhido.plano.id,
      cycle: planoEscolhido.ciclo,
    })
      .then(function (response) {
        if (!response.ok) throw new Error("status " + response.status);
        return response.json();
      })
      .then(function (resultado) {
        if (resultado.status === "APPLIED") {
          cupomAplicado = { code: codigo, finalPriceCents: resultado.finalPriceCents };
          var ok = $("cupom-ok");
          ok.textContent = "Cupom aplicado: -" + resultado.discountPercent + "%";
          ok.classList.add("visivel");
          atualizarTotal();
        } else if (resultado.status === "EXPIRED") {
          mostrarErro("cupom-erro", "Este cupom expirou.");
        } else {
          mostrarErro("cupom-erro", "Cupom não encontrado.");
        }
      })
      .catch(function () {
        mostrarErro("cupom-erro", "Não deu para validar o cupom agora.");
      });
  }

  // ---- Cartão (VUL-210) ----
  function lerFormCartao() {
    return {
      numero: $("cartao-numero").value,
      validade: $("cartao-validade").value,
      cvv: $("cartao-cvv").value,
      titular: $("cartao-titular").value,
      cep: $("cartao-cep").value,
      numeroEndereco: $("cartao-numero-endereco").value,
      telefone: $("cartao-telefone").value,
    };
  }

  // O cartão sai da memória assim que deixa de ser necessário. Ele nunca chegou a
  // localStorage nem à URL: só existiu nestes inputs e no corpo do POST.
  function limparFormCartao() {
    ["numero", "validade", "cvv", "titular", "cep", "numero-endereco", "telefone"]
      .forEach(function (campo) { $("cartao-" + campo).value = ""; });
  }

  function atualizarRotuloPagar() {
    $("botao-pagar").textContent =
      metodoEscolhido === "CREDIT_CARD" ? "Pagar com cartão" : "Gerar Pix";
  }

  function selecionarMetodo(metodo) {
    metodoEscolhido = metodo;
    document.querySelectorAll(".metodo").forEach(function (botao) {
      botao.setAttribute("aria-pressed", String(botao.dataset.metodo === metodo));
    });
    $("bloco-cartao").hidden = metodo !== "CREDIT_CARD";
    mostrarErro("dados-erro", "");
    atualizarRotuloPagar();
  }

  function pagar() {
    var cpf = $("dados-cpf").value.replace(/\D/g, "");
    if (cpf.length !== 11 && cpf.length !== 14) {
      mostrarErro("dados-erro", "Informe um CPF (11 dígitos) ou CNPJ (14 dígitos).");
      return;
    }
    var comCartao = metodoEscolhido === "CREDIT_CARD";
    var formCartao = comCartao ? lerFormCartao() : null;
    if (comCartao) {
      var invalido = cartao.validar(formCartao);
      if (invalido) {
        mostrarErro("dados-erro", invalido);
        return;
      }
    }
    mostrarErro("dados-erro", "");
    var botao = $("botao-pagar");
    botao.disabled = true;
    // Antifraude é lento e o backend espera até 60s nesse caminho: rótulo honesto e
    // nenhum reenvio automático. Retry é sempre manual, com o mesmo requestId — o
    // backend compara o payload e rotaciona a chave de idempotência sozinho.
    if (comCartao) botao.textContent = "Autorizando…";
    var user = auth.currentUser;
    checkoutAtual = checkoutPara(planoEscolhido.plano.id, planoEscolhido.ciclo);
    var corpo = {
      requestId: checkoutAtual.requestId,
      planId: planoEscolhido.plano.id,
      cycle: planoEscolhido.ciclo,
      billingType: metodoEscolhido,
      name: user.displayName || user.email,
      email: user.email,
      cpfCnpj: cpf,
      couponCode: cupomAplicado ? cupomAplicado.code : null,
    };
    if (comCartao) {
      var blocos = cartao.blocosDoPagamento(formCartao, {
        name: corpo.name,
        email: corpo.email,
        cpfCnpj: cpf,
      });
      corpo.creditCard = blocos.creditCard;
      corpo.creditCardHolderInfo = blocos.creditCardHolderInfo;
    }
    apiJson("/subscriptions", corpo)
      .then(function (response) {
        if (response.status === 201) {
          return response.json().then(comCartao ? mostrarCartao : mostrarPix);
        }
        // Recusa tem shape próprio pinado com o mobile ({error, reason, message}),
        // não o ApiProblem — por isso vem antes do tratamento genérico.
        if (response.status === 402) {
          return response.json().catch(function () { return {}; }).then(function (recusa) {
            // A descrição da Asaas já vem em PT-BR e já diz que foi recusa: prefixar
            // "Cartão recusado:" só faria a frase gaguejar.
            mostrarErro("dados-erro", recusa.message ||
              "Cartão recusado. Confira os dados ou tente outro cartão.");
          });
        }
        return response.json().catch(function () { return {}; }).then(function (problema) {
          if (problema.code === "SUBSCRIPTION_CONFLICT") return iniciar();
          if (problema.code === "SUBSCRIPTION_PENDING_CHECKOUT_MISMATCH") {
            localStorage.removeItem(CHECKOUT_KEY);
            mostrarErro("dados-erro",
              "Você já tem um pagamento pendente de outro plano. Recarregando para retomá-lo…");
            setTimeout(iniciar, 2500);
            return;
          }
          if (problema.code === "COUPON_NOT_FOUND" || problema.code === "COUPON_EXPIRED" ||
              problema.code === "COUPON_ALREADY_REDEEMED") {
            mostrarErro("dados-erro", "O cupom não pôde ser aplicado. Remova ou troque o cupom.");
            return;
          }
          if (problema.fieldErrors) {
            mostrarErro("dados-erro", "Confira os dados informados e tente de novo.");
            return;
          }
          mostrarErro("dados-erro", "Não deu para gerar o pagamento agora. Tente de novo.");
        });
      })
      .catch(function () {
        mostrarErro("dados-erro", "Não deu para gerar o pagamento agora. Tente de novo.");
      })
      .finally(function () {
        botao.disabled = false;
        atualizarRotuloPagar();
      });
  }

  // Sem argumento de propósito: ao contrário do Pix, a resposta não traz nada para a
  // tela — o cartão já foi autorizado e o que falta é o recibo do webhook.
  function mostrarCartao() {
    var total = cupomAplicado ? cupomAplicado.finalPriceCents : precoLista();
    $("cartao-resumo").textContent =
      "Plano " + planoEscolhido.plano.name + " · " + reais(total) +
      (planoEscolhido.ciclo === "ANNUAL" ? "/ano" : "/mês");
    // Autorizado: os dados do cartão não servem mais para nada nesta aba.
    limparFormCartao();
    mostrar("cartao");
    iniciarPoll();
  }

  function mostrarPix(resposta) {
    var total = cupomAplicado ? cupomAplicado.finalPriceCents : precoLista();
    $("pix-resumo").textContent =
      "Plano " + planoEscolhido.plano.name + " · " + reais(total) +
      (planoEscolhido.ciclo === "ANNUAL" ? "/ano" : "/mês");
    var qr = $("pix-qr");
    if (resposta.pixQrCodeBase64) {
      qr.src = "data:image/png;base64," + resposta.pixQrCodeBase64;
      qr.hidden = false;
    } else {
      qr.hidden = true;
    }
    // O backend devolve pixCopyPaste e invoiceUrl sempre; a UI segue o método escolhido.
    $("pix-codigo").textContent = resposta.pixCopyPaste || "Código indisponível — gere um novo.";
    mostrarErro("pix-erro", "");
    mostrar("pix");
    iniciarPoll();
  }

  function copiarPix() {
    var codigo = $("pix-codigo").textContent;
    navigator.clipboard.writeText(codigo).then(function () {
      var botao = $("botao-copiar");
      botao.textContent = "Copiado!";
      setTimeout(function () { botao.textContent = "Copiar código Pix"; }, 2000);
    });
  }

  // ---- Confirmação: recibo é o único sinal real de pagamento (status nasce PAST_DUE). ----
  function iniciarPoll() {
    pararPoll();
    pollTimer = setInterval(verificarRecibo, POLL_MS);
    verificarRecibo();
  }

  function pararPoll() {
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
  }

  function verificarRecibo() {
    api("/subscriptions/me/receipts?limit=1&offset=0")
      .then(function (response) { return response.ok ? response.json() : { receipts: [] }; })
      .then(function (corpo) {
        var recibo = corpo.receipts && corpo.receipts[0];
        // Recibo antigo (assinatura anterior) não confirma este checkout.
        if (recibo && checkoutAtual && recibo.confirmedAt >= checkoutAtual.startedAt) {
          pararPoll();
          localStorage.removeItem(CHECKOUT_KEY);
          mostrar("sucesso");
        }
      })
      .catch(function () { /* rede oscilou: o próximo tick tenta de novo */ });
  }

  document.addEventListener("visibilitychange", function () {
    if (!$("estado-pix").hidden || !$("estado-cartao").hidden) {
      if (document.hidden) pararPoll();
      else iniciarPoll();
    }
  });

  // ---- Login ----
  $("form-login").addEventListener("submit", function (event) {
    event.preventDefault();
    mostrarErro("login-erro", "");
    var botao = event.target.querySelector(".btn");
    botao.disabled = true;
    auth.signInWithEmailAndPassword($("login-email").value.trim(), $("login-senha").value)
      .catch(function () { mostrarErro("login-erro", "E-mail ou senha incorretos."); })
      .finally(function () { botao.disabled = false; });
  });

  $("botao-google").addEventListener("click", function () {
    mostrarErro("login-erro", "");
    auth.signInWithPopup(new firebase.auth.GoogleAuthProvider()).catch(function (error) {
      if (error && error.code === "auth/popup-closed-by-user") return;
      mostrarErro("login-erro", "Não deu para entrar com o Google agora.");
    });
  });

  // ---- Demais botões ----
  document.querySelectorAll(".ciclo button").forEach(function (botao) {
    botao.addEventListener("click", function () {
      cicloEscolhido = botao.dataset.ciclo;
      document.querySelectorAll(".ciclo button").forEach(function (outro) {
        outro.setAttribute("aria-pressed", String(outro === botao));
      });
      renderizarPlanos();
    });
  });
  document.querySelectorAll(".metodo").forEach(function (botao) {
    botao.addEventListener("click", function () { selecionarMetodo(botao.dataset.metodo); });
  });
  [
    ["cartao-numero", cartao.mascaraNumero],
    ["cartao-validade", cartao.mascaraValidade],
    ["cartao-cvv", cartao.mascaraCvv],
    ["cartao-cep", cartao.mascaraCep],
    ["cartao-telefone", cartao.mascaraTelefone],
  ].forEach(function (par) {
    var campo = $(par[0]);
    campo.addEventListener("input", function () { campo.value = par[1](campo.value); });
  });
  $("botao-cupom").addEventListener("click", validarCupom);
  $("form-dados").addEventListener("submit", function (event) {
    event.preventDefault();
    pagar();
  });
  $("botao-voltar-planos").addEventListener("click", function () {
    mostrar("planos");
  });
  $("botao-copiar").addEventListener("click", copiarPix);
  // Mesmo requestId: o backend re-emite o checkout em vez de criar outra cobrança.
  $("botao-regerar").addEventListener("click", function () {
    mostrar("dados");
  });
  $("botao-tentar-novamente").addEventListener("click", iniciar);

  if (loginPorLink) entrarComLink(loginPorLink);

  auth.onAuthStateChanged(function (user) {
    pararPoll();
    if (redimindoLink) return;
    if (user) iniciar();
    else mostrar("login");
  });
})();
