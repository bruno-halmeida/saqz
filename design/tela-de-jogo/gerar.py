#!/usr/bin/env python3
"""Gera os artboards (.dc.html) do canvas da tela de jogo (claude.ai/artifact/EMQMpFSFJFoxhdg4YTU8Z9): versão cartão,
versão tela cheia (prefixo Cheia-, a escolhida) e os testes da barra de cima (Topo-), além das prévias em HTML puro.

Uso: python3 gerar.py '{"Main": 1352, "Cinco-um-atleta": 1480, "Confirmado-nao-vao": 696, "Na-espera": 796, "Organizador": 1244, "Organizador-espera": 896, "Rascunho": 412, "Encerrado": 692, "Menu-do-jogo": 844, "Cheia-Main": 1320, "Cheia-Cinco-um-atleta": 1444, "Cheia-Confirmado-nao-vao": 844, "Cheia-Na-espera": 844, "Cheia-Organizador": 1216, "Cheia-Organizador-espera": 868, "Cheia-Rascunho": 844, "Cheia-Encerrado": 844, "Cheia-Menu-do-jogo": 844, "Topo-escura": 560, "Topo-marinho": 560, "Topo-branca": 560}' branca

Tokens e medidas copiados de mobile/core/design-system (SaqzColorTokens, SaqzTypography,
SaqzMetrics, SaqzHeroCard, AttendanceScoreBoard, SaqzMemberRow, SaqzStatusChip).
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
W82 = "rgba(255,255,255,0.82)"
W72 = "rgba(255,255,255,0.72)"
W70 = "rgba(255,255,255,0.7)"
W60 = "rgba(255,255,255,0.6)"
W18 = "rgba(255,255,255,0.18)"
W14 = "rgba(255,255,255,0.14)"
W12 = "rgba(255,255,255,0.12)"
W08 = "rgba(255,255,255,0.08)"
LIME18 = "rgba(199,243,0,0.18)"
LIME32 = "rgba(199,243,0,0.32)"
LIME14 = "rgba(199,243,0,0.14)"
WARN14 = "rgba(245,166,35,0.14)"
SCRIM = "rgba(14,23,56,0.46)"

UI = "font-family: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
DISPLAY = "font-family: Anybody, 'Arial Black', Inter, sans-serif; font-stretch: 110%; font-weight: 800"
FONTS = ("https://fonts.googleapis.com/css2?family=Anybody:wdth,wght@110,800"
         "&family=Inter:wght@400;500;600;700&display=swap")

BALL = (
    '<svg width="210" height="210" viewBox="0 0 1254 1254" fill="#FFFFFF" aria-hidden="true" '
    'style="position: absolute; right: -58px; top: -62px; opacity: 0.2;">'
    '<path d="M 597,108 C 493,207 449,406 583,580 C 533,663 469,727 397,767 C 263,638 211,410 279,252 C 294,230 313,211 333,196 C 416,145 503,118 597,108 Z"></path>'
    '<path d="M 649,108 C 854,108 1037,248 1113,441 C 940,284 741,222 548,265 C 567,196 603,144 649,108 Z"></path>'
    '<path d="M 542,309 C 764,255 1001,350 1138,531 C 1161,607 1155,701 1125,772 C 1009,584 802,480 590,518 C 557,457 539,382 542,309 Z"></path>'
    '<path d="M 634,552 C 733,535 842,561 928,610 C 861,854 601,1047 317,1048 C 259,1003 211,945 176,878 C 388,858 553,744 634,552 Z"></path>'
    '<path d="M 962,632 C 1032,690 1083,757 1107,826 C 1009,1040 826,1146 627,1146 C 532,1146 443,1122 365,1078 C 674,1050 897,852 962,632 Z"></path>'
    '<path d="M 277,245 C 111,400 52,642 157,844 C 228,834 296,815 360,784 C 232,649 206,445 277,245 Z"></path>'
    '</svg>'
)

ICONS = {
    "back": '<path d="m15 18-6-6 6-6"></path>',
    "more": '<circle cx="12" cy="12" r="1"></circle><circle cx="19" cy="12" r="1"></circle><circle cx="5" cy="12" r="1"></circle>',
    "more_v": '<circle cx="12" cy="12" r="1"></circle><circle cx="12" cy="5" r="1"></circle><circle cx="12" cy="19" r="1"></circle>',
    "pin": '<path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"></path><circle cx="12" cy="10" r="3"></circle>',
    "clock": '<circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline>',
    "megaphone": '<path d="m3 11 18-5v12L3 14v-3z"></path><path d="M11.6 16.8a3 3 0 1 1-5.8-1.6"></path>',
    "user_plus": '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><line x1="19" x2="19" y1="8" y2="14"></line><line x1="22" x2="16" y1="11" y2="11"></line>',
    "plus": '<path d="M5 12h14"></path><path d="M12 5v14"></path>',
    "check": '<path d="M20 6 9 17l-5-5"></path>',
    "pencil": '<path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"></path>',
    "users": '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><path d="M22 21v-2a4 4 0 0 0-3-3.87"></path><path d="M16 3.13a4 4 0 0 1 0 7.75"></path>',
    "circle_x": '<circle cx="12" cy="12" r="10"></circle><path d="m15 9-6 6"></path><path d="m9 9 6 6"></path>',
    "x": '<path d="M18 6 6 18"></path><path d="m6 6 12 12"></path>',
    "card": '<rect width="20" height="14" x="2" y="5" rx="2"></rect><line x1="2" x2="22" y1="10" y2="10"></line>',
    "calendar": '<path d="M8 2v4"></path><path d="M16 2v4"></path><rect width="18" height="18" x="3" y="4" rx="2"></rect><path d="M3 10h18"></path>',
    "timer": '<line x1="10" x2="14" y1="2" y2="2"></line><line x1="12" x2="15" y1="14" y2="11"></line><circle cx="12" cy="14" r="8"></circle>',
    "send": '<path d="M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z"></path><path d="m21.854 2.147-10.94 10.939"></path>',
}


# Tela cheia: o azul vira o cabeçalho da tela (de borda a borda, com a barra de cima) e a lista ocupa a largura toda.
FULL = False
# Barra de cima na tela cheia: "azul" (some no cabeçalho), "escura" (azul pressionado), "marinho" ou "branca".
TOPBAR = "azul"
APPBAR_MARK = "<!--appbar-->"
NAVY_BAR = NAVY
BLUE_DARK = "#052BB3"
BALL_FULL = BALL.replace("right: -58px; top: -62px;", "right: -58px; top: -6px;")
BALL_FULL_BAND = BALL.replace("right: -58px; top: -62px;", "right: -58px; top: 0px;")


def icon(name, size=20, color="currentColor", extra=""):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" stroke="{color}" '
            f'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" '
            f'style="flex: none;{extra}">{ICONS[name]}</svg>')


# ---------------------------------------------------------------- moldura
def appbar(menu=False):
    trailing = ""
    if menu:
        trailing = (f'<button aria-label="Mais ações do jogo" style="width: 44px; height: 44px; flex: none; '
                    f'display: flex; align-items: center; justify-content: center; padding: 0; border: 0; '
                    f'border-radius: 22px; background: transparent; color: {BLUE};">{icon("more", 24)}</button>')
    return (
        f'<div style="display: flex; align-items: center; gap: 4px; min-height: 56px; box-sizing: border-box; '
        f'padding: 6px 8px 6px 4px; flex: none;">'
        f'<button aria-label="Voltar" style="width: 44px; height: 44px; flex: none; display: flex; align-items: center; '
        f'justify-content: center; padding: 0; border: 0; border-radius: 22px; background: transparent; color: {BLUE};">'
        f'{icon("back", 24)}</button>'
        f'<span style="flex-grow: 1; min-width: 0; font-size: 22px; line-height: 27px; font-weight: 700; '
        f'letter-spacing: -0.02em; color: {NAVY};">Jogo</span>'
        f'{trailing}</div>'
    )


def appbar_band(menu=False, title="Vôlei do CERET"):
    """Barra de cima com fundo próprio, acima do cabeçalho azul."""
    if TOPBAR == "branca":
        fg, bg, size, weight, track = BLUE, WHITE, "22px", "700", " letter-spacing: -0.02em;"
        title_color = NAVY
        line = f" border-bottom: 1px solid {BORDER};"
    else:
        fg, bg, size, weight, track = WHITE, (BLUE_DARK if TOPBAR == "escura" else NAVY_BAR), "17px", "600", ""
        title_color = WHITE
        line = ""
    lh = "27px" if size == "22px" else "22px"
    trailing = ""
    if menu:
        trailing = (f'<button aria-label="Mais ações do jogo" style="width: 44px; height: 44px; flex: none; '
                    f'display: flex; align-items: center; justify-content: center; padding: 0; border: 0; '
                    f'border-radius: 22px; background: transparent; color: {fg};">{icon("more", 24)}</button>')
    return (
        f'<div style="position: relative; display: flex; align-items: center; gap: 4px; min-height: 56px; '
        f'box-sizing: border-box; padding: 6px 8px 6px 4px; background: {bg};{line} flex: none;">'
        f'<button aria-label="Voltar" style="width: 44px; height: 44px; flex: none; display: flex; align-items: center; '
        f'justify-content: center; padding: 0; border: 0; border-radius: 22px; background: transparent; color: {fg};">'
        f'{icon("back", 24)}</button>'
        f'<span style="flex-grow: 1; min-width: 0; font-size: {size}; line-height: {lh}; font-weight: {weight};{track} '
        f'color: {title_color}; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">{title}</span>'
        f'{trailing}</div>'
    )


def appbar_blue(menu=False, title="Vôlei do CERET"):
    trailing = ""
    if menu:
        trailing = (f'<button aria-label="Mais ações do jogo" style="width: 44px; height: 44px; flex: none; '
                    f'display: flex; align-items: center; justify-content: center; padding: 0; border: 0; '
                    f'border-radius: 22px; background: transparent; color: {WHITE};">{icon("more", 24)}</button>')
    return (
        f'<div style="position: relative; display: flex; align-items: center; gap: 4px; min-height: 56px; '
        f'box-sizing: border-box; padding: 6px 8px 6px 4px; margin: 0 -20px -4px;">'
        f'<button aria-label="Voltar" style="width: 44px; height: 44px; flex: none; display: flex; align-items: center; '
        f'justify-content: center; padding: 0; border: 0; border-radius: 22px; background: transparent; color: {WHITE};">'
        f'{icon("back", 24)}</button>'
        f'<span style="flex-grow: 1; min-width: 0; font-size: 17px; line-height: 22px; font-weight: 600; color: {WHITE}; '
        f'white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">{title}</span>'
        f'{trailing}</div>'
    )


def full_inner(inner, menu):
    """Na tela cheia a barra fica dentro do azul ("azul") ou numa faixa própria acima dele."""
    if TOPBAR == "azul":
        return inner.replace(APPBAR_MARK, appbar_blue(menu))
    body_html = inner.replace(BALL_FULL, BALL_FULL_BAND).replace(APPBAR_MARK, '<span style="height: 8px; flex: none;"></span>')
    return appbar_band(menu) + body_html


def screen(inner, height, menu=False):
    if FULL:
        return (
            f'<div style="width: 390px; height: {height}px; box-sizing: border-box; background: {WHITE}; {UI}; '
            f'color: {NAVY}; display: flex; flex-direction: column; overflow: hidden; position: relative;">'
            f'{full_inner(inner, menu)}</div>'
        )
    return (
        f'<div style="width: 390px; height: {height}px; box-sizing: border-box; background: {CANVAS}; {UI}; '
        f'color: {NAVY}; display: flex; flex-direction: column; overflow: hidden; position: relative;">'
        f'{appbar(menu)}'
        f'<div style="display: flex; flex-direction: column; gap: 12px; padding: 4px 16px 24px;">{inner}</div>'
        f'</div>'
    )


# ---------------------------------------------------------------- hero
def kicker_row(kicker, chip, muted_dot=False):
    halo = W18 if muted_dot else LIME18
    dot = W60 if muted_dot else LIME
    chip_html = "" if FULL or not chip else (
        f'<span style="flex: none; font-size: 12px; line-height: 16px; font-weight: 600; padding: 5px 10px; '
        f'border-radius: 999px; background: {W14}; color: {WHITE}; white-space: nowrap;">{chip}</span>')
    return (
        f'<div style="position: relative; display: flex; align-items: center; justify-content: space-between; gap: 8px; min-height: 26px;">'
        f'<div style="display: flex; align-items: center; gap: 9px; min-width: 0;">'
        f'<span style="width: 16px; height: 16px; flex: none; border-radius: 8px; background: {halo}; display: flex; '
        f'align-items: center; justify-content: center;"><span style="width: 8px; height: 8px; border-radius: 4px; '
        f'background: {dot};"></span></span>'
        f'<span style="font-size: 12px; line-height: 16px; font-weight: 600; letter-spacing: 0.08em; '
        f'text-transform: uppercase; color: {W82}; white-space: nowrap;">{kicker}</span></div>'
        f'{chip_html}</div>'
    )


def title(text):
    return (f'<h1 style="position: relative; margin: 0; {DISPLAY}; font-size: 36px; line-height: 38px; '
            f'letter-spacing: -0.03em; color: {WHITE};">{text}</h1>')


def meta(text):
    return (f'<p style="position: relative; margin: 0; font-size: 14px; line-height: 20px; font-weight: 500; '
            f'color: {W82};">{text}</p>')


def info_row(icon_name, text, extra=""):
    extra_html = (f'<span style="font-size: 14px; line-height: 20px; font-weight: 400; color: {W82};"> · {extra}</span>'
                  if extra else "")
    return (f'<div style="display: flex; align-items: center; gap: 8px;">{icon(icon_name, 18, W82)}'
            f'<span style="font-size: 15px; line-height: 20px; font-weight: 600; color: {WHITE};">{text}{extra_html}</span></div>')


def info(date, duration, end_time, venue, address=None, map_link=True):
    link = ""
    if map_link:
        link = (f'<button style="flex: none; min-height: 44px; margin: -12px -8px -12px 0; padding: 0 8px; border: 0; '
                f'border-radius: 999px; background: transparent; color: {WHITE}; font-family: inherit; font-size: 14px; '
                f'line-height: 20px; font-weight: 600;">Ver no mapa</button>')
    addr = (f'<span style="padding-left: 26px; font-size: 14px; line-height: 20px; color: {W82};">{address}</span>'
            if address else "")
    place_html = (
        f'<div style="display: flex; flex-direction: column;">'
        f'<div style="display: flex; align-items: center; gap: 8px;">{icon("pin", 18, W82)}'
        f'<span style="flex-grow: 1; min-width: 0; font-size: 15px; line-height: 20px; font-weight: 600; '
        f'color: {WHITE};">{venue}</span>{link}</div>{addr}</div>'
    )
    return (f'<div style="position: relative; display: flex; flex-direction: column; gap: 10px; padding: 2px 0;">'
            f'{info_row("calendar", date)}{info_row("timer", duration, end_time)}{place_html}</div>')


def deadline(text):
    return (f'<p style="position: relative; margin: 0; display: flex; align-items: center; gap: 8px; font-size: 14px; '
            f'line-height: 20px; font-weight: 600; color: {WHITE};">{icon("clock", 16)}<span>{text}</span></p>')


def answer_buttons():
    return (
        f'<div style="position: relative; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 4px;">'
        f'<button style="height: 52px; border: 0; border-radius: 999px; background: {LIME}; color: {NAVY}; '
        f'font-family: inherit; font-size: 16px; line-height: 20px; font-weight: 600;">Vou</button>'
        f'<button style="height: 52px; border: 1.5px solid {W60}; border-radius: 999px; background: transparent; '
        f'color: {WHITE}; font-family: inherit; font-size: 16px; line-height: 20px; font-weight: 600;">Não vou</button>'
        f'</div>'
    )


def big_button(label):
    return (f'<button style="position: relative; height: 52px; border: 0; border-radius: 999px; background: {LIME}; '
            f'color: {NAVY}; font-family: inherit; font-size: 16px; line-height: 20px; font-weight: 600;">{label}</button>')


def answered(text, dot=SUCCESS):
    return (
        f'<div style="position: relative; display: flex; align-items: center; gap: 8px; min-height: 48px; '
        f'box-sizing: border-box; padding: 4px 4px 4px 12px; border-radius: 10px; background: {W12};">'
        f'<span style="width: 8px; height: 8px; flex: none; border-radius: 4px; background: {dot};"></span>'
        f'<span style="flex-grow: 1; min-width: 0; font-size: 14px; line-height: 20px; font-weight: 600; color: {WHITE}; white-space: nowrap;">{text}</span>'
        f'<button style="flex: none; min-height: 40px; padding: 0 8px; border: 0; border-radius: 999px; '
        f'background: transparent; color: {WHITE}; font-family: inherit; font-size: 14px; line-height: 20px; '
        f'font-weight: 600;">Alterar</button></div>'
    )


def lime_line(text):
    return (f'<p style="position: relative; margin: 0; display: flex; align-items: center; gap: 8px; font-size: 14px; '
            f'line-height: 20px; font-weight: 600; color: {LIME};"><span style="width: 6px; height: 6px; flex: none; '
            f'border-radius: 3px; background: {LIME};"></span><span>{text}</span></p>')


def hint(text, center=False):
    align = " text-align: center;" if center else ""
    return (f'<p style="position: relative; margin: 0; font-size: 14px; line-height: 20px; color: {W82};{align}">'
            f'{text}</p>')


def fee_note(text):
    return (f'<p style="position: relative; margin: 0; display: flex; align-items: center; gap: 8px; font-size: 14px; '
            f'line-height: 20px; color: {W82};">{icon("card", 16, W82)}<span>{text}</span></p>')


def pending_row(text):
    return (
        f'<div style="position: relative; display: flex; align-items: center; gap: 12px; min-height: 52px; '
        f'box-sizing: border-box; padding: 6px 6px 6px 14px; border-radius: 10px; background: {W12};">'
        f'{icon("megaphone", 20, WHITE)}'
        f'<span style="flex-grow: 1; min-width: 0; font-size: 14px; line-height: 20px; font-weight: 600; color: {WHITE};">{text}</span>'
        f'<button style="flex: none; min-height: 40px; padding: 0 18px; border: 0; border-radius: 999px; '
        f'background: {WHITE}; color: {NAVY}; font-family: inherit; font-size: 14px; line-height: 20px; '
        f'font-weight: 600;">Avisar</button></div>'
    )


def waitlist_block(chip, box_title, box_body, leave):
    return (
        f'<span style="position: relative; align-self: flex-start; display: flex; align-items: center; gap: 6px; '
        f'font-size: 12px; line-height: 16px; font-weight: 600; padding: 5px 10px; border-radius: 999px; '
        f'background: {W14}; color: {WHITE};"><span style="width: 6px; height: 6px; border-radius: 3px; '
        f'background: {WHITE};"></span>{chip}</span>'
        f'<div style="position: relative; display: flex; align-items: flex-start; gap: 12px; padding: 14px 16px; '
        f'border-radius: 12px; background: {W12};">'
        f'<span style="width: 36px; height: 36px; flex: none; border-radius: 18px; background: {W14}; display: flex; '
        f'align-items: center; justify-content: center; color: {WHITE};">{icon("clock", 20)}</span>'
        f'<div style="display: flex; flex-direction: column; gap: 2px; min-width: 0;">'
        f'<span style="font-size: 15px; line-height: 20px; font-weight: 600; color: {WHITE};">{box_title}</span>'
        f'<span style="font-size: 14px; line-height: 20px; color: {W82};">{box_body}</span></div></div>'
        f'<button style="position: relative; align-self: center; min-height: 44px; padding: 0 16px; border: 0; '
        f'border-radius: 999px; background: transparent; color: {WHITE}; font-family: inherit; font-size: 15px; '
        f'line-height: 20px; font-weight: 600;">{leave}</button>'
    )


def placar(tabs):
    """tabs: lista de (numero, sufixo, rotulo, cor_numero, selecionado)."""
    cells = []
    for number, suffix, label, color, selected in tabs:
        suffix_html = ""
        if suffix:
            suffix_html = (f'<span style="font-size: 13px; line-height: 16px; font-weight: 600; color: {W60};">'
                           f'{suffix}</span>')
        indicator = ""
        if selected:
            indicator = (f'<span style="position: absolute; left: 50%; bottom: 0; width: 44px; margin-left: -22px; '
                         f'height: 4px; border-radius: 4px 4px 0 0; background: {LIME};"></span>')
        label_style = (f"font-weight: 600; color: {WHITE};" if selected else f"font-weight: 400; color: {W72};")
        cells.append(
            f'<button role="tab" aria-selected="{"true" if selected else "false"}" style="position: relative; '
            f'display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 14px 4px 16px; '
            f'border: 0; background: transparent; font-family: inherit;">'
            f'<span style="display: flex; align-items: baseline; gap: 1px;">'
            f'<span style="{DISPLAY}; font-size: 28px; line-height: 28px; letter-spacing: -0.03em; color: {color};">'
            f'{number}</span>{suffix_html}</span>'
            f'<span style="font-size: 12px; line-height: 16px; {label_style}">{label}</span>{indicator}</button>'
        )
    cols = len(tabs)
    return (
        f'<div role="tablist" style="position: relative; display: grid; grid-template-columns: repeat({cols}, '
        f'minmax(0, 1fr)); margin: 4px -20px -20px; background: {W08}; border-top: 1px solid {W18};">'
        f'{"".join(cells)}</div>'
    )


def hero(parts, joined=True):
    if FULL:
        return (
            f'<section style="position: relative; overflow: hidden; background: {BLUE}; padding: 0 20px 20px; '
            f'display: flex; flex-direction: column; gap: 12px; color: {WHITE}; flex: none;">'
            f'{BALL_FULL}{APPBAR_MARK}{"".join(parts)}</section>'
        )
    radius = "20px 20px 0 0" if joined else "20px"
    return (
        f'<section style="position: relative; overflow: hidden; background: {BLUE}; border-radius: {radius}; '
        f'padding: 20px; display: flex; flex-direction: column; gap: 12px; color: {WHITE};">'
        f'{BALL}{"".join(parts)}</section>'
    )


# ---------------------------------------------------------------- lista
def body(rows):
    if FULL:
        return f'<div style="display: flex; flex-direction: column; background: {WHITE};">{"".join(rows)}</div>'
    return (f'<div style="margin-top: -12px; background: {WHITE}; border: 1px solid {BORDER}; border-top: 0; '
            f'border-radius: 0 0 20px 20px; overflow: hidden; display: flex; flex-direction: column;">'
            f'{"".join(rows)}</div>')


def initials(name):
    parts = [p for p in name.replace("(", "").split() if p[0].isalpha()]
    return (parts[0][0] + parts[-1][0]).upper() if len(parts) > 1 else parts[0][:2].upper()


def number_cell(text):
    return (f'<span style="width: 26px; flex: none; text-align: center; {DISPLAY}; font-size: 14px; line-height: 16px; '
            f'color: {MUTED};">{text}</span>')


def avatar(name, muted=False):
    color = MUTED if muted else BLUE
    return (f'<span style="width: 40px; height: 40px; flex: none; box-sizing: border-box; border-radius: 20px; '
            f'background: {ICE}; border: 1px solid {BORDER}; display: flex; align-items: center; justify-content: center; '
            f'font-size: 14px; line-height: 14px; font-weight: 600; color: {color};">{initials(name)}</span>')


def guest_avatar():
    return (f'<span style="width: 40px; height: 40px; flex: none; box-sizing: border-box; border-radius: 20px; '
            f'background: {ICE}; border: 1px solid {BORDER}; display: flex; align-items: center; justify-content: center; '
            f'color: {BLUE};">{icon("user_plus", 18)}</span>')


def chip(text, tone="accent"):
    bg, fg = {"accent": (LIME32, NAVY), "warning": (WARN14, WARN_FG), "brand": ("rgba(6,56,223,0.08)", BLUE)}[tone]
    return (f'<span style="flex: none; font-size: 12px; line-height: 16px; font-weight: 600; padding: 5px 10px; '
            f'border-radius: 999px; background: {bg}; color: {fg}; white-space: nowrap;">{text}</span>')


def more_button(name):
    return (f'<button aria-label="Ações de {name}" style="width: 44px; height: 44px; flex: none; margin: -10px -10px -10px 0; '
            f'display: flex; align-items: center; justify-content: center; padding: 0; border: 0; border-radius: 22px; '
            f'background: transparent; color: {MUTED};">{icon("more_v", 20)}</button>')


def small_secondary(label):
    return (f'<button style="flex: none; min-height: 40px; box-sizing: border-box; padding: 0 16px; '
            f'border: 1.5px solid {BLUE}; border-radius: 999px; background: {WHITE}; color: {BLUE}; '
            f'font-family: inherit; font-size: 14px; line-height: 20px; font-weight: 600;">{label}</button>')


def small_primary(label):
    return (f'<button style="flex: none; min-height: 40px; padding: 0 16px; border: 0; border-radius: 999px; '
            f'background: {BLUE}; color: {WHITE}; font-family: inherit; font-size: 14px; line-height: 20px; '
            f'font-weight: 600;">{label}</button>')


TEXT_COLUMN_BUTTON = "width: 100%; border: 0; background: transparent; text-align: left; font-family: inherit;"


def row(left_html, right_html, first=False, bg="", left_pad="10px", button=False):
    tag = "button" if button else "div"
    reset = TEXT_COLUMN_BUTTON if button else ""
    top = "" if first else f" border-top: 1px solid {BORDER};"
    if FULL:
        return (
            f'<{tag} style="display: flex; align-items: stretch; padding: 0 0 0 {left_pad}; {reset}{bg}">'
            f'<span style="display: flex; align-items: center; gap: 12px; flex: none;">{left_html}</span>'
            f'<span style="flex-grow: 1; min-width: 0; display: flex; align-items: center; gap: 12px; min-height: 64px; '
            f'box-sizing: border-box; margin-left: 12px; padding: 12px 16px 12px 0;{top}">{right_html}</span></{tag}>'
        )
    return (
        f'<{tag} style="display: flex; align-items: center; gap: 12px; min-height: 64px; box-sizing: border-box; '
        f'{reset} padding: 12px 16px 12px {left_pad};{top}{bg}">{left_html}{right_html}</{tag}>'
    )


def person(num, name, meta_text, first=False, you=False, trailing="", guest=False, out=False):
    bg = f" background: {LIME14};" if you else ""
    you_chip = chip("Você") if you else ""
    av = guest_avatar() if guest else avatar(name, muted=out)
    meta_html = (f'<span style="font-size: 14px; line-height: 20px; color: {MUTED}; white-space: nowrap; overflow: hidden; '
                 f'text-overflow: ellipsis;">{meta_text}</span>' if meta_text else "")
    left = "16px" if num is None else "10px"
    num_html = "" if num is None else number_cell(num)
    text = (f'<span style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px;">'
            f'<span style="font-size: 16px; line-height: 23px; font-weight: 600; color: {NAVY}; white-space: nowrap; '
            f'overflow: hidden; text-overflow: ellipsis;">{name}</span>{meta_html}</span>')
    return row(f"{num_html}{av}", f"{text}{you_chip}{trailing}", first=first, bg=bg, left_pad=left)


def open_slot(num, first=False, sub=None, label="Vaga aberta"):
    sub_html = (f'<span style="font-size: 14px; line-height: 20px; color: {MUTED};">{sub}</span>' if sub else "")
    left = (f'{number_cell(num)}<span style="width: 40px; height: 40px; flex: none; box-sizing: border-box; '
            f'border-radius: 20px; border: 1.5px dashed {PLACEHOLDER};"></span>')
    text = (f'<span style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px;">'
            f'<span style="font-size: 16px; line-height: 23px; font-weight: 600; color: {MUTED};">{label}</span>'
            f'{sub_html}</span>')
    return row(left, text, first=first)


def open_slot_action(num, label, sub, first=False):
    left = (f'{number_cell(num)}<span style="width: 40px; height: 40px; flex: none; box-sizing: border-box; '
            f'border-radius: 20px; border: 1.5px dashed {BLUE}; display: flex; align-items: center; '
            f'justify-content: center; color: {BLUE};">{icon("plus", 20)}</span>')
    text = (f'<span style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px;">'
            f'<span style="font-size: 16px; line-height: 23px; font-weight: 600; color: {BLUE};">{label}</span>'
            f'<span style="font-size: 14px; line-height: 20px; color: {MUTED};">{sub}</span></span>')
    return row(left, text, first=first, button=True)


def action_row(icon_name, label, sub, first=False):
    left = (f'<span style="width: 26px; flex: none;"></span><span style="width: 40px; height: 40px; flex: none; '
            f'box-sizing: border-box; border-radius: 20px; border: 1.5px dashed {BLUE}; display: flex; '
            f'align-items: center; justify-content: center; color: {BLUE};">{icon(icon_name, 18)}</span>')
    text = (f'<span style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px;">'
            f'<span style="font-size: 16px; line-height: 23px; font-weight: 600; color: {BLUE};">{label}</span>'
            f'<span style="font-size: 14px; line-height: 20px; color: {MUTED};">{sub}</span></span>')
    return row(left, text, first=first, button=True)


def group_header(label, complete, count, missing=0, first=False):
    if complete:
        right = (f'<span style="display: flex; align-items: center; gap: 4px; font-size: 13px; line-height: 16px; '
                 f'font-weight: 600; color: {SUCCESS_FG};">{icon("check", 14)}{count} · completo</span>')
    else:
        word = "Falta" if missing == 1 else "Faltam"
        right = (f'<span style="display: flex; align-items: center; gap: 8px;">'
                 f'<span style="font-size: 13px; line-height: 16px; font-weight: 600; color: {MUTED};">{count}</span>'
                 f'{chip(f"{word} {missing}", "warning")}</span>')
    top = "" if first or FULL else f" border-top: 1px solid {BORDER};"
    return (
        f'<div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 44px; '
        f'box-sizing: border-box; padding: 8px 16px; background: {ICE};{top}">'
        f'<span style="font-size: 15px; line-height: 18px; font-weight: 600; color: {NAVY};">{label}</span>{right}</div>'
    )


def note(text, first=False):
    top = "" if first else f" border-top: 1px solid {BORDER};"
    if FULL:
        return (f'<p style="margin: 0 0 0 100px; padding: 12px 16px 14px 0; font-size: 14px; line-height: 20px; '
                f'color: {MUTED};{top}">{text}</p>')
    return (f'<p style="margin: 0; padding: 12px 16px 14px 48px; font-size: 14px; line-height: 20px; color: {MUTED};{top}">'
            f'{text}</p>')


# ---------------------------------------------------------------- artboards
COMMON_INFO = info("28 de julho", "2h de jogo", "até 21h30", "CERET — Quadra 2", "R. Canuto Abreu, s/n · Tatuapé")
CHIP = "Vôlei do CERET"


def board_a():
    rows = [
        person("1", "Lucas Prado", "Levantador", first=True),
        person("2", "Bia Souza", "Ponta"),
        person("3", "Thiago Melo", "Central"),
        person("4", "Carla Mendes", "Oposta"),
        person("5", "Felipe Nunes", "Ponta"),
        person("6", "Gabi Torres", "Líbero"),
        person("7", "Igor Santos", "Central"),
        person("8", "Júlia Rocha", "Convidada de Bia Souza", guest=True),
        person("9", "Marcos Lima", "Ponta"),
        open_slot("10", sub="Pode ser a sua"),
        open_slot("11"),
        open_slot("12"),
    ]
    inner = hero([
        kicker_row("Confirmações abertas", CHIP),
        title("Terça, 19h30"),
        COMMON_INFO,
        deadline("Prioridade dos mensalistas até 17h30."),
        answer_buttons(),
        lime_line("Restam 3 vagas"),
        placar([("9", "/12", "Vão", LIME, True), ("2", "", "Na espera", WHITE, False),
                ("3", "", "Não vão", W70, False)]),
    ]) + body(rows)
    return inner


def board_b():
    rows = [
        person(None, "Rafa Dias", "Ponta", first=True, out=True),
        person(None, "Marina Costa", "Central", out=True),
        person(None, "Pedro Henrique", "Levantador", out=True),
    ]
    inner = hero([
        kicker_row("Confirmações abertas", CHIP),
        title("Terça, 19h30"),
        COMMON_INFO,
        deadline("Prioridade dos mensalistas até 17h30."),
        answered("Sua presença está confirmada."),
        placar([("10", "/12", "Vão", LIME, False), ("2", "", "Na espera", WHITE, False),
                ("3", "", "Não vão", W70, True)]),
    ]) + body(rows)
    return inner


def board_c():
    rows = [
        person("1º", "Caio Lima", "Central", first=True),
        person("2º", "Duda Alves", "Ponta", you=True),
        note("Se alguém desistir, a fila anda pela ordem."),
    ]
    inner = hero([
        kicker_row("Jogo lotado", CHIP),
        title("Terça, 19h30"),
        COMMON_INFO,
        waitlist_block("Lista de espera · 2º", "As vagas acabaram. Você entrou na lista de espera.",
                       "Se alguém desistir, você entra pela ordem da fila.", "Sair da lista de espera"),
        placar([("12", "/12", "Vão", LIME, False), ("2", "", "Na espera", WHITE, True),
                ("1", "", "Não vão", W70, False)]),
    ]) + body(rows)
    return inner


def board_d():
    rows = [
        group_header("Ponteiros(as)", True, "2/2", first=True),
        person("1", "Bia Souza", "Mensalista", first=True, trailing=more_button("Bia Souza")),
        person("2", "Felipe Nunes", "Avulso", trailing=more_button("Felipe Nunes")),
        group_header("Centrais", False, "1/2", missing=1),
        person("1", "Thiago Melo", "Mensalista", first=True, trailing=more_button("Thiago Melo")),
        open_slot_action("2", "Vaga de central", "Toque para colocar alguém"),
        group_header("Oposto(a)", True, "1/1"),
        person("1", "Carla Mendes", "Avulsa", first=True, trailing=more_button("Carla Mendes")),
        group_header("Levantador(a)", True, "1/1"),
        person("1", "Lucas Prado", "Mensalista", first=True, you=True),
        action_row("user_plus", "Levar convidado", "Entra na lista de espera."),
    ]
    inner = hero([
        kicker_row("Confirmações abertas", CHIP),
        title("Terça, 19h30"),
        COMMON_INFO,
        deadline("Prioridade dos mensalistas até 17h30."),
        answered("Sua presença está confirmada."),
        pending_row("4 pessoas sem resposta"),
        lime_line("Falta 1 central"),
        placar([("5", "/6", "Vão", LIME, True), ("2", "", "Na espera", WHITE, False),
                ("2", "", "Não vão", W70, False)]),
    ]) + body(rows)
    return inner


def board_h():
    """Organizador no 5-1, escolha manual: a aba Na espera com o botão de colocar cada pessoa no jogo."""
    rows = [
        group_header("Ponteiros(as)", True, "2/2", first=True),
        person("1º", "Duda Alves", "Avulsa", first=True,
               trailing=small_secondary("Colocar") + more_button("Duda Alves")),
        group_header("Centrais", False, "1/2", missing=1),
        person("1º", "Caio Lima", "Avulso", first=True,
               trailing=small_primary("Colocar") + more_button("Caio Lima")),
        note("Você escolhe quem entra. Em posição completa, quem você colocar fica acima das vagas."),
    ]
    inner = hero([
        kicker_row("Confirmações abertas", CHIP),
        title("Terça, 19h30"),
        COMMON_INFO,
        deadline("Prioridade dos mensalistas até 17h30."),
        answered("Sua presença está confirmada."),
        pending_row("4 pessoas sem resposta"),
        lime_line("Falta 1 central"),
        placar([("5", "/6", "Vão", LIME, False), ("2", "", "Na espera", WHITE, True),
                ("2", "", "Não vão", W70, False)]),
    ]) + body(rows)
    return inner


def board_g():
    """5-1 visto pelo atleta que ainda não respondeu: a lista de quem vai em uma seção por posição."""
    rows = [
        group_header("Ponteiros(as)", True, "4/4", first=True),
        person("1", "Bia Souza", "", first=True),
        person("2", "Felipe Nunes", ""),
        person("3", "Marcos Lima", ""),
        person("4", "Júlia Rocha", "Convidada de Bia Souza", guest=True),
        group_header("Centrais", False, "3/4", missing=1),
        person("1", "Thiago Melo", "", first=True),
        person("2", "Igor Santos", ""),
        person("3", "Paulo Reis", ""),
        open_slot("4", label="Vaga de central"),
        group_header("Opostos(as)", False, "1/2", missing=1),
        person("1", "Carla Mendes", "", first=True),
        open_slot("2", label="Vaga de oposto"),
        group_header("Levantadores(as)", True, "2/2"),
        person("1", "Lucas Prado", "", first=True),
        person("2", "Gabi Torres", ""),
    ]
    inner = hero([
        kicker_row("Confirmações abertas", CHIP),
        title("Terça, 19h30"),
        COMMON_INFO,
        deadline("Prioridade dos mensalistas até 17h30."),
        answer_buttons(),
        lime_line("Tem vaga para central e oposto"),
        placar([("10", "/12", "Vão", LIME, True), ("1", "", "Na espera", WHITE, False),
                ("2", "", "Não vão", W70, False)]),
    ]) + body(rows)
    return inner


def board_e():
    """Menu ⋯ do jogo: folha sobre a tela escurecida."""
    behind = hero([
        kicker_row("Confirmações abertas", CHIP),
        title("Terça, 19h30"),
        COMMON_INFO,
        deadline("Prioridade dos mensalistas até 17h30."),
        answered("Sua presença está confirmada."),
    ], joined=False)

    def item(icon_name, label, sub, danger=False):
        color = ERROR if danger else NAVY
        icolor = ERROR if danger else BLUE
        sub_html = (f'<span style="font-size: 14px; line-height: 20px; font-weight: 400; color: {MUTED};">{sub}</span>'
                    if sub else "")
        return (f'<button style="display: flex; align-items: center; gap: 14px; width: 100%; min-height: 64px; '
                f'box-sizing: border-box; padding: 10px 4px; border: 0; border-top: 1px solid {BORDER}; '
                f'background: transparent; text-align: left; font-family: inherit;">'
                f'{icon(icon_name, 22, icolor)}'
                f'<span style="display: flex; flex-direction: column; gap: 2px; min-width: 0;">'
                f'<span style="font-size: 16px; line-height: 23px; font-weight: 600; color: {color};">{label}</span>'
                f'{sub_html}</span></button>')

    sheet = (
        f'<div style="position: absolute; inset: 0; z-index: 2; background: {SCRIM}; display: flex; flex-direction: column; '
        f'justify-content: flex-end;">'
        f'<div role="dialog" aria-label="Ações do jogo" style="background: {WHITE}; border-radius: 28px 28px 0 0; '
        f'padding: 10px 16px 28px; box-shadow: 0 -12px 40px rgba(14,23,56,0.12); display: flex; flex-direction: column;">'
        f'<span style="align-self: center; width: 40px; height: 4px; border-radius: 2px; background: {BORDER}; '
        f'margin-bottom: 14px;"></span>'
        f'<div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 0 0 12px 4px;">'
        f'<span style="font-size: 22px; line-height: 27px; font-weight: 700; letter-spacing: -0.02em; color: {NAVY};">'
        f'Jogo de terça, 28/07</span>'
        f'<button aria-label="Fechar" style="width: 44px; height: 44px; flex: none; display: flex; align-items: center; '
        f'justify-content: center; padding: 0; border: 0; border-radius: 22px; background: {ICE}; color: {NAVY};">'
        f'{icon("x", 22)}</button></div>'
        f'{item("pencil", "Editar jogo", "Data, hora, quadra e valor")}'
        f'{item("users", "Ajustar vagas", "Hoje são 12 vagas")}'
        f'{item("circle_x", "Cancelar jogo", "Pede confirmação antes", danger=True)}'
        f'</div></div>'
    )
    return behind, sheet


def board_f1():
    return hero([
        kicker_row("Rascunho", CHIP, muted_dot=True),
        title("Terça, 19h30"),
        info("4 de agosto", "2h de jogo", "até 21h30", "CERET — Quadra 2", "R. Canuto Abreu, s/n · Tatuapé"),
        big_button("Publicar jogo"),
        hint("Só você vê este jogo até publicar.", center=True),
    ], joined=False)


def board_f2():
    rows = [
        person("1", "Lucas Prado", "Mensalista", first=True, you=True),
        person("2", "Bia Souza", "Mensalista"),
        person("3", "Felipe Nunes", "Avulso"),
        note("e mais 8"),
    ]
    inner = hero([
        kicker_row("Jogo realizado", CHIP, muted_dot=True),
        title("Terça, 19h30"),
        info("21 de julho", "2h de jogo", "até 21h30", "CERET — Quadra 2", map_link=False),
        big_button("Encerrar jogo e acertar"),
        placar([("11", "/12", "Jogaram", LIME, True), ("3", "", "Não foram", W70, False)]),
    ]) + body(rows)
    return inner


# ---------------------------------------------------------------- saída
def dc_html(page_title, root, height):
    props = json.dumps({"$preview": {"width": 390, "height": height}})
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


BOARDS = {
    "Main": ("Tela de jogo · atleta sem resposta", lambda h: screen(board_a(), h)),
    "Cinco-um-atleta": ("Tela de jogo · 5-1, atleta sem resposta", lambda h: screen(board_g(), h)),
    "Confirmado-nao-vao": ("Tela de jogo · confirmado, aba Não vão", lambda h: screen(board_b(), h)),
    "Na-espera": ("Tela de jogo · na lista de espera", lambda h: screen(board_c(), h)),
    "Organizador": ("Tela de jogo · organizador com posições", lambda h: screen(board_d(), h, menu=True)),
    "Organizador-espera": ("Tela de jogo · organizador, lista de espera do 5-1", lambda h: screen(board_h(), h, menu=True)),
    "Rascunho": ("Tela de jogo · rascunho", lambda h: screen(board_f1(), h, menu=True)),
    "Encerrado": ("Tela de jogo · jogo encerrado", lambda h: screen(board_f2(), h, menu=True)),
}


def menu_root(h):
    behind, sheet = board_e()
    if FULL:
        return (
            f'<div style="width: 390px; height: {h}px; box-sizing: border-box; background: {WHITE}; {UI}; color: {NAVY}; '
            f'display: flex; flex-direction: column; overflow: hidden; position: relative;">'
            f'{full_inner(behind, True)}{sheet}</div>'
        )
    return (
        f'<div style="width: 390px; height: {h}px; box-sizing: border-box; background: {CANVAS}; {UI}; color: {NAVY}; '
        f'display: flex; flex-direction: column; overflow: hidden; position: relative;">'
        f'{appbar(True)}<div style="display: flex; flex-direction: column; gap: 12px; padding: 4px 16px 24px;">{behind}</div>'
        f'{sheet}</div>'
    )


def write_set(heights, prefix="", suffix=""):
    for name, (page_title, make) in BOARDS.items():
        key = prefix + name
        h = heights.get(key, 1600)
        with open(os.path.join(PROJECT, f"{key}.dc.html"), "w") as f:
            f.write(dc_html(page_title + suffix, make(h), h))
        with open(os.path.join(RENDER, f"{key}.html"), "w") as f:
            f.write(preview_html(make(h)))
    key = prefix + "Menu-do-jogo"
    h = heights.get(key, 844)
    with open(os.path.join(PROJECT, f"{key}.dc.html"), "w") as f:
        f.write(dc_html("Tela de jogo · menu do jogo" + suffix, menu_root(h), h))
    with open(os.path.join(RENDER, f"{key}.html"), "w") as f:
        f.write(preview_html(menu_root(h)).replace("r.style.height='auto';", ""))


if __name__ == "__main__":
    heights = json.loads(sys.argv[1]) if len(sys.argv) > 1 else {}
    topbar = sys.argv[2] if len(sys.argv) > 2 else "escura"
    write_set(heights)
    FULL = True
    for variant in ("escura", "marinho", "branca"):
        TOPBAR = variant
        h = heights.get(f"Topo-{variant}", 1308)
        with open(os.path.join(PROJECT, f"Topo-{variant}.dc.html"), "w") as f:
            f.write(dc_html(f"Tela de jogo · barra de cima {variant}", BOARDS["Main"][1](h), h))
        with open(os.path.join(RENDER, f"Topo-{variant}.html"), "w") as f:
            f.write(preview_html(BOARDS["Main"][1](h)))
    TOPBAR = topbar
    write_set(heights, prefix="Cheia-", suffix=" · tela cheia")
    print("ok")
