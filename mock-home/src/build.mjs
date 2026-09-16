// Gera os artboards da Home do Saqz (todos os estados) a partir dos tokens reais do
// design system (SaqzColorTokens / SaqzTypography / SaqzMetrics) e da proposta:
// hero azul, CTA lima, data em Anybody. Saída: *.dc.html + canvas.json nesta pasta.
import { writeFileSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));

// ---- tokens (hex a hex do app) -------------------------------------------------
const C = {
  bg: '#F5F5F7', surface: '#FFFFFF', soft: '#F4F8FB', primary: '#0638DF', primaryPressed: '#052BB3',
  accent: '#C7F300', ink: '#0E1738', sec: '#667085', ph: '#98A2B3', border: '#D8DDE8',
  success: '#17B26A', warning: '#F5A623', warningFg: '#B26B00', error: '#E5484D',
  chrome: 'rgba(255,255,255,.96)',
};
const W = 390, NAV = 68, INSET = 44, PAD = 16;

// ---- ícones (Lucide, os mesmos do SaqzIcons) -----------------------------------
const P = {
  house: '<path d="M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8"/><path d="M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
  users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
  user: '<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
  card: '<rect width="20" height="14" x="2" y="5" rx="2"/><line x1="2" x2="22" y1="10" y2="10"/>',
  chevron: '<path d="m9 18 6-6-6-6"/>',
  plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
  mail: '<rect width="20" height="16" x="2" y="4" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>',
  calendar: '<path d="M8 2v4"/><path d="M16 2v4"/><rect width="18" height="18" x="3" y="4" rx="2"/><path d="M3 10h18"/>',
  clock: '<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>',
  bell: '<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>',
  alert: '<circle cx="12" cy="12" r="10"/><line x1="12" x2="12" y1="8" y2="12"/><line x1="12" x2="12.01" y1="16" y2="16"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
};
const icon = (n, s = 24, c = 'currentColor', extra = '') =>
  `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="${c}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex:0 0 auto;${extra}">${P[n]}</svg>`;

// Bola do saqz_volleyball.xml (viewport 1254), a mesma do SaqzSpinner: gomos curvos,
// não os traços retos do material_sports_volleyball.
const BALL = [
  'M 597,108 C 493,207 449,406 583,580 C 533,663 469,727 397,767 C 263,638 211,410 279,252 C 294,230 313,211 333,196 C 416,145 503,118 597,108 Z',
  'M 649,108 C 854,108 1037,248 1113,441 C 940,284 741,222 548,265 C 567,196 603,144 649,108 Z',
  'M 542,309 C 764,255 1001,350 1138,531 C 1161,607 1155,701 1125,772 C 1009,584 802,480 590,518 C 557,457 539,382 542,309 Z',
  'M 634,552 C 733,535 842,561 928,610 C 861,854 601,1047 317,1048 C 259,1003 211,945 176,878 C 388,858 553,744 634,552 Z',
  'M 962,632 C 1032,690 1083,757 1107,826 C 1009,1040 826,1146 627,1146 C 532,1146 443,1122 365,1078 C 674,1050 897,852 962,632 Z',
  'M 277,245 C 111,400 52,642 157,844 C 228,834 296,815 360,784 C 232,649 206,445 277,245 Z',
];
const ball = (size, color, opacity, extra) =>
  `<svg viewBox="0 0 1254 1254" width="${size}" height="${size}" style="position:absolute;opacity:${opacity};pointer-events:none;${extra}">${BALL.map((d) => `<path d="${d}" fill="${color}"/>`).join('')}</svg>`;

// Símbolo do logo (landing-page/assets/saqz-logo.svg): só os subpaths do quadrado, sem as letras.
const logoSvg = readFileSync(join(here, 'saqz-logo.svg'), 'utf8');
const pathD = (id) => logoSvg.match(new RegExp(`id="${id}" d="([^"]+)"`))[1];
const markOnly = (d) => d.split(/(?=M )/).filter((sub) => parseFloat(sub.slice(2)) < 380).join('');
const LOGO = { blue: markOnly(pathD('blue')), white: markOnly(pathD('white-details')), lime: markOnly(pathD('green-accent')) };
const mark = (h = 30) =>
  `<svg width="${h}" height="${h}" viewBox="12 0 358 360" style="flex:0 0 auto"><path d="${LOGO.blue}" fill="${C.primary}" fill-rule="evenodd"/><path d="${LOGO.white}" fill="#FFFFFF" fill-rule="evenodd"/><path d="${LOGO.lime}" fill="${C.accent}" fill-rule="evenodd"/></svg>`;

// ---- primitivos ------------------------------------------------------------------
const col = (gap, kids, extra = '') => `<div style="display:flex;flex-direction:column;gap:${gap}px;${extra}">${kids.join('')}</div>`;
const row = (gap, kids, extra = '') => `<div style="display:flex;flex-direction:row;align-items:center;gap:${gap}px;${extra}">${kids.join('')}</div>`;
const t = (cls, text, extra = '') => `<div class="${cls}" style="${extra}">${text}</div>`;

const BTN = {
  lime: `background:${C.accent};color:${C.ink};border:0;font-weight:700`,
  outlineW: `background:transparent;color:#FFFFFF;border:1.5px solid rgba(255,255,255,.45);font-weight:600`,
  solidW: `background:#FFFFFF;color:${C.ink};border:0;font-weight:600`,
  ghostW: `background:transparent;color:#FFFFFF;border:0;font-weight:600`,
  primary: `background:${C.primary};color:#FFFFFF;border:0;font-weight:600`,
  secondary: `background:${C.surface};color:${C.primary};border:1px solid ${C.primary};font-weight:600`,
  ghost: `background:transparent;color:${C.primary};border:0;font-weight:600`,
};
// Pílula em toda variante (SaqzButton): md 52/20px/15px, sm 44/16px/14px.
const btn = (label, kind, { size = 'md', full = true, disabled = false, extra = '' } = {}) => {
  const h = size === 'md' ? 52 : 44, pad = size === 'md' ? 20 : 16, fs = size === 'md' ? 15 : 14;
  return `<div style="display:flex;align-items:center;justify-content:center;min-height:${h}px;padding:0 ${pad}px;border-radius:999px;font-size:${fs}px;line-height:18px;white-space:nowrap;${BTN[kind]};${full ? 'flex:1 1 0;' : ''}${disabled ? 'opacity:.45;' : ''}${extra}">${label}</div>`;
};

// SaqzStatusChip: 10×5, caption 600, ponto 6.
const chip = (text, fg, bg, dot = null) =>
  `<div style="display:inline-flex;align-items:center;gap:6px;padding:5px 10px;border-radius:999px;background:${bg};color:${fg};font-weight:600;font-size:12px;line-height:16px;white-space:nowrap;flex:0 0 auto">${dot ? `<span style="width:6px;height:6px;border-radius:50%;background:${dot};flex:0 0 auto"></span>` : ''}${text}</div>`;
const chipWarning = (text) => chip(text, C.warningFg, 'rgba(245,166,35,.14)', C.warningFg);
const chipBrand = (text) => chip(text, C.primary, 'rgba(6,56,223,.08)');
const chipOnBlue = (text, dot = C.accent) => chip(text, '#FFFFFF', 'rgba(255,255,255,.14)', dot);

const initials = (name) => {
  const p = name.trim().split(/\s+/);
  return (p.length === 1 ? p[0].slice(0, 1) : p[0][0] + p[p.length - 1][0]).toUpperCase();
};
// SaqzAvatar: círculo, anel 1px por dentro, iniciais 600 a 0,36× do diâmetro.
const avatar = (name, size, bg, fg, ring, extra = '') =>
  `<div style="width:${size}px;height:${size}px;border-radius:50%;background:${bg};color:${fg};display:flex;align-items:center;justify-content:center;font-weight:600;font-size:${(size * 0.36).toFixed(1)}px;line-height:1;box-shadow:${ring};flex:0 0 auto;${extra}">${initials(name)}</div>`;
// SaqzAvatarStack: 30px, sobreposição -8, máximo 3, "+N" no círculo mais forte.
const stack = (names, onBlue = false, max = 3) => {
  const shown = names.slice(0, max), over = names.length - shown.length;
  const ring = onBlue ? `0 0 0 2px ${C.primary}` : `inset 0 0 0 1px ${C.border}`;
  const kids = shown.map((n, i) => avatar(n, 30, C.surface, C.ink, ring, i ? 'margin-left:-8px' : ''));
  if (over > 0) kids.push(`<div style="width:30px;height:30px;border-radius:50%;margin-left:-8px;background:${onBlue ? C.accent : C.primary};color:${onBlue ? C.ink : '#fff'};display:flex;align-items:center;justify-content:center;font-weight:600;font-size:10.8px;line-height:1;flex:0 0 auto;${onBlue ? `box-shadow:0 0 0 2px ${C.primary}` : ''}">+${over}</div>`);
  return row(0, kids, 'flex:0 0 auto');
};

const divider = (color = C.border) => `<div style="height:1px;background:${color};flex:0 0 auto"></div>`;
const card = (kids, { soft = false, flush = false, radius = 12, extra = '' } = {}) =>
  `<div style="display:flex;flex-direction:column;gap:${flush ? 0 : 12}px;border-radius:${radius}px;background:${soft ? C.soft : C.surface};${soft ? '' : `border:1px solid ${C.border};`}${flush ? '' : 'padding:16px;'}${extra}">${kids.join('')}</div>`;
const sectionHeader = (title, action = null) =>
  row(8, [t('subtitle', title, `color:${C.ink};flex:1 1 auto`), action ? t('support', action, `font-weight:600;color:${C.primary};padding:6px 8px`) : ''], 'min-height:23px');

// ---- cabeçalho -------------------------------------------------------------------
// Logo antes da saudação; o sino (Notificações, hoje só pelo Perfil) ocupa a direita.
// Alvo de toque de 44 com margem negativa para a linha continuar na altura do título.
const bell = (unread) =>
  `<div style="position:relative;width:44px;height:44px;margin:-10px -10px -10px 0;display:flex;align-items:center;justify-content:center;flex:0 0 auto">${icon('bell', 24, C.ink)}${unread ? `<span style="position:absolute;top:9px;right:11px;width:9px;height:9px;border-radius:50%;background:${C.primary};box-shadow:0 0 0 2px ${C.bg}"></span>` : ''}</div>`;
const header = (subtitle = null, { unread = false } = {}) =>
  col(4, [
    row(10, [mark(30), t('title', 'Fala, Bruna!', `font-weight:800;color:${C.ink};flex:1 1 auto`), bell(unread)]),
    subtitle ? t('support', subtitle, `color:${C.sec};padding-left:40px`) : '',
  ]);

// ---- hero azul -------------------------------------------------------------------
const kicker = (text, trailing) =>
  row(8, [
    row(9, [`<span style="width:8px;height:8px;border-radius:50%;background:${C.accent};box-shadow:0 0 0 4px rgba(199,243,0,.18);flex:0 0 auto"></span>`, t('eyebrow', text, 'color:rgba(255,255,255,.82)')], 'flex:1 1 auto'),
    trailing || '',
  ]);
const heroLine = (ic, text, color = 'rgba(255,255,255,.9)') =>
  row(6, [icon(ic, 14, color), t('support', text, `font-weight:600;font-size:13px;line-height:18px;color:${color}`)]);
const deadlineOpen = () => heroLine('clock', 'As confirmações encerram hoje às 18h.');
const deadlineClosed = () => heroLine('clock', 'Confirmações encerradas.', 'rgba(255,255,255,.7)');

// "Vou" é sempre o CTA lima (ação principal); "Não vou" fica em contorno. Quando há
// resposta marcada (modo alterar), a marcada leva o check e a outra vira contorno.
const rsvpRow = (selected = null, { size = 'md', disabled = false } = {}) => {
  const check = (dark) => icon('check', 16, dark ? C.ink : C.ink, 'margin-right:6px');
  return row(8, [
    btn(`${selected === 'yes' ? check(true) : ''}Vou`, selected === 'no' ? 'outlineW' : 'lime', { size, disabled }),
    btn(`${selected === 'no' ? check(true) : ''}Não vou`, selected === 'no' ? 'solidW' : 'outlineW', { size, disabled }),
  ]);
};
const answered = (status, { changeEnabled = true } = {}) => {
  const confirmed = status === 'yes';
  return row(8, [
    `<span style="width:10px;height:10px;border-radius:50%;background:${confirmed ? C.success : 'rgba(255,255,255,.45)'};flex:0 0 auto;margin-left:4px"></span>`,
    t('support', confirmed ? 'Você está confirmado.' : 'Tudo bem, na próxima. Sua vaga já foi liberada.', 'font-weight:600;color:#fff;flex:1 1 auto'),
    btn('Alterar', 'ghostW', { size: 'sm', full: false, disabled: !changeEnabled }),
  ], 'background:rgba(255,255,255,.12);border-radius:12px;padding:4px 4px 4px 12px');
};
const editing = () => col(8, [
  rsvpRow('yes', { size: 'sm' }),
  row(0, [btn('Cancelar', 'ghostW', { size: 'sm', full: false })], 'justify-content:flex-end'),
]);
const responseError = () =>
  row(6, [icon('alert', 16, '#FFD6D8'), t('support', 'Não foi possível atualizar sua presença. Tente novamente.', 'font-size:13px;line-height:18px;font-weight:500;color:#FFD6D8')]);

const rosterRow = (summary, spots = null) =>
  row(12, [
    stack(['Ana Souza', 'Bruna Lima', 'Caio', 'Duda'], true),
    t('support', summary, 'font-weight:600;color:rgba(255,255,255,.88);flex:1 1 auto'),
    spots ? chipOnBlue(spots) : '',
  ]);

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
  btn('Ver o jogo', 'solidW', { size: 'sm' }),
  btn(kind === 'reserva' ? 'Sair da lista de espera' : 'Sair da lista', 'ghostW', { size: 'sm' }),
]);

const scoreboard = ({ going, out, pending }) => {
  const cell = (n, label, color) => col(4, [
    t('display', String(n), `font-size:28px;line-height:28px;color:${color};text-align:center`),
    t('caption', label, 'color:rgba(255,255,255,.72);text-align:center'),
  ], 'flex:1 1 0;padding:12px 4px');
  const vdiv = '<div style="width:1px;background:rgba(255,255,255,.18);flex:0 0 auto;align-self:stretch"></div>';
  return row(0, [cell(going, 'Vão', C.accent), vdiv, cell(out, 'Não vão', 'rgba(255,255,255,.7)'), vdiv, cell(pending, 'Sem resposta', C.warning)], 'background:rgba(255,255,255,.10);border-radius:12px');
};

/**
 * O bloco de destaque. `kind`: pending | pendingClosed | confirmed | declined | editing |
 * error | reserva | avulso | empty | emptyAdmin. `admin` acrescenta o placar e troca o
 * chip do grupo pelo prazo.
 */
const hero = ({ kind, admin = false, closed = false, score = null }) => {
  const kids = [];
  const trailing = admin
    ? chipOnBlue(closed ? 'Encerrou 28/07 · 18h' : 'Encerra 28/07 · 18h', null)
    : chip('Vôlei do CERET', '#FFFFFF', 'rgba(255,255,255,.14)');
  const isEmpty = kind === 'empty' || kind === 'emptyAdmin';
  kids.push(kicker('Próximo jogo', isEmpty ? '' : trailing));
  if (isEmpty) {
    kids.push(t('display', 'Sem jogo marcado', 'color:#fff'));
    kids.push(t('support', 'Quando a galera marcar, aparece aqui e você confirma em um toque.', 'font-weight:500;color:rgba(255,255,255,.82);max-width:280px'));
    kids.push(kind === 'emptyAdmin'
      ? row(8, [btn('Marcar jogo', 'lime'), btn('Convidar', 'outlineW')])
      : row(8, [btn('Ver meus grupos', 'outlineW')]));
  } else {
    kids.push(t('display', 'Terça, 19h30', 'color:#fff'));
    kids.push(t('support', '28 de julho · CERET — Quadra 2 · Tatuapé', 'font-weight:500;color:rgba(255,255,255,.82)'));
    if (admin) kids.push(scoreboard(score));
    kids.push(closed ? deadlineClosed() : deadlineOpen());
    switch (kind) {
      case 'pending': kids.push(rsvpRow(null, { disabled: closed })); break;
      case 'confirmed': kids.push(answered('yes', { changeEnabled: !closed })); break;
      case 'declined': kids.push(answered('no', { changeEnabled: !closed })); break;
      case 'editing': kids.push(editing()); break;
      case 'error': kids.push(rsvpRow()); kids.push(responseError()); break;
      case 'reserva': kids.push(row(0, [chipOnBlue('Lista de espera · 1º')])); kids.push(waitBox('reserva')); kids.push(waitActions('reserva')); break;
      case 'avulso': kids.push(row(0, [chipOnBlue('Lista de espera')])); kids.push(waitBox('avulso')); kids.push(waitActions('avulso')); break;
    }
    if (!admin && kind !== 'reserva' && kind !== 'avulso') {
      kids.push(rosterRow('9 de 12 confirmados', kind === 'pending' && !closed ? 'Restam 3 vagas' : null));
    }
  }
  return `<div style="position:relative;overflow:hidden;display:flex;flex-direction:column;gap:12px;border-radius:20px;background:${C.primary};padding:20px;color:#fff">${ball(210, '#FFFFFF', 0.2, 'right:-58px;top:-62px')}${kids.join('')}</div>`;
};

// ---- seções claras (vocabulário atual do app) -----------------------------------
const groupRow = (name, meta, isAdmin = false) =>
  row(12, [
    `<div style="width:42px;height:42px;border-radius:12px;background:${C.soft};display:flex;align-items:center;justify-content:center;flex:0 0 auto"><span class="label" style="color:${C.primary}">${initials(name)}</span></div>`,
    // Chip ao lado do nome, meta embaixo: no app o chip espreme a coluna e a meta quebra.
    col(2, [row(8, [t('ctitle', name, `color:${C.ink}`), isAdmin ? chipBrand('Administrador') : '']), t('cmeta', meta, `color:${C.sec}`)], 'flex:1 1 auto'),
    icon('chevron', 24, C.sec),
  ], 'min-height:48px;padding:12px 16px');
const groups = (adminOf = []) => col(12, [
  sectionHeader('Seus grupos', 'Ver todos'),
  card([
    groupRow('Vôlei do CERET', '26 pessoas · 18 jogos', adminOf.includes('ceret')),
    divider(),
    groupRow('Vôlei Pacaembu', '14 pessoas · 6 jogos', adminOf.includes('pacaembu')),
  ], { flush: true }),
]);

const waitingRow = (ic, title, meta, trailing) =>
  row(12, [
    `<div style="width:40px;height:40px;border-radius:50%;background:${C.soft};display:flex;align-items:center;justify-content:center;flex:0 0 auto">${icon(ic, 24, C.sec)}</div>`,
    col(2, [t('ctitle', title, `color:${C.ink}`), t('cmeta', meta, `color:${C.sec}`)], 'flex:1 1 auto'),
    trailing,
  ], 'padding:12px 16px');
const waiting = (items) => col(12, [
  sectionHeader('Esperando você'),
  card(items.flatMap((it, i) => (i ? [divider(), it] : [it])), { flush: true }),
]);
const WAIT = {
  entry: (n, group) => waitingRow('users', `${n} pedidos para entrar`, group, chipWarning(String(n))),
  monthly: () => waitingRow('card', '2 mensalidades a receber', 'R$ 640,00 · JUL', icon('chevron', 24, C.sec)),
  settle: () => waitingRow('calendar', 'Acertar o jogo de 28/07', '4 avulsos · R$ 320,00 a receber', icon('chevron', 24, C.sec)),
};

const tile = (ic, label) => col(4, [icon(ic, 24, C.primary), t('cmeta', label, `font-weight:600;color:${C.ink}`)], `flex:1 1 0;align-items:center;padding:12px;border-radius:12px;background:${C.surface}`);
const shortcuts = () => row(4, [tile('plus', 'Marcar jogo'), tile('mail', 'Convidar')]);

// Cobrança só aparece vencida (decisão 2026-09-16): card branco, valor em display, chip
// âmbar, recebedor no lugar da chave, um botão azul. Copiado: botão secundário com check.
const ticket = ({ copied = false, count = null } = {}) => card([
  row(8, [t('eyebrow', 'Mensalidade de julho', `color:${C.primary};flex:1 1 auto`), chipBrand('Vôlei do CERET')]),
  row(12, [t('display', 'R$ 80,00', `color:${C.ink};flex:1 1 auto`), chipWarning('Venceu em 05/08')]),
  count ? t('support', count, `color:${C.sec}`) : '',
  row(8, [icon('card', 16, C.sec), t('support', 'Pix de Ana Souza · Nubank', `color:${C.sec}`)]),
  copied ? btn(`${icon('check', 18, C.primary, 'margin-right:8px')}Chave copiada`, 'secondary') : btn('Copiar chave Pix', 'primary'),
], { radius: 20 });
const charges = (opts = {}) => col(12, [sectionHeader('Minhas cobranças'), ticket(opts)]);

// "Próximos jogos": o que vem depois do hero, em todos os grupos. É a única informação
// que ainda é "agora" e não é filler; pede `upcomingGames` no agregado da Home.
const dateBox = (day, month) =>
  `<div style="width:44px;height:44px;border-radius:12px;background:${C.soft};display:flex;flex-direction:column;align-items:center;justify-content:center;flex:0 0 auto"><span style="font-weight:800;font-size:17px;line-height:20px;color:${C.ink}">${day}</span><span style="font-weight:700;font-size:11px;line-height:14px;letter-spacing:.08em;color:${C.primary}">${month}</span></div>`;
const upcomingRow = (day, month, title, meta, status) =>
  row(12, [dateBox(day, month), col(0, [t('ctitle', title, `color:${C.ink}`), t('cmeta', meta, `color:${C.sec}`)], 'flex:1 1 auto'), status], 'padding:12px 16px');
const upcoming = () => col(12, [
  sectionHeader('Próximos jogos'),
  card([
    upcomingRow('30', 'JUL', 'Vôlei Pacaembu · 20h', 'Quinta · 6 confirmados', chip('Sem resposta', C.sec, C.soft)),
    divider(),
    upcomingRow('04', 'AGO', 'Vôlei do CERET · 19h30', 'Terça · 3 confirmados', chip('Você vai', C.success, 'rgba(23,178,106,.12)', C.success)),
  ], { flush: true }),
]);

const confirmedSection = () => col(12, [
  sectionHeader('Confirmados', '12 de 12'),
  card([stack(['Ana Souza', 'Bruna Lima', 'Caio', 'Duda', 'Eva Lima', 'Tiago Moraes'])]),
]);
const bellCard = () => row(8, [icon('bell', 24, C.primary), t('support', 'Avisamos você se abrir vaga até 18h00 de 28/07.', `color:${C.ink}`)], `align-items:flex-start;padding:12px;border-radius:12px;background:${C.soft}`);
const positionLine = () => t('support', '2º na lista · 9 confirmados', `color:${C.sec}`);
const queueRow = (name, pos, self = false) =>
  row(12, [
    avatar(self ? 'Você' : name, 40, C.soft, C.primary, `inset 0 0 0 1px ${C.border}`),
    t('body', self ? 'Você' : name, `font-weight:600;color:${self ? C.primary : C.ink};flex:1 1 auto`),
    t('support', `${pos}º na espera`, `font-weight:600;color:${self ? C.primary : C.sec}`),
  ], `padding:12px 16px;${self ? `background:${C.soft}` : ''}`);
const queueSection = () => col(12, [
  sectionHeader('Como está a lista'),
  card([queueRow('Lucas Pereira', 1), divider(), queueRow('Bruna Silva', 2, true), divider(), queueRow('Tiago Moraes', 3)], { flush: true }),
]);
const upsellCard = () => row(8, [icon('card', 24, C.primary), col(4, [t('body', 'Quer entrar direto nos próximos?', `font-weight:700;color:${C.ink}`), t('support', 'Mensalistas têm vaga garantida. Peça pro admin te incluir.', `color:${C.sec}`)])], `align-items:flex-start;padding:12px;border-radius:12px;background:${C.soft}`);

// ---- shell: faixa, navegação, toast, dobra ---------------------------------------
const banner = (overdue) => {
  const bg = overdue ? 'rgba(245,166,35,.14)' : C.ink, fg = overdue ? C.warningFg : '#FFFFFF';
  return row(8, [icon('card', 16, overdue ? fg : C.accent), t('support', 'Você tem R$ 80,00 em aberto', `color:${fg};flex:1 1 auto`), icon('chevron', 16, fg)], `padding:12px 16px;border-radius:12px;background:${bg}`);
};
const navTab = (ic, label, selected) => col(4, [
  icon(ic, 24, selected ? C.primary : C.sec),
  t('nav', label, `color:${selected ? C.primary : C.sec}`),
  `<div style="width:18px;height:3px;border-radius:999px;background:${selected ? C.accent : 'transparent'}"></div>`,
], 'flex:1 1 0;align-items:center;justify-content:center;padding:8px 0;height:68px;box-sizing:border-box');
const bottomNav = (admin) =>
  `<div style="position:absolute;left:0;right:0;bottom:0;background:${C.chrome};border-top:1px solid ${C.border};display:flex;flex-direction:row">${[
    navTab('house', 'Início', true), navTab('users', 'Grupos', false),
    admin ? navTab('card', 'Caixa', false) : '', navTab('user', 'Perfil', false),
  ].join('')}</div>`;
const toast = (text) => `<div style="position:absolute;left:16px;right:16px;bottom:${NAV + 16}px;padding:14px 16px;border-radius:12px;background:${C.ink};box-shadow:0 12px 24px rgba(14,23,56,.18)"><div class="support" style="color:#fff">${text}</div></div>`;
const fold = () => `<div style="position:absolute;left:0;right:0;top:844px;border-top:1px dashed ${C.border};pointer-events:none"><span class="caption" style="position:absolute;right:8px;top:2px;font-size:10px;color:${C.ph};background:${C.bg};padding:0 4px">dobra · 844</span></div>`;

// ---- montagem --------------------------------------------------------------------
const CSS = `
body{margin:0;background:${C.bg};font-family:Inter,-apple-system,system-ui,sans-serif;color:${C.ink};-webkit-font-smoothing:antialiased}
a{color:${C.primary}}a:hover{color:${C.primaryPressed}}
.title{font-weight:700;font-size:22px;line-height:27px;letter-spacing:-.02em}
.subtitle{font-weight:600;font-size:18px;line-height:23px}
.body{font-weight:400;font-size:16px;line-height:23px}
.support{font-weight:400;font-size:14px;line-height:20px}
.label{font-weight:600;font-size:15px;line-height:18px}
.caption{font-weight:400;font-size:12px;line-height:16px}
.eyebrow{font-weight:600;font-size:12px;line-height:16px;letter-spacing:.08em;text-transform:uppercase}
.nav{font-weight:600;font-size:11px;line-height:14px}
.ctitle{font-weight:700;font-size:14.5px;line-height:18px}
.cmeta{font-weight:400;font-size:12.5px;line-height:16px}
.display{font-family:Anybody,'Arial Black',Impact,system-ui,sans-serif;font-stretch:110%;font-weight:800;font-size:36px;line-height:38px;letter-spacing:-.03em;text-wrap:balance}
`;
const FONT = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Anybody:wdth,wght@50..150,400..900&family=Inter:wght@400;500;600;700;800&display=swap">';

// A faixa de aviso entra logo abaixo do cabeçalho (cabeçalho + faixa num bloco de gap 12).
const top = (hdr, bn = null) => (bn ? col(12, [hdr, bn]) : hdr);
const screen = ({ h, admin = false, sections = [], overlay = '', showFold = true }) => {
  const body = col(24, sections, `padding:${INSET}px ${PAD}px ${NAV + 24}px`);
  const root = `<div style="position:relative;overflow:hidden;width:${W}px;height:${h}px;background:${C.bg}">${body}${bottomNav(admin)}${overlay}${h > 844 && showFold ? fold() : ''}</div>`;
  return `<!doctype html>\n<html>\n<head>\n  <meta charset="utf-8">\n  <script src="./support.js"></script>\n</head>\n<body>\n<x-dc>\n<helmet>\n  ${FONT}\n  <style>${CSS}</style>\n</helmet>\n${root}\n</x-dc>\n</body>\n</html>\n`;
};

// Esqueleto e erro: os dois estados sem conteúdo.
const sk = (w, hh, r = 8) => `<div style="${w ? `width:${w}px;` : ''}height:${hh}px;border-radius:${r}px;background:${C.border};flex:0 0 auto"></div>`;
const skeleton = () => screen({ h: 844, sections: [
  row(12, [sk(150, 24), '<div style="flex:1 1 auto"></div>', sk(30, 30, 15)]),
  `<div style="display:flex;flex-direction:column;gap:12px;border-radius:20px;background:${C.soft};padding:20px">${sk(96, 12)}${sk(230, 34)}${sk(260, 14)}${sk(200, 14)}${row(8, [sk(null, 52, 26), sk(null, 52, 26)].map((s) => s.replace('flex:0 0 auto', 'flex:1 1 0')))}${sk(170, 30, 15)}</div>`,
  col(12, [sk(110, 20), card([row(12, [sk(42, 42, 12), col(6, [sk(150, 14), sk(110, 12)])], 'padding:12px 16px'), divider(), row(12, [sk(42, 42, 12), col(6, [sk(150, 14), sk(110, 12)])], 'padding:12px 16px')], { flush: true })]),
] });
const failure = () => screen({ h: 844, sections: [
  col(12, [
    t('subtitle', 'Não foi possível carregar sua Home', `color:${C.ink};text-align:center`),
    t('support', 'Tente novamente em alguns instantes.', `color:${C.sec};text-align:center`),
    row(0, [btn('Tentar novamente', 'primary', { full: false })], 'justify-content:center'),
  ], `align-items:center;padding:24px;margin-top:${(844 - NAV - INSET) / 2 - 90}px`),
] });

// ---- estados ---------------------------------------------------------------------
const SCORE = { going: 9, out: 1, pending: 2 };
const PEND = [WAIT.entry(3, 'Vôlei do CERET'), WAIT.monthly(), WAIT.settle()];
const boards = {
  // Atleta — ordem: hero → cobrança vencida (se houver) → próximos jogos → grupos.
  Main: { page: 'atleta', title: 'Atleta · Sem resposta', h: 900, html: screen({ h: 900, sections: [header(null, { unread: true }), hero({ kind: 'pending' }), upcoming(), groups()] }) },
  AtletaCarregando: { page: 'atleta', title: 'Atleta · Carregando', h: 844, html: skeleton() },
  AtletaErro: { page: 'atleta', title: 'Atleta · Erro ao carregar', h: 844, html: failure() },
  AtletaConfirmado: { page: 'atleta', title: 'Atleta · Confirmado (+ toast)', h: 900, html: screen({ h: 900, sections: [header(), hero({ kind: 'confirmed' }), upcoming(), groups()], overlay: toast('Presença confirmada. Bom jogo!') }) },
  AtletaNaoVou: { page: 'atleta', title: 'Atleta · Não vou', h: 900, html: screen({ h: 900, sections: [header(), hero({ kind: 'declined' }), upcoming(), groups()] }) },
  AtletaAlterando: { page: 'atleta', title: 'Atleta · Alterando resposta', h: 900, html: screen({ h: 900, sections: [header(), hero({ kind: 'editing' }), upcoming(), groups()] }) },
  AtletaErroResposta: { page: 'atleta', title: 'Atleta · Erro ao responder', h: 900, html: screen({ h: 900, sections: [header(), hero({ kind: 'error' }), upcoming(), groups()] }) },
  AtletaEncerradas: { page: 'atleta', title: 'Atleta · Confirmações encerradas', h: 900, html: screen({ h: 900, sections: [header(), hero({ kind: 'pending', closed: true }), upcoming(), groups()] }) },
  AtletaSemJogo: { page: 'atleta', title: 'Atleta · Sem jogo', h: 844, html: screen({ h: 844, sections: [header(), hero({ kind: 'empty' }), groups()] }) },
  AtletaReserva: { page: 'atleta', title: 'Atleta · Reserva (6b)', h: 1280, html: screen({ h: 1280, sections: [header(), hero({ kind: 'reserva' }), col(12, [confirmedSection(), bellCard()]), upcoming(), groups()] }) },
  AtletaListaAvulso: { page: 'atleta', title: 'Atleta · Lista de espera do avulso (6e)', h: 1460, html: screen({ h: 1460, sections: [header(), hero({ kind: 'avulso' }), col(12, [positionLine(), queueSection(), upsellCard()]), upcoming(), groups()] }) },
  AtletaCobrancaVencida: { page: 'atleta', title: 'Atleta · Cobrança vencida', h: 1180, html: screen({ h: 1180, sections: [header(), hero({ kind: 'confirmed' }), charges({ count: '2 cobranças em aberto' }), upcoming(), groups()] }) },
  AtletaChaveCopiada: { page: 'atleta', title: 'Atleta · Chave Pix copiada', h: 1180, html: screen({ h: 1180, sections: [header(), hero({ kind: 'confirmed' }), charges({ count: '2 cobranças em aberto', copied: true }), upcoming(), groups()], overlay: toast('Chave copiada. Depois de pagar, o admin dá baixa.') }) },
  // Gestor / owner — hero → cobrança vencida → esperando você → atalhos → próximos jogos → grupos.
  GestorPendencias: { page: 'gestor', title: 'Gestor · Com pendências, sem resposta', h: 1380, html: screen({ h: 1380, admin: true, sections: [header('2 grupos · 3 coisas esperando você', { unread: true }), hero({ kind: 'pending', admin: true, score: SCORE }), waiting(PEND), shortcuts(), upcoming(), groups(['ceret'])] }) },
  GestorSemPendencias: { page: 'gestor', title: 'Gestor · Sem pendências', h: 1120, html: screen({ h: 1120, admin: true, sections: [header(), hero({ kind: 'pending', admin: true, score: SCORE }), shortcuts(), upcoming(), groups(['ceret'])] }) },
  GestorConfirmado: { page: 'gestor', title: 'Gestor · Confirmado', h: 1380, html: screen({ h: 1380, admin: true, sections: [header('2 grupos · 3 coisas esperando você'), hero({ kind: 'confirmed', admin: true, score: { going: 10, out: 1, pending: 1 } }), waiting(PEND), shortcuts(), upcoming(), groups(['ceret'])] }) },
  GestorEncerradas: { page: 'gestor', title: 'Gestor · Confirmações encerradas', h: 1380, html: screen({ h: 1380, admin: true, sections: [header('2 grupos · 3 coisas esperando você'), hero({ kind: 'confirmed', admin: true, closed: true, score: { going: 10, out: 2, pending: 0 } }), waiting(PEND), shortcuts(), upcoming(), groups(['ceret'])] }) },
  GestorTambemDeve: { page: 'gestor', title: 'Gestor · Também deve (cobrança vencida)', h: 1620, html: screen({ h: 1620, admin: true, sections: [header('2 grupos · 3 coisas esperando você'), hero({ kind: 'pending', admin: true, score: SCORE }), charges(), waiting(PEND), shortcuts(), upcoming(), groups(['ceret'])] }) },
  GestorSemJogo: { page: 'gestor', title: 'Gestor · Sem jogo marcado', h: 1040, html: screen({ h: 1040, admin: true, sections: [header('2 grupos · 2 coisas esperando você'), hero({ kind: 'emptyAdmin' }), waiting([WAIT.entry(3, 'Vôlei do CERET'), WAIT.monthly()]), groups(['ceret'])] }) },
  GestorMisto: { page: 'gestor', title: 'Gestor de outro grupo · atleta no próximo jogo', h: 1180, html: screen({ h: 1180, admin: true, sections: [header('2 grupos · 1 coisa esperando você'), hero({ kind: 'pending' }), waiting([WAIT.entry(1, 'Vôlei Pacaembu')]), shortcuts(), upcoming(), groups(['pacaembu'])] }) },
};

// ---- layout do canvas ------------------------------------------------------------
const GAP_X = 80, GAP_Y = 160;
const artboards = [], annotations = [];
const lay = (page, names, y0, x0 = 0) => {
  let x = x0;
  for (const n of names) {
    const b = boards[n];
    artboards.push({ file: `${n}.dc.html`, title: b.title, x, y: y0, w: W, h: b.h, page });
    x += W + GAP_X;
  }
};
// Atleta: linha 1 os que cabem em 844, linha 2 os longos.
lay('atleta', ['Main', 'AtletaConfirmado', 'AtletaNaoVou', 'AtletaAlterando', 'AtletaErroResposta', 'AtletaEncerradas', 'AtletaSemJogo', 'AtletaCarregando', 'AtletaErro'], 0);
lay('atleta', ['AtletaReserva', 'AtletaListaAvulso', 'AtletaCobrancaVencida', 'AtletaChaveCopiada'], 900 + GAP_Y);
lay('gestor', ['GestorPendencias', 'GestorSemPendencias', 'GestorConfirmado', 'GestorEncerradas', 'GestorTambemDeve', 'GestorSemJogo', 'GestorMisto'], 0);

const note = (id, page, x, y, w, text) => annotations.push({ id, page, x, y, w, text });
note('a-hero', 'atleta', 0, -230, 420, 'HERO = MARCA\nAzul (primary) + lima (accent) + bola: os três elementos do logo. Só tokens que já existem no design system. A bola é a do SaqzSpinner (saqz_volleyball), em branco a 20%. Data em Anybody 800 (a fonte dos títulos da landing). "Vou" em lima com texto ink é o CTA da landing; "Não vou" fica em contorno.');
note('a-topo', 'atleta', -470, -230, 420, 'CABEÇALHO\nLogo antes da saudação. O sino abre Notificações (hoje só se chega pelo Perfil); o ponto azul marca não lidas. A faixa de aviso (cobrança em aberto) entra logo abaixo do cabeçalho, não acima dele.');
note('a-cortes', 'atleta', 470, -230, 420, 'CORTADO\n• Subtítulo da saudação (repetia o hero)\n• Emoji do "Fala, Bruna!" (marca não usa emoji)\n• Card "Da última vez": histórico mora no grupo (princípio do VUL-200)\n• Roster dentro do hero nos estados de espera (as seções abaixo já mostram)');
note('a-strings', 'atleta', 940, -230, 420, 'STRINGS NOVAS (para você serializar)\n• "Terça, 19h30" — weekday + time, já existem no HomeNextGameUi\n• "28 de julho · {local}" — meta do hero\n• "Sem jogo marcado" — título do hero vazio\n• "Confirmações encerradas." — prazo fechado\n• "Encerrou {data} · {hora}" — chip do gestor com prazo fechado\nTodo o resto reaproveita as chaves do strings_home.xml.');
note('a-cobranca', 'atleta', 940, 900 + GAP_Y - 220, 460, 'COBRANÇA: SÓ QUANDO VENCIDA\nNo prazo, a Início não mostra nada; a dívida mora no detalhe do grupo e em Perfil → Mensalidades. Vencida: card branco, valor em display, chip âmbar (sem vermelho), recebedor no lugar da chave, um botão azul. Um card por grupo; com mais de uma cobrança entra "N cobranças em aberto". Ao copiar, botão vira "Chave copiada" e o toast explica a baixa manual.\n\nDecisão em aberto: a faixa do shell nas outras abas segue a mesma regra (só vencida)?\n\nStrings novas: "Mensalidade de julho", "Pix de {nome} · {banco}", "Chave copiada", "Chave copiada. Depois de pagar, o admin dá baixa."');
note('a-proximos', 'atleta', 1410, -230, 460, 'PRÓXIMOS JOGOS (proposta para preencher)\nO que vem depois do hero, em todos os grupos: data, grupo e hora, quantos confirmaram, e o seu estado (Sem resposta / Você vai). Toque abre o jogo. É a única informação que ainda é "agora" e não é filler; quem tem dois grupos vê os dois, quem tem um vê a semana que vem. Some quando não há mais nada marcado.\n\nCusta: `upcomingGames` (os N seguintes) no GET /api/me/home.');
note('a-espera', 'atleta', 0, 900 + GAP_Y - 200, 420, 'ESPERA (6b / 6e)\nMesmo hero azul, com o chip e a caixa de aviso em branco translúcido. "Ver o jogo" sobe para botão sólido; "Sair" vira ação fantasma abaixo. As seções Confirmados / Como está a lista seguem o vocabulário atual.');
note('g-hero', 'gestor', 0, -260, 420, 'GESTOR\nPlacar (Vão / Não vão / Sem resposta) entra no hero azul; "Vão" em lima. O gestor também responde presença no mesmo lugar (dono e admin jogam). Chip da direita mostra o prazo.');
note('g-atalhos', 'gestor', 470, -260, 420, 'ATALHOS: SÓ DOIS\n"Marcar jogo" e "Convidar". Caixa e Grupos já são abas na barra inferior. Sem jogo marcado, os dois verbos sobem para dentro do hero e a linha de atalhos some.');
note('g-misto', 'gestor', 940, -260, 420, 'MISTO\nAdmin de um grupo, atleta no grupo do próximo jogo: hero de atleta (RSVP) + "Esperando você" do grupo que administra. É a regra atual (isAdminOfNextGame), só com a roupa nova.');

for (const [n, b] of Object.entries(boards)) writeFileSync(join(here, `${n}.dc.html`), b.html);
writeFileSync(join(here, 'canvas.json'), JSON.stringify({
  artboards, annotations,
  pages: [{ id: 'atleta', name: 'Atleta' }, { id: 'gestor', name: 'Gestor / owner' }],
  launch: { view: 'canvas', page: 'atleta' },
}, null, 2));
console.log(`ok: ${artboards.length} artboards, ${annotations.length} notas`);
