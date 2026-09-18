const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const sitemap = fs.readFileSync(path.join(root, "sitemap.xml"), "utf8");
const sitemapTxt = fs.readFileSync(path.join(root, "sitemap.txt"), "utf8");
const robots = fs.readFileSync(path.join(root, "robots.txt"), "utf8");
const nginx = fs.readFileSync(path.join(root, "../nginx-landing.conf"), "utf8");
const compose = fs.readFileSync(path.join(root, "../compose.server.yaml"), "utf8");
const home = fs.readFileSync(path.join(root, "index.html"), "utf8");

test("xml sitemap follows Google's loc/lastmod example and lists only indexable URLs", () => {
  assert.match(sitemap, /^<\?xml version="1.0" encoding="UTF-8"\?>\n<urlset xmlns="http:\/\/www.sitemaps.org\/schemas\/sitemap\/0.9">/);
  assert.match(sitemap, /<loc>https:\/\/saqz\.app\/<\/loc>/);
  assert.match(sitemap, /<loc>https:\/\/saqz\.app\/termos\/recebimentos\/<\/loc>/);
  assert.match(sitemap, /<loc>https:\/\/saqz\.app\/privacidade\/<\/loc>/);
  assert.match(sitemap, /<loc>https:\/\/saqz\.app\/termos\/<\/loc>/);
  assert.equal([...sitemap.matchAll(/<loc>/g)].length, 4);
  assert.doesNotMatch(sitemap, /changefreq|priority|recebimentos\/retorno/);
});

test("text sitemap lists the same public URLs one per line", () => {
  assert.equal(sitemapTxt, "https://saqz.app/\nhttps://saqz.app/termos/recebimentos/\nhttps://saqz.app/privacidade/\nhttps://saqz.app/termos/\n");
});

test("robots.txt allows crawlers and points to both sitemaps", () => {
  assert.match(robots, /^User-agent: \*\nAllow: \/\n\nSitemap: https:\/\/saqz\.app\/sitemap\.xml\nSitemap: https:\/\/saqz\.app\/sitemap\.txt\n$/);
});

test("home page advertises the xml sitemap", () => {
  assert.match(home, /rel="sitemap"[^>]+href="https:\/\/saqz\.app\/sitemap\.xml"/);
});

test("landing nginx serves the xml sitemap as application/xml", () => {
  assert.match(nginx, /location = \/sitemap\.xml/);
  assert.match(nginx, /default_type application\/xml;/);
  assert.match(nginx, /location = \/sitemap\.txt/);
  assert.match(compose, /nginx-landing\.conf:\/etc\/nginx\/conf\.d\/default\.conf:ro/);
});
