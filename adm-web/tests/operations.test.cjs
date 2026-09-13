const { test } = require("node:test");
const assert = require("node:assert/strict");
const operations = require("../recebimentos/operations.js");

test("queue normalization drops PII secrets and raw provider payload", () => {
  const normalized = operations.normalizeOperation({
    id: "op", accountId: "account", requestId: "request", kind: "CREATE_INSTRUMENT", resourceId: "resource",
    status: "UNKNOWN", attempts: 2, failureCode: "TIMEOUT", recoverable: true,
    cpfCnpj: "123", email: "secret@example.com", providerPayload: { access_token: "secret" }
  });
  assert.deepEqual(Object.keys(normalized).sort(), ["accountId", "attempts", "failureCode", "id", "kind", "recoverable", "requestId", "resourceId", "status"].sort());
  assert.equal(JSON.stringify(normalized).includes("secret"), false);
});

test("withdraw and refund never become actionable even if server payload is wrong", () => {
  for (const kind of ["WITHDRAW", "REFUND"]) assert.equal(operations.normalizeOperation({ kind, recoverable: true }).recoverable, false);
});

test("recovery request trims reason and preserves exact stable request id", () => {
  assert.deepEqual(operations.recoveryBody("stable-id", "  consultar timeout  "), { requestId: "stable-id", reason: "consultar timeout" });
  assert.throws(() => operations.recoveryBody("id", "x"));
});

test("browser return vocabulary cannot claim confirmation", () => {
  assert.equal(operations.checkoutReturnStatus(), "PENDING_VERIFICATION");
});
