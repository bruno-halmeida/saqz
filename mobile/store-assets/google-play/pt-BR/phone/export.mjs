import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, resolve } from 'node:path';
import { mkdir, readFile, writeFile } from 'node:fs/promises';

const root = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const output = resolve(root, 'png');
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: process.argv.includes('--headless') });
const failures = [];
const summary = [];

try {
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  page.on('pageerror', error => failures.push(error.message));
  page.on('requestfailed', request => failures.push(`${request.url()}: ${request.failure()?.errorText}`));
  const entry = pathToFileURL(resolve(root, 'index.html'));
  await page.goto(entry.href, { waitUntil: 'load' });
  const assets = await page.evaluate(() => window.SAQZ_PHONE_ASSETS);
  if (!Array.isArray(assets) || assets.length !== 4) throw new Error('São esperadas exatamente quatro peças.');

  for (const asset of assets) {
    entry.searchParams.set('asset', asset.id);
    await page.goto(entry.href, { waitUntil: 'load' });
    await page.evaluate(async () => {
      await document.fonts.ready;
      await Promise.all([...document.images].map(image => image.decode()));
    });

    const layout = await page.evaluate(() => {
      const board = document.querySelector('.artboard').getBoundingClientRect();
      const hero = document.querySelector('.hero').getBoundingClientRect();
      const phone = document.querySelector('.phone-frame').getBoundingClientRect();
      const surface = document.querySelector('.screen-surface').getBoundingClientRect();
      const textNodes = [...document.querySelectorAll('.headline span, .description')];
      const lines = textNodes.map(node => {
        const range = document.createRange();
        range.selectNodeContents(node);
        const text = range.getBoundingClientRect();
        return { text: node.textContent, left: text.left, right: text.right, bottom: text.bottom };
      });
      const screen = document.querySelector('.app-screen');
      return {
        width: board.width,
        height: board.height,
        sloganHeight: hero.height,
        textClipped: lines.some(line => line.left < 0 || line.right > 1080 || line.bottom > phone.top - 12),
        phoneClipped: phone.left < 0 || phone.right > 1080 || phone.bottom > 1920 || phone.top < hero.bottom,
        screenshotClipped: Math.abs(surface.height - screen.getBoundingClientRect().height) > 1,
        imageLoaded: screen.complete && screen.naturalWidth > 0,
        screenshotRatio: screen.naturalWidth / screen.naturalHeight,
        displayedRatio: screen.getBoundingClientRect().width / screen.getBoundingClientRect().height,
      };
    });
    if (layout.width !== 1080 || layout.height !== 1920 || layout.textClipped || layout.phoneClipped || layout.screenshotClipped || !layout.imageLoaded) {
      throw new Error(`Layout inválido em ${asset.id}: ${JSON.stringify(layout)}`);
    }
    if (layout.sloganHeight > 1920 * 0.2) throw new Error(`Texto ocupa mais de 20% da altura em ${asset.id}.`);
    if (Math.abs(layout.screenshotRatio - layout.displayedRatio) > 0.0001) throw new Error(`Captura distorcida em ${asset.id}.`);
    if (asset.alt.length > 140) throw new Error(`Texto alternativo longo em ${asset.id}.`);

    const filename = resolve(output, `${asset.id}.png`);
    await page.locator('.artboard').screenshot({ path: filename, type: 'png', omitBackground: false, animations: 'disabled' });
    const bytes = await readFile(filename);
    const width = bytes.readUInt32BE(16);
    const height = bytes.readUInt32BE(20);
    const depth = bytes[24];
    const color = bytes[25];
    if (width !== 1080 || height !== 1920 || depth !== 8 || color !== 2) {
      throw new Error(`PNG inválido: ${asset.id} (${width}x${height}, depth=${depth}, color=${color}).`);
    }
    if (bytes.length > 8_000_000) throw new Error(`PNG acima de 8 MB: ${asset.id}.`);
    const result = { file: `${asset.id}.png`, width, height, format: 'PNG RGB 24 bits', bytes: bytes.length, alt: asset.alt };
    summary.push(result);
    console.log(`${result.file}: ${width} × ${height}, ${(bytes.length / 1000).toFixed(1)} KB — OK`);
  }
  if (failures.length) throw new Error(failures.join('\n'));
  await writeFile(resolve(output, 'manifest.json'), `${JSON.stringify(summary, null, 2)}\n`);
  console.log('Exportação concluída: quatro PNGs, sem transparência, fontes locais e proporções preservadas.');
} finally {
  await browser.close();
}
