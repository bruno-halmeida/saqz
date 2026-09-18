const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const sitemap = fs.readFileSync(path.join(root, "sitemap.xml"), "utf8");
const robots = fs.readFileSync(path.join(root, "robots.txt"), "utf8");
const nginx = fs.readFileSync(path.join(root, "../nginx-landing.conf"), "utf8");
const compose = fs.readFileSync(path.join(root, "../compose.server.yaml"), "utf8");

test("sitemap lists only indexable public URLs on saqz.app", () => {
  assert.match(sitemap, /^<\?xml version="1.0" encoding="UTF-8"\?>\n<urlset xmlns="http:\/\/www.sitemaps.org\/schemas\/sitemap\/0.9">/);
  assert.match(sitemap, /<loc>https:\/\/saqz\.app\/<\/loc>/);
  assert.match(sitemap, /<loc>https:\/\/saqz\.app\/termos\/recebimentos\/<\/loc>/);
  assert.equal([...sitemap.matchAll(/<loc>/g)].length, 2);
  assert.doesNotMatch(sitemap, /recebimentos\/retorno/);
});

test("robots.txt allows crawlers and points to the sitemap", () => {
  assert.match(robots, /^User-agent: \*\nAllow: \/\n\nSitemap: https:\/\/saqz\.app\/sitemap\.xml\n$/);
});

test("landing nginx serves the sitemap as application/xml with cache", () => {
  assert.match(nginx, /location = \/sitemap\.xml/);
  assert.match(nginx, /default_type "application\/xml; charset=utf-8"/);
  assert.match(nginx, /Cache-Control "public, max-age=3600"/);
  assert.match(compose, /nginx-landing\.conf:\/etc\/nginx\/conf\.d\/default\.conf:ro/);
});
