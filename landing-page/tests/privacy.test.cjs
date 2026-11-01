const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const page = fs.readFileSync(path.join(root, "privacidade/index.html"), "utf8");
const home = fs.readFileSync(path.join(root, "index.html"), "utf8");

test("privacy page names the controller, the CNPJ and the contact channel", () => {
  assert.match(page, /Égide Sistemas/);
  assert.match(page, /68\.648\.777\/0001-09/);
  assert.match(page, /mailto:contato@egysis\.com/);
});

test("privacy page declares usage and diagnostics data and the operators behind them", () => {
  assert.match(page, /Dados de uso:/);
  assert.match(page, /Dados de diagnóstico:/);
  assert.match(page, /Google \(Firebase\)/);
  assert.match(page, /Asaas/);
  assert.match(page, /identificador de publicidade/);
});

test("privacy page covers LGPD rights, retention and account deletion", () => {
  assert.match(page, /Lei 13\.709\/2018/);
  assert.match(page, /Seus direitos/);
  assert.match(page, /excluir a conta/);
  assert.match(page, /ANPD/);
});

test("privacy page is static and canonical", () => {
  assert.doesNotMatch(page, /<script/);
  assert.match(page, /rel="canonical" href="https:\/\/saqz\.app\/privacidade\/"/);
  assert.doesNotMatch(page, /\{\{|TODO|PLACEHOLDER/);
});

test("home links to the privacy policy", () => {
  assert.match(home, /href="\/privacidade\/"/);
});

test("privacy describes the Brazil launch and both account deletion channels", () => {
  assert.match(page, /Google ou Apple/);
  assert.match(page, /chat não está disponível/);
  assert.match(page, /Recebimentos automáticos por API e assinaturas de grupos estão desativados/);
  assert.match(page, /Perfil → Excluir conta/);
  assert.match(page, /href="\/excluir-conta\/"/);
  assert.doesNotMatch(page, /<strong>Branch:/);
});

test("privacy page declares the WhatsApp messages read by the admin team", () => {
  assert.match(page, /Atualizada em 1º de novembro de 2026 \(mensagens no WhatsApp\)\./);
  assert.match(page, /<strong>Mensagens no WhatsApp:<\/strong> as mensagens enviadas ao número do Saqz/);
  assert.match(page, /inclusive de pessoas sem conta no app\. Não copiamos essas mensagens para o nosso banco de dados\./);
  assert.match(page, /<strong>Atender quem escreve para o número do Saqz<\/strong> e acompanhar as mensagens desse número: legítimo interesse/);
  assert.match(page, /<strong>Uazapi \(WhatsApp\):<\/strong> envio dos avisos do grupo, quando ativado pelo organizador, e recebimento e armazenamento das mensagens enviadas ao número do Saqz\./);
  assert.match(page, /<p>As mensagens do número do Saqz ficam guardadas na Uazapi por até 7 dias\.<\/p>/);
});
