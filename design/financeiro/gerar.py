#!/usr/bin/env python3
"""Gera os artboards (.dc.html) do canvas do financeiro (claude.ai/artifact/9SGq9SxGvzAbeHCaGEtM1F): hoje (prints do
Roborazzi) e as propostas do caixa, da entrada e saída, do acerto com a diária retroativa (FIN-016), do valor por jogo
obrigatório (JOGO-008) e da vaga só com pagamento antecipado, além das prévias em HTML puro que o Brave mede.

Uso: python3 gerar.py ['{"Caixa-A": 1000, ...}']   (alturas medidas; sem elas, 844)

Tokens e medidas copiados de mobile/core/design-system e das telas aprovadas (tela de jogo cheia, lista de grupos H).
Dados de exemplo coerentes: mensalidade R$ 80 com 12 mensalistas, diária R$ 20 (80 ÷ 4), quadra R$ 160 por jogo,
saldo inicial R$ 250; outubro: entrou R$ 880, saiu R$ 640, saldo R$ 490, falta receber R$ 280 (3 × 80 + 2 × 20).
"""
import json
import os
import sys

OUT = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.join(OUT, "project")
RENDER = os.path.join(OUT, "previa")
os.makedirs(PROJECT, exist_ok=True)
os.makedirs(RENDER, exist_ok=True)

# ---- tokens do app ----
BLUE = "#0638DF"
LIME = "#C7F300"
NAVY = "#0E1738"
MUTED = "#667085"
PLACEHOLDER = "#98A2B3"
BORDER = "#D8DDE8"
ICE = "#F4F8FB"
CANVAS = "#F5F5F7"
WHITE = "#FFFFFF"
SUCCESS = "#17B26A"
SUCCESS_FG = "#0E7A4B"
WARN = "#F5A623"
WARN_FG = "#8F5600"
ERROR = "#C9363B"
# Vermelho claro das saídas sobre o azul da marca: 4,7:1 com o #0638DF (texto grande e botão).
OUT_ON_BLUE = "#FFB3B8"
W82 = "rgba(255,255,255,0.82)"
W72 = "rgba(255,255,255,0.72)"
W60 = "rgba(255,255,255,0.6)"
W18 = "rgba(255,255,255,0.18)"
W14 = "rgba(255,255,255,0.14)"
W12 = "rgba(255,255,255,0.12)"
W08 = "rgba(255,255,255,0.08)"
LIME18 = "rgba(199,243,0,0.18)"
LIME14 = "rgba(199,243,0,0.14)"
BRAND08 = "rgba(6,56,223,0.08)"
SUCCESS12 = "rgba(23,178,106,0.12)"
WARN14 = "rgba(245,166,35,0.14)"
ERROR10 = "rgba(201,54,59,0.10)"
NEUTRAL10 = "rgba(102,112,133,0.10)"
SCRIM = "rgba(14,23,56,0.46)"

UI = "font-family: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
DISPLAY = "font-family: Anybody, 'Arial Black', Inter, sans-serif; font-stretch: 110%; font-weight: 800"
FONTS = ("https://fonts.googleapis.com/css2?family=Anybody:wdth,wght@110,800"
         "&family=Inter:wght@400;500;600;700&display=swap")

BALL_PATHS = (
    '<path d="M 597,108 C 493,207 449,406 583,580 C 533,663 469,727 397,767 C 263,638 211,410 279,252 C 294,230 313,211 333,196 C 416,145 503,118 597,108 Z"></path>'
    '<path d="M 649,108 C 854,108 1037,248 1113,441 C 940,284 741,222 548,265 C 567,196 603,144 649,108 Z"></path>'
    '<path d="M 542,309 C 764,255 1001,350 1138,531 C 1161,607 1155,701 1125,772 C 1009,584 802,480 590,518 C 557,457 539,382 542,309 Z"></path>'
    '<path d="M 634,552 C 733,535 842,561 928,610 C 861,854 601,1047 317,1048 C 259,1003 211,945 176,878 C 388,858 553,744 634,552 Z"></path>'
    '<path d="M 962,632 C 1032,690 1083,757 1107,826 C 1009,1040 826,1146 627,1146 C 532,1146 443,1122 365,1078 C 674,1050 897,852 962,632 Z"></path>'
    '<path d="M 277,245 C 111,400 52,642 157,844 C 228,834 296,815 360,784 C 232,649 206,445 277,245 Z"></path>'
)


def ball(size=210, right=-58, top=-6):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 1254 1254" fill="#FFFFFF" aria-hidden="true" '
            f'style="position: absolute; right: {right}px; top: {top}px; opacity: 0.2;">{BALL_PATHS}</svg>')


ICONS = {
    "back": '<path d="m15 18-6-6 6-6"></path>',
    "x": '<path d="M18 6 6 18"></path><path d="m6 6 12 12"></path>',
    "more": '<circle cx="12" cy="12" r="1"></circle><circle cx="19" cy="12" r="1"></circle><circle cx="5" cy="12" r="1"></circle>',
    "more_v": '<circle cx="12" cy="12" r="1"></circle><circle cx="12" cy="5" r="1"></circle><circle cx="12" cy="19" r="1"></circle>',
    "plus": '<path d="M5 12h14"></path><path d="M12 5v14"></path>',
    "minus": '<path d="M5 12h14"></path>',
    "check": '<path d="M20 6 9 17l-5-5"></path>',
    "chevron": '<path d="m9 18 6-6-6-6"></path>',
    "chevron_down": '<path d="m6 9 6 6 6-6"></path>',
    "card": '<rect width="20" height="14" x="2" y="5" rx="2"></rect><line x1="2" x2="22" y1="10" y2="10"></line>',
    "calendar": '<path d="M8 2v4"></path><path d="M16 2v4"></path><rect width="18" height="18" x="3" y="4" rx="2"></rect><path d="M3 10h18"></path>',
    "clock": '<circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline>',
    "timer": '<line x1="10" x2="14" y1="2" y2="2"></line><line x1="12" x2="15" y1="14" y2="11"></line><circle cx="12" cy="14" r="8"></circle>',
    "pin": '<path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"></path><circle cx="12" cy="10" r="3"></circle>',
    "megaphone": '<path d="m3 11 18-5v12L3 14v-3z"></path><path d="M11.6 16.8a3 3 0 1 1-5.8-1.6"></path>',
    "in": '<path d="M17 7 7 17"></path><path d="M17 17H7V7"></path>',
    "out": '<path d="M7 17 17 7"></path><path d="M7 7h10v10"></path>',
    "list": '<path d="M3 12h.01"></path><path d="M3 18h.01"></path><path d="M3 6h.01"></path><path d="M8 12h13"></path><path d="M8 18h13"></path><path d="M8 6h13"></path>',
    "alert": '<circle cx="12" cy="12" r="10"></circle><line x1="12" x2="12" y1="8" y2="12"></line><line x1="12" x2="12.01" y1="16" y2="16"></line>',
    "copy": '<rect width="14" height="14" x="8" y="8" rx="2" ry="2"></rect><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"></path>',
    "flag": '<path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"></path><line x1="4" x2="4" y1="22" y2="15"></line>',
    "users": '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><path d="M22 21v-2a4 4 0 0 0-3-3.87"></path><path d="M16 3.13a4 4 0 0 1 0 7.75"></path>',
    "user_x": '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><line x1="17" x2="22" y1="8" y2="13"></line><line x1="22" x2="17" y1="8" y2="13"></line>',
    "bell": '<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"></path><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"></path>',
    "banknote": '<rect width="20" height="12" x="2" y="6" rx="2"></rect><circle cx="12" cy="12" r="2"></circle><path d="M6 12h.01M18 12h.01"></path>',
    "wallet": '<path d="M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1"></path><path d="M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4"></path>',
    "rotate": '<path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"></path><path d="M3 3v5h5"></path>',
}


def icon(name, size=20, color="currentColor"):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" stroke="{color}" '
            f'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" '
            f'style="flex: none;">{ICONS[name]}</svg>')


def initials(name):
    words = [w for w in name.split() if w[0].isalpha()]
    return (words[0][0] + words[-1][0]).upper() if len(words) > 1 else words[0][:2].upper()


# ---------------------------------------------------------------- moldura
# O que vem depois deste marcador cobre a tela inteira (folha com fundo escurecido).
OVERLAY = "<!--overlay-->"


def page(inner, height, bottom="", bg=CANVAS):
    inner, _, overlay = inner.partition(OVERLAY)
    bar = (f'<div style="margin-top: auto; flex: none; display: flex; flex-direction: column; gap: 8px; '
           f'padding: 12px 16px 20px; background: {WHITE}; border-top: 1px solid {BORDER};">{bottom}</div>'
           if bottom else "")
    return (
        f'<div style="position: relative; width: 390px; height: {height}px; box-sizing: border-box; '
        f'background: {bg}; {UI}; color: {NAVY}; display: flex; flex-direction: column; overflow: hidden;">'
        f'<div style="display: flex; flex-direction: column; flex: none;">{inner}</div>{bar}{overlay}</div>'
    )


def appbar(title, trailing=None, close=False, bg=CANVAS):
    lead = "x" if close else "back"
    trail = ""
    if trailing:
        trail = (f'<button aria-label="{trailing}" style="width: 44px; height: 44px; flex: none; display: flex; '
                 f'align-items: center; justify-content: center; padding: 0; border: 0; border-radius: 22px; '
                 f'background: transparent; color: {BLUE};">{icon("more", 24)}</button>')
    return (
        f'<div style="display: flex; align-items: center; gap: 4px; min-height: 56px; box-sizing: border-box; '
        f'padding: 6px 8px 6px 4px; background: {bg}; flex: none;">'
        f'<button aria-label="{"Fechar" if close else "Voltar"}" style="width: 44px; height: 44px; flex: none; '
        f'display: flex; align-items: center; justify-content: center; padding: 0; border: 0; border-radius: 22px; '
        f'background: transparent; color: {BLUE};">{icon(lead, 24)}</button>'
        f'<span style="flex-grow: 1; min-width: 0; font-size: 18px; line-height: 23px; font-weight: 600; '
        f'color: {NAVY}; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">{title}</span>{trail}</div>'
    )


def column(items, gap=16, pad="8px 16px 24px"):
    return f'<div style="display: flex; flex-direction: column; gap: {gap}px; padding: {pad};">{"".join(items)}</div>'


# ---------------------------------------------------------------- peças do azul
def hero(parts, gap=12):
    return (f'<section style="position: relative; overflow: hidden; background: {BLUE}; padding: 16px 20px 20px; '
            f'display: flex; flex-direction: column; gap: {gap}px; color: {WHITE}; flex: none;">{ball()}'
            f'{"".join(parts)}</section>')


def kicker_row(kicker, trailing="", muted_dot=False):
    halo, dot = (W18, W60) if muted_dot else (LIME18, LIME)
    return (
        f'<div style="position: relative; display: flex; align-items: center; justify-content: space-between; gap: 8px; '
        f'min-height: 26px;"><div style="display: flex; align-items: center; gap: 9px; min-width: 0;">'
        f'<span style="width: 16px; height: 16px; flex: none; border-radius: 8px; background: {halo}; display: flex; '
        f'align-items: center; justify-content: center;"><span style="width: 8px; height: 8px; border-radius: 4px; '
        f'background: {dot};"></span></span>'
        f'<span style="font-size: 12px; line-height: 16px; font-weight: 600; letter-spacing: 0.08em; '
        f'text-transform: uppercase; color: {W82}; white-space: nowrap;">{kicker}</span></div>{trailing}</div>'
    )


def month_chip(label):
    return (f'<button style="position: relative; flex: none; display: flex; align-items: center; gap: 4px; '
            f'min-height: 32px; padding: 0 8px 0 12px; border: 0; border-radius: 999px; background: {W14}; '
            f'color: {WHITE}; font-family: inherit; font-size: 13px; line-height: 16px; font-weight: 600;">'
            f'{label}{icon("chevron_down", 16)}</button>')


def big_money(value, size=40):
    return (f'<p style="position: relative; margin: 0; {DISPLAY}; font-size: {size}px; line-height: {size + 4}px; '
            f'letter-spacing: -0.03em; color: {WHITE}; white-space: nowrap;">{value}</p>')


def display_title(text, size=36):
    return (f'<h1 style="position: relative; margin: 0; {DISPLAY}; font-size: {size}px; line-height: {size + 2}px; '
            f'letter-spacing: -0.03em; color: {WHITE};">{text}</h1>')


def hero_meta(text, strong=False):
    weight, color = ("600", WHITE) if strong else ("500", W82)
    return (f'<p style="position: relative; margin: 0; font-size: 14px; line-height: 20px; font-weight: {weight}; '
            f'color: {color};">{text}</p>')


def info_row(icon_name, text, extra=""):
    extra_html = (f'<span style="font-weight: 400; color: {W82};"> · {extra}</span>' if extra else "")
    return (f'<div style="position: relative; display: flex; align-items: center; gap: 8px;">{icon(icon_name, 18, W82)}'
            f'<span style="font-size: 15px; line-height: 20px; font-weight: 600; color: {WHITE};">{text}{extra_html}'
            f'</span></div>')


def hero_buttons(pairs):
    """pairs: lista de (rótulo, ícone, 'white'|'outline')."""
    cells = []
    for label, icon_name, kind in pairs:
        if kind == "white":
            style = f"border: 0; background: {WHITE}; color: {NAVY};"
        elif kind == "lime":
            style = f"border: 0; background: {LIME}; color: {NAVY};"
        elif kind == "outline_out":
            style = f"border: 1.5px solid {OUT_ON_BLUE}; background: transparent; color: {OUT_ON_BLUE};"
        else:
            style = f"border: 1.5px solid {W60}; background: transparent; color: {WHITE};"
        ic = icon(icon_name, 20) if icon_name else ""
        cells.append(f'<button style="height: 52px; display: flex; align-items: center; justify-content: center; '
                     f'gap: 8px; border-radius: 999px; {style} font-family: inherit; font-size: 16px; '
                     f'line-height: 20px; font-weight: 600;">{ic}{label}</button>')
    return (f'<div style="position: relative; display: grid; grid-template-columns: repeat({len(pairs)}, '
            f'minmax(0, 1fr)); gap: 8px;">{"".join(cells)}</div>')


def pending_row(icon_name, text, action, sub=None):
    sub_html = (f'<span style="font-size: 13px; line-height: 18px; color: {W82};">{sub}</span>' if sub else "")
    return (
        f'<div style="position: relative; display: flex; align-items: center; gap: 12px; min-height: 56px; '
        f'box-sizing: border-box; padding: 8px 8px 8px 14px; border-radius: 12px; background: {W12};">'
        f'{icon(icon_name, 20, WHITE)}<span style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; '
        f'gap: 1px;"><span style="font-size: 14px; line-height: 20px; font-weight: 600; color: {WHITE};">{text}</span>'
        f'{sub_html}</span><button style="flex: none; min-height: 40px; padding: 0 18px; border: 0; border-radius: 999px; '
        f'background: {WHITE}; color: {NAVY}; font-family: inherit; font-size: 14px; line-height: 20px; '
        f'font-weight: 600;">{action}</button></div>'
    )


def placar(cells, tabs=True):
    """cells: lista de (numero, sufixo, rotulo, selecionado[, tom]). Números em Anybody, como o placar do jogo;
    o tom "out" pinta o número de vermelho claro (saídas)."""
    out = []
    for cell in cells:
        number, suffix, label, selected = cell[:4]
        number_color = OUT_ON_BLUE if len(cell) > 4 and cell[4] == "out" else WHITE
        suffix_html = (f'<span style="font-size: 13px; line-height: 16px; font-weight: 600; color: {W60};">{suffix}'
                       f'</span>' if suffix else "")
        indicator = (f'<span style="position: absolute; left: 50%; bottom: 0; width: 44px; margin-left: -22px; '
                     f'height: 4px; border-radius: 4px 4px 0 0; background: {LIME};"></span>' if selected else "")
        label_style = f"font-weight: 600; color: {WHITE};" if selected or not tabs else f"font-weight: 400; color: {W72};"
        size = 22 if number.startswith("R$") else 28
        tag = "button" if tabs else "div"
        role = (f' role="tab" aria-selected="{"true" if selected else "false"}"' if tabs else "")
        out.append(
            f'<{tag}{role} style="position: relative; display: flex; flex-direction: column; align-items: center; gap: 4px; '
            f'padding: 14px 4px 16px; border: 0; background: transparent; font-family: inherit;">'
            f'<span style="display: flex; align-items: baseline; gap: 1px; min-height: 28px;">'
            f'<span style="{DISPLAY}; font-size: {size}px; line-height: 28px; letter-spacing: -0.03em; color: {number_color}; '
            f'white-space: nowrap;">{number}</span>{suffix_html}</span>'
            f'<span style="font-size: 12px; line-height: 16px; {label_style}">{label}</span>{indicator}</{tag}>'
        )
    role = ' role="tablist"' if tabs else ""
    return (f'<div{role} style="position: relative; display: grid; grid-template-columns: repeat({len(cells)}, '
            f'minmax(0, 1fr)); margin: 4px -20px -20px; background: {W08}; border-top: 1px solid {W18};">'
            f'{"".join(out)}</div>')


def hero_chip(text, dot=WHITE):
    return (f'<span style="position: relative; align-self: flex-start; display: flex; align-items: center; gap: 6px; '
            f'font-size: 12px; line-height: 16px; font-weight: 600; padding: 5px 10px; border-radius: 999px; '
            f'background: {W14}; color: {WHITE};"><span style="width: 6px; height: 6px; border-radius: 3px; '
            f'background: {dot};"></span>{text}</span>')


def hero_box(icon_name, title, body_text, extra=""):
    return (
        f'<div style="position: relative; display: flex; flex-direction: column; gap: 12px; padding: 14px 16px; '
        f'border-radius: 12px; background: {W12};"><div style="display: flex; align-items: flex-start; gap: 12px;">'
        f'<span style="width: 36px; height: 36px; flex: none; border-radius: 18px; background: {W14}; display: flex; '
        f'align-items: center; justify-content: center; color: {WHITE};">{icon(icon_name, 20)}</span>'
        f'<div style="display: flex; flex-direction: column; gap: 2px; min-width: 0;">'
        f'<span style="font-size: 15px; line-height: 20px; font-weight: 600; color: {WHITE};">{title}</span>'
        f'<span style="font-size: 14px; line-height: 20px; color: {W82};">{body_text}</span></div></div>{extra}</div>'
    )


def fee_line(text):
    return (f'<p style="position: relative; margin: 0; display: flex; align-items: center; gap: 8px; font-size: 14px; '
            f'line-height: 20px; font-weight: 600; color: {WHITE};">{icon("banknote", 18, W82)}<span>{text}</span></p>')


# ---------------------------------------------------------------- peças comuns
def chip(text, tone="neutral", dot=False):
    bg, fg, dot_color = {
        "brand": (BRAND08, BLUE, BLUE),
        "success": (SUCCESS12, SUCCESS_FG, SUCCESS),
        "warning": (WARN14, WARN_FG, WARN),
        "error": (ERROR10, ERROR, ERROR),
        "neutral": (NEUTRAL10, MUTED, MUTED),
    }[tone]
    dot_html = (f'<span style="width: 6px; height: 6px; flex: none; border-radius: 3px; background: {dot_color};"></span>'
                if dot else "")
    return (f'<span style="flex: none; display: inline-flex; align-items: center; gap: 6px; font-size: 12px; '
            f'line-height: 16px; font-weight: 600; padding: 4px 10px; border-radius: 999px; background: {bg}; '
            f'color: {fg}; white-space: nowrap;">{dot_html}{text}</span>')


def btn(label, kind="primary", size="md", icon_name=None, full=False, disabled=False):
    height, font, pad = {"sm": (36, 14, 14), "md": (44, 15, 18), "lg": (52, 16, 20)}[size]
    style = {
        "primary": f"border: 0; background: {BLUE}; color: {WHITE};",
        "secondary": f"border: 1.5px solid {BLUE}; background: {WHITE}; color: {BLUE};",
        "ghost": f"border: 0; background: transparent; color: {BLUE};",
        "danger": f"border: 0; background: transparent; color: {ERROR};",
        "out": f"border: 1.5px solid {ERROR}; background: {WHITE}; color: {ERROR};",
    }[kind]
    if disabled:
        style = f"border: 0; background: {BORDER}; color: {MUTED};"
    width = " width: 100%;" if full else " flex: none;"
    ic = icon(icon_name, 18) if icon_name else ""
    return (f'<button style="display: flex; align-items: center; justify-content: center; gap: 6px; '
            f'min-height: {height}px; box-sizing: border-box; padding: 0 {pad}px; border-radius: 999px; {style}{width} '
            f'font-family: inherit; font-size: {font}px; line-height: 20px; font-weight: 600; white-space: nowrap;">'
            f'{ic}{label}</button>')


def more_button(name):
    return (f'<button aria-label="Ações de {name}" style="width: 40px; height: 40px; flex: none; margin-right: -10px; '
            f'display: flex; align-items: center; justify-content: center; padding: 0; border: 0; border-radius: 20px; '
            f'background: transparent; color: {MUTED};">{icon("more_v", 20)}</button>')


def avatar(name, muted=False, size=40):
    color = MUTED if muted else BLUE
    return (f'<span aria-hidden="true" style="width: {size}px; height: {size}px; flex: none; box-sizing: border-box; '
            f'border-radius: {size // 2}px; background: {ICE}; border: 1px solid {BORDER}; display: flex; '
            f'align-items: center; justify-content: center; font-size: 14px; line-height: 14px; font-weight: 600; '
            f'color: {color};">{initials(name)}</span>')


def icon_tile(icon_name, tone="brand", size=40):
    bg, fg = {"brand": (BRAND08, BLUE), "in": (SUCCESS12, SUCCESS_FG), "out": (ERROR10, ERROR),
              "neutral": (NEUTRAL10, MUTED), "warning": (WARN14, WARN_FG)}[tone]
    return (f'<span aria-hidden="true" style="width: {size}px; height: {size}px; flex: none; border-radius: '
            f'{size // 2}px; background: {bg}; color: {fg}; display: flex; align-items: center; '
            f'justify-content: center;">{icon(icon_name, 20)}</span>')


def text_block(title, sub=None, muted=False, sub_color=MUTED):
    color = MUTED if muted else NAVY
    sub_html = (f'<span style="font-size: 14px; line-height: 20px; color: {sub_color}; white-space: nowrap; '
                f'overflow: hidden; text-overflow: ellipsis;">{sub}</span>' if sub else "")
    return (f'<span style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px;">'
            f'<span style="font-size: 16px; line-height: 22px; font-weight: 600; color: {color}; white-space: nowrap; '
            f'overflow: hidden; text-overflow: ellipsis;">{title}</span>{sub_html}</span>')


def row(left, middle, trailing="", first=False, below="", bg="", pad_left=16, below_left=None):
    top = "" if first else f" border-top: 1px solid {BORDER};"
    below_left = pad_left + 52 if below_left is None else below_left
    below_html = (f'<div style="display: flex; align-items: center; gap: 8px; padding: 0 16px 14px '
                  f'{below_left}px;">{below}</div>' if below else "")
    return (f'<div style="display: flex; flex-direction: column;{top}{bg}">'
            f'<div style="display: flex; align-items: center; gap: 12px; min-height: 64px; box-sizing: border-box; '
            f'padding: 12px 16px 12px {pad_left}px;">{left}{middle}{trailing}</div>{below_html}</div>')


def money(text, tone=None):
    color = {"in": SUCCESS_FG, "out": ERROR, None: NAVY}[tone]
    return (f'<span style="flex: none; font-size: 15px; line-height: 20px; font-weight: 600; color: {color}; '
            f'white-space: nowrap;">{text}</span>')


def group_header(label, right="", first=False):
    top = "" if first else f" border-top: 1px solid {BORDER};"
    right_html = (f'<span style="font-size: 13px; line-height: 16px; font-weight: 600; color: {MUTED}; '
                  f'white-space: nowrap;">{right}</span>' if isinstance(right, str) and right and not right.startswith("<")
                  else right)
    return (f'<div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; '
            f'min-height: 44px; box-sizing: border-box; padding: 8px 16px; background: {ICE};{top}">'
            f'<span style="font-size: 15px; line-height: 18px; font-weight: 600; color: {NAVY};">{label}</span>'
            f'{right_html}</div>')


def white_list(rows):
    return f'<div style="display: flex; flex-direction: column; background: {WHITE};">{"".join(rows)}</div>'


def card(items, gap=0, pad="0"):
    return (f'<div style="display: flex; flex-direction: column; gap: {gap}px; padding: {pad}; background: {WHITE}; '
            f'border: 1px solid {BORDER}; border-radius: 16px; overflow: hidden;">{"".join(items)}</div>')


def section_title(text, right=""):
    return (f'<div style="display: flex; align-items: baseline; justify-content: space-between; gap: 12px;">'
            f'<h2 style="margin: 0; font-size: 18px; line-height: 24px; font-weight: 600; color: {NAVY};">{text}</h2>'
            f'{right}</div>')


def support(text, color=MUTED, center=False):
    align = " text-align: center;" if center else ""
    return f'<p style="margin: 0; font-size: 14px; line-height: 20px; color: {color};{align}">{text}</p>'


def caption(text):
    """Etiqueta de estado, só nas pranchas que mostram vários estados do mesmo campo."""
    return (f'<p style="margin: 0; font-size: 12px; line-height: 16px; font-weight: 600; letter-spacing: 0.06em; '
            f'text-transform: uppercase; color: {MUTED};">{text}</p>')


def label(text, required=False):
    return (f'<span style="font-size: 15px; line-height: 20px; font-weight: 600; color: {NAVY};">{text}</span>')


def segmented(options, selected=None, error=False, size=15, height=44):
    cells = []
    for opt in options:
        on = opt == selected
        style = (f"background: {BLUE}; color: {WHITE}; font-weight: 600;" if on
                 else f"background: transparent; color: {NAVY}; font-weight: 500;")
        cells.append(f'<button role="radio" aria-checked="{"true" if on else "false"}" style="min-width: 0; '
                     f'height: {height - 8}px; padding: 0 2px; border: 0; border-radius: 999px; {style} '
                     f'font-family: inherit; font-size: {size}px; line-height: 18px; white-space: nowrap;">{opt}</button>')
    border = ERROR if error else BORDER
    return (f'<div role="radiogroup" style="display: grid; grid-template-columns: repeat({len(options)}, minmax(0, 1fr)); '
            f'gap: 2px; padding: 3px; box-sizing: border-box; border: 1px solid {border}; border-radius: 999px; '
            f'background: {WHITE};">{"".join(cells)}</div>')


def money_field(value=None, placeholder="0,00", error=False, disabled=False, big=False):
    border = ERROR if error else BORDER
    bg = ICE if disabled else WHITE
    if big:
        text = value or placeholder
        color = NAVY if value else PLACEHOLDER
        return (f'<label style="display: flex; align-items: baseline; gap: 8px; padding: 6px 0 10px; '
                f'border-bottom: 2px solid {BLUE};"><span style="font-size: 20px; line-height: 24px; font-weight: 600; '
                f'color: {MUTED};">R$</span><span style="{DISPLAY}; font-size: 40px; line-height: 44px; '
                f'letter-spacing: -0.03em; color: {color};">{text}</span></label>')
    if disabled:
        inner = f'<span style="font-size: 16px; line-height: 22px; font-weight: 500; color: {MUTED};">{value}</span>'
    else:
        text = value or placeholder
        color = NAVY if value else PLACEHOLDER
        inner = (f'<span style="font-size: 16px; line-height: 22px; font-weight: 600; color: {MUTED};">R$</span>'
                 f'<span style="font-size: 16px; line-height: 22px; color: {color};">{text}</span>')
    return (f'<div style="display: flex; align-items: center; gap: 10px; min-height: 52px; box-sizing: border-box; '
            f'padding: 0 16px; border: 1px solid {border}; border-radius: 12px; background: {bg};">{inner}</div>')


def text_field(value, icon_name=None, placeholder=False):
    color = PLACEHOLDER if placeholder else NAVY
    ic = icon(icon_name, 20, MUTED) if icon_name else ""
    return (f'<div style="display: flex; align-items: center; gap: 10px; min-height: 52px; box-sizing: border-box; '
            f'padding: 0 16px; border: 1px solid {BORDER}; border-radius: 12px; background: {WHITE};">'
            f'{ic}<span style="flex-grow: 1; font-size: 16px; line-height: 22px; color: {color};">{value}</span></div>')


def field(label_text, control, apoio=None, error=None):
    extra = ""
    if error:
        extra = (f'<p style="margin: 0; display: flex; align-items: center; gap: 6px; font-size: 14px; line-height: 20px; '
                 f'font-weight: 500; color: {ERROR};">{icon("alert", 16, ERROR)}<span>{error}</span></p>')
    elif apoio:
        extra = support(apoio)
    return (f'<div style="display: flex; flex-direction: column; gap: 8px;">{label(label_text)}{control}{extra}</div>')


def toggle(on):
    bg = BLUE if on else BORDER
    pos = "22px" if on else "2px"
    return (f'<span role="switch" aria-checked="{"true" if on else "false"}" style="position: relative; width: 52px; '
            f'height: 32px; flex: none; border-radius: 16px; background: {bg};"><span style="position: absolute; '
            f'top: 2px; left: {pos}; width: 28px; height: 28px; border-radius: 14px; background: {WHITE}; '
            f'box-shadow: 0 1px 2px rgba(14,23,56,0.2);"></span></span>')


def toggle_row(title, apoio, on):
    return (f'<div style="display: flex; align-items: center; gap: 16px;"><span style="flex-grow: 1; min-width: 0; '
            f'display: flex; flex-direction: column; gap: 4px;"><span style="font-size: 16px; line-height: 22px; '
            f'font-weight: 600; color: {NAVY};">{title}</span>{support(apoio)}</span>{toggle(on)}</div>')


def sheet(parts, title):
    return (
        f'{OVERLAY}<div style="position: absolute; inset: 0; background: {SCRIM};"></div>'
        f'<div role="dialog" aria-label="{title}" style="position: absolute; left: 0; right: 0; bottom: 0; '
        f'display: flex; flex-direction: column; gap: 16px; padding: 10px 16px 24px; background: {WHITE}; '
        f'border-radius: 24px 24px 0 0;"><span style="align-self: center; width: 40px; height: 4px; '
        f'border-radius: 2px; background: {BORDER};"></span>{"".join(parts)}</div>'
    )


def placeholder_block(text, height=160):
    return (f'<div style="display: flex; align-items: center; justify-content: center; height: {height}px; '
            f'box-sizing: border-box; padding: 16px; border: 1.5px dashed {BORDER}; border-radius: 16px; '
            f'font-size: 14px; line-height: 20px; color: {MUTED}; text-align: center;">{text}</div>')


# ================================================================ 1 · caixa
def debtor(name, meta, first=False):
    return row(avatar(name), text_block(name, meta), btn("Recebi", "secondary", "sm") + more_button(name), first=first)


def caixa_a_hero(tab):
    return hero([
        kicker_row("Saldo do caixa", month_chip("Outubro")),
        big_money("R$ 490,00"),
        hero_meta("Inclui o saldo inicial de R$ 250"),
        hero_buttons([("Entrada", "plus", "white"), ("Saída", "minus", "outline_out")]),
        pending_row("users", "6 jogaram sem cobrança", "Revisar", "Em 2 jogos de outubro"),
        placar([("R$ 880", "", "Entrou", tab == "in"), ("R$ 640", "", "Saiu", tab == "out", "out"),
                ("R$ 280", "", "Falta receber", tab == "due")]),
    ])


def board_caixa_a_saiu():
    body = white_list([
        group_header("Saídas de outubro", "4 lançamentos", first=True),
        ledger_row("out", "out", "Quadra", "Terça, 27/10", "−R$ 160", first=True),
        ledger_row("out", "out", "Quadra", "Terça, 20/10", "−R$ 160"),
        ledger_row("out", "out", "Quadra", "Terça, 13/10", "−R$ 160"),
        ledger_row("out", "out", "Quadra", "Terça, 06/10", "−R$ 160"),
    ])
    return appbar("Vôlei do CERET", trailing="Mais ações do caixa") + caixa_a_hero("out") + body, ""


def board_caixa_a():
    top = caixa_a_hero("due")
    body = white_list([
        group_header("Mensalidades de outubro", "9 de 12 pagaram", first=True),
        debtor("Camila Alves", "Venceu 10/10 · R$ 80", first=True),
        debtor("Pedro Henrique", "Venceu 10/10 · R$ 80"),
        debtor("Thiago Melo", "Venceu 10/10 · R$ 80"),
        group_header("Diárias", "2 em aberto"),
        debtor("Caio Lima", "Terça, 27/10 · R$ 20", first=True),
        debtor("Bia Souza", "Convidada Júlia · R$ 20"),
    ])
    inner = appbar("Vôlei do CERET", trailing="Mais ações do caixa") + top + body
    return inner, btn("Cobrar os 5 que faltam", "primary", "lg", "megaphone", full=True)


def board_caixa_a_menu():
    inner = board_caixa_a()[0] + sheet([
        f'<h2 style="margin: 0; font-size: 20px; line-height: 26px; font-weight: 700; color: {NAVY};">Mais ações do caixa</h2>',
        card([
            resolve_row("flag", "brand", "Saldo inicial", "R$ 250 · antes do Saqz", first=True),
            resolve_row("calendar", "brand", "Criar cobranças do mês", "Mensalidades de outubro"),
        ]),
        support("O Pix do grupo fica na página do grupo.", center=True),
    ], "Mais ações do caixa")
    return inner, ""


def action_circle(icon_name, text):
    return (f'<button style="display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 0; '
            f'border: 0; background: transparent; font-family: inherit;"><span style="width: 52px; height: 52px; '
            f'box-sizing: border-box; border-radius: 26px; background: {WHITE}; border: 1px solid {BORDER}; '
            f'display: flex; align-items: center; justify-content: center; color: {BLUE};">{icon(icon_name, 22)}'
            f'</span><span style="font-size: 13px; line-height: 16px; font-weight: 600; color: {NAVY};">{text}</span>'
            f'</button>')


def resolve_row(icon_name, tone, title, sub, first=False):
    return row(icon_tile(icon_name, tone), text_block(title, sub), icon("chevron", 20, MUTED), first=first)


def ledger_row(icon_name, tone, title, sub, value, first=False):
    return row(icon_tile(icon_name, tone), text_block(title, sub), money(value, tone), first=first)


def board_caixa_b():
    hero_card = (
        f'<section style="position: relative; overflow: hidden; background: {BLUE}; border-radius: 20px; '
        f'padding: 20px; display: flex; flex-direction: column; gap: 8px; color: {WHITE};">{ball(180, -50, -40)}'
        f'{kicker_row("Saldo do caixa")}{big_money("R$ 490,00")}'
        f'{hero_meta("Outubro: entrou R$ 880 · saiu R$ 640")}</section>'
    )
    actions = (f'<div style="display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px;">'
               f'{action_circle("plus", "Entrada")}{action_circle("minus", "Saída")}'
               f'{action_circle("megaphone", "Cobrar")}{action_circle("list", "Extrato")}</div>')
    resolve = card([
        resolve_row("users", "warning", "6 jogaram sem cobrança", "Terças 13/10 e 20/10", first=True),
        resolve_row("wallet", "brand", "R$ 280 para receber", "3 mensalidades e 2 diárias"),
    ])
    recent = card([
        ledger_row("out", "out", "Quadra", "Terça, 27/10", "−R$ 160", first=True),
        ledger_row("in", "in", "Diária · Bia Souza", "Pix · 27/10", "+R$ 20"),
        ledger_row("in", "in", "Diária · Duda Alves", "Dinheiro · 27/10", "+R$ 20"),
        ledger_row("in", "in", "Mensalidade · Igor Santos", "Pix · 26/10", "+R$ 80"),
        f'<button style="min-height: 52px; border: 0; border-top: 1px solid {BORDER}; background: transparent; '
        f'color: {BLUE}; font-family: inherit; font-size: 15px; line-height: 20px; font-weight: 600;">Ver extrato</button>',
    ])
    inner = appbar("Vôlei do CERET", trailing="Mais ações do caixa") + column([
        hero_card, actions, section_title("Para resolver"), resolve, section_title("Últimos lançamentos"), recent,
    ], gap=16, pad="4px 16px 24px")
    return inner, ""


def board_caixa_zerado():
    top = hero([
        kicker_row("Saldo do caixa", muted_dot=True),
        big_money("R$ 0,00"),
        hero_meta("Nada lançado ainda"),
        hero_buttons([("Entrada", "plus", "white"), ("Saída", "minus", "outline")]),
    ])
    start = card([
        section_title("Quanto o grupo já tem em caixa?"),
        support("Entra como saldo inicial e soma no saldo."),
        money_field("250,00"),
        btn("Salvar saldo inicial", "primary", "lg", full=True),
        btn("Começar do zero", "ghost", "md", full=True),
    ], gap=12, pad="20px 16px 12px")
    inner = appbar("Vôlei do CERET", trailing="Mais ações do caixa") + top + column([
        start,
        support("Depois disso o caixa anda sozinho: cada mensalidade e diária recebida entra aqui."),
    ], gap=12, pad="16px 16px 24px")
    return inner, ""


# ================================================================ 2 · entrada, saída e extrato
def board_extrato():
    pinned = (f'<div style="display: flex; align-items: center; gap: 12px; padding: 12px 16px; background: {WHITE}; '
              f'border: 1px solid {BORDER}; border-radius: 16px;">{icon_tile("flag", "brand")}'
              f'{text_block("Saldo inicial", "Antes do Saqz")}{money("R$ 250")}'
              f'{btn("Editar", "ghost", "sm")}</div>')
    day = lambda text: (f'<p style="margin: 0; padding: 16px 4px 8px; font-size: 13px; line-height: 16px; '  # noqa: E731
                        f'font-weight: 600; color: {MUTED};">{text}</p>')
    inner = appbar("Extrato") + column([
        segmented(["Tudo", "Entradas", "Saídas"], "Tudo"),
        support(f'Outubro · entrou R$ 880 · <span style="color: {ERROR}; font-weight: 600;">saiu R$ 640</span>'),
        pinned,
        f'<div style="display: flex; flex-direction: column;">{day("Terça, 27/10")}'
        + card([
            ledger_row("out", "out", "Quadra", "Saída · Quadra", "−R$ 160", first=True),
            ledger_row("in", "in", "Diária · Bia Souza", "Pix", "+R$ 20"),
            ledger_row("in", "in", "Diária · Duda Alves", "Dinheiro", "+R$ 20"),
        ])
        + day("Segunda, 26/10")
        + card([
            ledger_row("in", "in", "Mensalidade · Igor Santos", "Pix", "+R$ 80", first=True),
            ledger_row("in", "in", "Venda de camisas", "Entrada · Venda", "+R$ 120"),
        ]) + '</div>',
    ], gap=12, pad="4px 16px 16px")
    bottom = (f'<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px;">'
              f'{btn("Entrada", "secondary", "lg", "plus", full=True)}{btn("Saída", "out", "lg", "minus", full=True)}'
              f'</div>')
    return inner, bottom


def board_entrada():
    inner = appbar("Nova entrada", close=True) + column([
        field("Valor", money_field("120,00", big=True)),
        field("Descrição", text_field("Venda de camisas")),
        field("Categoria", segmented(["Patrocínio", "Venda", "Outros"], "Venda")),
        field("Data", text_field("Segunda, 26/10", "calendar")),
    ], gap=24, pad="8px 16px 24px")
    return inner, btn("Salvar entrada", "primary", "lg", full=True)


def board_saida():
    inner = appbar("Nova saída", close=True) + column([
        field("Valor", money_field("160,00", big=True) + segmented(["R$ 80", "R$ 120", "R$ 160"], "R$ 160")),
        field("Descrição", text_field("Quadra de terça")),
        field("Categoria", segmented(["Quadra", "Material", "Árbitro", "Outros"], "Quadra")),
        field("Data", text_field("Hoje · terça, 27/10", "calendar"), apoio="Toque para escolher outro dia."),
    ], gap=24, pad="8px 16px 24px")
    return inner, btn("Salvar saída", "primary", "lg", full=True)


# ================================================================ 3 · acerto e quem jogou sem cobrança
def board_acerto():
    top = hero([
        kicker_row("Jogo encerrado", muted_dot=True),
        display_title("Terça, 27/10"),
        hero_meta("19h30 · CERET — Quadra 2"),
        placar([("12", "", "Jogaram", False), ("2", "/4", "Pagaram", False), ("R$ 40", "", "Falta receber", False)],
               tabs=False),
    ])
    paid = chip("Recebido", "success", dot=True)
    body = white_list([
        group_header("Diárias · R$ 20", "2 de 4 pagaram", first=True),
        row(avatar("Bia Souza"), text_block("Bia Souza", "Avulsa · Pix"), paid, first=True),
        row(avatar("Júlia Rocha"), text_block("Júlia Rocha", "Convidada · paga Bia"),
            btn("Recebi", "secondary", "sm") + more_button("Júlia Rocha")),
        row(avatar("Caio Lima"), text_block("Caio Lima", "Avulso"), btn("Recebi", "secondary", "sm") + more_button("Caio Lima")),
        row(avatar("Duda Alves"), text_block("Duda Alves", "Avulsa · Dinheiro"), paid),
        group_header("Mensalistas", "8 · cobertos pela mensalidade"),
        group_header("Saídas do jogo", ""),
        row(icon_tile("card", "out"), text_block("Quadra", "Ainda não lançada"), btn("Lançar R$ 160", "secondary", "sm"),
            first=True),
    ])
    inner = appbar("Acerto do jogo") + top + body
    return inner, btn("Cobrar os 2 que faltam", "primary", "lg", "megaphone", full=True)


def board_acerto_retro():
    top = hero([
        kicker_row("Jogo encerrado", muted_dot=True),
        display_title("Terça, 20/10"),
        hero_meta("19h30 · CERET — Quadra 2"),
        pending_row("users", "4 jogaram sem cobrança", "Revisar", "O jogo não tinha valor por jogo"),
        placar([("11", "", "Jogaram", False), ("0", "", "Diárias", False), ("R$ 0", "", "Falta receber", False)],
               tabs=False),
    ])
    none = chip("Sem diária", "neutral")
    body = white_list([
        group_header("Sem cobrança", "5 confirmados", first=True),
        row(avatar("Caio Lima"), text_block("Caio Lima", "Avulso"), none, first=True),
        row(avatar("Duda Alves"), text_block("Duda Alves", "Avulsa"), none),
        row(avatar("Júlia Rocha"), text_block("Júlia Rocha", "Convidada de Bia Souza"), none),
        row(avatar("Rafa Nunes"), text_block("Rafa Nunes", "Avulso · saiu do grupo"), none),
        row(avatar("Marcos Lima", muted=True), text_block("Marcos Lima", "Avulso", muted=True), chip("Faltou", "warning")),
        group_header("Mensalistas", "7 · cobertos pela mensalidade"),
    ])
    return appbar("Acerto do jogo") + top + body, ""


REVIEW = [
    ("Caio Lima", "Avulso", "Cobrar", False),
    ("Duda Alves", "Avulsa", "Já pagou", False),
    ("Júlia Rocha", "Convidada · paga Bia", "Cobrar", False),
    ("Rafa Nunes", "Saiu do grupo", "Não cobrar", False),
    ("Marcos Lima", "Avulso · faltou", "Não cobrar", True),
]


def review_header():
    return [
        f'<div style="display: flex; flex-direction: column; gap: 4px;">'
        f'<h1 style="margin: 0; font-size: 22px; line-height: 28px; font-weight: 700; letter-spacing: -0.02em; '
        f'color: {NAVY};">Terça, 20/10 · CERET</h1>'
        f'{support("O jogo não tinha valor quando confirmaram.")}</div>',
        card([field("Valor da diária", money_field("20,00"), apoio="O valor por jogo do grupo.")], pad="16px"),
    ]


def review_bottom():
    return (f'<p style="margin: 0; font-size: 14px; line-height: 20px; color: {MUTED}; text-align: center;">'
            f'<b style="color: {NAVY}; font-weight: 600;">2 cobranças</b> · 1 já pagou · 2 sem cobrança</p>'
            + btn("Confirmar", "primary", "lg", full=True))


def paid_line(text="Entra no caixa com a data do jogo ·"):
    return (f'<p style="margin: 0; display: flex; align-items: center; gap: 6px; font-size: 14px; '
            f'line-height: 20px; color: {MUTED};">{icon("check", 16, SUCCESS_FG)}'
            f'<span>{text}</span><button style="padding: 0; border: 0; '
            f'background: transparent; color: {BLUE}; font-family: inherit; font-size: 14px; '
            f'line-height: 20px; font-weight: 600;">Pix</button></p>')


def board_revisao_a():
    blocks = []
    for i, (name, meta, state, muted) in enumerate(REVIEW):
        top = "" if i == 0 else f" border-top: 1px solid {BORDER};"
        blocks.append(
            f'<div style="display: flex; flex-direction: column; gap: 12px; padding: 14px 16px 16px;{top}">'
            f'<div style="display: flex; align-items: center; gap: 12px;">{avatar(name, muted=muted)}'
            f'{text_block(name, meta)}</div>'
            f'{segmented(["Cobrar", "Já pagou", "Não cobrar"], state, size=14, height=40)}'
            f'{paid_line() if state == "Já pagou" else ""}</div>'
        )
    inner = appbar("Quem jogou sem cobrança", close=True) + column(
        review_header() + [card(blocks), support("7 mensalistas ficam de fora: já pagam a mensalidade.")],
        gap=16, pad="4px 16px 24px")
    return inner, review_bottom()


def state_pill(state):
    tone = {"Cobrar": (BRAND08, BLUE), "Já pagou": (SUCCESS12, SUCCESS_FG), "Não cobrar": (NEUTRAL10, MUTED)}[state]
    return (f'<button style="flex: none; display: flex; align-items: center; gap: 4px; min-height: 36px; '
            f'padding: 0 8px 0 12px; border: 0; border-radius: 999px; background: {tone[0]}; color: {tone[1]}; '
            f'font-family: inherit; font-size: 14px; line-height: 18px; font-weight: 600; white-space: nowrap;">'
            f'{state}{icon("chevron_down", 16)}</button>')


def board_revisao_b(open_menu=True):
    rows = []
    for i, (name, meta, state, muted) in enumerate(REVIEW):
        below = paid_line("Entra no caixa em 20/10 ·") if state == "Já pagou" and not open_menu else ""
        rows.append(row(avatar(name, muted=muted), text_block(name, meta), state_pill(state), first=i == 0,
                        below=below))
    inner = appbar("Quem jogou sem cobrança", close=True) + column(
        review_header() + [card(rows), support("7 mensalistas ficam de fora: já pagam a mensalidade.")],
        gap=16, pad="4px 16px 24px")
    if not open_menu:
        return inner, review_bottom()
    menu_items = []
    for opt, sub, on in (("Cobrar", "Diária pendente de R$ 20", False), ("Já pagou", "Recebida, com a data do jogo", True),
                         ("Não cobrar", "Fica marcado, dá para reabrir", False)):
        mark = icon("check", 18, BLUE) if on else '<span style="width: 18px; flex: none;"></span>'
        menu_items.append(
            f'<button style="display: flex; align-items: center; gap: 12px; min-height: 56px; padding: 8px 16px; '
            f'border: 0; background: transparent; text-align: left; font-family: inherit;">{mark}'
            f'<span style="display: flex; flex-direction: column; gap: 1px;"><span style="font-size: 15px; '
            f'line-height: 20px; font-weight: 600; color: {NAVY};">{opt}</span><span style="font-size: 13px; '
            f'line-height: 18px; color: {MUTED};">{sub}</span></span></button>')
    menu = (f'<div role="menu" style="position: absolute; right: 24px; top: 404px; width: 260px; display: flex; '
            f'flex-direction: column; padding: 6px 0; background: {WHITE}; border: 1px solid {BORDER}; '
            f'border-radius: 16px; box-shadow: 0 12px 32px rgba(14,23,56,0.16);">{"".join(menu_items)}</div>')
    return inner + OVERLAY + menu, review_bottom()


# ================================================================ 4 · valor por jogo obrigatório
def fee_states(kind):
    if kind == "a":
        choice = lambda sel, err=False: segmented(["Cobrar diária", "Sem diária"], sel, error=err)  # noqa: E731
        states = [
            ("1 · Nada escolhido (não salva)", field(
                "Valor por jogo", choice(None, True), error="Escolha o valor ou Sem diária.")),
            ("2 · Cobrar diária", field(
                "Valor por jogo", choice("Cobrar diária") + money_field("20,00"),
                apoio="Sugestão: mensalidade ÷ 4 (R$ 80 ÷ 4).")),
            ("3 · Sem diária", field(
                "Valor por jogo", choice("Sem diária"), apoio="O avulso joga sem pagar por jogo.")),
        ]
    else:
        no_fee = lambda on: toggle_row("Sem diária", "O avulso joga sem pagar por jogo.", on)  # noqa: E731
        states = [
            ("1 · Vazio (não salva)", field(
                "Valor por jogo", money_field(None, error=True), error="Informe o valor ou ligue Sem diária.")
             + no_fee(False)),
            ("2 · Com valor", field(
                "Valor por jogo", money_field("20,00"), apoio="Sugestão: mensalidade ÷ 4 (R$ 80 ÷ 4).") + no_fee(False)),
            ("3 · Sem diária ligado", field("Valor por jogo", money_field("Sem diária", disabled=True)) + no_fee(True)),
        ]
    blocks = []
    for cap, html in states:
        blocks.append(f'<div style="display: flex; flex-direction: column; gap: 8px;">{caption(cap)}'
                      + card([f'<div style="display: flex; flex-direction: column; gap: 16px;">{html}</div>'],
                             pad="16px") + '</div>')
    return blocks


def board_valor(kind):
    banner = (f'<div style="display: flex; align-items: flex-start; gap: 10px; padding: 12px 14px; border-radius: 12px; '
              f'background: {ERROR10};">{icon("alert", 20, ERROR)}<span style="font-size: 14px; line-height: 20px; '
              f'font-weight: 500; color: {ERROR};">Falta o valor por jogo em Regras dos jogos.</span></div>')
    inner = appbar("Editar grupo") + column(
        [banner, section_title("Regras dos jogos")] + fee_states(kind), gap=16, pad="4px 16px 24px")
    return inner, btn("Salvar alterações", "primary", "lg", full=True)


def board_aviso_grupo():
    faixa = (
        f'<section style="position: relative; overflow: hidden; background: {BLUE}; border-radius: 20px; '
        f'padding: 20px; display: flex; flex-direction: column; gap: 12px; color: {WHITE};">{ball(170, -50, -40)}'
        f'{kicker_row("Só você e os admins veem")}'
        f'<h2 style="position: relative; margin: 0; font-size: 20px; line-height: 26px; font-weight: 700; '
        f'color: {WHITE};">Defina o valor por jogo</h2>'
        f'{hero_meta("Hoje o avulso joga sem diária. Sugestão: R$ 10, a mensalidade ÷ 4.")}'
        f'<button style="position: relative; align-self: flex-start; min-height: 44px; padding: 0 20px; border: 0; '
        f'border-radius: 999px; background: {WHITE}; color: {NAVY}; font-family: inherit; font-size: 15px; '
        f'line-height: 20px; font-weight: 600;">Definir valor</button></section>'
    )
    central = card([
        row(icon_tile("bell", "brand"),
            f'<span style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px;">'
            f'<span style="font-size: 15px; line-height: 20px; font-weight: 600; color: {NAVY};">O Vôlei de quinta '
            f'ainda não tem valor por jogo</span><span style="font-size: 14px; line-height: 20px; color: {MUTED};">'
            f'Toque para definir ou escolher Sem diária · agora</span></span>', first=True),
    ])
    inner = appbar("Vôlei de quinta", trailing="Mais ações do grupo") + column([
        faixa,
        placeholder_block("Resto da tela do grupo, como hoje"),
        section_title("Na central de avisos"),
        central,
    ], gap=16, pad="4px 16px 24px")
    return inner, ""


def avulso_hero(parts):
    return hero([
        kicker_row("Confirmações abertas"),
        display_title("Quinta, 20h"),
        f'<div style="position: relative; display: flex; flex-direction: column; gap: 10px; padding: 2px 0;">'
        f'{info_row("calendar", "29 de outubro")}{info_row("timer", "2h de jogo", "até 22h")}'
        f'{info_row("pin", "CERET — Quadra 2")}</div>',
    ] + parts)


def roster(rows):
    return white_list(rows)


def plain_person(num, name, meta, first=False, trailing="", you=False, below=""):
    number = (f'<span style="width: 26px; flex: none; text-align: center; {DISPLAY}; font-size: 14px; '
              f'line-height: 16px; color: {MUTED};">{num}</span>' if num else "")
    you_chip = chip("Você", "brand") if you else ""
    bg = f" background: {LIME14};" if you else ""
    return row(number + avatar(name), text_block(name, meta), you_chip + trailing, first=first, bg=bg,
               pad_left=10 if num else 16, below=below, below_left=48 if num else None)


def board_avulso_diaria():
    top = avulso_hero([
        fee_line("Diária de R$ 20, cobrada ao confirmar"),
        hero_buttons([("Vou", None, "lime"), ("Não vou", None, "outline")]),
        placar([("9", "/12", "Vão", True), ("0", "", "Na espera", False), ("2", "", "Não vão", False)]),
    ])
    body = roster([
        plain_person("1", "Bia Souza", "Ponteira", first=True),
        plain_person("2", "Igor Santos", "Central"),
        plain_person("3", "Paulo Reis", "Levantador"),
    ])
    return appbar("Vôlei do CERET") + top + body, ""


# ================================================================ 5 · vaga só com pagamento antecipado
def board_config_antecipado():
    vagas = card([
        f'<div style="display: flex; flex-direction: column; gap: 6px;"><span style="font-size: 16px; line-height: 22px; '
        f'font-weight: 600; color: {NAVY};">Quem entra quando surge uma vaga?</span>'
        f'{support("Escolha manual: todo Vou vai para a espera.")}</div>',
        segmented(["Ordem de chegada", "Escolha manual"], "Escolha manual"),
    ], gap=12, pad="16px")
    prepay = card([
        toggle_row("Vaga só com pagamento antecipado", "O avulso paga antes de entrar.", True),
        f'<div style="display: flex; align-items: flex-start; gap: 8px; padding: 10px 12px; border-radius: 10px; '
        f'background: {ICE};">{icon("banknote", 18, BLUE)}<span style="font-size: 14px; line-height: 20px; '
        f'color: {NAVY};">Vale para avulso e convidado. A diária nasce no Vou, com o Pix do grupo.</span></div>',
    ], gap=12, pad="16px")
    aviso = card([
        f'<div style="display: flex; flex-direction: column; gap: 6px;"><span style="font-size: 16px; line-height: 22px; '
        f'font-weight: 600; color: {NAVY};">Aviso para escolher quem joga</span>'
        f'{support("Horas antes do jogo em que você é avisado.")}</div>',
        segmented(["3h", "6h", "12h", "24h"], "24h"),
    ], gap=12, pad="16px")
    inner = appbar("Editar grupo") + column([
        section_title("Como preencher as vagas"), vagas, prepay, aviso,
        support("Voltar para Ordem de chegada desliga o pagamento antecipado, com aviso."),
    ], gap=12, pad="4px 16px 24px")
    return inner, ""


def board_avulso_paga():
    copy_btn = (f'<button style="position: relative; display: flex; align-items: center; justify-content: center; '
                f'gap: 8px; min-height: 44px; border: 0; border-radius: 999px; background: {WHITE}; color: {NAVY}; '
                f'font-family: inherit; font-size: 15px; line-height: 20px; font-weight: 600;">{icon("copy", 18)}'
                f'Copiar o Pix do grupo</button>')
    top = avulso_hero([
        hero_chip("Na espera · falta pagar", WARN),
        hero_box("banknote", "Pague R$ 20 para entrar",
                 "A vaga é liberada pelo organizador depois do pagamento.", copy_btn),
        f'<button style="position: relative; align-self: center; min-height: 44px; padding: 0 16px; border: 0; '
        f'border-radius: 999px; background: transparent; color: {WHITE}; font-family: inherit; font-size: 15px; '
        f'line-height: 20px; font-weight: 600;">Sair da lista</button>',
        placar([("10", "/12", "Vão", False), ("3", "", "Na espera", True), ("2", "", "Não vão", False)]),
    ])
    body = roster([
        plain_person("1º", "Caio Lima", "Central", first=True),
        plain_person("2º", "Rafa Nunes", "Ponteiro", you=True),
        plain_person("3º", "Duda Alves", "Ponteira"),
        f'<p style="margin: 0; padding: 12px 16px 14px 102px; border-top: 1px solid {BORDER}; font-size: 14px; '
        f'line-height: 20px; color: {MUTED};">O organizador coloca quem já pagou.</p>',
    ])
    return appbar("Vôlei do CERET") + top + body, ""


def pay_actions(paid):
    if paid:
        return chip("Pagou R$ 20", "success", dot=True) + '<span style="flex-grow: 1;"></span>' + btn("Colocar", "primary", "sm")
    return (chip("Falta pagar R$ 20", "warning", dot=True) + '<span style="flex-grow: 1;"></span>'
            + btn("Recebi", "ghost", "sm") + btn("Colocar", "secondary", "sm"))


def org_hero():
    return hero([
        kicker_row("Confirmações abertas"),
        display_title("Quinta, 20h"),
        f'<div style="position: relative; display: flex; flex-direction: column; gap: 10px; padding: 2px 0;">'
        f'{info_row("calendar", "29 de outubro")}{info_row("pin", "CERET — Quadra 2")}</div>',
        fee_line("Vaga do avulso só com pagamento antecipado"),
        placar([("10", "/12", "Vão", False), ("3", "", "Na espera", True), ("2", "", "Não vão", False)]),
    ])


def board_espera_org(with_sheet=False):
    body = roster([
        plain_person("1º", "Caio Lima", "Avulso · Central", first=True, trailing=more_button("Caio Lima"),
                     below=pay_actions(True)),
        plain_person("2º", "Rafa Nunes", "Avulso · Ponteiro", trailing=more_button("Rafa Nunes"),
                     below=pay_actions(False)),
        plain_person("3º", "Júlia Rocha", "Convidada · paga Bia", trailing=more_button("Júlia Rocha"),
                     below=pay_actions(False).replace("Falta pagar R$ 20", "Bia não pagou R$ 20")),
        f'<p style="margin: 0; padding: 12px 16px 14px 102px; border-top: 1px solid {BORDER}; font-size: 14px; '
        f'line-height: 20px; color: {MUTED};">Você escolhe quem entra. Pix e dinheiro chegam por fora: marque Recebi.</p>',
    ])
    inner = appbar("Vôlei do CERET", trailing="Mais ações do jogo") + org_hero() + body
    if with_sheet:
        inner += sheet([
            f'<div style="display: flex; flex-direction: column; gap: 4px;"><h2 style="margin: 0; font-size: 20px; '
            f'line-height: 26px; font-weight: 700; color: {NAVY};">Rafa ainda não pagou</h2>'
            f'{support("Diária de R$ 20. Como você quer colocar o Rafa?")}</div>',
            f'<div style="display: flex; flex-direction: column; gap: 8px;">'
            f'{btn("Colocar, ele já pagou", "primary", "lg", full=True)}'
            f'{support("Marca os R$ 20 como recebidos.", center=True)}</div>',
            f'<div style="display: flex; flex-direction: column; gap: 8px;">'
            f'{btn("Colocar e cobrar depois", "secondary", "lg", full=True)}'
            f'{support("A diária fica pendente, como hoje.", center=True)}</div>',
            btn("Voltar", "ghost", "md", full=True),
        ], "Colocar quem não pagou")
    return inner, ""


def board_acerto_antecipado():
    top = hero([
        kicker_row("Jogo encerrado", muted_dot=True),
        display_title("Quinta, 29/10"),
        hero_meta("20h · CERET — Quadra 2"),
        placar([("12", "", "Jogaram", False), ("4", "/4", "Pagaram", False), ("2", "", "A decidir", False)],
               tabs=False),
    ])
    paid = chip("Recebido", "success", dot=True)
    body = white_list([
        group_header("Diárias · R$ 20", "4 de 4 pagaram", first=True),
        row(avatar("Caio Lima"), text_block("Caio Lima", "Avulso · Pix"), paid, first=True),
        row(avatar("Duda Alves"), text_block("Duda Alves", "Avulsa · Pix"), paid),
        group_header("Não jogaram", "você decide"),
        row(avatar("Rafa Nunes"), text_block("Rafa Nunes", "Pagou R$ 20 · Pix"), chip("Não liberado", "warning"),
            first=True, below=btn("Desfazer recebimento", "ghost", "sm")),
        row(avatar("Marcos Lima", muted=True), text_block("Marcos Lima", "Falta pagar R$ 20", muted=True),
            chip("Desistiu", "neutral"),
            below=btn("Recebi", "secondary", "sm") + btn("Dispensar", "ghost", "sm") + btn("Cancelar", "danger", "sm")),
    ])
    return appbar("Acerto do jogo") + top + body, ""


# ---------------------------------------------------------------- hoje (prints do Roborazzi)
TODAY = {
    "Hoje-confirmado-oferta": ("Pagamento confirmado · hoje, com a oferta", "/_blob/8014a61304d8ff52129cea1d362bef93", 868,
                               "Pagamento confirmado de hoje: check verde fino, título e valor em marinho e a oferta do "
                               "automático num bloco azul"),
    "Hoje-confirmado-diaria": ("Pagamento confirmado · hoje, diária", "/_blob/4823364a1b5c35bdfdc40aee768e0015", 868,
                               "Pagamento confirmado de hoje, diária: check verde fino, título e valor em marinho, "
                               "Voltar ao grupo azul"),
    "Main": ("Caixa · hoje", "/_blob/8d64e4f7d32cba2dae57718a1a8a88af", 868,
             "Caixa de hoje: saldo, Cobrar quem falta e Registrar, mensalidades do mês e lista de quem não pagou com "
             "Cobrar, Recebi e Cancelar cobrança empilhados"),
    "Hoje-caixa-zerado": ("Caixa · hoje, zerado", "/_blob/12e1a2e722c4832dbdfe2b70bddd07c5", 868,
                          "Caixa zerado de hoje: cartão Caixa zerado com Registrar primeiro lançamento"),
    "Hoje-lancamento": ("Novo lançamento · hoje", "/_blob/b6e880ba226cd78b1cfe73c1162f7224", 868,
                        "Novo lançamento de hoje: Entrada ou Saída, valor com atalhos, descrição, categoria e data"),
    "Hoje-extrato": ("Extrato · hoje", "/_blob/1ab2295ec1717a25fc71c1cb2e4b773e", 868,
                     "Extrato de hoje: filtros Tudo, Entradas e Saídas e a lista de lançamentos"),
    "Hoje-acerto": ("Acerto do jogo · hoje", "/_blob/a03e21f0d3cc4b3b9e2a551ad0c892ea", 868,
                    "Acerto de hoje: data do jogo, progresso, mensalistas, aluguel da quadra e quem paga por jogo"),
    "Hoje-editor": ("Marcar jogo · hoje", "/_blob/fc880d23351fb4f8286b790b7aff44f4", 868,
                    "Editor de jogo de hoje: o Valor por jogo aparece como opcional no fim do formulário"),
    "Hoje-regras": ("Editar grupo · hoje, regras dos jogos", "/_blob/ecc8d403ff3db52ef61bd6df91853b96", 532,
                    "Regras dos jogos de hoje: Valor por jogo com R$ 0,00 de exemplo e o apoio Para avulsos"),
    "Hoje-vagas": ("Como preencher as vagas · hoje", "/_blob/502f17113f99233f00e41033cebca582", 868,
                   "Como preencher as vagas de hoje: prioridade, Ordem de chegada ou Escolha manual e o aviso"),
    "Hoje-espera-org": ("Espera do organizador · hoje", "/_blob/07d5de827a06d0b015caa2e2460a6894", 1044,
                        "Aba Na espera do organizador hoje: cada pessoa com o botão Colocar"),
    "Hoje-espera-avulso": ("Avulso na espera · hoje", "/_blob/a78389475277755ef17a5a2f8f2d3df5", 1330,
                           "Avulso na espera hoje, no modo de escolha manual"),
    "Hoje-grupo-sem": ("Página do grupo · hoje, sem cobrança", "/_blob/cf3e82f5805bfd689c56dd94b0526e31", 1100,
                       "Página do grupo hoje para o membro sem cobrança: o Pix do grupo não aparece"),
    "Hoje-grupo-com": ("Página do grupo · hoje, com cobrança", "/_blob/c8a4c94aa90c00896472b6a0a51d6bff", 1510,
                       "Página do grupo hoje para o membro com cobrança: o Pix fica no cartão da cobrança"),
}


# ================================================================ 6 · pagamento confirmado (atleta)
# Mesmo texto de hoje (PaymentConfirmedUi: resumo, oferta, dia); muda só a cor e o peso da confirmação.
CONFIRMED = {
    "oferta": ("R$ 100,00 · mensalidade de agosto", True),
    "diaria": ("R$ 20,00 · diária de terça, 27/10", False),
}


def big_check(size, color, stroke=2.5):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" stroke="{color}" '
            f'stroke-width="{stroke}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'
            f'{ICONS["check"]}</svg>')


def close_button(color):
    return (f'<button aria-label="Fechar" style="position: relative; width: 44px; height: 44px; flex: none; '
            f'display: flex; align-items: center; justify-content: center; padding: 0; border: 0; border-radius: 22px; '
            f'background: transparent; color: {color};">{icon("x", 24)}</button>')


def offer_blue():
    """O cartão aprovado em 26/10 (protótipo Atleta-Confirmado)."""
    return (
        f'<section style="position: relative; overflow: hidden; border-radius: 16px; background: {BLUE}; padding: 20px; '
        f'display: flex; flex-direction: column; gap: 10px; color: {WHITE};">{ball(190, -60, -50)}'
        f'<span style="position: relative; display: flex; align-items: center; gap: 8px; font-size: 12px; '
        f'line-height: 16px; font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase; color: {W82};">'
        f'{icon("card", 16)}Pagamento automático</span>'
        f'<span style="position: relative; {DISPLAY}; font-size: 24px; line-height: 28px; letter-spacing: -0.02em;">'
        f'Quer deixar no automático?</span>'
        f'<span style="position: relative; font-size: 15px; line-height: 22px; color: rgba(255,255,255,0.9);">No mês que '
        f'vem, a mensalidade se paga sozinha no cartão, todo dia 10. Cancele quando quiser.</span>'
        f'<div style="position: relative; display: flex; flex-direction: column; gap: 4px; margin-top: 4px;">'
        f'{hero_buttons([("Deixar no automático", None, "white")])}'
        f'<button style="min-height: 44px; border: 0; background: transparent; color: {WHITE}; font-family: inherit; '
        f'font-size: 15px; line-height: 20px; font-weight: 600;">Agora não</button></div></section>'
    )


def offer_white():
    return card([
        f'<span style="display: flex; align-items: center; gap: 8px; font-size: 12px; line-height: 16px; '
        f'font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase; color: {BLUE};">{icon("card", 16)}'
        f'Pagamento automático</span>',
        f'<span style="{DISPLAY}; font-size: 24px; line-height: 28px; letter-spacing: -0.02em; color: {NAVY};">'
        f'Quer deixar no automático?</span>',
        support("No mês que vem, a mensalidade se paga sozinha no cartão, todo dia 10. Cancele quando quiser."),
        f'<div style="display: flex; flex-direction: column; gap: 4px; margin-top: 4px;">'
        f'{btn("Deixar no automático", "primary", "lg", full=True)}{btn("Agora não", "ghost", "md", full=True)}</div>',
    ], gap=10, pad="20px 20px 12px")


def confirmed_page(top, offer_html, offer, height=844):
    footer = btn("Voltar ao grupo", "secondary" if offer else "primary", "lg", full=True)
    offer_block = f'<div style="padding: 20px 16px 0; flex: none;">{offer_html}</div>' if offer else ""
    return (f'<div style="position: relative; width: 390px; height: {height}px; box-sizing: border-box; '
            f'background: {WHITE}; {UI}; color: {NAVY}; display: flex; flex-direction: column; overflow: hidden;">'
            f'{top}{offer_block}<div style="flex-grow: 1;"></div>'
            f'<div style="padding: 12px 16px 20px; flex: none; display: flex;">{footer}</div></div>')


def board_confirmado_a(kind):
    summary, offer = CONFIRMED[kind]
    top = (
        f'<section style="position: relative; overflow: hidden; background: {SUCCESS_FG}; padding: 6px 20px 24px; '
        f'display: flex; flex-direction: column; align-items: flex-start; gap: 12px; color: {WHITE}; flex: none;">'
        f'{ball(240, -70, 46)}<div style="margin-left: -14px;">{close_button(WHITE)}</div>'
        f'<span style="position: relative; width: 56px; height: 56px; margin-top: 12px; border-radius: 28px; '
        f'background: {WHITE}; display: flex; align-items: center; justify-content: center;">'
        f'{big_check(30, SUCCESS_FG, 3)}</span>'
        f'{display_title("Pagamento confirmado", 34)}'
        f'<p style="position: relative; margin: 0; font-size: 17px; line-height: 24px; font-weight: 600;">{summary}</p>'
        f'<p style="position: relative; margin: 0; display: flex; align-items: center; gap: 8px; font-size: 14px; '
        f'line-height: 20px; color: {W82};">{icon("wallet", 18, W82)}O organizador já vê no caixa do grupo.</p>'
        f'</section>'
    )
    return confirmed_page(top, offer_white(), offer)


def board_confirmado_b(kind):
    summary, offer = CONFIRMED[kind]
    top = (
        f'<div style="padding: 6px 4px 0; flex: none;">{close_button(BLUE)}</div>'
        f'<div style="padding: 20px 24px 8px; display: flex; flex-direction: column; align-items: center; gap: 14px; '
        f'text-align: center; flex: none;">'
        f'<span style="padding: 12px; border-radius: 999px; background: {SUCCESS12}; display: flex;">'
        f'<span style="width: 80px; height: 80px; border-radius: 40px; background: {SUCCESS_FG}; display: flex; '
        f'align-items: center; justify-content: center;">{big_check(40, WHITE, 3)}</span></span>'
        f'<h1 style="margin: 0; {DISPLAY}; font-size: 34px; line-height: 36px; letter-spacing: -0.03em; '
        f'color: {SUCCESS_FG};">Pagamento confirmado</h1>'
        f'<span style="font-size: 17px; line-height: 24px; font-weight: 600; color: {NAVY};">{summary}</span>'
        f'{chip("O organizador já vê no caixa", "success", dot=True)}</div>'
    )
    return confirmed_page(top, offer_blue(), offer)


GRUPO_SEM = "/_blob/cf3e82f5805bfd689c56dd94b0526e31"
GRUPO_SEM_CUT = 1010  # fim do cartão "Minhas cobranças" no print de 390 de largura


def board_grupo_pix():
    """O print de hoje cortado depois de "Minhas cobranças", com a seção nova no meio."""
    shot = f'width: 390px; height: 1100px; display: block;'
    top = (f'<div style="height: {GRUPO_SEM_CUT}px; overflow: hidden;"><img src="{GRUPO_SEM}" alt="Página do grupo, '
           f'do topo até Minhas cobranças" style="{shot}"></div>')
    pix = column([
        section_title("Pix do grupo"),
        card([row(icon_tile("card", "brand"), text_block("ceret@volei.com.br", "Chave Pix · E-mail"),
                  btn("Copiar", "secondary", "sm"), first=True)]),
    ], gap=10, pad="20px 16px 0")
    bottom = (f'<div style="position: relative; height: 90px; overflow: hidden;"><img src="{GRUPO_SEM}" alt="Denunciar '
              f'grupo e Sair do grupo" style="position: absolute; top: -{GRUPO_SEM_CUT}px; left: 0; {shot}"></div>')
    return (f'<div style="width: 390px; height: 1220px; background: {CANVAS}; {UI};">{top}{pix}{bottom}</div>')


def today(src, height, alt):
    return (f'<div style="width: 390px; height: {height}px; background: {CANVAS};">'
            f'<img src="{src}" alt="{alt}" style="width: 390px; height: {height}px; display: block;"></div>')


BOARDS = {
    "Caixa-A": ("Caixa · A, placar do caixa", board_caixa_a),
    "Caixa-A-saiu": ("Caixa · A, aba Saiu", board_caixa_a_saiu),
    "Caixa-A-menu": ("Caixa · A, menu ⋯", board_caixa_a_menu),
    "Caixa-B": ("Caixa · B, caixa como conta", board_caixa_b),
    "Caixa-zerado": ("Caixa · primeiro uso com saldo inicial", board_caixa_zerado),
    "Extrato": ("Extrato · com o saldo inicial", board_extrato),
    "Entrada": ("Nova entrada", board_entrada),
    "Saida": ("Nova saída", board_saida),
    "Acerto": ("Acerto do jogo", board_acerto),
    "Acerto-retro": ("Acerto · jogo com gente sem cobrança", board_acerto_retro),
    "Revisao-A": ("Quem jogou sem cobrança · A", board_revisao_a),
    "Revisao-B": ("Quem jogou sem cobrança · B", board_revisao_b),
    "Revisao-B-lista": ("Quem jogou sem cobrança · B, lista", lambda: board_revisao_b(False)),
    "Valor-A": ("Valor por jogo · A", lambda: board_valor("a")),
    "Valor-B": ("Valor por jogo · B", lambda: board_valor("b")),
    "Aviso-grupo": ("Aviso aos grupos sem valor", board_aviso_grupo),
    "Avulso-diaria": ("Avulso vê a diária", board_avulso_diaria),
    "Antecipado-config": ("Pagamento antecipado · configuração", board_config_antecipado),
    "Antecipado-avulso": ("Pagamento antecipado · avulso na espera", board_avulso_paga),
    "Antecipado-espera": ("Pagamento antecipado · espera do organizador", board_espera_org),
    "Antecipado-folha": ("Pagamento antecipado · colocar quem não pagou", lambda: board_espera_org(True)),
    "Antecipado-acerto": ("Pagamento antecipado · acerto", board_acerto_antecipado),
    "Confirmado-A-oferta": ("Pagamento confirmado · A, faixa verde", lambda: board_confirmado_a("oferta")),
    "Confirmado-A-diaria": ("Pagamento confirmado · A, diária", lambda: board_confirmado_a("diaria")),
    "Confirmado-B-oferta": ("Pagamento confirmado · B, selo verde", lambda: board_confirmado_b("oferta")),
    "Confirmado-B-diaria": ("Pagamento confirmado · B, diária", lambda: board_confirmado_b("diaria")),
    "Grupo-pix": ("Página do grupo · Pix do grupo", board_grupo_pix),
}


# ---------------------------------------------------------------- saída
def dc_html(page_title, root, width, height):
    props = json.dumps({"$preview": {"width": width, "height": height}})
    fonts = FONTS.replace("&", "&amp;")
    return f"""<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>{page_title}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="{fonts}" rel="stylesheet">
<style>
body{{margin:0}}
</style>
</helmet>
{root}
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{props}'>
class Component extends DCLogic {{
renderVals() {{ return {{}}; }}
}}
</script>
</body>
</html>
"""


def preview_html(root):
    return f"""<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8">
<link href="{FONTS.replace('&', '&amp;')}" rel="stylesheet">
<style>body{{margin:0;background:#FFFFFF}}</style></head>
<body>{root}
<script>
document.fonts.ready.then(function(){{
  var r=document.body.firstElementChild; r.style.height='auto';
  var h=Math.ceil(r.getBoundingClientRect().height);
  var m=document.createElement('pre'); m.id='measured'; m.textContent='H='+h; document.body.appendChild(m);
}});
</script></body></html>
"""


GAP_X = 80
ROW_TITLE = 260
ROW_GAP = 160
STEP = 390 + GAP_X


def canvas(heights):
    boards, order, notes = {}, [], {}

    def add(key, col, y, title):
        h = heights[key]
        boards[f"{key}.dc.html"] = {"x": col * STEP, "y": y, "w": 390, "h": h, "title": title}
        order.append(f"{key}.dc.html")
        return h

    def sticky(note_id, col, y, text, fill, w=440, dy=0):
        notes[note_id] = {"x": col * STEP, "y": y + dy, "w": w, "text": text, "fill": fill}

    def title(note_id, y, text, cols):
        notes[note_id] = {"x": 0, "y": y - ROW_TITLE, "text": text, "kind": "title1", "maxW": cols * STEP}

    y = 0
    rows_heights = []

    # 1 · caixa
    title("t1", y, "1 · Caixa do grupo", 9)
    hs = [add("Main", 0, y, "Hoje · caixa"), add("Hoje-caixa-zerado", 1, y, "Hoje · caixa zerado"),
          add("Caixa-A", 2, y, "A · placar do caixa (escolhida)"), add("Caixa-A-saiu", 3, y, "A · aba Saiu, saídas em vermelho"),
          add("Caixa-A-menu", 4, y, "A · menu ⋯ (decidido em 29/10)"),
          add("Caixa-B", 5, y, "B · caixa como conta (não escolhida)"),
          add("Caixa-zerado", 6, y, "Primeiro uso: saldo inicial")]
    sticky("n1hoje", 7, y,
           "O que pega hoje:\n"
           "1. O saldo é só um número; nada diz de onde veio nem o que falta.\n"
           "2. \"Registrar\" não diz se é entrada ou saída.\n"
           "3. Cada devedor tem três botões empilhados (Cobrar, Recebi, Cancelar): a linha fica alta e o Recebi se perde.\n"
           "4. O aviso \"Cobrança é manual\" ocupa o topo sempre.\n"
           "5. Diária em aberto não aparece com nome do jogo; quem jogou sem cobrança não aparece em lugar nenhum.\n"
           "6. Não existe saldo inicial: o caixa sempre começa em zero.", "gray")
    sticky("n1prop", 8, y,
           "A · Placar do caixa (escolhida em 28/10): o caixa vira o cabeçalho azul da tela de jogo. Saldo em Anybody, Entrada e "
           "Saída lado a lado, a pendência \"6 jogaram sem cobrança\" no mesmo lugar do \"4 pessoas sem resposta\" e o "
           "placar Entrou · Saiu · Falta receber como abas: cada uma troca a lista de baixo. Cada devedor tem um botão "
           "(Recebi) e o ⋮ com Cobrar, Dispensar e Cancelar. Cobrar todos fica embaixo, no alcance do polegar.\n"
           "Saídas em vermelho (pedido de 29/10): o número do Saiu no placar em vermelho claro (4,7:1 sobre o azul), o "
           "botão Saída com contorno vermelho claro e, na aba Saiu, cada saída com ícone e valor em vermelho, como no "
           "extrato.\n\n"
           "B · Caixa como conta (não escolhida): o jeito de app de banco. Cartão azul com o saldo, quatro atalhos redondos, \"Para "
           "resolver\" e os últimos lançamentos. Mais calma, mas quem deve fica a um toque de distância.\n\n"
           "Primeiro uso: com o caixa zerado, o app pergunta quanto o grupo já tem. \"Começar do zero\" fecha o cartão.\n"
           "Decidido em 29/10: o saldo inicial e \"Criar cobranças do mês\" vão para o ⋯ do caixa. O Pix do grupo sai do "
           "caixa e fica na página do grupo, para todos os membros (linha 7).", "blue", w=460)
    y += max(hs) + ROW_GAP + ROW_TITLE

    # 2 · entrada, saída e extrato
    title("t2", y, "2 · Entrada, saída e extrato (aprovada em 29/10)", 7)
    hs = [add("Hoje-lancamento", 0, y, "Hoje · novo lançamento"), add("Hoje-extrato", 1, y, "Hoje · extrato"),
          add("Entrada", 2, y, "Nova entrada"), add("Saida", 3, y, "Nova saída"),
          add("Extrato", 4, y, "Extrato com o saldo inicial")]
    sticky("n2hoje", 5, y,
           "O que pega hoje:\n"
           "1. Um formulário só para entrada e saída: a categoria e os atalhos de R$ 80/120/160 são de saída, mas "
           "aparecem na entrada também.\n"
           "2. As categorias quebram em duas linhas (Quadra, Material / Racha, Outros).\n"
           "3. O valor tem o mesmo peso de qualquer campo.\n"
           "4. O extrato não mostra de onde o saldo partiu.", "gray")
    sticky("n2prop", 6, y,
           "Entrada e Saída são dois botões e duas telas com o título certo. O valor vem primeiro e grande, em Anybody "
           "como o placar.\n"
           "Entrada: Patrocínio, Venda e Outros, sem atalhos.\n"
           "Saída: os atalhos de valor e Quadra, Material, Árbitro e Outros, quatro opções de largura igual. Racha entra "
           "em Outros (decidido em 29/10); as saídas antigas de Racha continuam com o nome no extrato.\n"
           "Data (decidido em 29/10): vem com hoje e o toque abre o calendário para escolher outro dia. O \"Lançar "
           "R$ 160\" do acerto abre a Saída com Quadra e a data do jogo.\n"
           "Extrato: o saldo inicial fica fixo no topo, com Editar; Entrada e Saída embaixo. Saídas em vermelho: "
           "o valor, o \"saiu\" do mês e o botão Saída.", "blue", w=460)
    y += max(hs) + ROW_GAP + ROW_TITLE

    # 3 · acerto e retroativa
    title("t3", y, "3 · Acerto do jogo e quem jogou sem cobrança", 8)
    hs = [add("Hoje-acerto", 0, y, "Hoje · acerto do jogo"), add("Acerto", 1, y, "Acerto do jogo (proposta)"),
          add("Acerto-retro", 2, y, "Acerto · jogo com gente sem cobrança"),
          add("Revisao-B-lista", 3, y, "Revisão · B, lista (escolhida)"),
          add("Revisao-B", 4, y, "Revisão · B, menu aberto (escolhida)"),
          add("Revisao-A", 5, y, "Revisão · A, escolha na linha (não escolhida)")]
    sticky("n3hoje", 6, y,
           "O que pega hoje:\n"
           "1. A tela chama o avulso de \"Diarista\".\n"
           "2. Recebi e Cancelar cobrança empilhados em cada pessoa, como no caixa.\n"
           "3. Mensalistas e aluguel da quadra ganham cartões do tamanho da lista de quem paga.\n"
           "4. Dois avisos de texto no fim e \"Cobrar quem falta\" cinza, longe do resto.\n"
           "5. Quem confirmou sem valor no jogo simplesmente não aparece.", "gray")
    sticky("n3prop", 7, y,
           "Acerto: o cabeçalho azul do jogo com o placar Jogaram · Pagaram · Falta receber. Uma lista só, dividida em "
           "Diárias, Mensalistas (uma linha) e Saídas do jogo, com \"Lançar R$ 160\" para a quadra.\n\n"
           "Jogo com gente sem cobrança: a pendência azul \"4 jogaram sem cobrança\" com Revisar, e a lista mostra "
           "quem está sem diária (Faltou à parte).\n\n"
           "Revisão B (escolhida em 28/10): um valor para o lote e uma linha por pessoa, com o estado num botão "
           "(Cobrar · Já pagou · Não cobrar) que abre o menu. Já pagou ganha a linha de baixo com o meio (Pix) para "
           "trocar. Embaixo, o resumo antes de confirmar.\n"
           "Revisão A (não escolhida): as três opções abertas em cada pessoa.\n\n"
           "Nada vai para o atleta; depois de confirmar, o Cobrar do acerto avisa quem ficou devendo.", "blue", w=460)
    y += max(hs) + ROW_GAP + ROW_TITLE

    # 4 · valor por jogo
    title("t4", y, "4 · Valor por jogo obrigatório", 8)
    hs = [add("Hoje-editor", 0, y, "Hoje · marcar jogo"), add("Hoje-regras", 1, y, "Hoje · regras dos jogos"),
          add("Valor-A", 2, y, "A · Cobrar diária ou Sem diária (escolhida)"),
          add("Valor-B", 3, y, "B · campo com Sem diária embaixo (não escolhida)"),
          add("Aviso-grupo", 4, y, "Grupos que já existem sem valor"),
          add("Avulso-diaria", 5, y, "O avulso vê a diária antes do Vou")]
    sticky("n4hoje", 6, y,
           "O que pega hoje:\n"
           "1. \"Valor por jogo (opcional)\": dá para deixar vazio sem perceber, e o avulso joga de graça.\n"
           "2. \"R$ 0,00\" no campo parece um valor escolhido.\n"
           "3. O avulso só lê \"a cobrança deste jogo será gerada\", sem o valor.", "gray")
    sticky("n4prop", 7, y,
           "A (escolhida em 28/10): duas opções de largura igual, Cobrar diária ou Sem diária; nada vem marcado. Cobrar diária abre "
           "o campo, com a sugestão da mensalidade ÷ 4 no apoio (em Criar grupo, o campo já vem preenchido com ela).\n"
           "B (não escolhida): o campo de sempre e, embaixo, a chave Sem diária, que trava o campo.\n"
           "Nas duas, o aviso de erro fica no topo e no campo. O mesmo bloco vale para o editor de jogo, o horário "
           "regular, Criar grupo e Editar grupo.\n\n"
           "Grupos sem valor: o cartão azul aparece no grupo só para dono e admins até escolherem; o push e a central "
           "abrem o Valor por jogo já preenchido com a sugestão.\n"
           "Texto do push: \"Vôlei de quinta: defina o valor por jogo\" / \"Hoje o avulso joga sem diária. Toque para "
           "definir.\"\n\n"
           "O avulso vê \"Diária de R$ 20, cobrada ao confirmar\" no jogo; o link de presença ganha a mesma "
           "linha.", "blue", w=460)
    y += max(hs) + ROW_GAP + ROW_TITLE

    # 5 · pagamento antecipado
    title("t5", y, "5 · Vaga do avulso só com pagamento antecipado (aprovada em 29/10)", 10)
    hs = [add("Hoje-vagas", 0, y, "Hoje · como preencher as vagas"),
          add("Hoje-espera-org", 1, y, "Hoje · espera do organizador"),
          add("Hoje-espera-avulso", 2, y, "Hoje · avulso na espera"),
          add("Antecipado-config", 3, y, "Configuração (só com Escolha manual)"),
          add("Antecipado-avulso", 4, y, "Avulso: Vou, paga e espera"),
          add("Antecipado-espera", 5, y, "Organizador: aba Na espera"),
          add("Antecipado-folha", 6, y, "Colocar quem não pagou"),
          add("Antecipado-acerto", 7, y, "Acerto: pagou e não jogou")]
    sticky("n5prop", 8, y,
           "Configuração: a chave aparece só com Escolha manual, logo abaixo dela.\n\n"
           "Avulso: o Vou leva à espera como hoje, e a caixa azul diz quanto pagar e que o organizador libera a vaga "
           "depois do pagamento, com Copiar o Pix do grupo. A cobrança aparece também em Minhas cobranças e na Início.\n\n"
           "Organizador: na aba Na espera, cada pessoa ganha a linha do pagamento: Pagou (verde) com Colocar, ou Falta "
           "pagar com Recebi e Colocar. Colocar quem não pagou abre a folha com as duas saídas.\n\n"
           "Acerto: quem pagou e não foi liberado, ou desistiu, fica em \"Não jogaram\" e você decide. A devolução do "
           "Pix é por fora.", "blue", w=460)
    sticky("n5conversar", 9, y,
           "Confirmado pelo dono em 29/10:\n"
           "1. Recebi e Colocar ficam na aba Na espera, como desenhado.\n"
           "2. Quem pagou e não jogou, ou desistiu: só a etiqueta e as ações de sempre (Desfazer recebimento, "
           "Dispensar, Cancelar), sem nada automático.\n"
           "3. O convidado aparece na espera como \"Bia não pagou R$ 20\": quem paga é o anfitrião.", "blue", w=420)
    y += max(hs) + ROW_GAP + ROW_TITLE

    # 6 · pagamento confirmado
    title("t6", y, "6 · Pagamento confirmado (atleta)", 8)
    hs = [add("Hoje-confirmado-oferta", 0, y, "Hoje · mensalista, com a oferta"),
          add("Hoje-confirmado-diaria", 1, y, "Hoje · diária"),
          add("Confirmado-A-oferta", 2, y, "A · faixa verde, com a oferta (escolhida)"),
          add("Confirmado-A-diaria", 3, y, "A · faixa verde, diária (escolhida)"),
          add("Confirmado-B-oferta", 4, y, "B · selo verde, com a oferta (não escolhida)"),
          add("Confirmado-B-diaria", 5, y, "B · selo verde, diária (não escolhida)")]
    sticky("n6hoje", 6, y,
           "O que pega hoje:\n"
           "1. A única cor da confirmação é um check verde fino, sem fundo; título, valor e texto ficam em marinho e "
           "cinza.\n"
           "2. Com a oferta do automático, o maior bloco da tela é azul: a oferta chama mais atenção que o pagamento.\n"
           "3. \"Pagamento confirmado\" aparece duas vezes (barra de cima e título).\n"
           "4. O protótipo aprovado em 26/10 tinha o check num círculo azul: a tela já nasceu sem o verde.", "gray")
    sticky("n6prop", 7, y,
           "Escolhida pelo dono em 29/10: A (DEB-149).\n\n"
           "A · Faixa verde (escolhida): a confirmação vira o cabeçalho, como o azul da tela de jogo, no verde de "
           "confirmação (#0E7A4B, texto branco com 5,4:1) e com a bola ao fundo. Check em círculo branco e \"Pagamento "
           "confirmado\" em Anybody. A oferta do automático vira cartão branco com botão azul, para não brigar com o "
           "verde.\n\n"
           "B · Selo verde (não escolhida): página branca, selo verde grande com halo, título em Anybody verde e a etiqueta verde "
           "\"O organizador já vê no caixa\". A oferta continua no cartão azul aprovado, logo abaixo.\n\n"
           "Nas duas, a barra de cima perde o título repetido e fica só o X. O texto é o mesmo de hoje: o "
           "servidor não muda.", "blue", w=460)
    y += max(hs) + ROW_GAP + ROW_TITLE

    # 7 · Pix do grupo na página do grupo
    title("t7", y, "7 · Pix do grupo na página do grupo", 6)
    hs = [add("Hoje-grupo-sem", 0, y, "Hoje · membro sem cobrança"),
          add("Grupo-pix", 1, y, "Proposta · seção Pix do grupo"),
          add("Hoje-grupo-com", 2, y, "Com cobrança · não muda")]
    sticky("n7hoje", 3, y,
           "O que pega hoje:\n"
           "1. O membro só vê o Pix do grupo quando deve: dentro do cartão da cobrança, com Copiar chave Pix.\n"
           "2. Sem cobrança, a chave não aparece em lugar nenhum da página do grupo.\n"
           "3. Dono e admins copiam o Pix por um cartão no fim do caixa, que sai no caixa novo.", "gray")
    sticky("n7prop", 4, y,
           "Decidido em 29/10 (opção a): o Pix do grupo fica na página do grupo, para todos os membros.\n\n"
           "Seção \"Pix do grupo\" logo depois de Minhas cobranças: a chave, o tipo (como no cartão da cobrança) e Copiar. Quem "
           "nunca teve cobrança (dono, admin, mensalista novo) vê a seção depois da Galera.\n\n"
           "Com cobrança em aberto, o Copiar já está no cartão da cobrança e a seção não se repete.\n"
           "Com o recebimento pelo app ligado, a seção não aparece: o pagamento é pelo app.\n"
           "Grupo sem chave: nada para o membro; o gestor continua com o aviso e a checklist de hoje.", "blue", w=460)
    y += max(hs) + ROW_GAP

    return {
        "v": 3,
        "createdOnFiles": {"v": 1, "at": "2026-10-28T19:40:00Z"},
        "title": "Financeiro · hoje e propostas",
        "launch": {"view": "canvas"},
        "pages": [],
        "boards": boards,
        "order": order,
        "notes": notes,
        "designSystems": [],
    }


if __name__ == "__main__":
    measured = json.loads(sys.argv[1]) if len(sys.argv) > 1 else {}
    heights = {}
    for key, (page_title, src, h, alt) in TODAY.items():
        heights[key] = h
        with open(os.path.join(PROJECT, f"{key}.dc.html"), "w") as f:
            f.write(dc_html(page_title, today(src, h, alt), 390, h))
    for key, (page_title, make) in BOARDS.items():
        h = max(844, measured.get(key, 844))
        heights[key] = h
        made = make()
        root = made if isinstance(made, str) else page(made[0], h, made[1])
        with open(os.path.join(PROJECT, f"{key}.dc.html"), "w") as f:
            f.write(dc_html(page_title, root, 390, h))
        with open(os.path.join(RENDER, f"{key}.html"), "w") as f:
            f.write(preview_html(root))
    with open(os.path.join(PROJECT, "canvas.json"), "w") as f:
        json.dump(canvas(heights), f, ensure_ascii=False)
    print("ok")
