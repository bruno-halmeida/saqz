/* @ds-bundle: {"format":4,"namespace":"SaqzDesignSystem_48df71","components":[{"name":"Button","sourcePath":"components/buttons/Button.jsx"},{"name":"IconButton","sourcePath":"components/buttons/IconButton.jsx"},{"name":"Card","sourcePath":"components/data-display/Card.jsx"},{"name":"GameSummaryCard","sourcePath":"components/data-display/GameSummaryCard.jsx"},{"name":"MemberRow","sourcePath":"components/data-display/MemberRow.jsx"},{"name":"SectionHeader","sourcePath":"components/data-display/SectionHeader.jsx"},{"name":"StatusChip","sourcePath":"components/data-display/StatusChip.jsx"},{"name":"BottomSheet","sourcePath":"components/feedback/BottomSheet.jsx"},{"name":"EmptyState","sourcePath":"components/feedback/EmptyState.jsx"},{"name":"Toast","sourcePath":"components/feedback/Toast.jsx"},{"name":"AttendanceSelector","sourcePath":"components/forms/AttendanceSelector.jsx"},{"name":"Input","sourcePath":"components/forms/Input.jsx"},{"name":"BottomNav","sourcePath":"components/navigation/BottomNav.jsx"},{"name":"TopAppBar","sourcePath":"components/navigation/TopAppBar.jsx"}],"sourceHashes":{"components/buttons/Button.jsx":"e6d2b4777be7","components/buttons/IconButton.jsx":"016a84441f5e","components/data-display/Card.jsx":"8e49dc225adb","components/data-display/GameSummaryCard.jsx":"dfcea84a65ad","components/data-display/MemberRow.jsx":"5ad0f95d041f","components/data-display/SectionHeader.jsx":"32e41b1950d1","components/data-display/StatusChip.jsx":"be2b23146f55","components/feedback/BottomSheet.jsx":"f310c11232d3","components/feedback/EmptyState.jsx":"5834f675ba4e","components/feedback/Toast.jsx":"fc39284291c8","components/forms/AttendanceSelector.jsx":"ff8058e3f037","components/forms/Input.jsx":"c5d242da763e","components/navigation/BottomNav.jsx":"974a908fb1c5","components/navigation/TopAppBar.jsx":"6c1b6aad689e","ui_kits/mobile-app/App.jsx":"828cbff590e1","ui_kits/mobile-app/CreateGroupScreen.jsx":"d800155f2ff5","ui_kits/mobile-app/GroupDetailScreen.jsx":"c827e0f62dd1","ui_kits/mobile-app/GroupsScreen.jsx":"d12cced46045","ui_kits/mobile-app/HomeScreen.jsx":"b53dd4bdd2eb","ui_kits/mobile-app/Icons.jsx":"b30069f691cd","ui_kits/mobile-app/LoginScreen.jsx":"42e57da73b7a","ui_kits/mobile-app/data.js":"a42457f84370"},"inlinedExternals":[],"unexposedExports":[]} */

(() => {

const __ds_ns = (window.SaqzDesignSystem_48df71 = window.SaqzDesignSystem_48df71 || {});

const __ds_scope = {};

(__ds_ns.__errors = __ds_ns.__errors || []);

// components/buttons/Button.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const CSS = `
.saqz-btn{display:inline-flex;align-items:center;justify-content:center;gap:10px;
  border:1px solid transparent;border-radius:var(--radius-pill);cursor:pointer;
  font-family:var(--font-ui);font-weight:700;line-height:1;text-align:center;
  white-space:nowrap;transition:background-color .15s ease,border-color .15s ease,color .15s ease,filter .15s ease,transform .15s ease;}
.saqz-btn:focus-visible{outline:3px solid var(--brand-fill-11);outline-offset:2px;}
.saqz-btn--md{min-height:52px;padding:0 24px;font-size:17px;}
.saqz-btn--sm{min-height:44px;padding:0 18px;font-size:15px;}
.saqz-btn--full{width:100%;}
.saqz-btn--primary{background:var(--saqz-blue);color:var(--saqz-white);}
.saqz-btn--primary:hover{background:var(--saqz-blue-pressed);}
.saqz-btn--primary:active{transform:translateY(1px) scale(.995);}
.saqz-btn--secondary{background:var(--saqz-white);color:var(--saqz-blue);border-color:var(--saqz-blue);}
.saqz-btn--secondary:hover{background:var(--saqz-ice);}
.saqz-btn--secondary:active{transform:translateY(1px);}
.saqz-btn--danger{background:var(--saqz-error);color:var(--saqz-white);}
.saqz-btn--danger:hover{filter:brightness(.95);}
.saqz-btn--ghost{background:transparent;color:var(--saqz-blue);}
.saqz-btn--ghost:hover{background:var(--saqz-ice);}
.saqz-btn:disabled{cursor:not-allowed;background:var(--saqz-disabled-bg);color:var(--saqz-disabled-fg);border-color:transparent;transform:none;filter:none;}
.saqz-btn--secondary:disabled{background:var(--saqz-white);border-color:var(--saqz-border);color:var(--saqz-disabled-fg);}
.saqz-btn__icon{display:inline-flex;flex:0 0 auto;}
`;
function inject() {
  if (typeof document === "undefined") return;
  if (document.getElementById("saqz-button-css")) return;
  const s = document.createElement("style");
  s.id = "saqz-button-css";
  s.textContent = CSS;
  document.head.appendChild(s);
}

/**
 * Saqz pill button. One filled primary per screen/block.
 * variant: "primary" | "secondary" | "danger" | "ghost"
 */
function Button({
  variant = "primary",
  size = "md",
  fullWidth = false,
  leftIcon,
  rightIcon,
  children,
  className = "",
  ...rest
}) {
  inject();
  const cls = ["saqz-btn", `saqz-btn--${variant}`, `saqz-btn--${size}`, fullWidth ? "saqz-btn--full" : "", className].filter(Boolean).join(" ");
  return /*#__PURE__*/React.createElement("button", _extends({
    className: cls
  }, rest), leftIcon ? /*#__PURE__*/React.createElement("span", {
    className: "saqz-btn__icon"
  }, leftIcon) : null, children, rightIcon ? /*#__PURE__*/React.createElement("span", {
    className: "saqz-btn__icon"
  }, rightIcon) : null);
}
Object.assign(__ds_scope, { Button });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/buttons/Button.jsx", error: String((e && e.message) || e) }); }

// components/buttons/IconButton.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const CSS = `
.saqz-iconbtn{position:relative;display:grid;place-items:center;width:44px;height:44px;
  border:1px solid transparent;border-radius:50%;background:transparent;color:var(--saqz-navy);
  cursor:pointer;transition:background .15s ease,border-color .15s ease;}
.saqz-iconbtn:hover{background:var(--saqz-white);border-color:var(--saqz-border);}
.saqz-iconbtn--soft{background:var(--saqz-ice);}
.saqz-iconbtn--soft:hover{background:var(--saqz-ice);border-color:var(--saqz-border);}
.saqz-iconbtn:focus-visible{outline:3px solid var(--brand-fill-11);outline-offset:2px;}
.saqz-iconbtn__dot{position:absolute;top:7px;right:7px;width:9px;height:9px;border-radius:50%;
  background:var(--saqz-lime);border:2px solid var(--saqz-canvas);}
`;
function inject() {
  if (typeof document === "undefined") return;
  if (document.getElementById("saqz-iconbutton-css")) return;
  const s = document.createElement("style");
  s.id = "saqz-iconbutton-css";
  s.textContent = CSS;
  document.head.appendChild(s);
}

/**
 * Circular 44dp icon-only button (top bar, sheet close, inline actions).
 * Pass an SVG glyph as children. `dot` shows the lime notification indicator.
 */
function IconButton({
  children,
  dot = false,
  soft = false,
  className = "",
  ...rest
}) {
  inject();
  const cls = ["saqz-iconbtn", soft ? "saqz-iconbtn--soft" : "", className].filter(Boolean).join(" ");
  return /*#__PURE__*/React.createElement("button", _extends({
    className: cls
  }, rest), children, dot ? /*#__PURE__*/React.createElement("span", {
    className: "saqz-iconbtn__dot",
    "aria-hidden": "true"
  }) : null);
}
Object.assign(__ds_scope, { IconButton });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/buttons/IconButton.jsx", error: String((e && e.message) || e) }); }

// components/data-display/Card.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const CSS = `
.saqz-card{border:1px solid var(--saqz-border);border-radius:var(--radius-card);
  background:var(--saqz-white);font-family:var(--font-ui);color:var(--saqz-navy);}
.saqz-card--pad{padding:16px;}
.saqz-card--soft{background:var(--saqz-ice);border-color:transparent;}
.saqz-card--flush{overflow:hidden;}
`;
function inject() {
  if (typeof document === "undefined") return;
  if (document.getElementById("saqz-card-css")) return;
  const s = document.createElement("style");
  s.id = "saqz-card-css";
  s.textContent = CSS;
  document.head.appendChild(s);
}

/**
 * Base surface: white, 1px border, 12dp radius, no shadow. Never nest cards.
 * tone="soft" gives the ice-tinted borderless variant (used for the next-game block).
 */
function Card({
  tone = "default",
  padded = true,
  flush = false,
  className = "",
  children,
  ...rest
}) {
  inject();
  const cls = ["saqz-card", padded ? "saqz-card--pad" : "", tone === "soft" ? "saqz-card--soft" : "", flush ? "saqz-card--flush" : "", className].filter(Boolean).join(" ");
  return /*#__PURE__*/React.createElement("div", _extends({
    className: cls
  }, rest), children);
}
Object.assign(__ds_scope, { Card });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/data-display/Card.jsx", error: String((e && e.message) || e) }); }

// components/data-display/GameSummaryCard.jsx
try { (() => {
const CSS = `
.saqz-game{position:relative;overflow:hidden;padding:18px;border:1px solid var(--saqz-border);
  border-radius:var(--radius-card);background:var(--saqz-white);font-family:var(--font-ui);color:var(--saqz-navy);}
.saqz-game__eyebrow{margin:0 0 12px;color:var(--saqz-blue);font-size:12px;font-weight:700;
  letter-spacing:.08em;text-transform:uppercase;}
.saqz-game__title{position:relative;z-index:1;margin:0;max-width:80%;font-size:25px;line-height:1.15;
  letter-spacing:-.025em;font-weight:700;}
.saqz-game__loc{position:relative;z-index:1;display:flex;gap:10px;align-items:flex-start;margin-top:14px;}
.saqz-game__loc-icon{flex:0 0 auto;color:var(--saqz-blue);display:inline-flex;margin-top:1px;}
.saqz-game__venue{display:block;font-size:16px;font-weight:600;}
.saqz-game__addr{display:block;margin-top:2px;color:var(--saqz-muted);font-size:14px;line-height:1.4;}
.saqz-game__stats{display:grid;grid-template-columns:repeat(3,1fr);margin-top:18px;padding:16px 0;
  border-top:1px solid var(--saqz-border);border-bottom:1px solid var(--saqz-border);}
.saqz-game__stat{text-align:center;}
.saqz-game__stat + .saqz-game__stat{border-left:1px solid var(--saqz-border);}
.saqz-game__stat strong{display:block;font-size:24px;line-height:1;font-weight:700;}
.saqz-game__stat span{display:block;margin-top:6px;color:var(--saqz-muted);font-size:12px;}
.saqz-game__stat--confirmed strong{color:var(--saqz-success);}
.saqz-game__stat--maybe strong{color:var(--saqz-warning);}
.saqz-game__stat--out strong{color:var(--saqz-error);}
.saqz-game__body{margin-top:16px;}
.saqz-game__watermark{position:absolute;inset:0 0 auto auto;width:145px;height:145px;opacity:.055;
  transform:translate(25px,-24px);pointer-events:none;
  background-repeat:no-repeat;background-position:center;
  background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 120 120'%3E%3Cg fill='none' stroke='%230638DF' stroke-width='4'%3E%3Ccircle cx='60' cy='60' r='45'/%3E%3Cpath d='M31 25c23 7 37 21 42 43M87 25c-6 22-20 36-42 42M30 90c20-12 42-13 61-2M22 51c21-2 38 6 52 23M96 52c-20 3-34 13-43 31'/%3E%3C/g%3E%3C/svg%3E");}
`;
function inject() {
  if (typeof document === "undefined") return;
  if (document.getElementById("saqz-gamesummary-css")) return;
  const s = document.createElement("style");
  s.id = "saqz-gamesummary-css";
  s.textContent = CSS;
  document.head.appendChild(s);
}
const PIN = /*#__PURE__*/React.createElement("svg", {
  width: "22",
  height: "22",
  viewBox: "0 0 24 24",
  "aria-hidden": "true"
}, /*#__PURE__*/React.createElement("path", {
  d: "M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: "1.8"
}), /*#__PURE__*/React.createElement("circle", {
  cx: "12",
  cy: "10",
  r: "2.5",
  fill: "var(--saqz-lime)",
  stroke: "currentColor",
  strokeWidth: "1.2"
}));

/**
 * The home "Próximo jogo" hero: eyebrow, date/time title, venue, and a 3-up
 * confirmed/maybe/out stat strip. Pass the AttendanceSelector (and note) as children.
 */
function GameSummaryCard({
  eyebrow = "Próximo jogo",
  title,
  venue,
  address,
  confirmed,
  maybe,
  out,
  children,
  className = ""
}) {
  inject();
  const showStats = confirmed != null || maybe != null || out != null;
  return /*#__PURE__*/React.createElement("article", {
    className: ["saqz-game", className].filter(Boolean).join(" ")
  }, /*#__PURE__*/React.createElement("span", {
    className: "saqz-game__watermark",
    "aria-hidden": "true"
  }), eyebrow ? /*#__PURE__*/React.createElement("p", {
    className: "saqz-game__eyebrow"
  }, eyebrow) : null, /*#__PURE__*/React.createElement("h2", {
    className: "saqz-game__title"
  }, title), venue ? /*#__PURE__*/React.createElement("div", {
    className: "saqz-game__loc"
  }, /*#__PURE__*/React.createElement("span", {
    className: "saqz-game__loc-icon"
  }, PIN), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("strong", {
    className: "saqz-game__venue"
  }, venue), address ? /*#__PURE__*/React.createElement("span", {
    className: "saqz-game__addr"
  }, address) : null)) : null, showStats ? /*#__PURE__*/React.createElement("div", {
    className: "saqz-game__stats",
    "aria-label": "Resumo de presen\xE7as"
  }, /*#__PURE__*/React.createElement("div", {
    className: "saqz-game__stat saqz-game__stat--confirmed"
  }, /*#__PURE__*/React.createElement("strong", null, confirmed ?? 0), /*#__PURE__*/React.createElement("span", null, "Confirmados")), /*#__PURE__*/React.createElement("div", {
    className: "saqz-game__stat saqz-game__stat--maybe"
  }, /*#__PURE__*/React.createElement("strong", null, maybe ?? 0), /*#__PURE__*/React.createElement("span", null, "Talvez")), /*#__PURE__*/React.createElement("div", {
    className: "saqz-game__stat saqz-game__stat--out"
  }, /*#__PURE__*/React.createElement("strong", null, out ?? 0), /*#__PURE__*/React.createElement("span", null, "N\xE3o v\xE3o"))) : null, children ? /*#__PURE__*/React.createElement("div", {
    className: "saqz-game__body"
  }, children) : null);
}
Object.assign(__ds_scope, { GameSummaryCard });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/data-display/GameSummaryCard.jsx", error: String((e && e.message) || e) }); }

// components/data-display/MemberRow.jsx
try { (() => {
const CSS = `
.saqz-memberrow{display:flex;align-items:center;gap:12px;min-height:64px;padding:10px 4px;
  font-family:var(--font-ui);width:100%;text-align:left;background:transparent;border:0;}
.saqz-memberrow--button{cursor:pointer;}
.saqz-memberrow__avatar{flex:0 0 auto;width:44px;height:44px;border-radius:50%;overflow:hidden;
  display:grid;place-items:center;background:var(--saqz-ice);color:var(--saqz-blue);
  box-shadow:inset 0 0 0 1px var(--saqz-border);font-weight:700;font-size:15px;}
.saqz-memberrow__avatar img{width:100%;height:100%;object-fit:cover;}
.saqz-memberrow__body{flex:1;min-width:0;}
.saqz-memberrow__name{display:flex;align-items:center;gap:8px;color:var(--saqz-navy);
  font-size:15px;line-height:1.35;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.saqz-memberrow__meta{margin-top:3px;color:var(--saqz-muted);font-size:13px;line-height:1.35;
  overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.saqz-memberrow__role{color:var(--saqz-blue);font-size:12px;font-weight:700;}
.saqz-memberrow__trailing{flex:0 0 auto;color:var(--saqz-muted);display:inline-flex;align-items:center;}
`;
function inject() {
  if (typeof document === "undefined") return;
  if (document.getElementById("saqz-memberrow-css")) return;
  const s = document.createElement("style");
  s.id = "saqz-memberrow-css";
  s.textContent = CSS;
  document.head.appendChild(s);
}
function initials(name = "") {
  return name.trim().split(/\s+/).slice(0, 2).map(w => w[0]).join("").toUpperCase();
}

/**
 * Person/list row: avatar (image or initials) + name + meta, optional admin role
 * tag and a trailing node (chevron, chip, status). Renders as a button when onClick given.
 */
function MemberRow({
  name,
  meta,
  avatar,
  role,
  trailing,
  onClick,
  className = ""
}) {
  inject();
  const Tag = onClick ? "button" : "div";
  return /*#__PURE__*/React.createElement(Tag, {
    type: onClick ? "button" : undefined,
    onClick: onClick,
    className: ["saqz-memberrow", onClick ? "saqz-memberrow--button" : "", className].filter(Boolean).join(" ")
  }, /*#__PURE__*/React.createElement("span", {
    className: "saqz-memberrow__avatar"
  }, avatar ? /*#__PURE__*/React.createElement("img", {
    src: avatar,
    alt: ""
  }) : initials(name)), /*#__PURE__*/React.createElement("span", {
    className: "saqz-memberrow__body"
  }, /*#__PURE__*/React.createElement("span", {
    className: "saqz-memberrow__name"
  }, name, role ? /*#__PURE__*/React.createElement("span", {
    className: "saqz-memberrow__role"
  }, role) : null), meta ? /*#__PURE__*/React.createElement("span", {
    className: "saqz-memberrow__meta"
  }, meta) : null), trailing ? /*#__PURE__*/React.createElement("span", {
    className: "saqz-memberrow__trailing"
  }, trailing) : null);
}
Object.assign(__ds_scope, { MemberRow });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/data-display/MemberRow.jsx", error: String((e && e.message) || e) }); }

// components/data-display/SectionHeader.jsx
try { (() => {
const CSS = `
.saqz-sectionhead{display:flex;align-items:center;justify-content:space-between;gap:12px;
  margin:0 2px 10px;font-family:var(--font-ui);}
.saqz-sectionhead__titles{display:flex;align-items:center;gap:10px;min-width:0;}
.saqz-sectionhead__icon{display:inline-flex;flex:0 0 auto;color:var(--saqz-blue);}
.saqz-sectionhead__title{margin:0;font-size:20px;line-height:1.25;font-weight:700;letter-spacing:-.02em;color:var(--saqz-navy);}
.saqz-sectionhead__action{min-height:44px;border:0;padding:0 4px;background:transparent;
  color:var(--saqz-blue);font-family:var(--font-ui);font-size:14px;font-weight:600;cursor:pointer;}
.saqz-sectionhead__action:hover{text-decoration:underline;}
`;
function inject() {
  if (typeof document === "undefined") return;
  if (document.getElementById("saqz-sectionheader-css")) return;
  const s = document.createElement("style");
  s.id = "saqz-sectionheader-css";
  s.textContent = CSS;
  document.head.appendChild(s);
}

/**
 * Row heading with optional leading icon and a trailing text action ("Ver todos").
 */
function SectionHeader({
  title,
  icon,
  action,
  onAction,
  className = ""
}) {
  inject();
  return /*#__PURE__*/React.createElement("div", {
    className: ["saqz-sectionhead", className].filter(Boolean).join(" ")
  }, /*#__PURE__*/React.createElement("div", {
    className: "saqz-sectionhead__titles"
  }, icon ? /*#__PURE__*/React.createElement("span", {
    className: "saqz-sectionhead__icon"
  }, icon) : null, /*#__PURE__*/React.createElement("h2", {
    className: "saqz-sectionhead__title"
  }, title)), action ? /*#__PURE__*/React.createElement("button", {
    type: "button",
    className: "saqz-sectionhead__action",
    onClick: onAction
  }, action) : null);
}
Object.assign(__ds_scope, { SectionHeader });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/data-display/SectionHeader.jsx", error: String((e && e.message) || e) }); }

// components/data-display/StatusChip.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const CSS = `
.saqz-chip{display:inline-flex;align-items:center;gap:6px;padding:7px 10px;
  border-radius:var(--radius-pill);font-family:var(--font-ui);font-size:12px;font-weight:700;
  line-height:1;white-space:nowrap;}
.saqz-chip--neutral{background:var(--saqz-ice);color:var(--saqz-muted);}
.saqz-chip--accent{background:var(--lime-fill-32);color:var(--saqz-navy);}
.saqz-chip--brand{background:var(--brand-fill-08);color:var(--saqz-blue);}
.saqz-chip--success{background:rgba(23,178,106,.12);color:var(--saqz-success);}
.saqz-chip--warning{background:rgba(245,166,35,.14);color:#B26B00;}
.saqz-chip--error{background:var(--error-fill-10);color:var(--saqz-error);}
.saqz-chip__dot{width:7px;height:7px;border-radius:50%;background:currentColor;}
`;
function inject() {
  if (typeof document === "undefined") return;
  if (document.getElementById("saqz-statuschip-css")) return;
  const s = document.createElement("style");
  s.id = "saqz-statuschip-css";
  s.textContent = CSS;
  document.head.appendChild(s);
}

/**
 * Small pill for status/counts (e.g. "14/14", "Confirmado", "2 avisos novos").
 * tone: neutral | accent | brand | success | warning | error.
 */
function StatusChip({
  tone = "neutral",
  dot = false,
  className = "",
  children,
  ...rest
}) {
  inject();
  const cls = ["saqz-chip", `saqz-chip--${tone}`, className].filter(Boolean).join(" ");
  return /*#__PURE__*/React.createElement("span", _extends({
    className: cls
  }, rest), dot ? /*#__PURE__*/React.createElement("span", {
    className: "saqz-chip__dot",
    "aria-hidden": "true"
  }) : null, children);
}
Object.assign(__ds_scope, { StatusChip });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/data-display/StatusChip.jsx", error: String((e && e.message) || e) }); }

// components/feedback/BottomSheet.jsx
try { (() => {
const CSS = `
.saqz-sheet-scrim{position:absolute;inset:0;background:var(--scrim);opacity:0;pointer-events:none;
  transition:opacity .22s ease;z-index:20;}
.saqz-sheet-scrim.is-open{opacity:1;pointer-events:auto;}
.saqz-sheet{position:absolute;z-index:30;left:50%;bottom:0;width:min(100%,520px);
  max-height:min(88%,760px);display:flex;flex-direction:column;background:var(--saqz-white);
  border-radius:var(--radius-sheet) var(--radius-sheet) 0 0;box-shadow:var(--shadow-sheet);
  transform:translate(-50%,110%);transition:transform .32s cubic-bezier(.22,1,.36,1);
  font-family:var(--font-ui);color:var(--saqz-navy);overflow:hidden;}
.saqz-sheet.is-open{transform:translate(-50%,0);}
.saqz-sheet__handle-area{min-height:36px;display:grid;place-items:center;flex:0 0 auto;}
.saqz-sheet__handle{width:42px;height:5px;border-radius:var(--radius-pill);background:#c9ced9;}
.saqz-sheet__header{padding:4px 20px 16px;display:grid;grid-template-columns:1fr auto;gap:16px;
  align-items:start;border-bottom:1px solid var(--saqz-border);}
.saqz-sheet__title{margin:0;font-size:21px;line-height:1.25;font-weight:700;letter-spacing:-.025em;}
.saqz-sheet__desc{margin:6px 0 0;color:var(--saqz-muted);font-size:14px;line-height:1.42;}
.saqz-sheet__close{width:44px;height:44px;display:grid;place-items:center;border:0;border-radius:50%;
  background:var(--saqz-ice);color:var(--saqz-navy);cursor:pointer;}
.saqz-sheet__content{overflow-y:auto;padding:16px 20px 24px;flex:1 1 auto;}
.saqz-sheet__footer{flex:0 0 auto;display:grid;gap:10px;padding:12px 20px 16px;
  border-top:1px solid var(--saqz-border);}
.saqz-sheet__footer--split{grid-template-columns:1fr 1fr;}
`;
function inject() {
  if (typeof document === "undefined") return;
  if (document.getElementById("saqz-bottomsheet-css")) return;
  const s = document.createElement("style");
  s.id = "saqz-bottomsheet-css";
  s.textContent = CSS;
  document.head.appendChild(s);
}
const CLOSE = /*#__PURE__*/React.createElement("svg", {
  viewBox: "0 0 24 24",
  width: "20",
  height: "20",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinecap: "round"
}, /*#__PURE__*/React.createElement("path", {
  d: "M6 6l12 12M18 6L6 18"
}));

/**
 * Generic bottom sheet: drag handle, title/description, close button, scrollable
 * content, and an optional footer. Renders absolutely inside its positioned parent
 * (e.g. a phone frame). Controlled via `open`/`onClose`.
 */
function BottomSheet({
  open,
  onClose,
  title,
  description,
  footer,
  splitFooter = false,
  children
}) {
  inject();
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "saqz-sheet-scrim" + (open ? " is-open" : ""),
    onClick: onClose,
    "aria-hidden": "true"
  }), /*#__PURE__*/React.createElement("section", {
    className: "saqz-sheet" + (open ? " is-open" : ""),
    role: "dialog",
    "aria-modal": "true",
    "aria-hidden": !open
  }, /*#__PURE__*/React.createElement("div", {
    className: "saqz-sheet__handle-area",
    "aria-hidden": "true"
  }, /*#__PURE__*/React.createElement("div", {
    className: "saqz-sheet__handle"
  })), /*#__PURE__*/React.createElement("header", {
    className: "saqz-sheet__header"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("h2", {
    className: "saqz-sheet__title"
  }, title), description ? /*#__PURE__*/React.createElement("p", {
    className: "saqz-sheet__desc"
  }, description) : null), /*#__PURE__*/React.createElement("button", {
    type: "button",
    className: "saqz-sheet__close",
    onClick: onClose,
    "aria-label": "Fechar"
  }, CLOSE)), /*#__PURE__*/React.createElement("div", {
    className: "saqz-sheet__content"
  }, children), footer ? /*#__PURE__*/React.createElement("footer", {
    className: "saqz-sheet__footer" + (splitFooter ? " saqz-sheet__footer--split" : "")
  }, footer) : null));
}
Object.assign(__ds_scope, { BottomSheet });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/BottomSheet.jsx", error: String((e && e.message) || e) }); }

// components/feedback/EmptyState.jsx
try { (() => {
const CSS = `
.saqz-empty{display:grid;justify-items:center;text-align:center;gap:6px;padding:32px 20px;
  font-family:var(--font-ui);color:var(--saqz-navy);}
.saqz-empty__icon{display:grid;place-items:center;width:64px;height:64px;border-radius:50%;
  background:var(--saqz-ice);color:var(--saqz-blue);margin-bottom:6px;}
.saqz-empty__title{margin:0;font-size:17px;font-weight:600;line-height:1.3;}
.saqz-empty__desc{margin:0;max-width:34ch;color:var(--saqz-muted);font-size:14px;line-height:1.45;}
.saqz-empty__action{margin-top:12px;}
`;
function inject() {
  if (typeof document === "undefined") return;
  if (document.getElementById("saqz-emptystate-css")) return;
  const s = document.createElement("style");
  s.id = "saqz-emptystate-css";
  s.textContent = CSS;
  document.head.appendChild(s);
}

/**
 * Friendly empty state: soft round icon, short title, one line of guidance,
 * and an optional action. Copy is plain and encouraging ("Nenhum jogo marcado…").
 */
function EmptyState({
  icon,
  title,
  description,
  action,
  className = ""
}) {
  inject();
  return /*#__PURE__*/React.createElement("div", {
    className: ["saqz-empty", className].filter(Boolean).join(" ")
  }, icon ? /*#__PURE__*/React.createElement("div", {
    className: "saqz-empty__icon"
  }, icon) : null, /*#__PURE__*/React.createElement("p", {
    className: "saqz-empty__title"
  }, title), description ? /*#__PURE__*/React.createElement("p", {
    className: "saqz-empty__desc"
  }, description) : null, action ? /*#__PURE__*/React.createElement("div", {
    className: "saqz-empty__action"
  }, action) : null);
}
Object.assign(__ds_scope, { EmptyState });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/EmptyState.jsx", error: String((e && e.message) || e) }); }

// components/feedback/Toast.jsx
try { (() => {
const CSS = `
.saqz-toast{position:absolute;z-index:40;left:50%;bottom:24px;transform:translate(-50%,16px);
  width:max-content;max-width:calc(100% - 32px);padding:12px 16px;border-radius:var(--radius-card);
  background:var(--saqz-navy);color:var(--saqz-white);box-shadow:var(--shadow-toast);
  font-family:var(--font-ui);font-size:14px;font-weight:600;text-align:center;line-height:1.4;
  opacity:0;pointer-events:none;transition:opacity .16s ease,transform .16s ease;}
.saqz-toast.is-visible{opacity:1;transform:translate(-50%,0);}
`;
function inject() {
  if (typeof document === "undefined") return;
  if (document.getElementById("saqz-toast-css")) return;
  const s = document.createElement("style");
  s.id = "saqz-toast-css";
  s.textContent = CSS;
  document.head.appendChild(s);
}

/**
 * Transient navy confirmation toast, anchored to the bottom of its positioned
 * parent. Controlled via `visible`; pass the message as children.
 */
function Toast({
  visible = false,
  children,
  className = ""
}) {
  inject();
  return /*#__PURE__*/React.createElement("div", {
    className: ["saqz-toast", visible ? "is-visible" : "", className].filter(Boolean).join(" "),
    role: "status",
    "aria-live": "polite"
  }, children);
}
Object.assign(__ds_scope, { Toast });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/Toast.jsx", error: String((e && e.message) || e) }); }

// components/forms/AttendanceSelector.jsx
try { (() => {
const CSS = `
.saqz-attend{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;font-family:var(--font-ui);}
.saqz-attend__btn{min-height:48px;padding:10px 8px;border:1px solid var(--saqz-border);
  border-radius:var(--radius-pill);background:var(--saqz-white);color:var(--saqz-navy);
  font-family:var(--font-ui);font-size:13px;font-weight:700;cursor:pointer;
  transition:transform .12s ease,border-color .12s ease,background .12s ease,color .12s ease;}
.saqz-attend__btn:hover{border-color:#b7c0d3;}
.saqz-attend__btn:active{transform:scale(.98);}
.saqz-attend__btn:focus-visible{outline:3px solid var(--brand-fill-11);outline-offset:2px;}
.saqz-attend__btn.is-active[data-intent="going"]{color:var(--saqz-white);border-color:var(--saqz-blue);background:var(--saqz-blue);}
.saqz-attend__btn.is-active[data-intent="maybe"]{color:var(--saqz-navy);border-color:var(--saqz-lime);background:var(--saqz-lime);}
.saqz-attend__btn.is-active[data-intent="out"]{color:var(--saqz-white);border-color:var(--saqz-error);background:var(--saqz-error);}
`;
function inject() {
  if (typeof document === "undefined") return;
  if (document.getElementById("saqz-attendance-css")) return;
  const s = document.createElement("style");
  s.id = "saqz-attendance-css";
  s.textContent = CSS;
  document.head.appendChild(s);
}
const OPTIONS = [{
  intent: "going",
  label: "Vou"
}, {
  intent: "maybe",
  label: "Talvez"
}, {
  intent: "out",
  label: "Não vou"
}];

/**
 * Three unambiguous presence actions. Selected fills with its status color
 * (going→blue, maybe→lime, out→red). value/onSelect are controlled.
 */
function AttendanceSelector({
  value,
  onSelect,
  options = OPTIONS,
  className = ""
}) {
  inject();
  return /*#__PURE__*/React.createElement("div", {
    className: ["saqz-attend", className].filter(Boolean).join(" "),
    role: "group",
    "aria-label": "Confirmar presen\xE7a"
  }, options.map(o => {
    const active = value === o.intent;
    return /*#__PURE__*/React.createElement("button", {
      key: o.intent,
      type: "button",
      "data-intent": o.intent,
      "aria-pressed": active,
      className: "saqz-attend__btn" + (active ? " is-active" : ""),
      onClick: () => onSelect && onSelect(o.intent)
    }, o.label);
  }));
}
Object.assign(__ds_scope, { AttendanceSelector });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/AttendanceSelector.jsx", error: String((e && e.message) || e) }); }

// components/forms/Input.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const CSS = `
.saqz-field{display:grid;gap:7px;font-family:var(--font-ui);}
.saqz-field__label{font-size:14px;font-weight:600;color:var(--saqz-navy);}
.saqz-field__wrap{min-height:54px;display:flex;align-items:center;gap:12px;padding:0 15px;
  border:1px solid var(--saqz-border);border-radius:var(--radius-input);background:var(--saqz-white);
  transition:border-color .2s ease,box-shadow .2s ease;}
.saqz-field__wrap:focus-within{border-color:var(--saqz-blue);box-shadow:0 0 0 3px var(--brand-fill-11);}
.saqz-field__wrap--invalid{border-color:var(--saqz-error);box-shadow:0 0 0 3px var(--error-fill-10);}
.saqz-field__icon{flex:0 0 auto;display:inline-flex;color:var(--saqz-blue);}
.saqz-field__input{width:100%;min-width:0;border:0;outline:0;background:transparent;
  color:var(--saqz-navy);font-family:var(--font-ui);font-size:16px;}
.saqz-field__input::placeholder{color:var(--saqz-placeholder);}
.saqz-field__trailing{display:inline-flex;flex:0 0 auto;}
.saqz-field__error{min-height:16px;color:var(--saqz-error);font-size:12px;}
`;
function inject() {
  if (typeof document === "undefined") return;
  if (document.getElementById("saqz-input-css")) return;
  const s = document.createElement("style");
  s.id = "saqz-input-css";
  s.textContent = CSS;
  document.head.appendChild(s);
}

/**
 * Labeled text field. Label is always visible (never placeholder-only).
 * Left icon is blue; optional trailing node for show/hide toggles etc.
 */
function Input({
  label,
  icon,
  trailing,
  error,
  invalid = false,
  id,
  className = "",
  ...rest
}) {
  inject();
  const fieldId = id || rest.name;
  const showInvalid = invalid || !!error;
  return /*#__PURE__*/React.createElement("div", {
    className: ["saqz-field", className].filter(Boolean).join(" ")
  }, label ? /*#__PURE__*/React.createElement("label", {
    className: "saqz-field__label",
    htmlFor: fieldId
  }, label) : null, /*#__PURE__*/React.createElement("div", {
    className: "saqz-field__wrap" + (showInvalid ? " saqz-field__wrap--invalid" : "")
  }, icon ? /*#__PURE__*/React.createElement("span", {
    className: "saqz-field__icon"
  }, icon) : null, /*#__PURE__*/React.createElement("input", _extends({
    id: fieldId,
    className: "saqz-field__input"
  }, rest)), trailing ? /*#__PURE__*/React.createElement("span", {
    className: "saqz-field__trailing"
  }, trailing) : null), error ? /*#__PURE__*/React.createElement("div", {
    className: "saqz-field__error"
  }, error) : null);
}
Object.assign(__ds_scope, { Input });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Input.jsx", error: String((e && e.message) || e) }); }

// components/navigation/BottomNav.jsx
try { (() => {
const CSS = `
.saqz-bottomnav{border-top:1px solid var(--saqz-border);background:rgba(255,255,255,.96);
  backdrop-filter:blur(18px);-webkit-backdrop-filter:blur(18px);font-family:var(--font-ui);}
.saqz-bottomnav__inner{display:grid;align-items:center;height:var(--bottom-nav-height,76px);
  padding:4px 8px 6px;}
.saqz-bottomnav__item{display:grid;justify-items:center;align-content:center;gap:4px;min-height:60px;
  border:0;border-radius:10px;background:transparent;color:var(--saqz-muted);
  font-family:var(--font-ui);font-size:11px;font-weight:600;cursor:pointer;}
.saqz-bottomnav__item.is-active{color:var(--saqz-blue);}
.saqz-bottomnav__icon{display:inline-flex;}
.saqz-bottomnav__indicator{width:18px;height:3px;border-radius:var(--radius-pill);background:transparent;}
.saqz-bottomnav__item.is-active .saqz-bottomnav__indicator{background:var(--saqz-lime);}
`;
function inject() {
  if (typeof document === "undefined") return;
  if (document.getElementById("saqz-bottomnav-css")) return;
  const s = document.createElement("style");
  s.id = "saqz-bottomnav-css";
  s.textContent = CSS;
  document.head.appendChild(s);
}

/**
 * Fixed-bottom tab bar. Active tab turns blue with a lime underline indicator.
 * items: [{ id, label, icon }]. Controlled via `active`/`onChange`.
 */
function BottomNav({
  items = [],
  active,
  onChange,
  className = ""
}) {
  inject();
  return /*#__PURE__*/React.createElement("nav", {
    className: ["saqz-bottomnav", className].filter(Boolean).join(" "),
    "aria-label": "Navega\xE7\xE3o principal"
  }, /*#__PURE__*/React.createElement("div", {
    className: "saqz-bottomnav__inner",
    style: {
      gridTemplateColumns: `repeat(${items.length}, 1fr)`
    }
  }, items.map(it => {
    const isActive = active === it.id;
    return /*#__PURE__*/React.createElement("button", {
      key: it.id,
      type: "button",
      className: "saqz-bottomnav__item" + (isActive ? " is-active" : ""),
      "aria-current": isActive ? "page" : undefined,
      onClick: () => onChange && onChange(it.id)
    }, /*#__PURE__*/React.createElement("span", {
      className: "saqz-bottomnav__icon"
    }, it.icon), /*#__PURE__*/React.createElement("span", null, it.label), /*#__PURE__*/React.createElement("span", {
      className: "saqz-bottomnav__indicator"
    }));
  })));
}
Object.assign(__ds_scope, { BottomNav });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/BottomNav.jsx", error: String((e && e.message) || e) }); }

// components/navigation/TopAppBar.jsx
try { (() => {
const CSS = `
.saqz-topbar{display:flex;align-items:center;justify-content:space-between;gap:12px;
  min-height:52px;font-family:var(--font-ui);color:var(--saqz-navy);}
.saqz-topbar__leading{display:flex;align-items:center;gap:10px;min-width:0;}
.saqz-topbar__back{width:44px;height:44px;display:grid;place-items:center;border:0;border-radius:50%;
  background:transparent;color:var(--saqz-blue);cursor:pointer;}
.saqz-topbar__back:hover{background:var(--saqz-white);}
.saqz-topbar__logo{display:block;height:36px;width:auto;object-fit:contain;}
.saqz-topbar__title{margin:0;font-size:22px;line-height:1.15;font-weight:700;letter-spacing:-.02em;
  overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.saqz-topbar__actions{display:flex;align-items:center;gap:4px;flex:0 0 auto;}
`;
function inject() {
  if (typeof document === "undefined") return;
  if (document.getElementById("saqz-topappbar-css")) return;
  const s = document.createElement("style");
  s.id = "saqz-topappbar-css";
  s.textContent = CSS;
  document.head.appendChild(s);
}
const BACK = /*#__PURE__*/React.createElement("svg", {
  width: "24",
  height: "24",
  viewBox: "0 0 24 24",
  "aria-hidden": "true"
}, /*#__PURE__*/React.createElement("path", {
  d: "m15 5-7 7 7 7",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinecap: "round",
  strokeLinejoin: "round"
}));

/**
 * Top app bar. Either shows the brand logo (home surfaces) or a back button +
 * title (detail surfaces). `actions` holds trailing IconButtons.
 */
function TopAppBar({
  logoSrc,
  title,
  onBack,
  actions,
  className = ""
}) {
  inject();
  return /*#__PURE__*/React.createElement("header", {
    className: ["saqz-topbar", className].filter(Boolean).join(" ")
  }, /*#__PURE__*/React.createElement("div", {
    className: "saqz-topbar__leading"
  }, onBack ? /*#__PURE__*/React.createElement("button", {
    type: "button",
    className: "saqz-topbar__back",
    onClick: onBack,
    "aria-label": "Voltar"
  }, BACK) : null, logoSrc ? /*#__PURE__*/React.createElement("img", {
    className: "saqz-topbar__logo",
    src: logoSrc,
    alt: "Saqz"
  }) : title ? /*#__PURE__*/React.createElement("h1", {
    className: "saqz-topbar__title"
  }, title) : null), actions ? /*#__PURE__*/React.createElement("div", {
    className: "saqz-topbar__actions"
  }, actions) : null);
}
Object.assign(__ds_scope, { TopAppBar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/TopAppBar.jsx", error: String((e && e.message) || e) }); }

// ui_kits/mobile-app/App.jsx
try { (() => {
// App shell: auth gate, phone chrome, top bar, screen router, bottom nav.
// Owns overlays (BottomSheet, Toast) so they anchor to the fixed-height phone
// viewport, not to the scrolling screen content.
const {
  useState: useStateApp,
  useEffect: useEffectApp
} = React;
function ProfileScreen() {
  const {
    Card,
    MemberRow,
    StatusChip,
    Button,
    SectionHeader
  } = window.SaqzDesignSystem_48df71;
  return /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "8px 16px 24px"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      gap: 10,
      padding: "16px 0 8px"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      width: 84,
      height: 84,
      borderRadius: "50%",
      background: "var(--saqz-ice)",
      color: "var(--saqz-blue)",
      display: "grid",
      placeItems: "center",
      boxShadow: "inset 0 0 0 1px var(--saqz-border)",
      fontSize: 30,
      fontWeight: 700
    }
  }, "RA"), /*#__PURE__*/React.createElement("div", {
    style: {
      textAlign: "center"
    }
  }, /*#__PURE__*/React.createElement("h2", {
    style: {
      margin: 0,
      fontSize: 22,
      fontWeight: 700
    }
  }, "Rafa Almeida"), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: "4px 0 0",
      color: "var(--saqz-muted)",
      fontSize: 14
    }
  }, "rafa@galera.com \xB7 Ponteiro"))), /*#__PURE__*/React.createElement(SectionHeader, {
    title: "Prefer\xEAncias"
  }), /*#__PURE__*/React.createElement(Card, {
    flush: true,
    padded: false
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "0 12px"
    }
  }, /*#__PURE__*/React.createElement(MemberRow, {
    name: "Notifica\xE7\xF5es de jogo",
    meta: "Push e e-mail",
    trailing: /*#__PURE__*/React.createElement(StatusChip, {
      tone: "success",
      dot: true
    }, "Ativo"),
    onClick: () => {}
  }), /*#__PURE__*/React.createElement(MemberRow, {
    name: "Posi\xE7\xE3o preferida",
    meta: "Ponteiro",
    trailing: /*#__PURE__*/React.createElement("span", {
      style: {
        color: "var(--saqz-muted)"
      }
    }, /*#__PURE__*/React.createElement(window.IconChevron, {
      size: 20
    })),
    onClick: () => {}
  }), /*#__PURE__*/React.createElement(MemberRow, {
    name: "Grupos",
    meta: "3 grupos ativos",
    trailing: /*#__PURE__*/React.createElement("span", {
      style: {
        color: "var(--saqz-muted)"
      }
    }, /*#__PURE__*/React.createElement(window.IconChevron, {
      size: 20
    })),
    onClick: () => {}
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 20
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "secondary",
    fullWidth: true
  }, "Sair da conta")));
}
function GamesScreen({
  onOpenGroup
}) {
  const {
    Card,
    StatusChip
  } = window.SaqzDesignSystem_48df71;
  const rows = [{
    d: "Terça, 21/05 · 19h30",
    v: "Quadra do Parque",
    chip: ["accent", "8/12"]
  }, {
    d: "Quinta, 23/05 · 19h30",
    v: "Quadra do Parque",
    chip: ["neutral", "Aberto"]
  }, {
    d: "Sábado, 25/05 · 16h00",
    v: "Parque Central",
    chip: ["success", "Confirmado"]
  }];
  return /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "6px 16px 24px"
    }
  }, /*#__PURE__*/React.createElement("h1", {
    style: {
      margin: "8px 2px 16px",
      fontSize: 32,
      fontWeight: 700,
      letterSpacing: "-0.03em"
    }
  }, "Jogos"), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gap: 12
    }
  }, rows.map(r => /*#__PURE__*/React.createElement(Card, {
    key: r.d
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      gap: 12,
      alignItems: "center"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      flex: "0 0 auto",
      width: 42,
      height: 42,
      borderRadius: "50%",
      background: "var(--saqz-ice)",
      color: "var(--saqz-blue)",
      display: "grid",
      placeItems: "center"
    }
  }, /*#__PURE__*/React.createElement(window.IconCalendar, {
    size: 22
  })), /*#__PURE__*/React.createElement("span", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("strong", {
    style: {
      display: "block",
      fontSize: 15,
      fontWeight: 600
    }
  }, r.d), /*#__PURE__*/React.createElement("span", {
    style: {
      display: "block",
      marginTop: 4,
      color: "var(--saqz-muted)",
      fontSize: 13
    }
  }, r.v)), /*#__PURE__*/React.createElement(StatusChip, {
    tone: r.chip[0]
  }, r.chip[1]))))));
}
function App() {
  const {
    TopAppBar,
    IconButton,
    BottomNav,
    BottomSheet,
    AttendanceSelector,
    Button,
    Toast
  } = window.SaqzDesignSystem_48df71;
  const [authed, setAuthed] = useStateApp(false);
  const [tab, setTab] = useStateApp("groups");
  const [sub, setSub] = useStateApp(null); // {type:'detail',group} | {type:'create'}
  const [sheet, setSheet] = useStateApp(null); // {title,description} for the attendance sheet
  const [intent, setIntent] = useStateApp(null);
  const [toast, setToast] = useStateApp("");
  useEffectApp(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(""), 2600);
    return () => clearTimeout(t);
  }, [toast]);
  if (!authed) return /*#__PURE__*/React.createElement(window.LoginScreen, {
    onLogin: () => {
      setAuthed(true);
      setTab("groups");
    }
  });
  const openAttendance = info => {
    setIntent(null);
    setSheet(info);
  };
  const bell = /*#__PURE__*/React.createElement(IconButton, {
    "aria-label": "Avisos",
    dot: true
  }, /*#__PURE__*/React.createElement(window.IconBell, {
    size: 24
  }));
  const more = /*#__PURE__*/React.createElement(IconButton, {
    "aria-label": "Mais op\xE7\xF5es"
  }, /*#__PURE__*/React.createElement(window.IconMore, {
    size: 24
  }));
  let top,
    body,
    showNav = true;
  if (sub && sub.type === "detail") {
    top = /*#__PURE__*/React.createElement(TopAppBar, {
      title: sub.group.name,
      onBack: () => setSub(null),
      actions: more
    });
    body = /*#__PURE__*/React.createElement(window.GroupDetailScreen, {
      group: sub.group,
      onConfirm: openAttendance
    });
  } else if (sub && sub.type === "create") {
    top = /*#__PURE__*/React.createElement(TopAppBar, {
      title: "Criar grupo",
      onBack: () => setSub(null)
    });
    body = /*#__PURE__*/React.createElement(window.CreateGroupScreen, {
      onCreate: () => {
        setSub(null);
        setTab("groups");
      }
    });
    showNav = false;
  } else if (tab === "home") {
    top = /*#__PURE__*/React.createElement(TopAppBar, {
      logoSrc: "../../assets/logo-horizontal.png",
      actions: bell
    });
    body = /*#__PURE__*/React.createElement(window.HomeScreen, {
      onOpenGroup: g => setSub({
        type: "detail",
        group: g
      }),
      onToast: setToast
    });
  } else if (tab === "groups") {
    top = /*#__PURE__*/React.createElement(TopAppBar, {
      logoSrc: "../../assets/logo-horizontal.png",
      actions: bell
    });
    body = /*#__PURE__*/React.createElement(window.GroupsScreen, {
      onOpenGroup: g => setSub({
        type: "detail",
        group: g
      }),
      onCreate: () => setSub({
        type: "create"
      })
    });
  } else if (tab === "games") {
    top = /*#__PURE__*/React.createElement(TopAppBar, {
      logoSrc: "../../assets/logo-horizontal.png",
      actions: bell
    });
    body = /*#__PURE__*/React.createElement(GamesScreen, null);
  } else {
    top = /*#__PURE__*/React.createElement(TopAppBar, {
      logoSrc: "../../assets/logo-horizontal.png",
      actions: bell
    });
    body = /*#__PURE__*/React.createElement(ProfileScreen, null);
  }
  return /*#__PURE__*/React.createElement("div", {
    style: {
      position: "relative",
      display: "flex",
      flexDirection: "column",
      height: "100%",
      overflow: "hidden"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      flex: "0 0 auto",
      padding: "6px 16px 0"
    }
  }, top), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: "1 1 auto",
      overflowY: "auto",
      background: "var(--saqz-canvas)"
    }
  }, body), showNav ? /*#__PURE__*/React.createElement("div", {
    style: {
      flex: "0 0 auto"
    }
  }, /*#__PURE__*/React.createElement(BottomNav, {
    active: tab,
    onChange: t => {
      setSub(null);
      setTab(t);
    },
    items: [{
      id: "home",
      label: "Início",
      icon: /*#__PURE__*/React.createElement(window.IconHome, {
        size: 24
      })
    }, {
      id: "games",
      label: "Jogos",
      icon: /*#__PURE__*/React.createElement(window.IconCalendar, {
        size: 24
      })
    }, {
      id: "groups",
      label: "Grupos",
      icon: /*#__PURE__*/React.createElement(window.IconUsers, {
        size: 24
      })
    }, {
      id: "profile",
      label: "Perfil",
      icon: /*#__PURE__*/React.createElement(window.IconUser, {
        size: 24
      })
    }]
  })) : null, /*#__PURE__*/React.createElement(BottomSheet, {
    open: !!sheet,
    onClose: () => setSheet(null),
    title: sheet ? sheet.title : "",
    description: sheet ? sheet.description : "",
    footer: /*#__PURE__*/React.createElement(Button, {
      fullWidth: true,
      disabled: !intent,
      onClick: () => {
        setSheet(null);
        setToast("Presença atualizada. A galera já sabe.");
      }
    }, "Salvar resposta")
  }, /*#__PURE__*/React.createElement(AttendanceSelector, {
    value: intent,
    onSelect: setIntent
  }), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: "12px 0 0",
      color: "var(--saqz-muted)",
      fontSize: 14,
      lineHeight: 1.5
    }
  }, intent === "going" ? "Você entra como confirmado enquanto houver vaga." : intent === "maybe" ? "Marcado como talvez — confirme antes do prazo." : intent === "out" ? "Tudo certo, o organizador será avisado." : "As confirmações encerram 6 horas antes do jogo.")), /*#__PURE__*/React.createElement(Toast, {
    visible: !!toast
  }, toast));
}
window.SaqzApp = App;
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/mobile-app/App.jsx", error: String((e && e.message) || e) }); }

// ui_kits/mobile-app/CreateGroupScreen.jsx
try { (() => {
// Create group — full scrollable form with defaults; sticky primary CTA.
const {
  useState: useStateCG
} = React;
function CreateGroupScreen({
  onCreate
}) {
  const {
    Card,
    Input,
    Button
  } = window.SaqzDesignSystem_48df71;
  const [name, setName] = useStateCG("");
  const [fee, setFee] = useStateCG(false);
  function Section({
    title,
    children
  }) {
    return /*#__PURE__*/React.createElement(Card, null, /*#__PURE__*/React.createElement("h3", {
      style: {
        margin: "0 0 4px",
        fontSize: 21,
        fontWeight: 600,
        letterSpacing: "-0.01em"
      }
    }, title), children);
  }
  function DefaultRow({
    label,
    value
  }) {
    return /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: 12,
        padding: "10px 0",
        borderTop: "1px solid var(--saqz-border)"
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        color: "var(--saqz-muted)",
        fontSize: 15
      }
    }, label), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 15,
        fontWeight: 600
      }
    }, value));
  }
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      minHeight: "100%"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      overflowY: "auto",
      padding: "4px 16px 16px",
      display: "grid",
      gap: 20
    }
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("h1", {
    style: {
      margin: "6px 2px 4px",
      fontSize: 28,
      fontWeight: 700,
      letterSpacing: "-0.02em"
    }
  }, "Novo grupo"), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: "0 2px",
      color: "var(--saqz-muted)",
      fontSize: 16
    }
  }, "Voc\xEA poder\xE1 alterar essas informa\xE7\xF5es depois.")), /*#__PURE__*/React.createElement(Section, {
    title: "Identidade do grupo"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gap: 12,
      marginTop: 14
    }
  }, /*#__PURE__*/React.createElement(Input, {
    label: "Nome do grupo",
    value: name,
    onChange: e => setName(e.target.value),
    placeholder: "Ex.: V\xF4lei da Galera"
  }), /*#__PURE__*/React.createElement(Input, {
    label: "Cidade",
    placeholder: "Ex.: S\xE3o Paulo"
  }))), /*#__PURE__*/React.createElement(Section, {
    title: "Perfil esportivo"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 8
    }
  }, /*#__PURE__*/React.createElement(DefaultRow, {
    label: "Modalidade",
    value: "V\xF4lei de quadra"
  }), /*#__PURE__*/React.createElement(DefaultRow, {
    label: "Composi\xE7\xE3o",
    value: "Misto"
  }), /*#__PURE__*/React.createElement(DefaultRow, {
    label: "N\xEDvel",
    value: "Todos os n\xEDveis"
  }))), /*#__PURE__*/React.createElement(Section, {
    title: "Rotina dos jogos"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 8
    }
  }, /*#__PURE__*/React.createElement(DefaultRow, {
    label: "Limite",
    value: "12 jogadores"
  }), /*#__PURE__*/React.createElement(DefaultRow, {
    label: "Encerramento",
    value: "6 horas antes"
  }))), /*#__PURE__*/React.createElement(Section, {
    title: "Cobran\xE7a"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      gap: 12,
      marginTop: 8
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--saqz-muted)",
      fontSize: 15
    }
  }, "Este grupo tem mensalidade?"), /*#__PURE__*/React.createElement("button", {
    type: "button",
    role: "switch",
    "aria-checked": fee,
    onClick: () => setFee(v => !v),
    style: {
      width: 52,
      height: 30,
      borderRadius: 999,
      border: 0,
      cursor: "pointer",
      position: "relative",
      background: fee ? "var(--saqz-blue)" : "var(--saqz-border)",
      transition: "background .18s ease"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      position: "absolute",
      top: 3,
      left: fee ? 25 : 3,
      width: 24,
      height: 24,
      borderRadius: "50%",
      background: "#fff",
      transition: "left .18s ease",
      boxShadow: "0 1px 3px rgba(0,0,0,.2)"
    }
  }))))), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: "0 0 auto",
      padding: 16,
      borderTop: "1px solid var(--saqz-border)",
      background: "var(--saqz-canvas)"
    }
  }, /*#__PURE__*/React.createElement(Button, {
    fullWidth: true,
    disabled: !name.trim(),
    onClick: onCreate
  }, "Criar grupo")));
}
window.CreateGroupScreen = CreateGroupScreen;
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/mobile-app/CreateGroupScreen.jsx", error: String((e && e.message) || e) }); }

// ui_kits/mobile-app/GroupDetailScreen.jsx
try { (() => {
// Group detail — header, next-game block, quick actions, announcement, members, invite.
const {
  useState: useStateGD
} = React;
function QuickAction({
  icon,
  label
}) {
  return /*#__PURE__*/React.createElement("button", {
    type: "button",
    style: {
      flex: 1,
      minWidth: 0,
      display: "grid",
      justifyItems: "center",
      gap: 8,
      padding: "16px 4px",
      border: "1px solid var(--saqz-border)",
      borderRadius: "var(--radius-card)",
      background: "var(--saqz-white)",
      cursor: "pointer",
      fontFamily: "var(--font-ui)"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--saqz-blue)"
    }
  }, icon), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 14,
      fontWeight: 600,
      color: "var(--saqz-navy)"
    }
  }, label));
}
function GroupDetailScreen({
  group,
  onConfirm
}) {
  const {
    Card,
    StatusChip,
    Button,
    MemberRow,
    SectionHeader
  } = window.SaqzDesignSystem_48df71;
  const {
    members
  } = window.SAQZ_DATA;
  const g = group || window.SAQZ_DATA.groups[0];
  return /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "4px 16px 24px",
      position: "relative",
      minHeight: "100%"
    }
  }, /*#__PURE__*/React.createElement(Card, null, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      gap: 14,
      alignItems: "flex-start"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      flex: "0 0 auto",
      width: 96,
      height: 96,
      borderRadius: "var(--radius-card)",
      background: "var(--saqz-blue)",
      display: "grid",
      placeItems: "center",
      overflow: "hidden"
    }
  }, /*#__PURE__*/React.createElement("img", {
    src: "../../assets/logo-simbolo.png",
    alt: "",
    style: {
      width: 74,
      height: 74,
      objectFit: "contain"
    }
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      gap: 8
    }
  }, /*#__PURE__*/React.createElement("h2", {
    style: {
      margin: 0,
      fontSize: 22,
      fontWeight: 700,
      letterSpacing: "-0.02em"
    }
  }, g.name), g.role === "Admin" ? /*#__PURE__*/React.createElement(StatusChip, {
    tone: "brand"
  }, "Admin") : null), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 8,
      display: "grid",
      gap: 6,
      color: "var(--saqz-muted)",
      fontSize: 14
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 8
    }
  }, /*#__PURE__*/React.createElement(window.IconUsers, {
    size: 18
  }), " ", g.members, " membros"), /*#__PURE__*/React.createElement("span", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 8
    }
  }, /*#__PURE__*/React.createElement(window.IconPin, {
    size: 18
  }), " ", g.city), /*#__PURE__*/React.createElement("span", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 8
    }
  }, /*#__PURE__*/React.createElement(window.IconCalendar, {
    size: 18
  }), " ", g.routine))))), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 16
    }
  }, /*#__PURE__*/React.createElement(Card, {
    tone: "soft"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 10,
      marginBottom: 12
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--saqz-blue)"
    }
  }, /*#__PURE__*/React.createElement(window.IconCalendar, {
    size: 22
  })), /*#__PURE__*/React.createElement("strong", {
    style: {
      fontSize: 18,
      fontWeight: 700
    }
  }, "Pr\xF3ximo jogo")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gap: 8,
      color: "var(--saqz-navy)",
      fontSize: 15
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 8
    }
  }, /*#__PURE__*/React.createElement(window.IconCalendar, {
    size: 18
  }), " Ter\xE7a, 21/05 \xB7 19h30"), /*#__PURE__*/React.createElement("span", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 8
    }
  }, /*#__PURE__*/React.createElement(window.IconPin, {
    size: 18
  }), " Quadra do Parque"), /*#__PURE__*/React.createElement("span", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 8,
      color: "var(--saqz-muted)"
    }
  }, /*#__PURE__*/React.createElement(window.IconUsers, {
    size: 18
  }), " 8 confirmados \xB7 3 talvez \xB7 1 pendente")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gridTemplateColumns: "1fr 1fr",
      gap: 12,
      marginTop: 16
    }
  }, /*#__PURE__*/React.createElement(Button, {
    onClick: () => onConfirm && onConfirm({
      title: "Confirmar presença",
      description: "Terça, 21/05 · 19h30 · Quadra do Parque"
    })
  }, "Confirmar presen\xE7a"), /*#__PURE__*/React.createElement(Button, {
    variant: "secondary"
  }, "Ver detalhes")))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      gap: 10,
      marginTop: 16
    }
  }, /*#__PURE__*/React.createElement(QuickAction, {
    icon: /*#__PURE__*/React.createElement(window.IconCalendar, {
      size: 24
    }),
    label: "Agenda"
  }), /*#__PURE__*/React.createElement(QuickAction, {
    icon: /*#__PURE__*/React.createElement(window.IconBell, {
      size: 24
    }),
    label: "Avisos"
  }), /*#__PURE__*/React.createElement(QuickAction, {
    icon: /*#__PURE__*/React.createElement(window.IconUsers, {
      size: 24
    }),
    label: "Membros"
  }), /*#__PURE__*/React.createElement(QuickAction, {
    icon: /*#__PURE__*/React.createElement(window.IconChat, {
      size: 24
    }),
    label: "Chat"
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 16
    }
  }, /*#__PURE__*/React.createElement(Card, null, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 12,
      marginBottom: 10
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      flex: "0 0 auto",
      width: 40,
      height: 40,
      borderRadius: "50%",
      display: "grid",
      placeItems: "center",
      background: "var(--saqz-ice)",
      color: "var(--saqz-blue)"
    }
  }, /*#__PURE__*/React.createElement(window.IconMega, {
    size: 22
  })), /*#__PURE__*/React.createElement("strong", {
    style: {
      fontSize: 17,
      fontWeight: 700,
      flex: 1
    }
  }, "Aviso recente"), /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--saqz-muted)"
    }
  }, /*#__PURE__*/React.createElement(window.IconMore, {
    size: 22
  }))), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: "0 0 4px",
      fontSize: 15
    }
  }, /*#__PURE__*/React.createElement("strong", null, "Lucas"), " ", /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--saqz-blue)",
      fontWeight: 700
    }
  }, "(Admin)")), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: 0,
      color: "var(--saqz-navy)",
      fontSize: 15,
      lineHeight: 1.45
    }
  }, "Pessoal, cheguem 15 min antes para montar a rede. Levem \xE1gua!"), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: "8px 0 0",
      color: "var(--saqz-muted)",
      fontSize: 13
    }
  }, "Hoje, 10h30"))), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 16
    }
  }, /*#__PURE__*/React.createElement(SectionHeader, {
    title: "Membros",
    icon: /*#__PURE__*/React.createElement(window.IconUsers, {
      size: 22
    }),
    action: "Ver todos"
  }), /*#__PURE__*/React.createElement(Card, {
    flush: true,
    padded: false
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "0 12px"
    }
  }, members.slice(0, 4).map(m => /*#__PURE__*/React.createElement(MemberRow, {
    key: m.name,
    name: m.name,
    role: m.role,
    meta: m.meta,
    trailing: m.role === "Admin" ? /*#__PURE__*/React.createElement(StatusChip, {
      tone: "brand"
    }, "Admin") : m.intent === "going" ? /*#__PURE__*/React.createElement(StatusChip, {
      tone: "success",
      dot: true
    }, m.waitlist ? "Reserva" : "Vou") : m.intent === "maybe" ? /*#__PURE__*/React.createElement(StatusChip, {
      tone: "warning",
      dot: true
    }, "Talvez") : /*#__PURE__*/React.createElement(StatusChip, {
      tone: "neutral"
    }, "Fora")
  }))))), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 16
    }
  }, /*#__PURE__*/React.createElement(Card, {
    tone: "soft"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 14
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      flex: "0 0 auto",
      width: 52,
      height: 52,
      borderRadius: "50%",
      display: "grid",
      placeItems: "center",
      background: "var(--saqz-white)",
      color: "var(--saqz-blue)",
      boxShadow: "inset 0 0 0 1px var(--saqz-border)"
    }
  }, /*#__PURE__*/React.createElement(window.IconInvite, {
    size: 26
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("strong", {
    style: {
      display: "block",
      fontSize: 16,
      fontWeight: 700
    }
  }, "Convidar mais gente"), /*#__PURE__*/React.createElement("span", {
    style: {
      display: "block",
      marginTop: 3,
      color: "var(--saqz-muted)",
      fontSize: 14,
      lineHeight: 1.4
    }
  }, "Compartilhe o grupo e organize sua galera com mais facilidade."))), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 12
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    rightIcon: /*#__PURE__*/React.createElement(window.IconArrowRight, {
      size: 18
    })
  }, "Enviar convite")))));
}
window.GroupDetailScreen = GroupDetailScreen;
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/mobile-app/GroupDetailScreen.jsx", error: String((e && e.message) || e) }); }

// ui_kits/mobile-app/GroupsScreen.jsx
try { (() => {
// "Meus grupos" — search, filter chips, group list, create/invite CTAs.
const {
  useState: useStateGroups
} = React;
function GroupsScreen({
  onOpenGroup,
  onCreate
}) {
  const {
    Card,
    StatusChip,
    Button
  } = window.SaqzDesignSystem_48df71;
  const {
    groups
  } = window.SAQZ_DATA;
  const [filter, setFilter] = useStateGroups("todos");
  const filters = [{
    id: "todos",
    label: "Todos"
  }, {
    id: "ativos",
    label: "Ativos"
  }, {
    id: "convites",
    label: "Convites"
  }];
  return /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "6px 16px 24px"
    }
  }, /*#__PURE__*/React.createElement("h1", {
    style: {
      margin: "8px 2px 6px",
      fontSize: 34,
      lineHeight: 1.08,
      letterSpacing: "-0.035em",
      fontWeight: 700
    }
  }, "Meus grupos"), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: "0 2px 20px",
      color: "var(--saqz-muted)",
      fontSize: 17,
      lineHeight: 1.45
    }
  }, "Organize sua galera e acompanhe tudo em um s\xF3 lugar."), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 12,
      minHeight: 54,
      padding: "0 15px",
      border: "1px solid var(--saqz-border)",
      borderRadius: "var(--radius-input)",
      background: "var(--saqz-white)",
      marginBottom: 16
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--saqz-muted)",
      display: "inline-flex"
    }
  }, /*#__PURE__*/React.createElement(window.IconSearch, {
    size: 22
  })), /*#__PURE__*/React.createElement("input", {
    placeholder: "Buscar grupo",
    style: {
      border: 0,
      outline: 0,
      background: "transparent",
      fontSize: 16,
      width: "100%",
      fontFamily: "var(--font-ui)",
      color: "var(--saqz-navy)"
    }
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      gap: 10,
      marginBottom: 18
    }
  }, filters.map(f => {
    const active = filter === f.id;
    return /*#__PURE__*/React.createElement("button", {
      key: f.id,
      type: "button",
      onClick: () => setFilter(f.id),
      style: {
        minHeight: 40,
        padding: "0 18px",
        borderRadius: "var(--radius-pill)",
        cursor: "pointer",
        fontFamily: "var(--font-ui)",
        fontSize: 15,
        fontWeight: 600,
        border: active ? "1px solid var(--saqz-blue)" : "1px solid var(--saqz-border)",
        background: active ? "var(--saqz-blue)" : "var(--saqz-white)",
        color: active ? "#fff" : "var(--saqz-navy)"
      }
    }, f.label);
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gap: 12
    }
  }, groups.map(g => /*#__PURE__*/React.createElement(Card, {
    key: g.id,
    padded: false
  }, /*#__PURE__*/React.createElement("button", {
    type: "button",
    onClick: () => onOpenGroup(g),
    style: {
      width: "100%",
      textAlign: "left",
      background: "transparent",
      border: 0,
      cursor: "pointer",
      padding: 16,
      display: "flex",
      gap: 12,
      alignItems: "center"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      flex: "0 0 auto",
      width: 56,
      height: 56,
      borderRadius: "50%",
      display: "grid",
      placeItems: "center",
      background: "var(--saqz-ice)",
      color: "var(--saqz-blue)",
      boxShadow: "inset 0 0 0 1px var(--saqz-border)"
    }
  }, /*#__PURE__*/React.createElement(window.IconUsers, {
    size: 26
  })), /*#__PURE__*/React.createElement("span", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("strong", {
    style: {
      display: "block",
      fontSize: 18,
      fontWeight: 700
    }
  }, g.name), /*#__PURE__*/React.createElement("span", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 6,
      marginTop: 5,
      color: "var(--saqz-muted)",
      fontSize: 14
    }
  }, /*#__PURE__*/React.createElement(window.IconPin, {
    size: 16
  }), " ", g.city, " \xB7 ", g.members, " membros"), /*#__PURE__*/React.createElement("span", {
    style: {
      display: "inline-block",
      marginTop: 8
    }
  }, /*#__PURE__*/React.createElement(StatusChip, {
    tone: g.badge.tone,
    dot: g.badge.dot
  }, g.badge.label))), /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--saqz-muted)",
      flex: "0 0 auto"
    }
  }, /*#__PURE__*/React.createElement(window.IconChevron, {
    size: 22
  })))))), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 20,
      display: "grid",
      gap: 10,
      justifyItems: "center"
    }
  }, /*#__PURE__*/React.createElement(Button, {
    fullWidth: true,
    leftIcon: /*#__PURE__*/React.createElement(window.IconPlus, {
      size: 20
    }),
    onClick: onCreate
  }, "Criar novo grupo"), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    leftIcon: /*#__PURE__*/React.createElement(window.IconInvite, {
      size: 20
    })
  }, "Entrar com convite")));
}
window.GroupsScreen = GroupsScreen;
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/mobile-app/GroupsScreen.jsx", error: String((e && e.message) || e) }); }

// ui_kits/mobile-app/HomeScreen.jsx
try { (() => {
// Member home — greeting, next-game hero with live attendance, announcements, group.
const {
  useState: useStateHome
} = React;
function HomeScreen({
  onOpenGroup,
  onToast
}) {
  const {
    GameSummaryCard,
    AttendanceSelector,
    SectionHeader,
    Card,
    MemberRow,
    StatusChip
  } = window.SaqzDesignSystem_48df71;
  const {
    groups,
    announcements
  } = window.SAQZ_DATA;
  const [intent, setIntent] = useStateHome(null);
  const msgs = {
    going: "Presença confirmada. A galera já sabe que você vai.",
    maybe: "Você marcou “Talvez”. Dá para alterar depois.",
    out: "Resposta atualizada. O organizador já sabe que você não vai."
  };
  const note = {
    going: "Você está confirmado. Pode alterar até o prazo.",
    maybe: "Marcado como talvez. Confirme quando decidir.",
    out: "Você marcou que não vai."
  };
  function choose(i) {
    setIntent(i);
    onToast && onToast(msgs[i]);
  }
  return /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "4px 16px 24px"
    }
  }, /*#__PURE__*/React.createElement("section", {
    style: {
      marginBottom: 20
    }
  }, /*#__PURE__*/React.createElement("h1", {
    style: {
      margin: "0 0 4px",
      fontSize: 32,
      lineHeight: 1.1,
      letterSpacing: "-0.035em",
      fontWeight: 700
    }
  }, "Fala, Rafa! \uD83D\uDC4B"), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: 0,
      color: "var(--saqz-muted)",
      fontSize: 17
    }
  }, "Veja o que est\xE1 rolando com a sua galera.")), /*#__PURE__*/React.createElement(GameSummaryCard, {
    title: "Ter\xE7a, 21/05 \xB7 19h30",
    venue: "Quadra do Parque",
    address: "Rua das Flores, 100 \xB7 Centro",
    confirmed: 12,
    maybe: 3,
    out: 2
  }, /*#__PURE__*/React.createElement(AttendanceSelector, {
    value: intent,
    onSelect: choose
  }), /*#__PURE__*/React.createElement("p", {
    style: {
      minHeight: 20,
      margin: "10px 4px 0",
      color: "var(--saqz-muted)",
      fontSize: 13,
      textAlign: "center"
    }
  }, intent ? note[intent] : "Confirme com um toque. Você pode alterar depois.")), /*#__PURE__*/React.createElement("section", {
    style: {
      marginTop: 24
    }
  }, /*#__PURE__*/React.createElement(SectionHeader, {
    title: "Avisos do grupo",
    action: "Ver todos"
  }), /*#__PURE__*/React.createElement(Card, {
    flush: true,
    padded: false
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "0 12px"
    }
  }, announcements.map(a => /*#__PURE__*/React.createElement(MemberRow, {
    key: a.id,
    name: a.title,
    avatar: undefined,
    meta: `${a.author} · ${a.time}`,
    trailing: /*#__PURE__*/React.createElement("span", {
      style: {
        color: "var(--saqz-muted)"
      }
    }, /*#__PURE__*/React.createElement(window.IconChevron, {
      size: 20
    }))
  }))))), /*#__PURE__*/React.createElement("section", {
    style: {
      marginTop: 24
    }
  }, /*#__PURE__*/React.createElement(SectionHeader, {
    title: "Seu grupo",
    action: "Ver grupo",
    onAction: () => onOpenGroup(groups[1])
  }), /*#__PURE__*/React.createElement(Card, null, /*#__PURE__*/React.createElement("button", {
    type: "button",
    onClick: () => onOpenGroup(groups[1]),
    style: {
      width: "100%",
      textAlign: "left",
      background: "transparent",
      border: 0,
      cursor: "pointer",
      display: "flex",
      gap: 12,
      alignItems: "center"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      flex: "0 0 auto",
      width: 52,
      height: 52,
      borderRadius: "50%",
      display: "grid",
      placeItems: "center",
      background: "var(--saqz-ice)",
      color: "var(--saqz-blue)",
      boxShadow: "inset 0 0 0 1px var(--saqz-border)"
    }
  }, /*#__PURE__*/React.createElement(window.IconUsers, {
    size: 26
  })), /*#__PURE__*/React.createElement("span", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("strong", {
    style: {
      display: "block",
      fontSize: 16,
      fontWeight: 700
    }
  }, "Galera do V\xF4lei"), /*#__PURE__*/React.createElement("span", {
    style: {
      display: "block",
      marginTop: 3,
      color: "var(--saqz-muted)",
      fontSize: 13
    }
  }, "28 membros \xB7 Misto \xB7 Todos os n\xEDveis")), /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--saqz-muted)"
    }
  }, /*#__PURE__*/React.createElement(window.IconChevron, {
    size: 22
  }))))), /*#__PURE__*/React.createElement("section", {
    style: {
      marginTop: 24
    }
  }, /*#__PURE__*/React.createElement(SectionHeader, {
    title: "\xDAltimos jogos",
    action: "Ver hist\xF3rico"
  }), /*#__PURE__*/React.createElement(Card, null, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      gap: 12,
      alignItems: "center"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      flex: "0 0 auto",
      width: 42,
      height: 42,
      borderRadius: "50%",
      display: "grid",
      placeItems: "center",
      background: "var(--saqz-ice)",
      color: "var(--saqz-blue)"
    }
  }, /*#__PURE__*/React.createElement(window.IconCalendar, {
    size: 22
  })), /*#__PURE__*/React.createElement("span", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("strong", {
    style: {
      display: "block",
      fontSize: 15,
      fontWeight: 600
    }
  }, "Quinta, 16/05 \xB7 19h30"), /*#__PURE__*/React.createElement("span", {
    style: {
      display: "block",
      marginTop: 4,
      color: "var(--saqz-muted)",
      fontSize: 13
    }
  }, "Quadra do Parque")), /*#__PURE__*/React.createElement(StatusChip, {
    tone: "accent"
  }, "14/14")))));
}
window.HomeScreen = HomeScreen;
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/mobile-app/HomeScreen.jsx", error: String((e && e.message) || e) }); }

// ui_kits/mobile-app/Icons.jsx
try { (() => {
// Saqz icon set — inline line glyphs (1.8px stroke, rounded caps) matching the
// reference prototypes. Exported to window for the other babel scripts.
const {
  createElement: h
} = React;
function Svg({
  size = 24,
  children,
  ...rest
}) {
  return h("svg", {
    width: size,
    height: size,
    viewBox: "0 0 24 24",
    fill: "none",
    "aria-hidden": "true",
    ...rest
  }, children);
}
const S = {
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round",
  strokeLinejoin: "round"
};
const IconHome = p => h(Svg, p, h("path", {
  d: "m3 11 9-8 9 8v9a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1v-9Z",
  ...S
}));
const IconCalendar = p => h(Svg, p, h("rect", {
  x: 4,
  y: 5,
  width: 16,
  height: 15,
  rx: 3,
  ...S
}), h("path", {
  d: "M8 3v4M16 3v4M4 10h16",
  ...S
}));
const IconUsers = p => h(Svg, p, h("circle", {
  cx: 9,
  cy: 8,
  r: 3,
  ...S
}), h("circle", {
  cx: 17,
  cy: 9,
  r: 2.4,
  ...S
}), h("path", {
  d: "M3.5 19a5.5 5.5 0 0 1 11 0M14 18a4 4 0 0 1 7 0",
  ...S
}));
const IconUser = p => h(Svg, p, h("circle", {
  cx: 12,
  cy: 8,
  r: 4,
  ...S
}), h("path", {
  d: "M4.5 21a7.5 7.5 0 0 1 15 0",
  ...S
}));
const IconBell = p => h(Svg, p, h("path", {
  d: "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9",
  ...S
}), h("path", {
  d: "M10 21h4",
  ...S
}));
const IconPin = p => h(Svg, p, h("path", {
  d: "M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z",
  ...S
}), h("circle", {
  cx: 12,
  cy: 10,
  r: 2.5,
  fill: "#C7F300",
  stroke: "currentColor",
  strokeWidth: 1.2
}));
const IconChevron = p => h(Svg, p, h("path", {
  d: "m9 6 6 6-6 6",
  ...S
}));
const IconChevronLeft = p => h(Svg, p, h("path", {
  d: "m15 5-7 7 7 7",
  ...S
}));
const IconMega = p => h(Svg, p, h("path", {
  d: "m3 11 12-5v12L3 13v-2Z",
  ...S
}), h("path", {
  d: "M7 14v5h3l1-4",
  ...S
}), h("path", {
  d: "M18 9c1 .7 1.5 1.7 1.5 3S19 14.3 18 15",
  stroke: "#C7F300",
  strokeWidth: 2,
  strokeLinecap: "round",
  fill: "none"
}));
const IconChat = p => h(Svg, p, h("path", {
  d: "M20 15a3 3 0 0 1-3 3H8l-4 3V6a3 3 0 0 1 3-3h10a3 3 0 0 1 3 3v9Z",
  ...S
}));
const IconSearch = p => h(Svg, p, h("circle", {
  cx: 11,
  cy: 11,
  r: 7,
  ...S
}), h("path", {
  d: "m20 20-3.5-3.5",
  ...S
}));
const IconPlus = p => h(Svg, p, h("path", {
  d: "M12 5v14M5 12h14",
  ...S
}));
const IconMail = p => h(Svg, p, h("path", {
  d: "M3.5 6.5A2.5 2.5 0 0 1 6 4h12a2.5 2.5 0 0 1 2.5 2.5v11A2.5 2.5 0 0 1 18 20H6a2.5 2.5 0 0 1-2.5-2.5v-11Z",
  ...S
}), h("path", {
  d: "m5 7 7 5 7-5",
  ...S
}));
const IconLock = p => h(Svg, p, h("rect", {
  x: 4,
  y: 9,
  width: 16,
  height: 11,
  rx: 3,
  ...S
}), h("path", {
  d: "M8 9V7a4 4 0 1 1 8 0v2M12 13v3",
  ...S
}));
const IconEye = p => h(Svg, p, h("path", {
  d: "M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6S2.5 12 2.5 12Z",
  ...S
}), h("circle", {
  cx: 12,
  cy: 12,
  r: 2.7,
  ...S
}));
const IconEyeOff = p => h(Svg, p, h("path", {
  d: "M4 4l16 16M9.5 5.4A9 9 0 0 1 12 6c6 0 9.5 6 9.5 6a17 17 0 0 1-3 3.6M6.2 7.6A17 17 0 0 0 2.5 12S6 18 12 18a8.7 8.7 0 0 0 3-.5",
  ...S
}), h("path", {
  d: "M9.9 9.9a3 3 0 0 0 4.2 4.2",
  ...S
}));
const IconMore = p => h(Svg, p, h("circle", {
  cx: 5,
  cy: 12,
  r: 1.6,
  fill: "currentColor",
  stroke: "none"
}), h("circle", {
  cx: 12,
  cy: 12,
  r: 1.6,
  fill: "currentColor",
  stroke: "none"
}), h("circle", {
  cx: 19,
  cy: 12,
  r: 1.6,
  fill: "currentColor",
  stroke: "none"
}));
const IconArrowRight = p => h(Svg, p, h("path", {
  d: "m9 5 7 7-7 7",
  stroke: "currentColor",
  strokeWidth: 2,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  fill: "none"
}));
const IconCheck = p => h(Svg, p, h("circle", {
  cx: 12,
  cy: 12,
  r: 9,
  ...S
}), h("path", {
  d: "m8.5 12 2.5 2.5 4.5-5",
  ...S
}));
const IconInvite = p => h(Svg, p, h("circle", {
  cx: 9,
  cy: 8,
  r: 3.2,
  ...S
}), h("path", {
  d: "M3.5 20a5.5 5.5 0 0 1 11 0M18 7v6M15 10h6",
  ...S
}));
Object.assign(window, {
  SaqzSvg: Svg,
  IconHome,
  IconCalendar,
  IconUsers,
  IconUser,
  IconBell,
  IconPin,
  IconChevron,
  IconChevronLeft,
  IconMega,
  IconChat,
  IconSearch,
  IconPlus,
  IconMail,
  IconLock,
  IconEye,
  IconEyeOff,
  IconMore,
  IconArrowRight,
  IconCheck,
  IconInvite
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/mobile-app/Icons.jsx", error: String((e && e.message) || e) }); }

// ui_kits/mobile-app/LoginScreen.jsx
try { (() => {
// Saqz login screen — email/password with show/hide, forgot link, sign-up link.
const {
  useState
} = React;
function LoginScreen({
  onLogin
}) {
  const {
    Button,
    Input,
    IconButton
  } = window.SaqzDesignSystem_48df71;
  const [show, setShow] = useState(false);
  const [email, setEmail] = useState("rafa@galera.com");
  const [pw, setPw] = useState("volei123");
  return /*#__PURE__*/React.createElement("div", {
    style: {
      position: "relative",
      minHeight: "100%",
      padding: "16px 26px 40px",
      background: "var(--saqz-white)",
      overflow: "hidden"
    }
  }, /*#__PURE__*/React.createElement("div", {
    "aria-hidden": "true",
    style: {
      position: "absolute",
      width: 290,
      height: 290,
      right: -118,
      top: 36,
      opacity: 0.035,
      border: "18px solid var(--saqz-blue)",
      borderRadius: "50%"
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      justifyContent: "center",
      margin: "18px 0 22px"
    }
  }, /*#__PURE__*/React.createElement("img", {
    src: "../../assets/logo-horizontal.png",
    alt: "Saqz",
    style: {
      height: 44
    }
  })), /*#__PURE__*/React.createElement("header", {
    style: {
      textAlign: "center",
      marginBottom: 26
    }
  }, /*#__PURE__*/React.createElement("h1", {
    style: {
      margin: 0,
      fontSize: 34,
      lineHeight: 1.08,
      letterSpacing: "-0.04em",
      fontWeight: 750
    }
  }, "Organize seu grupo.", /*#__PURE__*/React.createElement("br", null), "Jogue ", /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--saqz-blue)"
    }
  }, "junto.")), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: "12px auto 0",
      maxWidth: 300,
      color: "var(--saqz-muted)",
      fontSize: 16,
      lineHeight: 1.5
    }
  }, "Entre na sua conta e mantenha sua galera sempre alinhada.")), /*#__PURE__*/React.createElement("form", {
    style: {
      display: "grid",
      gap: 14,
      position: "relative",
      zIndex: 1
    },
    onSubmit: e => {
      e.preventDefault();
      onLogin();
    }
  }, /*#__PURE__*/React.createElement(Input, {
    label: "E-mail ou telefone",
    name: "identity",
    icon: /*#__PURE__*/React.createElement(window.IconMail, {
      size: 22
    }),
    value: email,
    onChange: e => setEmail(e.target.value),
    placeholder: "Digite seu e-mail"
  }), /*#__PURE__*/React.createElement(Input, {
    label: "Senha",
    name: "password",
    type: show ? "text" : "password",
    value: pw,
    onChange: e => setPw(e.target.value),
    placeholder: "Digite sua senha",
    icon: /*#__PURE__*/React.createElement(window.IconLock, {
      size: 22
    }),
    trailing: /*#__PURE__*/React.createElement(IconButton, {
      "aria-label": show ? "Ocultar senha" : "Mostrar senha",
      onClick: () => setShow(s => !s)
    }, show ? /*#__PURE__*/React.createElement(window.IconEyeOff, {
      size: 22
    }) : /*#__PURE__*/React.createElement(window.IconEye, {
      size: 22
    }))
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      justifyContent: "flex-end",
      marginTop: -2
    }
  }, /*#__PURE__*/React.createElement("a", {
    href: "#",
    onClick: e => e.preventDefault(),
    style: {
      color: "var(--saqz-blue)",
      fontSize: 14,
      fontWeight: 600,
      textDecoration: "none"
    }
  }, "Esqueci minha senha")), /*#__PURE__*/React.createElement(Button, {
    type: "submit",
    fullWidth: true,
    rightIcon: /*#__PURE__*/React.createElement(window.IconArrowRight, {
      size: 18
    })
  }, "Entrar")), /*#__PURE__*/React.createElement("p", {
    style: {
      textAlign: "center",
      marginTop: 26,
      color: "var(--saqz-muted)",
      fontSize: 14
    }
  }, "Ainda n\xE3o tem uma conta?", " ", /*#__PURE__*/React.createElement("a", {
    href: "#",
    onClick: e => e.preventDefault(),
    style: {
      color: "var(--saqz-blue)",
      fontWeight: 700,
      textDecoration: "none"
    }
  }, "Criar conta")));
}
window.LoginScreen = LoginScreen;
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/mobile-app/LoginScreen.jsx", error: String((e && e.message) || e) }); }

// ui_kits/mobile-app/data.js
try { (() => {
// Shared demo data for the Saqz UI kit.
window.SAQZ_DATA = {
  user: {
    name: "Rafa"
  },
  groups: [{
    id: "sabado",
    name: "Vôlei de Sábado",
    city: "Parque Central",
    members: 18,
    badge: {
      tone: "brand",
      label: "Próximo: Sáb, 16:00"
    },
    role: "Admin",
    routine: "Sáb • 16h00",
    cover: null
  }, {
    id: "quadra7",
    name: "Galera da Quadra 7",
    city: "Zona Norte",
    members: 12,
    badge: {
      tone: "warning",
      label: "2 avisos novos",
      dot: true
    },
    role: "Membro",
    routine: "Ter e Qui • 19h30"
  }, {
    id: "amigos",
    name: "Amigos do Vôlei",
    city: "Centro",
    members: 9,
    badge: {
      tone: "success",
      label: "Confirmar presença",
      dot: true
    },
    role: "Membro",
    routine: "Qua • 20h00"
  }],
  members: [{
    name: "Lucas Prado",
    role: "Admin",
    meta: "Levantador"
  }, {
    name: "Amanda Reis",
    meta: "Ponteira",
    intent: "going"
  }, {
    name: "Bruno Costa",
    meta: "Central",
    intent: "going"
  }, {
    name: "Carla Nunes",
    meta: "Líbero",
    intent: "maybe"
  }, {
    name: "Diego Alves",
    meta: "Oposto · na reserva",
    intent: "going",
    waitlist: true
  }, {
    name: "Elisa Moraes",
    meta: "Ponteira",
    intent: "out"
  }],
  announcements: [{
    id: "a1",
    author: "Lucas",
    role: "Admin",
    title: "Levar bolas para o jogo de terça!",
    time: "hoje às 14h32",
    accent: true
  }, {
    id: "a2",
    author: "Amanda",
    title: "Sábado teremos jogo extra às 16h",
    time: "ontem às 09h15"
  }]
};
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/mobile-app/data.js", error: String((e && e.message) || e) }); }

__ds_ns.Button = __ds_scope.Button;

__ds_ns.IconButton = __ds_scope.IconButton;

__ds_ns.Card = __ds_scope.Card;

__ds_ns.GameSummaryCard = __ds_scope.GameSummaryCard;

__ds_ns.MemberRow = __ds_scope.MemberRow;

__ds_ns.SectionHeader = __ds_scope.SectionHeader;

__ds_ns.StatusChip = __ds_scope.StatusChip;

__ds_ns.BottomSheet = __ds_scope.BottomSheet;

__ds_ns.EmptyState = __ds_scope.EmptyState;

__ds_ns.Toast = __ds_scope.Toast;

__ds_ns.AttendanceSelector = __ds_scope.AttendanceSelector;

__ds_ns.Input = __ds_scope.Input;

__ds_ns.BottomNav = __ds_scope.BottomNav;

__ds_ns.TopAppBar = __ds_scope.TopAppBar;

})();
