// Gera os artboards do DETALHE DO GRUPO (todos os estados) a partir dos tokens reais do
// design system e da linguagem da Home nova: hero azul, CTA lima, data em Anybody.
// Saída: project/*.dc.html + project/canvas.json (formato v3 do canvas de Design).
// Uso: node build.mjs [heights.json]  — heights.json vem do measure.mjs (altura real por board).
import { writeFileSync, mkdirSync, existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, 'project');
mkdirSync(out, { recursive: true });
const measuredPath = join(here, 'heights.json');
const MEASURED = existsSync(measuredPath) ? JSON.parse(readFileSync(measuredPath, 'utf8')) : {};
const BLOBS = existsSync(join(here, 'blobs.json')) ? JSON.parse(readFileSync(join(here, 'blobs.json'), 'utf8')) : {};

// ---- tokens (hex a hex do app) -------------------------------------------------
const C = {
  bg: '#F5F5F7', surface: '#FFFFFF', soft: '#F4F8FB', primary: '#0638DF', primaryPressed: '#052BB3',
  accent: '#C7F300', ink: '#0E1738', sec: '#667085', ph: '#98A2B3', border: '#D8DDE8',
  success: '#17B26A', warning: '#F5A623', warningFg: '#B26B00', error: '#E5484D',
};
const W = 390, INSET = 0, PAD = 16, FOLD = 800; // FOLD = 844 da tela - 44 da status bar

// ---- ícones (Lucide, os mesmos do SaqzIcons + os 3 que faltam: link, user-plus, log-out) ---
const P = {
  users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
  userPlus: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="19" x2="19" y1="8" y2="14"/><line x1="22" x2="16" y1="11" y2="11"/>',
  card: '<rect width="20" height="14" x="2" y="5" rx="2"/><line x1="2" x2="22" y1="10" y2="10"/>',
  chevron: '<path d="m9 18 6-6-6-6"/>',
  chevronLeft: '<path d="m15 18-6-6 6-6"/>',
  chevronDown: '<path d="m6 9 6 6 6-6"/>',
  chevronUp: '<path d="m18 15-6-6-6 6"/>',
  plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
  calendar: '<path d="M8 2v4"/><path d="M16 2v4"/><rect width="18" height="18" x="3" y="4" rx="2"/><path d="M3 10h18"/>',
  clock: '<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>',
  bell: '<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>',
  alert: '<circle cx="12" cy="12" r="10"/><line x1="12" x2="12" y1="8" y2="12"/><line x1="12" x2="12.01" y1="16" y2="16"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  pin: '<path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"/><circle cx="12" cy="10" r="3"/>',
  megaphone: '<path d="m3 11 18-5v12L3 14v-3z"/><path d="M11.6 16.8a3 3 0 1 1-5.8-1.6"/>',
  message: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
  link: '<path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>',
  logOut: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" x2="9" y1="12" y2="12"/>',
  refresh: '<path d="M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/>',
};
const icon = (n, s = 24, c = 'currentColor', extra = '') =>
  `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="${c}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" style="flex:0 0 auto;${extra}">${P[n]}</svg>`;

// Bola do saqz_volleyball.xml (viewport 1254), a mesma do SaqzSpinner e do hero da Home.
const BALL = [
  'M 597,108 C 493,207 449,406 583,580 C 533,663 469,727 397,767 C 263,638 211,410 279,252 C 294,230 313,211 333,196 C 416,145 503,118 597,108 Z',
  'M 649,108 C 854,108 1037,248 1113,441 C 940,284 741,222 548,265 C 567,196 603,144 649,108 Z',
  'M 542,309 C 764,255 1001,350 1138,531 C 1161,607 1155,701 1125,772 C 1009,584 802,480 590,518 C 557,457 539,382 542,309 Z',
  'M 634,552 C 733,535 842,561 928,610 C 861,854 601,1047 317,1048 C 259,1003 211,945 176,878 C 388,858 553,744 634,552 Z',
  'M 962,632 C 1032,690 1083,757 1107,826 C 1009,1040 826,1146 627,1146 C 532,1146 443,1122 365,1078 C 674,1050 897,852 962,632 Z',
  'M 277,245 C 111,400 52,642 157,844 C 228,834 296,815 360,784 C 232,649 206,445 277,245 Z',
];
const ballSvg = (size, color, opacity, extra) =>
  `<svg viewBox="0 0 1254 1254" width="${size}" height="${size}" aria-hidden="true" style="opacity:${opacity};pointer-events:none;flex:0 0 auto;${extra}">${BALL.map((d) => `<path d="${d}" fill="${color}"/>`).join('')}</svg>`;

// ---- primitivos ------------------------------------------------------------------
const col = (gap, kids, extra = '') => `<div style="display:flex;flex-direction:column;gap:${gap}px;${extra}">${kids.filter(Boolean).join('')}</div>`;
const row = (gap, kids, extra = '') => `<div style="display:flex;flex-direction:row;align-items:center;gap:${gap}px;${extra}">${kids.filter(Boolean).join('')}</div>`;
const t = (cls, text, extra = '') => `<div class="${cls}" style="${extra}">${text}</div>`;
const grow = '<div style="flex:1 1 auto"></div>';

const BTN = {
  lime: `background:${C.accent};color:${C.ink};border:0;font-weight:700`,
  outlineW: `background:transparent;color:#FFFFFF;border:1.5px solid rgba(255,255,255,.45);font-weight:600`,
  solidW: `background:#FFFFFF;color:${C.ink};border:0;font-weight:600`,
  ghostW: `background:transparent;color:#FFFFFF;border:0;font-weight:600`,
  primary: `background:${C.primary};color:#FFFFFF;border:0;font-weight:600`,
  secondary: `background:${C.surface};color:${C.primary};border:1px solid ${C.primary};font-weight:600`,
  ghost: `background:transparent;color:${C.primary};border:0;font-weight:600`,
  quiet: `background:transparent;color:${C.sec};border:0;font-weight:600`,
};
// Pílula em toda variante (SaqzButton): md 52/20px/15px, sm 44/16px/14px.
const btn = (label, kind, { size = 'md', full = true, disabled = false, extra = '' } = {}) => {
  const h = size === 'md' ? 52 : 44, pad = size === 'md' ? 20 : 16, fs = size === 'md' ? 15 : 14;
  return `<button type="button" style="display:flex;align-items:center;justify-content:center;gap:8px;min-height:${h}px;padding:0 ${pad}px;border-radius:999px;font-family:inherit;font-size:${fs}px;line-height:18px;white-space:nowrap;cursor:pointer;${BTN[kind]};${full ? 'flex:1 1 0;' : 'flex:0 0 auto;'}${disabled ? 'opacity:.45;' : ''}${extra}">${label}</button>`;
};
const spinner = (c = '#fff') => `<span style="width:16px;height:16px;border-radius:50%;border:2px solid ${c};border-right-color:transparent;display:inline-block;flex:0 0 auto"></span>`;

// SaqzStatusChip: 10×5, caption 600, ponto 6.
const chip = (text, fg, bg, dot = null) =>
  `<div style="display:inline-flex;align-items:center;gap:6px;padding:5px 10px;border-radius:999px;background:${bg};color:${fg};font-weight:600;font-size:12px;line-height:16px;white-space:nowrap;flex:0 0 auto">${dot ? `<span style="width:6px;height:6px;border-radius:50%;background:${dot};flex:0 0 auto"></span>` : ''}${text}</div>`;
const chipWarning = (text) => chip(text, C.warningFg, 'rgba(245,166,35,.14)', C.warningFg);
const chipBrand = (text) => chip(text, C.primary, 'rgba(6,56,223,.08)');
const chipNeutral = (text) => chip(text, C.sec, C.soft);
const chipSuccess = (text) => chip(text, '#0E7A48', 'rgba(23,178,106,.12)', C.success);
const chipOnBlue = (text, dot = C.accent) => chip(text, '#FFFFFF', 'rgba(255,255,255,.14)', dot);

const initials = (name) => {
  const p = name.trim().split(/\s+/);
  return (p.length === 1 ? p[0].slice(0, 1) : p[0][0] + p[p.length - 1][0]).toUpperCase();
};
const avatar = (name, size, bg, fg, ring, extra = '') =>
  `<div style="width:${size}px;height:${size}px;border-radius:50%;background:${bg};color:${fg};display:flex;align-items:center;justify-content:center;font-weight:600;font-size:${(size * 0.36).toFixed(1)}px;line-height:1;box-shadow:${ring};flex:0 0 auto;${extra}">${initials(name)}</div>`;
const stack = (names, { onBlue = false, max = 3, total = null } = {}) => {
  const shown = names.slice(0, max), over = (total ?? names.length) - shown.length;
  const ring = onBlue ? `0 0 0 2px ${C.primary}` : `0 0 0 2px ${C.surface}, inset 0 0 0 1px ${C.border}`;
  const kids = shown.map((n, i) => avatar(n, 30, onBlue ? C.surface : C.soft, C.ink, ring, i ? 'margin-left:-8px' : ''));
  if (over > 0) kids.push(`<div style="width:30px;height:30px;border-radius:50%;margin-left:-8px;background:${onBlue ? C.accent : C.primary};color:${onBlue ? C.ink : '#fff'};display:flex;align-items:center;justify-content:center;font-weight:600;font-size:10.8px;line-height:1;flex:0 0 auto;box-shadow:0 0 0 2px ${onBlue ? C.primary : C.surface}">+${over}</div>`);
  return row(0, kids, 'flex:0 0 auto');
};

const divider = (color = C.border) => `<div style="height:1px;background:${color};flex:0 0 auto"></div>`;
const card = (kids, { soft = false, flush = false, radius = 12, extra = '' } = {}) =>
  `<div style="display:flex;flex-direction:column;gap:${flush ? 0 : 12}px;border-radius:${radius}px;background:${soft ? C.soft : C.surface};${soft ? '' : `border:1px solid ${C.border};`}${flush ? 'overflow:hidden;' : 'padding:16px;'}${extra}">${kids.filter(Boolean).join('')}</div>`;
const listCard = (rows) => card(rows.filter(Boolean).flatMap((r, i) => (i ? [divider(), r] : [r])), { flush: true });
const sectionHeader = (title, action = null) =>
  row(8, [t('subtitle', title, `color:${C.ink};flex:1 1 auto`), action ? `<button type="button" class="support" style="font-family:inherit;background:transparent;border:0;cursor:pointer;font-weight:600;color:${C.primary};padding:6px 0 6px 8px">${action}</button>` : ''], 'min-height:23px');
const section = (title, body, action = null) => col(12, [sectionHeader(title, action), body]);

// ---- topo: barra sem título + identidade do grupo ---------------------------------
// O nome sai da top bar (já fica vazia durante o loading hoje) e vira o título da tela,
// UMA vez. "Editar" ocupa o slot `actions` do SaqzTopAppBar, que a tela não usa hoje.
const topBar = ({ admin = false, name = 'Vôlei do CERET' } = {}) =>
  row(4, [
    `<button type="button" aria-label="Voltar" style="width:44px;height:44px;display:flex;align-items:center;justify-content:center;background:transparent;border:0;cursor:pointer;flex:0 0 auto">${icon('chevronLeft', 24, C.primary)}</button>`,
    t('subtitle', name, `color:${C.ink};flex:1 1 auto;min-width:0;padding:0 4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis`),
    admin ? `<button type="button" class="label" style="font-family:inherit;background:transparent;border:0;cursor:pointer;color:${C.primary};min-height:44px;padding:0 8px;flex:0 0 auto">Editar</button>` : '',
  ], 'min-height:56px;margin:0 -4px');
const emblem = (size = 56) =>
  `<div style="width:${size}px;height:${size}px;border-radius:16px;background:${C.primary};display:flex;align-items:center;justify-content:center;flex:0 0 auto">${ballSvg(size * 0.62, '#FFFFFF', 1, '')}</div>`;
// A faixa de identidade foi REMOVIDA: o nome mora na barra (uma vez), e bairro/modalidade/
// nível são dado de descoberta — quem já está no grupo não precisa deles em toda visita.
// Restam em "Editar grupo"; "26 pessoas" vive na linha Galera e os dias, na agenda.

const photoFailedBanner = () =>
  row(10, [icon('alert', 20, C.warningFg), col(2, [t('ctitle', 'Grupo criado', `color:${C.ink}`), t('cmeta', 'A foto não carregou. Você pode tentar de novo em Editar.', `color:${C.sec}`)], 'flex:1 1 auto')],
    `align-items:flex-start;padding:12px;border-radius:12px;background:rgba(245,166,35,.14)`);

// ---- hero azul -------------------------------------------------------------------
const kicker = (text, trailing) =>
  row(8, [
    row(9, [`<span style="width:8px;height:8px;border-radius:50%;background:${C.accent};box-shadow:0 0 0 4px rgba(199,243,0,.18);flex:0 0 auto"></span>`, t('eyebrow', text, 'color:rgba(255,255,255,.82)')], 'flex:1 1 auto'),
    trailing || '',
  ]);
const heroLink = (label) => `<button type="button" style="font-family:inherit;display:flex;align-items:center;gap:2px;background:transparent;border:0;cursor:pointer;color:#fff;font-weight:600;font-size:13px;line-height:18px;padding:6px 0 6px 8px;margin:-6px 0">${label}${icon('chevron', 16, '#fff')}</button>`;
const heroLine = (ic, text, { color = 'rgba(255,255,255,.9)', trailing = '' } = {}) =>
  row(6, [icon(ic, 14, color), t('support', text, `font-weight:600;font-size:13px;line-height:18px;color:${color};flex:1 1 auto`), trailing]);
const mapLink = () => `<button type="button" style="font-family:inherit;background:transparent;border:0;cursor:pointer;color:#fff;font-weight:600;font-size:13px;line-height:18px;text-decoration:underline;text-underline-offset:3px;padding:6px 0 6px 8px;margin:-6px 0;flex:0 0 auto">Ver no mapa</button>`;
const deadlineOpen = () => heroLine('clock', 'As confirmações encerram hoje às 18h00.');
const deadlineClosed = () => heroLine('clock', 'As confirmações estão encerradas.', { color: 'rgba(255,255,255,.7)' });

// "Vou" é sempre o CTA lima; "Não vou" contorno. Em modo alterar, a marcada leva o check.
const rsvpRow = (selected = null, { size = 'md', disabled = false, loading = null } = {}) => {
  const check = icon('check', 16, C.ink);
  return row(8, [
    btn(`${loading === 'yes' ? spinner(C.ink) : selected === 'yes' ? check : ''}Vou`, selected === 'no' ? 'outlineW' : 'lime', { size, disabled }),
    btn(`${selected === 'no' ? check : ''}Não vou`, selected === 'no' ? 'solidW' : 'outlineW', { size, disabled: disabled || !!loading }),
  ]);
};
const answered = (status, { changeEnabled = true } = {}) => {
  const confirmed = status === 'yes';
  return row(8, [
    `<span style="width:10px;height:10px;border-radius:50%;background:${confirmed ? C.success : 'rgba(255,255,255,.45)'};flex:0 0 auto;margin-left:4px"></span>`,
    t('support', confirmed ? 'Sua presença está confirmada.' : 'Você não vai jogar.', 'font-size:13px;line-height:18px;font-weight:600;color:#fff;flex:1 1 auto'),
    btn('Alterar', 'ghostW', { size: 'sm', full: false, disabled: !changeEnabled }),
  ], 'background:rgba(255,255,255,.12);border-radius:12px;padding:4px 4px 4px 12px');
};
const editing = () => col(8, [
  rsvpRow('yes', { size: 'sm' }),
  row(0, [btn('Cancelar', 'ghostW', { size: 'sm', full: false })], 'justify-content:flex-end'),
]);
const heroAlert = (text, action = null) =>
  row(6, [icon('alert', 16, '#fff'), t('support', text, 'font-size:13px;line-height:18px;font-weight:500;color:rgba(255,255,255,.92);flex:1 1 auto'), action || ''], 'align-items:flex-start');
const feeNote = () => heroLine('card', 'Ao confirmar, a cobrança deste jogo será gerada.', { color: 'rgba(255,255,255,.82)' });

const NAMES = ['Lucas Prado', 'Bia Souza', 'Thiago Melo', 'Ana Lima', 'Caio Reis', 'Duda Nunes', 'Eva Rocha', 'Fábio Sá', 'Gil Matos'];
const rosterRow = (summary, spots = null) =>
  row(12, [stack(NAMES, { onBlue: true }), t('support', summary, 'font-weight:600;color:rgba(255,255,255,.88);flex:1 1 auto'), spots ? chipOnBlue(spots) : '']);

const waitBox = (kind) => {
  const reserva = kind === 'reserva';
  return row(8, [
    `<div style="width:42px;height:42px;border-radius:50%;background:rgba(255,255,255,.16);display:flex;align-items:center;justify-content:center;flex:0 0 auto">${icon(reserva ? 'clock' : 'alert', 22, '#fff')}</div>`,
    col(4, [
      t('body', reserva ? 'As vagas acabaram. Você entrou na lista de espera.' : 'Os mensalistas entram primeiro.', 'font-weight:700;color:#fff'),
      t('support', reserva ? 'Se alguém desistir, você entra e a gente avisa na hora.' : 'Você está na lista de espera porque joga como avulso. Assim que o admin liberar sua vaga, a gente te avisa.', 'color:rgba(255,255,255,.82)'),
    ], 'flex:1 1 auto'),
  ], 'align-items:flex-start;background:rgba(255,255,255,.12);border-radius:14px;padding:12px');
};
const waitActions = (kind) => col(4, [
  row(0, [btn('Ver o jogo', 'solidW', { size: 'sm' })]),
  row(0, [btn(kind === 'reserva' ? 'Sair da lista de espera' : 'Sair da lista', 'ghostW', { size: 'sm' })]),
]);

const scoreboard = ({ going, out, pending }) => {
  const cell = (n, label, color) => col(4, [
    t('display', String(n), `font-size:28px;line-height:28px;color:${color};text-align:center`),
    t('caption', label, 'color:rgba(255,255,255,.78);text-align:center'),
  ], 'flex:1 1 0;padding:12px 4px');
  const vdiv = '<div style="width:1px;background:rgba(255,255,255,.18);flex:0 0 auto;align-self:stretch"></div>';
  return `<button type="button" aria-label="9 vão, 1 não vai, 2 sem resposta. Ver o jogo." style="font-family:inherit;display:flex;flex-direction:row;align-items:center;padding:0;border:0;cursor:pointer;background:rgba(255,255,255,.10);border-radius:12px;color:#fff">${cell(going, 'Vão', C.accent)}${vdiv}${cell(out, 'Não vão', 'rgba(255,255,255,.78)')}${vdiv}${cell(pending, 'Sem resposta', C.warning)}</button>`;
};
/**
 * O bloco único do próximo jogo DO GRUPO: funde os 3 cards de hoje (jogo, "Você vai jogar?",
 * contadores) e absorve o card da Quadra (endereço do JOGO + "Ver no mapa").
 * kind: pending | confirmed | declined | editing | error | reserva | avulso | empty | emptyAdmin
 */
const hero = ({ kind, admin = false, closed = false, score = null, fee = false, stale = null, notify = null, loading = null, spots = 'Restam 3 vagas', first = false, mapFailed = false }) => {
  const kids = [];
  const isEmpty = kind === 'empty' || kind === 'emptyAdmin';
  kids.push(kicker('Próximo jogo', isEmpty ? '' : heroLink('Ver jogo')));
  if (isEmpty) {
    kids.push(t('display', first ? 'Grupo criado!' : 'Sem jogo marcado', 'color:#fff'));
    kids.push(t('support', first ? 'Vamos marcar o primeiro jogo? Defina data, local e vagas — depois é só chamar a galera.' : kind === 'emptyAdmin' ? 'Marque o próximo e a galera confirma em um toque.' : 'Quando a galera marcar, aparece aqui e você confirma em um toque.', 'font-weight:500;color:rgba(255,255,255,.82);max-width:290px'));
    if (kind === 'emptyAdmin') kids.push(row(8, [btn(first ? 'Marcar primeiro jogo' : 'Marcar jogo', 'lime'), first ? '' : btn('Convidar', 'outlineW')]));
  } else {
    kids.push(t('display', 'Terça, 19h30', 'color:#fff'));
    kids.push(col(6, [
      t('support', '28 de julho · CERET — Quadra 2', 'font-weight:500;color:rgba(255,255,255,.88)'),
      heroLine('pin', 'R. Canuto Abreu, s/n · Tatuapé', { color: 'rgba(255,255,255,.82)', trailing: mapLink() }),
      mapFailed ? heroAlert('Não foi possível abrir o mapa. Consulte o endereço acima ou tente novamente.') : '',
    ]));
    if (admin) kids.push(scoreboard(score));
    kids.push(closed ? deadlineClosed() : deadlineOpen());
    switch (kind) {
      case 'pending': kids.push(rsvpRow(null, { disabled: closed, loading })); break;
      case 'confirmed': kids.push(answered('yes', { changeEnabled: !closed })); break;
      case 'declined': kids.push(answered('no', { changeEnabled: !closed })); break;
      case 'editing': kids.push(editing()); break;
      case 'error': kids.push(rsvpRow()); kids.push(heroAlert('Não foi possível salvar sua resposta. Tente novamente.')); break;
      case 'reserva': kids.push(row(0, [chipOnBlue('Lista de espera · 1º')])); kids.push(waitBox('reserva')); kids.push(waitActions('reserva')); break;
      case 'avulso': kids.push(row(0, [chipOnBlue('Lista de espera · 2º')])); kids.push(waitBox('avulso')); kids.push(waitActions('avulso')); break;
    }
    if (fee) kids.push(feeNote());
    if (stale) kids.push(heroAlert('A lista de presença pode estar desatualizada.', `<button type="button" style="font-family:inherit;display:flex;align-items:center;gap:6px;background:transparent;border:0;cursor:pointer;color:#fff;font-weight:600;font-size:13px;line-height:18px;text-decoration:underline;text-underline-offset:3px;padding:0 0 0 8px;flex:0 0 auto">${stale === 'loading' ? spinner() : ''}Tentar novamente</button>`));
    if (!admin && kind !== 'reserva' && kind !== 'avulso') { const scarce = kind === 'pending' && !closed ? spots : null; kids.push(rosterRow(scarce ? '9 de 12' : '9 de 12 confirmados', scarce)); }
  }
  return `<div style="position:relative;overflow:hidden;display:flex;flex-direction:column;gap:12px;border-radius:20px;background:${C.primary};padding:20px;color:#fff"><div style="position:absolute;right:-58px;top:-62px;pointer-events:none">${ballSvg(210, '#FFFFFF', 0.2, '')}</div>${kids.join('')}</div>`;
};

// Confirmação automática: é do GRUPO e só do mensalista. SaqzSwitch não tem variante sobre
// azul, então fica fora do hero, colada nele (gap 12), em bloco ice sem borda.
const toggle = (on, { dim = false } = {}) =>
  `<span role="switch" aria-checked="${on}" style="width:52px;height:30px;border-radius:999px;background:${on ? C.primary : C.border};display:inline-flex;align-items:center;padding:3px;box-sizing:border-box;justify-content:${on ? 'flex-end' : 'flex-start'};flex:0 0 auto;${dim ? 'opacity:.45;' : ''}"><span style="width:24px;height:24px;border-radius:50%;background:#fff;display:block"></span></span>`;
const autoConfirm = (on, { updating = false, failed = false } = {}) =>
  col(8, [
    row(12, [t('label', 'Confirmar presença automaticamente', `color:${C.ink};flex:1 1 auto`), toggle(on, { dim: updating })], 'min-height:32px'),
    failed ? row(6, [icon('alert', 16, C.error), t('cmeta', 'Não foi possível atualizar a confirmação automática. Tente novamente.', `color:${C.error}`)], 'align-items:flex-start') : '',
  ], `padding:12px 16px;border-radius:12px;background:${C.soft}`);

// ---- linhas de lista (vocabulário da Home: contêiner 40–44 + compactTitle/compactMeta) ----
const circle = (ic, { tone = 'soft' } = {}) =>
  `<div style="width:40px;height:40px;border-radius:50%;background:${tone === 'success' ? 'rgba(23,178,106,.12)' : C.soft};display:flex;align-items:center;justify-content:center;flex:0 0 auto">${icon(ic, 22, tone === 'success' ? C.success : C.sec)}</div>`;
const listRow = (lead, title, meta, trailing = icon('chevron', 22, C.sec), { metaLines = 1 } = {}) =>
  row(12, [
    lead,
    col(2, [t('ctitle', title, `color:${C.ink}`), meta ? t('cmeta', meta, `color:${C.sec};${metaLines > 1 ? `display:-webkit-box;-webkit-line-clamp:${metaLines};-webkit-box-orient:vertical;overflow:hidden` : 'white-space:nowrap;overflow:hidden;text-overflow:ellipsis'}`) : ''], 'flex:1 1 auto;min-width:0'),
    trailing,
  ], 'min-height:48px;padding:12px 16px');

const dateBox = (day, month) =>
  `<div style="width:44px;height:44px;border-radius:10px;background:${C.soft};display:flex;flex-direction:column;align-items:center;justify-content:center;flex:0 0 auto"><span style="font-weight:800;font-size:17px;line-height:20px;color:${C.ink}">${day}</span><span style="font-weight:700;font-size:11px;line-height:14px;letter-spacing:.08em;color:${C.primary}">${month}</span></div>`;
const ST = {
  going: () => chipSuccess('Você vai'), none: () => chipNeutral('Sem resposta'), out: () => chipNeutral('Não vou'),
  wait: () => chipWarning('Na espera'), draft: () => chipBrand('Rascunho'),
};
// Agenda do grupo: o dado já é baixado (gameGateway.list) e jogado fora. O chip com a MINHA
// resposta pede `ownAttendance` também na listagem (backend, aditivo).
const agenda = ({ admin = false, more = 0 } = {}) => section('Próximos jogos',
  listCard([
    listRow(dateBox('30', 'JUL'), 'Quinta · 19h30', '6 de 12 confirmados', ST.none()),
    listRow(dateBox('04', 'AGO'), 'Terça · 19h30', '3 de 12 · Arena Mooca', ST.going()),
    admin ? listRow(dateBox('06', 'AGO'), 'Quinta · 19h30', 'Só você vê até publicar', ST.draft()) : listRow(dateBox('06', 'AGO'), 'Quinta · 19h30', 'Lotado · 12 de 12', ST.wait()),
    more ? `<button type="button" class="support" style="font-family:inherit;background:transparent;border:0;cursor:pointer;font-weight:600;color:${C.primary};min-height:48px;display:flex;align-items:center;justify-content:center;gap:4px">Ver mais ${more} jogos${icon('chevronDown', 18, C.primary)}</button>` : '',
  ]), admin ? 'Marcar jogo' : null);

// Mural: funde os atalhos Avisos/Conversa e o card "Aviso recente" (que hoje nem é clicável).
const mural = ({ notice = true } = {}) => section('Mural',
  listCard([
    listRow(circle('megaphone'), 'Avisos', notice ? 'Lucas: Cheguem 15 min antes para montar a rede. · Hoje, 10h30' : 'Nenhum aviso por enquanto', undefined, { metaLines: 2 }),
    listRow(circle('message'), 'Conversa', 'Fale com a galera do grupo'),
  ]));

const people = () => section('Galera',
  listCard([
    row(12, [stack(NAMES, { max: 4, total: 26 }), col(2, [t('ctitle', '26 pessoas', `color:${C.ink}`), t('cmeta', 'Lucas, Bia e mais 24', `color:${C.sec}`)], 'flex:1 1 auto;min-width:0'), icon('chevron', 22, C.sec)], 'min-height:48px;padding:12px 16px'),
  ]));

// Gestor: "o que ESTE grupo espera de mim" — o mesmo bloco da Home, escopado ao grupo.
const waiting = (items) => section('Esperando você', listCard(items));
const smallBtn = (label, { loading = false } = {}) => `<button type="button" style="font-family:inherit;display:flex;align-items:center;gap:6px;min-height:36px;padding:0 14px;border-radius:999px;background:${C.surface};color:${C.primary};border:1px solid ${C.primary};font-weight:600;font-size:13px;line-height:18px;cursor:pointer;flex:0 0 auto;${loading ? 'opacity:.45;' : ''}">${loading ? spinner(C.primary) : ''}${label}</button>`;
const quorum = (state = 'idle', n = 2) => {
  if (state === 'sent') return listRow(circle('check', { tone: 'success' }), `${n} pessoas sem resposta`, `Lembrete enviado no Saqz para ${n} pessoa(s).`, '', { metaLines: 2 });
  if (state === 'failed') return listRow(circle('megaphone'), `${n} pessoas sem resposta`, `<span style="color:${C.error}">Não foi possível concluir. Tente novamente.</span>`, smallBtn('Avisar'), { metaLines: 2 });
  return listRow(circle('megaphone'), `${n} pessoas sem resposta`, 'Encerra 28/07 · 18h00', smallBtn('Avisar', { loading: state === 'loading' }));
};
const WAIT = {
  entry: () => listRow(circle('users'), '3 pedidos para entrar', 'Rafa, Júlia e mais 1', chipWarning('3')),
  monthly: () => listRow(circle('card'), '2 mensalidades a receber', 'R$ 140,00 · AGO'),
  settle: () => listRow(circle('calendar'), 'Acertar o jogo de 21/07', '4 avulsos · R$ 100,00 a receber'),
};
const manage = ({ fresh = false, financeFailed = false } = {}) => section('Gestão',
  listCard([
    listRow(circle('users'), 'Membros e permissões', fresh ? '1 pessoa' : '26 pessoas'),
    listRow(circle('calendar'), 'Jogos e horários', 'Terça e Quinta · 19h30'),
    listRow(circle('link'), 'Convidar por link', 'Link, QR e pedidos de entrada'),
    listRow(circle('card'), 'Caixa do grupo', financeFailed ? '' : fresh ? 'Saldo R$ 0,00' : 'Saldo R$ 380,00'),
  ]));

// ---- minhas cobranças: só o que está em aberto sobe; histórico recolhido -----------------
const chargeLine = (title, due, amount, overdue) =>
  row(12, [
    col(2, [t('ctitle', title, `color:${C.ink}`), t('cmeta', due, `color:${overdue ? C.warningFg : C.sec};${overdue ? 'font-weight:600' : ''}`)], 'flex:1 1 auto;min-width:0'),
    t('ctitle', amount, `color:${C.ink}`),
  ], 'padding:10px 0');
const historyRow = (title, due, amount, status) =>
  row(12, [col(2, [t('ctitle', title, `color:${C.ink}`), t('cmeta', due, `color:${C.sec}`)], 'flex:1 1 auto;min-width:0'), col(4, [t('ctitle', amount, `color:${C.ink};text-align:right`), status], 'align-items:flex-end')], 'padding:12px 16px');
const HISTORY = () => [
  historyRow('Mensalidade · Julho', 'Vencimento 10/07', 'R$ 70,00', chipSuccess('Paga')),
  historyRow('Mensalidade · Junho', 'Vencimento 10/06', 'R$ 70,00', chipNeutral('Isenta')),
  historyRow('Jogo avulso', 'Vencimento 03/06', 'R$ 25,00', chipNeutral('Cancelada')),
];
const historyToggle = (open, n = 3) =>
  `<button type="button" class="support" style="font-family:inherit;background:transparent;border:0;cursor:pointer;font-weight:600;color:${C.primary};min-height:44px;display:flex;align-items:center;justify-content:center;gap:4px;width:100%">${open ? 'Ocultar histórico' : `Ver histórico (${n})`}${icon(open ? 'chevronUp' : 'chevronDown', 18, C.primary)}</button>`;
const ticket = ({ copied = false, single = false, noPix = false } = {}) => card([
  row(8, [t('eyebrow', 'Mensalidade · Agosto', `color:${C.primary};flex:1 1 auto`), chipWarning('Venceu em 10/08')]),
  t('display', single ? 'R$ 70,00' : 'R$ 95,00', `color:${C.ink}`),
  single ? '' : t('support', '2 cobranças em aberto', `color:${C.sec}`),
  single ? '' : col(0, [divider(), chargeLine('Mensalidade · Agosto', 'Venceu em 10/08', 'R$ 70,00', true), divider(), chargeLine('Jogo avulso', 'Vence em 28/08', 'R$ 25,00', false), divider()]),
  noPix ? '' : col(2, [row(8, [icon('card', 16, C.sec), t('support', 'Pix de Lucas Prado', `color:${C.sec}`)]), t('ctitle', 'ceret@volei.com.br', `color:${C.ink};padding-left:24px`)]),
  noPix ? '' : row(0, [copied ? btn(`${icon('check', 18, C.primary)}Chave copiada`, 'secondary') : btn('Copiar chave Pix', 'primary')]),
  t('caption', noPix ? 'O grupo ainda não cadastrou uma chave Pix. Combine o pagamento com o admin — a baixa acontece no caixa do grupo.' : 'Pagamento é manual: copie a chave Pix e pague fora do app — a baixa acontece no caixa do grupo.', `color:${C.sec}`),
], { radius: 20 });
const charges = ({ copied = false, single = false, historyOpen = false, noPix = false } = {}) => section('Minhas cobranças',
  col(8, [ticket({ copied, single, noPix }), historyOpen ? listCard(HISTORY()) : '', historyToggle(historyOpen)]));
const chargesSettled = ({ open = false } = {}) => section('Minhas cobranças',
  col(8, [listCard([listRow(circle('check', { tone: 'success' }), 'Tudo em dia', 'Nenhuma cobrança em aberto', icon(open ? 'chevronUp' : 'chevronDown', 22, C.sec))]), open ? listCard(HISTORY()) : '']));
const sk = (w, hh, r = 8, extra = '') => `<div style="${w ? `width:${w}px;` : 'flex:1 1 0;'}height:${hh}px;border-radius:${r}px;background:${C.border};flex-shrink:0;${extra}"></div>`;
const chargesLoading = () => section('Minhas cobranças', card([sk(90, 12), sk(160, 34), sk(250, 14), row(0, [sk(null, 52, 26)])], { radius: 20 }));
const chargesFailed = () => section('Minhas cobranças', card([
  row(8, [icon('alert', 20, C.error), t('support', 'Não foi possível carregar suas cobranças.', `color:${C.ink};flex:1 1 auto`)]),
  row(0, [btn('Tentar novamente', 'secondary', { size: 'sm' })]),
]));

// ---- onboarding (gestor) e intro do atleta: mesmos textos de hoje, tom ice ---------------
const guide = (title, body, actions) => card([t('ctitle', title, `color:${C.ink};font-size:16px;line-height:22px`), t('support', body, `color:${C.sec}`), col(8, actions.map((a) => row(0, [a])))], { soft: true, radius: 16 });
const onboardingInvite = () => guide('Jogo marcado. Chame a galera.', 'Envie o convite no grupo do WhatsApp. Os atletas entram pelo Saqz e você acompanha as confirmações e a lista de espera.', [btn('Preparar convite para WhatsApp', 'primary', { size: 'sm' }), btn('Acompanhar respostas', 'secondary', { size: 'sm' })]);
const onboardingFinance = () => guide('Primeiro jogo encerrado. E o acerto?', 'Veja quem ainda precisa pagar e registre os recebimentos do jogo. O caixa reúne entradas e saídas para você acompanhar o saldo do grupo.', [btn('Ver acerto do primeiro jogo', 'primary', { size: 'sm' })]);
const athleteIntro = ({ failed = false } = {}) => guide('Sua resposta foi salva. Conheça o Saqz.', 'Acompanhe os próximos jogos, a lista de presença e suas cobranças pelo app. Joga em outros grupos? Você pode participar deles com esta mesma conta.', [
  failed ? row(6, [icon('alert', 16, C.error), t('cmeta', 'Não foi possível abrir o compartilhamento. Tente novamente.', `color:${C.error}`)]) : '',
  btn('Indicar o Saqz a outro grupo', 'primary', { size: 'sm' }), btn('Agora não', 'ghost', { size: 'sm' }),
].filter(Boolean));

// Espera (6b/6e): as MESMAS seções da Home, já parametrizadas por primitivos.
const confirmedSection = () => section('Confirmados', card([row(12, [stack(['Ana Souza', 'Bruna Lima', 'Caio', 'Duda', 'Eva Lima', 'Tiago Moraes'], { max: 6, total: 12 })])]), null);
const bellCard = () => row(8, [icon('bell', 24, C.primary), t('support', 'Avisamos você se abrir vaga até 18h00 de 28/07.', `color:${C.ink}`)], `align-items:flex-start;padding:12px;border-radius:12px;background:${C.soft}`);
const queueRow = (name, pos, self = false) =>
  row(12, [avatar(self ? 'Você' : name, 40, C.soft, C.primary, `inset 0 0 0 1px ${C.border}`), t('body', self ? 'Você' : name, `font-weight:600;color:${self ? C.primary : C.ink};flex:1 1 auto`), t('support', `${pos}º na espera`, `font-weight:600;color:${self ? C.primary : C.sec}`)], `padding:12px 16px;${self ? `background:${C.soft}` : ''}`);
const queueSection = () => section('Como está a lista', listCard([queueRow('Lucas Pereira', 1), queueRow('Bruna Silva', 2, true), queueRow('Tiago Moraes', 3)]));
const upsellCard = () => row(8, [icon('card', 24, C.primary), col(4, [t('body', 'Quer entrar direto nos próximos?', `font-weight:700;color:${C.ink}`), t('support', 'Mensalistas têm vaga garantida. Peça pro admin te incluir.', `color:${C.sec}`)])], `align-items:flex-start;padding:12px;border-radius:12px;background:${C.soft}`);

const homeCourt = () => section('Onde a gente joga', listCard([listRow(circle('pin'), 'CERET — Quadra 2', 'R. Canuto Abreu, s/n · Tatuapé', `<span class="support" style="font-weight:600;color:${C.primary};flex:0 0 auto">Ver no mapa</span>`)]));
const leave = () => row(0, [`<button type="button" class="support" style="font-family:inherit;background:transparent;border:0;cursor:pointer;font-weight:600;color:${C.sec};min-height:48px;display:flex;align-items:center;gap:8px;padding:0 16px">${icon('logOut', 18, C.sec)}Sair do grupo</button>`], 'justify-content:center');

// ---- shell: toast, dobra, montagem ----------------------------------------------------
const toast = (text) => `<div style="position:absolute;left:16px;right:16px;bottom:24px;padding:14px 16px;border-radius:12px;background:${C.ink};box-shadow:0 12px 24px rgba(14,23,56,.18)"><div class="support" style="color:#fff">${text}</div></div>`;
const fold = () => `<div style="position:absolute;left:0;right:0;top:${FOLD}px;border-top:1px dashed ${C.border};pointer-events:none"><span class="caption" style="position:absolute;right:8px;top:2px;font-size:10px;color:${C.ph};background:${C.bg};padding:0 4px">dobra · 800 úteis</span></div>`;

const CSS = `
body{margin:0;background:${C.bg};font-family:Inter,-apple-system,system-ui,sans-serif;color:${C.ink};-webkit-font-smoothing:antialiased}
a{color:${C.primary}}a:hover{color:${C.primaryPressed}}
button{margin:0;text-align:inherit;box-sizing:border-box}
.title{font-weight:700;font-size:22px;line-height:27px;letter-spacing:-.02em}
.subtitle{font-weight:600;font-size:18px;line-height:23px}
.body{font-weight:400;font-size:16px;line-height:23px}
.support{font-weight:400;font-size:14px;line-height:20px}
.label{font-weight:600;font-size:15px;line-height:18px}
.caption{font-weight:400;font-size:12px;line-height:16px}
.eyebrow{font-weight:600;font-size:12px;line-height:16px;letter-spacing:.08em;text-transform:uppercase}
.ctitle{font-weight:700;font-size:14.5px;line-height:18px}
.cmeta{font-weight:400;font-size:12.5px;line-height:16px}
.display{font-family:Anybody,'Arial Black',Impact,system-ui,sans-serif;font-stretch:110%;font-weight:800;font-size:36px;line-height:38px;letter-spacing:-.03em;text-wrap:balance}
`;
const FONT = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Anybody:wdth,wght@50..150,400..900&family=Inter:wght@400;500;600;700;800&display=swap">';

// Tela empilhada sobre o shell: SEM bottom nav. Blocos do topo com gap 12 (identidade +
// faixa), seções com gap 24 — o ritmo da Home, no lugar do 16 uniforme de hoje.
const BOTTOM = 32;
const body = ({ admin = false, name, head = [], sections = [] }) => `<div style="display:flex;flex-direction:column;padding:${INSET}px ${PAD}px ${BOTTOM}px">${topBar({ admin, name })}${col(24, [...head, ...sections], 'padding-top:12px')}</div>`;
const wrap = (inner, h) =>
  `<!doctype html>\n<html lang="pt-BR">\n<head>\n  <meta charset="utf-8">\n  <script src="./support.js"></script>\n</head>\n<body>\n<x-dc>\n<helmet>\n  ${FONT}\n  <style>${CSS}</style>\n</helmet>\n${inner}\n</x-dc>\n<script data-dc-script data-props='{"$preview":{"width":${W},"height":${h}}}'>\nclass Component extends DCLogic {\n  renderVals() {\n    return {};\n  }\n}\n</script>\n</body>\n</html>\n`;

const skeleton = () => body({ name: '', sections: [
  `<div style="display:flex;flex-direction:column;gap:12px;border-radius:20px;background:${C.soft};padding:20px">${sk(96, 12)}${sk(230, 34)}${sk(260, 14)}${sk(220, 14)}${row(8, [sk(null, 52, 26), sk(null, 52, 26)])}${sk(170, 30, 15)}</div>`,
  col(12, [sk(130, 20), listCard([row(12, [sk(44, 44, 10), col(6, [sk(150, 14), sk(200, 12)])], 'padding:12px 16px'), row(12, [sk(44, 44, 10), col(6, [sk(150, 14), sk(200, 12)])], 'padding:12px 16px')])]),
  col(12, [sk(80, 20), listCard([row(12, [sk(40, 40, 20), col(6, [sk(90, 14), sk(220, 12)])], 'padding:12px 16px'), row(12, [sk(40, 40, 20), col(6, [sk(90, 14), sk(180, 12)])], 'padding:12px 16px')])]),
] });
const failure = () => body({ name: '', sections: [
  col(12, [
    t('subtitle', 'Não foi possível carregar o grupo', `color:${C.ink};text-align:center`),
    t('support', 'Confira sua conexão e tente de novo.', `color:${C.sec};text-align:center`),
    row(0, [btn('Tentar novamente', 'primary', { full: false })], 'justify-content:center'),
  ], 'align-items:center;padding:24px;margin-top:200px'),
] });

// ---- estados ---------------------------------------------------------------------
const SCORE = { going: 9, out: 1, pending: 2 };
const M = (o) => body({ name: o.name, head: [o.banner ? photoFailedBanner() : '', ...(o.head || [])], sections: o.sections });
const G = (o) => body({ admin: true, name: o.name, head: [o.banner ? photoFailedBanner() : '', ...(o.head || [])], sections: o.sections });
const PEND = (q = 'idle', n = 2) => [q ? quorum(q, n) : '', WAIT.entry(), WAIT.monthly(), WAIT.settle()].filter(Boolean);
const heroAuto = (h, on = true, opts = {}) => col(12, [h, autoConfirm(on, opts)]);
const tailM = (opts = {}) => [agenda(), mural(), people(), opts.settled === false ? '' : chargesSettled(), leave()].filter(Boolean);

const boards = {
  // Membro — ordem: identidade → hero → (auto) → cobrança em aberto → agenda → mural → galera → histórico → sair.
  Main: { page: 'membro', title: 'Membro · Sem resposta', html: M({ sections: [heroAuto(hero({ kind: 'pending' }), false), ...tailM()] }) },
  MembroConfirmado: { page: 'membro', title: 'Membro · Confirmado (+ toast)', html: M({ sections: [heroAuto(hero({ kind: 'confirmed' })), ...tailM()] }), overlay: toast('Presença confirmada. Bom jogo!') },
  MembroRespondendo: { page: 'membro', title: 'Membro · Respondendo', html: M({ sections: [heroAuto(hero({ kind: 'pending', loading: 'yes', closed: false }), false), ...tailM()] }) },
  MembroNaoVou: { page: 'membro', title: 'Membro · Não vou', html: M({ sections: [heroAuto(hero({ kind: 'declined' }), false), ...tailM()] }) },
  MembroAlterando: { page: 'membro', title: 'Membro · Alterando resposta', html: M({ sections: [heroAuto(hero({ kind: 'editing' })), ...tailM()] }) },
  MembroErroResposta: { page: 'membro', title: 'Membro · Erro ao responder', html: M({ sections: [heroAuto(hero({ kind: 'error' }), false), ...tailM()] }) },
  MembroEncerradas: { page: 'membro', title: 'Membro · Confirmações encerradas', html: M({ sections: [heroAuto(hero({ kind: 'confirmed', closed: true })), ...tailM()] }) },
  MembroEncerradasSemResposta: { page: 'membro', title: 'Membro · Encerradas, sem resposta', html: M({ sections: [heroAuto(hero({ kind: 'pending', closed: true }), false), ...tailM()] }) },
  MembroAvulsoTaxa: { page: 'membro', title: 'Membro avulso · Jogo com taxa', html: M({ sections: [hero({ kind: 'pending', fee: true }), ...tailM()] }) },
  MembroListaDesatualizada: { page: 'membro', title: 'Membro · Lista desatualizada (retry)', html: M({ sections: [heroAuto(hero({ kind: 'confirmed', stale: 'idle' })), ...tailM()] }) },
  MembroMapaFalhou: { page: 'membro', title: 'Membro · Mapa não abriu', html: M({ sections: [heroAuto(hero({ kind: 'confirmed', mapFailed: true })), ...tailM()] }) },
  MembroAutoFalha: { page: 'membro', title: 'Membro · Confirmação automática falhou', html: M({ sections: [col(12, [hero({ kind: 'confirmed' }), autoConfirm(false, { failed: true })]), ...tailM()] }) },
  MembroIntro: { page: 'membro', title: 'Membro novo · Intro após a 1ª resposta', html: M({ sections: [col(12, [hero({ kind: 'confirmed' }), athleteIntro()]), ...tailM()] }) },
  MembroSemJogo: { page: 'membro', title: 'Membro · Sem jogo marcado', html: M({ sections: [hero({ kind: 'empty' }), homeCourt(), mural(), people(), chargesSettled(), leave()] }) },
  MembroCarregando: { page: 'membro', title: 'Carregando', html: skeleton() },
  MembroErro: { page: 'membro', title: 'Falha de carga', html: failure() },
  MembroReserva: { page: 'membro2', title: 'Membro · Lista de espera (jogo lotado)', html: M({ sections: [hero({ kind: 'reserva' }), col(12, [confirmedSection(), bellCard()]), ...tailM()] }) },
  MembroListaAvulso: { page: 'membro2', title: 'Membro avulso · Lista de espera', html: M({ sections: [hero({ kind: 'avulso' }), col(12, [t('support', '2º na lista · 9 confirmados', `color:${C.sec}`), queueSection(), upsellCard()]), ...tailM()] }) },
  MembroDeve: { page: 'membro2', title: 'Membro · Deve (vencida + a vencer)', html: M({ sections: [heroAuto(hero({ kind: 'confirmed' })), charges(), ...tailM({ settled: false })] }) },
  MembroChaveCopiada: { page: 'membro2', title: 'Membro · Chave Pix copiada', html: M({ sections: [heroAuto(hero({ kind: 'confirmed' })), charges({ copied: true }), ...tailM({ settled: false })] }), overlay: toast('Chave copiada. Depois de pagar, o admin dá baixa.') },
  MembroDeveUma: { page: 'membro2', title: 'Membro · Uma cobrança só', html: M({ sections: [heroAuto(hero({ kind: 'confirmed' })), charges({ single: true }), ...tailM({ settled: false })] }) },
  MembroSemPix: { page: 'membro2', title: 'Membro · Deve e o grupo não tem Pix', html: M({ sections: [heroAuto(hero({ kind: 'confirmed' })), charges({ single: true, noPix: true }), ...tailM({ settled: false })] }) },
  MembroHistorico: { page: 'membro2', title: 'Membro · Histórico aberto', html: M({ sections: [heroAuto(hero({ kind: 'confirmed' })), charges({ historyOpen: true }), ...tailM({ settled: false })] }) },
  MembroCobrancasCarregando: { page: 'membro2', title: 'Membro · Cobranças carregando', html: M({ sections: [heroAuto(hero({ kind: 'confirmed' })), chargesLoading(), ...tailM({ settled: false })] }) },
  MembroNomeLongo: { page: 'membro2', title: 'Teste · Nome de 40 caracteres', html: M({ name: 'Vôlei Misto dos Amigos do CERET Tatuapé', sections: [heroAuto(hero({ kind: 'pending' }), false), ...tailM()] }) },
  MembroCobrancasFalha: { page: 'membro2', title: 'Membro · Cobranças com falha', html: M({ sections: [heroAuto(hero({ kind: 'confirmed' })), chargesFailed(), ...tailM({ settled: false })] }) },
  // Gestor — identidade → hero com placar → (cobrança própria) → esperando você → agenda → mural → gestão.
  GestorPendencias: { page: 'gestor', title: 'Gestor (dono) · Pendências, sem resposta', html: G({ sections: [hero({ kind: 'pending', admin: true, score: SCORE }), waiting(PEND()), agenda({ admin: true, more: 3 }), mural(), manage()] }) },
  GestorConfirmado: { page: 'gestor', title: 'Gestor · Confirmado', html: G({ sections: [heroAuto(hero({ kind: 'confirmed', admin: true, score: { going: 10, out: 1, pending: 1 } })), waiting(PEND('idle', 1)), agenda({ admin: true }), mural(), manage()] }) },
  GestorAvisando: { page: 'gestor', title: 'Gestor · Avisando…', html: G({ sections: [hero({ kind: 'confirmed', admin: true, score: SCORE }), waiting(PEND('loading')), agenda({ admin: true }), mural(), manage()] }) },
  GestorAvisado: { page: 'gestor', title: 'Gestor · Lembrete enviado', html: G({ sections: [hero({ kind: 'confirmed', admin: true, score: SCORE }), waiting(PEND('sent')), agenda({ admin: true }), mural(), manage()] }) },
  GestorAvisoFalhou: { page: 'gestor', title: 'Gestor · Falha ao avisar', html: G({ sections: [hero({ kind: 'confirmed', admin: true, score: SCORE }), waiting(PEND('failed')), agenda({ admin: true }), mural(), manage()] }) },
  GestorEncerradas: { page: 'gestor', title: 'Gestor · Encerradas (linha "Avisar" some)', html: G({ sections: [hero({ kind: 'confirmed', admin: true, closed: true, score: { going: 10, out: 2, pending: 0 } }), waiting(PEND(null)), agenda({ admin: true }), mural(), manage()] }) },
  GestorSemPendencias: { page: 'gestor', title: 'Admin (não dono) · Sem pendências', html: G({ sections: [hero({ kind: 'confirmed', admin: true, score: { going: 12, out: 2, pending: 0 } }), agenda({ admin: true }), mural(), manage(), leave()] }) },
  GestorTambemDeve: { page: 'gestor', title: 'Gestor · Também deve', html: G({ sections: [hero({ kind: 'pending', admin: true, score: SCORE }), charges({ single: true }), waiting(PEND()), agenda({ admin: true }), mural(), manage()] }) },
  GestorFinancasFalha: { page: 'gestor', title: 'Gestor · Finanças não carregaram', html: G({ sections: [hero({ kind: 'pending', admin: true, score: SCORE }), waiting([quorum('idle'), WAIT.entry()]), agenda({ admin: true }), mural(), manage({ financeFailed: true })] }) },
  GestorSemJogo: { page: 'gestor', title: 'Gestor · Sem jogo marcado', html: G({ sections: [hero({ kind: 'emptyAdmin' }), waiting([WAIT.entry(), WAIT.monthly()]), homeCourt(), mural(), manage()] }) },
  GestorGrupoNovo: { page: 'gestor', title: 'Dono · Grupo recém-criado (foto falhou)', html: body({ admin: true, head: [photoFailedBanner()], sections: [hero({ kind: 'emptyAdmin', first: true }), homeCourt(), mural({ notice: false }), manage({ fresh: true })] }) },
  GestorOnboardingConvite: { page: 'gestor', title: 'Dono · 1º jogo marcado (guia de convite)', html: G({ sections: [col(12, [hero({ kind: 'pending', admin: true, score: { going: 0, out: 0, pending: 1 } }), onboardingInvite()]), agenda({ admin: true }), mural({ notice: false }), manage()] }) },
  GestorOnboardingAcerto: { page: 'gestor', title: 'Dono · 1º jogo encerrado (guia de acerto)', html: G({ sections: [col(12, [hero({ kind: 'emptyAdmin' }), onboardingFinance()]), homeCourt(), mural(), manage()] }) },
};

// "Hoje": os prints reais da tela atual, para comparar. Só entram se já foram enviados.
const shot = (blob, w, h) => `<div style="width:${w}px;height:${h}px;background:${C.bg}"><img src="${blob}" alt="Tela atual" style="display:block;width:${w}px;height:${h}px"></div>`;

// ---- alturas + layout do canvas ---------------------------------------------------
const DEFAULT_H = 1500;
const heightOf = (n) => Math.max(FOLD, Math.ceil(MEASURED[n] ?? DEFAULT_H));
const root = (n, b) => {
  const h = heightOf(n);
  return { h, html: wrap(`<div style="position:relative;overflow:hidden;width:${W}px;height:${h}px;background:${C.bg}">${b.html}${b.overlay || ''}${h > FOLD ? fold() : ''}</div>`, h) };
};

// GAP_Y folgado: a faixa entre as duas linhas de artboards abriga as notas da segunda.
const GAP_X = 80, GAP_Y = 560;
const canvasBoards = {}, order = [], notes = {};
const lay = (page, names, y0, x0 = 0) => {
  let x = x0, maxH = 0;
  for (const n of names) {
    const { h, html } = root(n, boards[n]);
    writeFileSync(join(out, `${n}.dc.html`), html);
    canvasBoards[`${n}.dc.html`] = { x, y: y0, w: W, h, title: boards[n].title, page };
    order.push(`${n}.dc.html`);
    x += W + GAP_X; maxH = Math.max(maxH, h);
  }
  return y0 + maxH + GAP_Y;
};
const byPage = (p) => Object.keys(boards).filter((n) => boards[n].page === p);
const Y0 = 0;
let y = lay('membro', byPage('membro'), Y0);
lay('membro', byPage('membro2'), y);
for (const n of byPage('membro2')) canvasBoards[`${n}.dc.html`].page = 'membro';
lay('gestor', byPage('gestor'), Y0);

if (BLOBS.member && BLOBS.admin) {
  const hoje = { HojeMembro: { blob: BLOBS.member, w: 411, h: 2000, title: 'Hoje · Membro (print real, 2000dp e ainda cortado)' }, HojeAdmin: { blob: BLOBS.admin, w: 411, h: 1400, title: 'Hoje · Admin (print real — estado que o app nem produz)' } };
  let x = 0;
  for (const [n, s] of Object.entries(hoje)) {
    writeFileSync(join(out, `${n}.dc.html`), wrap(shot(s.blob, s.w, s.h), s.h).replace(`"width":${W}`, `"width":${s.w}`));
    canvasBoards[`${n}.dc.html`] = { x, y: 0, w: s.w, h: s.h, title: s.title, page: 'hoje' };
    order.push(`${n}.dc.html`);
    x += s.w + GAP_X;
  }
}

const note = (id, page, x, yy, w, text, color) => { notes[id] = { x, y: yy, w, maxH: 360, text, page, ...(color ? { color } : {}) }; };
const NOTE_Y = -420, NW = 430, NS = 470;
note('m-hero', 'membro', 0, NOTE_Y, NW, 'UM HERO, NÃO TRÊS CARDS\nHoje o próximo jogo ocupa 3 cards e ~610dp ("PRÓXIMO JOGO", "Você vai jogar?", "Próximo jogo 9/3/4") e repete "9 de 12". Aqui vira um bloco azul de ~300dp, o mesmo da Início: data em Anybody, "Vou" lima, e depois de responder os botões somem — fica "Sua presença está confirmada. [Alterar]". A Quadra some como card: o endereço DO JOGO entra no hero com "Ver no mapa" (hoje o mapa usa a quadra padrão do grupo, que pode ser outra).');
note('m-topo', 'membro', NS, NOTE_Y, NW, 'TOPO ENXUTO\nO nome volta a ser o título da barra (uso canônico do SaqzTopAppBar, 56dp) e aparece UMA vez. "Editar" ocupa o slot de ações da barra, que a tela não usa hoje — somem os dois botões do cabeçalho, incluindo o que quebrava em 2 linhas.\n\nO card de cabeçalho inteiro SAI: emblema, bola d\'água, chips e o nome repetido. Bairro/modalidade/nível é dado de descoberta — quem já está no grupo não precisa ver isso em toda visita; fica em Editar grupo. "26 pessoas" vive na linha Galera e os dias, na agenda.\n\nGanho: o hero começa em 112dp em vez de 228dp (hoje) ou 184dp (v2).', 'blue');
note('m-ordem', 'membro', NS * 2, NOTE_Y, NW, 'ORDEM POR URGÊNCIA\n1) responder o jogo · 2) pagar o que deve (só sobe se houver em aberto) · 3) agenda · 4) mural · 5) galera · 6) histórico · sair. Ritmo da Início: 24 entre seções, 12 dentro. Tudo que é ação do membro cabe acima da dobra no estado mais comum. A dobra marcada é 800: a tela de 844 menos a status bar, que é do sistema e não entra no artboard.');
note('m-agenda', 'membro', NS * 3, NOTE_Y, NW, 'PRÓXIMOS JOGOS — A PORTA DA AGENDA\nA lista de jogos já é baixada e jogada fora. Hoje o atalho "Jogos" leva o MEMBRO a uma tela que o backend nega (403): ele não tem agenda. A lista entra na tela e o atalho some. O chip com a minha resposta por jogo pede um campo aditivo no backend (ownAttendance na listagem). Toque abre o jogo. "Ver mais" expande no lugar.', 'blue');
note('m-mural', 'membro', NS * 4, NOTE_Y, NW, 'MURAL = AVISOS + CONVERSA\nFunde os 3 atalhos e o card "Aviso recente" (que hoje nem é clicável). O último aviso vira a prévia da linha Avisos. Mesmo vocabulário das listas da Início: círculo ice + título + meta + seta.');
note('m-galera', 'membro', NS * 5, NOTE_Y, NW, 'GALERA\nEm produção a prévia de membros e a contagem NUNCA são preenchidas (só existem no dado de preview). Vira uma linha: pilha de avatares + "26 pessoas" + os primeiros nomes (1 chamada que já existe).\n\nO convite SAI da visão do membro: hoje ele leva a um endpoint só de admin (403), e o link do convite é guardado só como hash — não existe link para o membro ler. "Membro convida" vira ticket de produto à parte.', 'orange');
note('m-cobranca', 'membro', 0, y + NOTE_Y, NW, 'COBRANÇA: SÓ O QUE ESTÁ EM ABERTO SOBE\nHoje são ~680dp (pendentes + histórico + Pix + ajuda) antes de tudo. Aqui: um ticket como o da Início — total em display, as linhas em aberto, Pix e UM botão azul; "Chave copiada" + toast. Histórico recolhido em "Ver histórico". Sem dívida, a seção desce para o fim como "Tudo em dia". 3 das 7 entradas nesta tela chegam por cobrança: por isso ela fica logo abaixo do hero.');
note('m-espera', 'membro', NS * 1, y + NOTE_Y, NW, 'ESPERA\nHoje é uma linha de texto com os dois botões habilitados. Passa a ser o mesmo desenho da Início (chip + caixa + "Ver o jogo"/"Sair da lista" + fila) — o dado já é baixado. E ganha as duas guardas que só a Início tem: não deixar tocar "Vou" já estando na fila, e prever a fila no otimista.');
note('g-hero', 'gestor', 0, NOTE_Y, NW, 'GESTOR\nO print de hoje mostra um estado que o app nem produz; o admin real empilha MAIS cards que o membro. Aqui o placar mora dentro do hero (toque abre o jogo) e o gestor responde no mesmo lugar que todo mundo — o hero é o mesmo do membro, só troca avatares por placar.');
note('g-espera', 'gestor', NS, NOTE_Y, NW, 'ESPERANDO VOCÊ — NESTE GRUPO\nO bloco da Início, escopado ao grupo. A 1ª linha é o quórum: "2 pessoas sem resposta · [Avisar]". O retorno fica NA LINHA (avisando → "Lembrete enviado no Saqz para 2 pessoa(s)." → falha), não em textos soltos na tela. Com confirmações encerradas ou ninguém pendente a linha some — hoje o botão fica ativo e o toque não faz nada. Depois: pedidos para entrar (com nomes), mensalidades a receber, jogo a acertar. Duas das três saem de dado que já é baixado.', 'blue');
note('g-decisoes', 'gestor', NS * 3, NOTE_Y, NW, 'DECISÕES TOMADAS (reversíveis, cada uma isolada num ticket)\n1) Convite do membro: sai (o link é guardado só como hash; vira ticket de produto).\n2) "Mensalidades a receber": só o mês corrente, como a Início. O quadro completo mora no Caixa.\n3) Agenda com chip da minha resposta: campo aditivo na listagem de jogos (ticket A).\n4) Linha do Caixa mostra só o saldo; o roteiro e2e que confere o texto antigo muda no mesmo PR.', 'orange');
note('g-gestao', 'gestor', NS * 2, NOTE_Y, NW, 'GESTÃO\nQuatro portas, um ícone cada (hoje Membros e Convidar usam o mesmo). A meta passa a dizer algo — contagem, dias e saldo, que hoje chegam vazios em produção. "Marcar jogo" mora no cabeçalho da agenda; sem jogo, sobe para dentro do hero com "Convidar", como na Início. Dono não vê "Sair do grupo"; admin não dono vê.');

writeFileSync(join(out, 'canvas.json'), JSON.stringify({
  v: 3,
  createdOnFiles: { v: 1, at: '2026-09-17T03:40:00Z' },
  title: 'Saqz · Detalhe do grupo (redesenho)',
  launch: { view: 'canvas', page: 'membro' },
  pages: [{ id: 'membro', name: 'Membro' }, { id: 'gestor', name: 'Gestor / dono' }, ...(BLOBS.member && BLOBS.admin ? [{ id: 'hoje', name: 'Hoje (antes)' }] : [])],
  boards: canvasBoards,
  order,
  notes,
  designSystems: [],
}, null, 2));

// Página de medição: todos os boards com altura automática, para o measure.mjs ler.
const measureHtml = `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">${FONT}<style>${CSS}</style></head><body>${Object.entries(boards).map(([n, b]) => `<div data-board="${n}" style="position:relative;width:${W}px;background:${C.bg}">${b.html}</div>`).join('')}<script>document.fonts.ready.then(()=>{const o={};document.querySelectorAll('[data-board]').forEach(e=>o[e.dataset.board]=Math.ceil(e.getBoundingClientRect().height));const p=document.createElement('pre');p.id='heights';p.textContent=JSON.stringify(o);document.body.appendChild(p);});</script></body></html>`;
writeFileSync(join(here, 'measure.html'), measureHtml);
console.log(`ok: ${order.length} artboards, ${Object.keys(notes).length} notas, alturas ${Object.keys(MEASURED).length ? 'medidas' : 'estimadas'}`);

// Prévias locais (HTML puro, sem o runtime do canvas) para conferir com screenshot headless.
mkdirSync(join(here, 'preview'), { recursive: true });
for (const [n, b] of Object.entries(boards)) {
  const h = heightOf(n);
  writeFileSync(join(here, 'preview', `${n}.html`), `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">${FONT}<style>${CSS}</style></head><body><div style="position:relative;overflow:hidden;width:${W}px;height:${h}px;background:${C.bg}">${b.html}${b.overlay || ''}${h > FOLD ? fold() : ''}</div></body></html>`);
}
