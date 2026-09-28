(() => {
  const assets = window.SAQZ_PHONE_ASSETS;
  const selectedId = new URLSearchParams(location.search).get('asset');
  if (selectedId && !assets.some(asset => asset.id === selectedId)) {
    throw new Error(`Peça inexistente: ${selectedId}`);
  }
  document.documentElement.dataset.mode = selectedId ? 'export' : 'preview';
  const gallery = document.querySelector('#gallery');
  const template = document.querySelector('#asset-template');

  assets.forEach((asset, index) => {
    if (selectedId && asset.id !== selectedId) return;
    const item = template.content.firstElementChild.cloneNode(true);
    const board = item.querySelector('.artboard');
    board.dataset.asset = asset.id;
    board.dataset.theme = asset.theme;
    board.setAttribute('aria-label', asset.alt);
    board.style.setProperty('--device-width', `${asset.device.width}px`);
    board.style.setProperty('--device-top', `${asset.device.top}px`);
    item.querySelector('.feature-label').textContent = asset.label;
    item.querySelectorAll('.headline span').forEach((line, i) => { line.textContent = asset.title[i]; });
    item.querySelector('.description').textContent = asset.description;
    const screenshot = item.querySelector('.app-screen');
    screenshot.src = `assets/screenshots/${asset.screenshot}`;
    screenshot.alt = asset.alt;
    item.querySelector('.sequence-number').textContent = `${String(index + 1).padStart(2, '0')} / 04`;
    item.querySelector('.caption-name').textContent = `${String(index + 1).padStart(2, '0')} · ${asset.label}`;
    item.querySelector('.png-link').href = `png/${asset.id}.png`;
    gallery.append(item);
  });

  const zoom = document.querySelector('#preview-scale');
  const applyZoom = () => document.documentElement.style.setProperty('--preview-scale', zoom.value);
  zoom.addEventListener('change', applyZoom);
  applyZoom();
  document.documentElement.dataset.ready = 'true';
})();
