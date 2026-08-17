// Máscaras e validação do cartão (VUL-210). Mora fora do assinar.js porque é a única
// parte da página com regra de verdade — e assim roda sem navegador:
// `node adm-web/assinar/cartao.js` executa o autoteste no fim do arquivo.
//
// Nada aqui persiste: as funções recebem e devolvem valores, sempre. Dado de cartão
// não pode encostar em localStorage, URL ou console (armadilha 1 do VUL-210).
(function (raiz) {
  "use strict";

  var MAX_NUMERO = 19;
  var MAX_VALIDADE = 4;
  var MAX_CVV = 4;
  var MAX_CEP = 8;
  var MAX_TELEFONE = 11;

  function digitos(valor) {
    return String(valor == null ? "" : valor).replace(/\D/g, "");
  }

  // mod 10 — a checagem que toda bandeira aceita pelo Asaas usa. É o que o front tem
  // a mais que o backend: lá a validação é só de tamanho.
  function luhn(valor) {
    var numero = digitos(valor);
    if (!numero) return false;
    var soma = 0;
    var dobra = false;
    for (var i = numero.length - 1; i >= 0; i--) {
      var digito = numero.charCodeAt(i) - 48;
      if (dobra) {
        digito *= 2;
        if (digito > 9) digito -= 9;
      }
      soma += digito;
      dobra = !dobra;
    }
    return soma % 10 === 0;
  }

  function mascaraNumero(valor) {
    return digitos(valor).slice(0, MAX_NUMERO).replace(/(\d{4})(?=\d)/g, "$1 ");
  }

  function mascaraValidade(valor) {
    var d = digitos(valor).slice(0, MAX_VALIDADE);
    return d.length > 2 ? d.slice(0, 2) + "/" + d.slice(2) : d;
  }

  function mascaraCvv(valor) {
    return digitos(valor).slice(0, MAX_CVV);
  }

  function mascaraCep(valor) {
    var d = digitos(valor).slice(0, MAX_CEP);
    return d.length > 5 ? d.slice(0, 5) + "-" + d.slice(5) : d;
  }

  function mascaraTelefone(valor) {
    var d = digitos(valor).slice(0, MAX_TELEFONE);
    if (d.length <= 2) return d;
    var corpo = d.slice(2);
    var quebra = d.length > 10 ? 5 : 4;
    if (corpo.length <= quebra) return "(" + d.slice(0, 2) + ") " + corpo;
    return "(" + d.slice(0, 2) + ") " + corpo.slice(0, quebra) + "-" + corpo.slice(quebra);
  }

  /**
   * Primeira mensagem de erro, ou null se o formulário passa. Os limites espelham o
   * `CreateSubscription.validateCreditCard` do backend de propósito: nenhuma combinação
   * pode ser aceita aqui e recusada lá — o cartão só sai do navegador quando tem chance.
   */
  function validar(form) {
    var numero = digitos(form.numero);
    if (numero.length < 13 || numero.length > 19 || !luhn(numero)) {
      return "Confira o número do cartão.";
    }
    var validade = digitos(form.validade);
    var mes = Number(validade.slice(0, 2));
    if (validade.length !== MAX_VALIDADE || mes < 1 || mes > 12) {
      return "Confira a validade (MM/AA).";
    }
    var cvv = digitos(form.cvv);
    if (cvv.length < 3 || cvv.length > 4) return "Confira o código de segurança.";
    if (String(form.titular == null ? "" : form.titular).trim().length < 2) {
      return "Informe o nome como está no cartão.";
    }
    if (digitos(form.cep).length !== MAX_CEP) return "Informe um CEP com 8 dígitos.";
    if (!String(form.numeroEndereco == null ? "" : form.numeroEndereco).trim()) {
      return "Informe o número do endereço.";
    }
    var telefone = digitos(form.telefone);
    if (telefone.length < 10 || telefone.length > 11) {
      return "Informe um telefone com DDD.";
    }
    return null;
  }

  /**
   * Os dois blocos do `POST /subscriptions`. MM/AA vira `expiryYear` de 4 dígitos, que é
   * o que o backend exige, e o titular (nome, e-mail, CPF) vem da sessão e do passo de
   * dados — a página não pede de novo o que já tem (mesma decisão do VUL-196).
   */
  function blocosDoPagamento(form, titular) {
    var validade = digitos(form.validade);
    return {
      creditCard: {
        holderName: String(form.titular).trim(),
        number: digitos(form.numero),
        expiryMonth: validade.slice(0, 2),
        expiryYear: "20" + validade.slice(2),
        ccv: digitos(form.cvv),
      },
      creditCardHolderInfo: {
        name: titular.name,
        email: titular.email,
        cpfCnpj: digitos(titular.cpfCnpj),
        postalCode: digitos(form.cep),
        addressNumber: String(form.numeroEndereco).trim(),
        phone: digitos(form.telefone),
      },
    };
  }

  raiz.SaqzCartao = {
    digitos: digitos,
    luhn: luhn,
    mascaraNumero: mascaraNumero,
    mascaraValidade: mascaraValidade,
    mascaraCvv: mascaraCvv,
    mascaraCep: mascaraCep,
    mascaraTelefone: mascaraTelefone,
    validar: validar,
    blocosDoPagamento: blocosDoPagamento,
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = raiz.SaqzCartao;

    // Autoteste: `node adm-web/assinar/cartao.js`. Sem framework — o repo não tem
    // runner de JS e uma dependência nova para nove asserts não se paga.
    if (require.main === module) {
      var assert = require("assert");
      var valido = {
        numero: "4111 1111 1111 1111",
        validade: "12/30",
        cvv: "123",
        titular: "ANA SILVA",
        cep: "01310-930",
        numeroEndereco: "1578",
        telefone: "(11) 98765-4321",
      };

      assert.ok(luhn("4111111111111111"));
      assert.ok(!luhn("4111111111111112"));
      assert.ok(!luhn(""));

      assert.strictEqual(mascaraNumero("4111111111111111"), "4111 1111 1111 1111");
      // 20 dígitos entram, 19 saem: o excedente é cortado antes da máscara.
      assert.strictEqual(mascaraNumero("41111111111111119999"), "4111 1111 1111 1111 999");
      assert.strictEqual(mascaraValidade("1230"), "12/30");
      assert.strictEqual(mascaraValidade("1"), "1");
      assert.strictEqual(mascaraCep("01310930"), "01310-930");
      assert.strictEqual(mascaraTelefone("11987654321"), "(11) 98765-4321");
      assert.strictEqual(mascaraTelefone("1133334444"), "(11) 3333-4444");

      assert.strictEqual(validar(valido), null);
      assert.match(validar(Object.assign({}, valido, { numero: "4111111111111112" })), /número/);
      assert.match(validar(Object.assign({}, valido, { numero: "411111111111" })), /número/);
      assert.match(validar(Object.assign({}, valido, { validade: "13/30" })), /validade/);
      assert.match(validar(Object.assign({}, valido, { validade: "1/30" })), /validade/);
      assert.match(validar(Object.assign({}, valido, { cvv: "12" })), /segurança/);
      assert.match(validar(Object.assign({}, valido, { titular: " A " })), /nome/);
      assert.match(validar(Object.assign({}, valido, { cep: "0131093" })), /CEP/);
      assert.match(validar(Object.assign({}, valido, { numeroEndereco: "  " })), /endereço/);
      assert.match(validar(Object.assign({}, valido, { telefone: "119876543" })), /telefone/);

      var blocos = blocosDoPagamento(valido, {
        name: "Ana Silva",
        email: "ana@example.com",
        cpfCnpj: "123.456.789-09",
      });
      assert.strictEqual(blocos.creditCard.number, "4111111111111111");
      assert.strictEqual(blocos.creditCard.expiryMonth, "12");
      assert.strictEqual(blocos.creditCard.expiryYear, "2030");
      assert.strictEqual(blocos.creditCardHolderInfo.cpfCnpj, "12345678909");
      assert.strictEqual(blocos.creditCardHolderInfo.postalCode, "01310930");
      assert.strictEqual(blocos.creditCardHolderInfo.phone, "11987654321");

      console.log("cartao.js: autoteste ok");
    }
  }
})(typeof window !== "undefined" ? window : globalThis);
