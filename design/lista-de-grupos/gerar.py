#!/usr/bin/env python3
"""Gera os artboards (.dc.html) do canvas da lista de grupos (claude.ai/artifact/21tQwX3kE25DkxY2vXCksF):
hoje (prints do Roborazzi), proposta A (cartão que responde), B (lista como a do WhatsApp), C (capa do grupo) e
o primeiro acesso, além das prévias em HTML puro que o Brave mede.

Uso: python3 gerar.py ['{"A-atleta": 1000, ...}']   (alturas medidas; sem elas, 844)

Tokens e medidas copiados de mobile/core/design-system (SaqzColorTokens, SaqzTypography, SaqzMetrics,
SaqzHeroCard, SaqzStatusChip) e da lista de hoje (GroupListSections).
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
BORDER = "#D8DDE8"
ICE = "#F4F8FB"
CANVAS = "#F5F5F7"
WHITE = "#FFFFFF"
SUCCESS = "#17B26A"
SUCCESS_FG = "#0E7A4B"
WARN = "#F5A623"
WARN_FG = "#8F5600"
W82 = "rgba(255,255,255,0.82)"
W18 = "rgba(255,255,255,0.18)"
W14 = "rgba(255,255,255,0.14)"
LIME18 = "rgba(199,243,0,0.18)"
BRAND08 = "rgba(6,56,223,0.08)"
SUCCESS12 = "rgba(23,178,106,0.12)"
WARN14 = "rgba(245,166,35,0.14)"

UI = "font-family: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
DISPLAY = "font-family: Anybody, 'Arial Black', Inter, sans-serif; font-stretch: 110%; font-weight: 800"
FONTS = ("https://fonts.googleapis.com/css2?family=Anybody:wdth,wght@110,800"
         "&family=Inter:wght@400;500;600;700;800&display=swap")

BALL_PATHS = (
    '<path d="M 597,108 C 493,207 449,406 583,580 C 533,663 469,727 397,767 C 263,638 211,410 279,252 C 294,230 313,211 333,196 C 416,145 503,118 597,108 Z"></path>'
    '<path d="M 649,108 C 854,108 1037,248 1113,441 C 940,284 741,222 548,265 C 567,196 603,144 649,108 Z"></path>'
    '<path d="M 542,309 C 764,255 1001,350 1138,531 C 1161,607 1155,701 1125,772 C 1009,584 802,480 590,518 C 557,457 539,382 542,309 Z"></path>'
    '<path d="M 634,552 C 733,535 842,561 928,610 C 861,854 601,1047 317,1048 C 259,1003 211,945 176,878 C 388,858 553,744 634,552 Z"></path>'
    '<path d="M 962,632 C 1032,690 1083,757 1107,826 C 1009,1040 826,1146 627,1146 C 532,1146 443,1122 365,1078 C 674,1050 897,852 962,632 Z"></path>'
    '<path d="M 277,245 C 111,400 52,642 157,844 C 228,834 296,815 360,784 C 232,649 206,445 277,245 Z"></path>'
)


def ball(size, right, top):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 1254 1254" fill="#FFFFFF" aria-hidden="true" '
            f'style="position: absolute; right: {right}px; top: {top}px; opacity: 0.2;">{BALL_PATHS}</svg>')


ICONS = {
    "home": '<path d="M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8"></path><path d="M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path>',
    "users": '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><path d="M22 21v-2a4 4 0 0 0-3-3.87"></path><path d="M16 3.13a4 4 0 0 1 0 7.75"></path>',
    "user": '<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle>',
    "plus": '<path d="M5 12h14"></path><path d="M12 5v14"></path>',
    "chevron": '<path d="m9 18 6-6-6-6"></path>',
    "calendar": '<path d="M8 2v4"></path><path d="M16 2v4"></path><rect width="18" height="18" x="3" y="4" rx="2"></rect><path d="M3 10h18"></path>',
    "clock": '<circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline>',
    "link": '<path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path>',
    "user_plus": '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><line x1="19" x2="19" y1="8" y2="14"></line><line x1="22" x2="16" y1="11" y2="11"></line>',
    "megaphone": '<path d="m3 11 18-5v12L3 14v-3z"></path><path d="M11.6 16.8a3 3 0 1 1-5.8-1.6"></path>',
    "clipboard": '<rect width="8" height="4" x="8" y="2" rx="1" ry="1"></rect><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"></path><path d="m9 14 2 2 4-4"></path>',
    "wallet": '<path d="M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1"></path><path d="M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4"></path>',
    "pin": '<path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"></path><circle cx="12" cy="10" r="3"></circle>',
}


def icon(name, size=20, color="currentColor"):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" stroke="{color}" '
            f'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" '
            f'style="flex: none;">{ICONS[name]}</svg>')


def initials(name):
    words = name.split()
    return (words[0][0] + words[-1][0]).upper()


# ---------------------------------------------------------------- moldura
def bottom_nav():
    cells = []
    for name, label, active in (("home", "Início", False), ("users", "Grupos", True), ("user", "Perfil", False)):
        color = BLUE if active else MUTED
        bar = LIME if active else "transparent"
        weight = 600 if active else 500
        cells.append(
            f'<button style="display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 10px 0 8px; '
            f'border: 0; background: transparent; font-family: inherit; color: {color};">{icon(name, 24)}'
            f'<span style="font-size: 12px; line-height: 16px; font-weight: {weight};">{label}</span>'
            f'<span style="width: 18px; height: 4px; border-radius: 2px; background: {bar};"></span></button>'
        )
    return (f'<nav style="margin-top: auto; flex: none; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); '
            f'background: {WHITE}; border-top: 1px solid {BORDER};">{"".join(cells)}</nav>')


# O que vem depois deste marcador cobre a tela inteira, barra de baixo incluída (folha com fundo escurecido).
OVERLAY = "<!--overlay-->"


def page(inner, height):
    inner, _, overlay = inner.partition(OVERLAY)
    return (
        f'<div style="position: relative; width: 390px; height: {height}px; box-sizing: border-box; '
        f'background: {CANVAS}; {UI}; color: {NAVY}; display: flex; flex-direction: column; overflow: hidden;">'
        f'<main style="display: flex; flex-direction: column; padding-bottom: 24px;">{inner}</main>{bottom_nav()}'
        f'{overlay}</div>'
    )


def header(subtitle=None, trailing=""):
    sub = (f'<p style="margin: 0; font-size: 14px; line-height: 20px; color: {MUTED};">{subtitle}</p>'
           if subtitle else "")
    # O botão fica na linha do título e o resumo embaixo, na largura toda: ao lado do botão ele quebrava.
    return (
        f'<header style="display: flex; flex-direction: column; gap: 2px; padding: 14px 16px 10px;">'
        f'<div style="display: flex; align-items: center; gap: 12px; min-height: 44px;">'
        f'<h1 style="flex-grow: 1; min-width: 0; margin: 0; font-size: 26px; line-height: 32px; font-weight: 800; '
        f'letter-spacing: -0.03em; color: {NAVY};">Grupos</h1>{trailing}</div>{sub}</header>'
    )


def create_pill():
    return (f'<button style="flex: none; display: flex; align-items: center; gap: 6px; min-height: 40px; '
            f'box-sizing: border-box; padding: 0 14px 0 10px; border: 1.5px solid {BLUE}; border-radius: 999px; '
            f'background: {WHITE}; color: {BLUE}; font-family: inherit; font-size: 14px; line-height: 20px; '
            f'font-weight: 600; white-space: nowrap;">{icon("plus", 18)}Criar grupo</button>')


def create_round():
    return (f'<button aria-label="Criar grupo" style="width: 44px; height: 44px; flex: none; display: flex; '
            f'align-items: center; justify-content: center; padding: 0; box-sizing: border-box; '
            f'border: 1.5px solid {BLUE}; border-radius: 22px; background: {WHITE}; color: {BLUE};">'
            f'{icon("plus", 20)}</button>')


def stack(items, gap=12, pad="4px 16px 0"):
    return f'<div style="display: flex; flex-direction: column; gap: {gap}px; padding: {pad};">{"".join(items)}</div>'


# ---------------------------------------------------------------- peças
def gavatar(name, size=48, radius=14, on_blue=False):
    bg, fg = (W14, WHITE) if on_blue else (ICE, BLUE)
    border = "" if on_blue else f" border: 1px solid {BORDER};"
    return (f'<span aria-hidden="true" style="width: {size}px; height: {size}px; flex: none; box-sizing: border-box; '
            f'border-radius: {radius}px; background: {bg};{border} display: flex; align-items: center; '
            f'justify-content: center; font-size: 15px; line-height: 15px; font-weight: 600; color: {fg};">'
            f'{initials(name)}</span>')


def chip(text, tone="brand"):
    bg, fg, dot = {
        "brand": (BRAND08, BLUE, None),
        "success": (SUCCESS12, SUCCESS_FG, SUCCESS),
        "warning": (WARN14, WARN_FG, WARN),
        "neutral": ("rgba(102,112,133,0.10)", MUTED, None),
        "on_blue": (W14, WHITE, None),
    }[tone]
    dot_html = (f'<span style="width: 6px; height: 6px; flex: none; border-radius: 3px; background: {dot};"></span>'
                if dot else "")
    return (f'<span style="flex: none; display: inline-flex; align-items: center; gap: 6px; font-size: 12px; '
            f'line-height: 16px; font-weight: 600; padding: 4px 10px; border-radius: 999px; background: {bg}; '
            f'color: {fg}; white-space: nowrap;">{dot_html}{text}</span>')


def status_chip(status):
    return {
        "going": chip("Você vai", "success"),
        "wait": chip("Na espera · 2º", "warning"),
        "pending": "",
        None: "",
    }[status]


def slots(going, cap):
    cells = "".join(
        f'<span style="height: 6px; border-radius: 3px; background: {BLUE};"></span>' if i < going else
        f'<span style="height: 6px; box-sizing: border-box; border-radius: 3px; background: {ICE}; '
        f'border: 1px solid {BORDER};"></span>'
        for i in range(cap)
    )
    return (f'<div style="display: flex; align-items: center; gap: 10px;">'
            f'<div aria-hidden="true" style="flex-grow: 1; display: grid; grid-template-columns: repeat({cap}, '
            f'minmax(0, 1fr)); gap: 3px;">{cells}</div>'
            f'<span style="flex: none; font-size: 13px; line-height: 16px; font-weight: 600; color: {NAVY};">'
            f'{going} de {cap} vão</span></div>')


def deadline_line(text):
    return (f'<p style="margin: 0; display: flex; align-items: center; gap: 8px; font-size: 13px; line-height: 18px; '
            f'color: {MUTED};">{icon("clock", 16, MUTED)}<span>{text}</span></p>')


def answer_buttons():
    return (
        f'<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px;">'
        f'<button style="height: 44px; border: 0; border-radius: 999px; background: {BLUE}; color: {WHITE}; '
        f'font-family: inherit; font-size: 15px; line-height: 20px; font-weight: 600;">Vou</button>'
        f'<button style="height: 44px; box-sizing: border-box; border: 1.5px solid {BLUE}; border-radius: 999px; '
        f'background: {WHITE}; color: {BLUE}; font-family: inherit; font-size: 15px; line-height: 20px; '
        f'font-weight: 600;">Não vou</button></div>'
    )


def day_tile(weekday, day, size=52):
    """O DateTile de "Próximos jogos" (UpcomingGameRow) com o dia da semana no lugar do mês, em cima, como folha de
    calendário: o dono pediu o calendário só com o dia e o horário em linha própria (28/10)."""
    return (f'<span aria-hidden="true" style="width: {size}px; height: {size}px; flex: none; box-sizing: border-box; '
            f'border-radius: 10px; background: {ICE}; border: 1px solid {BORDER}; display: flex; flex-direction: column; '
            f'align-items: center; justify-content: center; gap: 1px;">'
            f'<span style="font-size: 11px; line-height: 14px; font-weight: 700; letter-spacing: 0.08em; color: {BLUE};">'
            f'{weekday}</span>'
            f'<span style="font-size: 20px; line-height: 22px; font-weight: 800; color: {NAVY};">{day}</span></span>')


def game_info(group, status=None):
    """Selo do dia à esquerda; à direita, o horário (com o estado de quem olha) e, embaixo, as vagas."""
    hours = (f'<div style="display: flex; align-items: center; gap: 8px; min-height: 24px;">{icon("clock", 16, MUTED)}'
             f'<span style="flex-grow: 1; min-width: 0; font-size: 15px; line-height: 20px; font-weight: 600; '
             f'color: {NAVY}; white-space: nowrap;">{group["hours"]}</span>{status_chip(status)}</div>')
    return (f'<div style="display: flex; align-items: center; gap: 12px;">{day_tile(group["wd"], group["d"])}'
            f'<div style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 8px;">'
            f'{hours}{slots(group["going"], group["cap"])}</div></div>')


def no_game():
    return (f'<p style="margin: 0; font-size: 14px; line-height: 20px; color: {MUTED};">'
            f'Nenhum jogo marcado por enquanto.</p>')


def waiting_block(items):
    """items: (ícone, texto, ação) — ação None vira chevron (a linha toda abre), texto vira botão próprio."""
    rows = []
    for index, (icon_name, text, action) in enumerate(items):
        top = "" if index == 0 else f" border-top: 1px solid {BORDER};"
        label = (f'<span style="flex-grow: 1; min-width: 0; font-size: 14px; line-height: 20px; font-weight: 600; '
                 f'color: {NAVY};">{text}</span>')
        if action is None:
            rows.append(
                f'<button style="display: flex; align-items: center; gap: 10px; min-height: 44px; width: 100%; '
                f'box-sizing: border-box; padding: 0 8px 0 12px; border: 0;{top} background: transparent; '
                f'text-align: left; font-family: inherit;">{icon(icon_name, 18, BLUE)}{label}'
                f'{icon("chevron", 18, MUTED)}</button>')
        else:
            rows.append(
                f'<div style="display: flex; align-items: center; gap: 10px; min-height: 44px; box-sizing: border-box; '
                f'padding: 0 4px 0 12px;{top}">{icon(icon_name, 18, BLUE)}{label}'
                f'<button style="flex: none; min-height: 40px; padding: 0 8px; border: 0; border-radius: 999px; '
                f'background: transparent; color: {BLUE}; font-family: inherit; font-size: 14px; line-height: 20px; '
                f'font-weight: 600;">{action}</button></div>')
    return (f'<div style="display: flex; flex-direction: column; border-radius: 10px; background: {ICE};">'
            f'<span style="padding: 10px 12px 2px; font-size: 12px; line-height: 16px; font-weight: 600; '
            f'color: {MUTED};">Esperando você</span>{"".join(rows)}</div>')


# ---------------------------------------------------------------- dados de cena (hoje = qua, 28/10/2026)
CERET = dict(name="Vôlei do CERET", place="Quadra · Tatuapé", wd="QUI", d="29", day="Qui, 29/10",
             hours="19h30 às 21h30", time="19h30", going=9, cap=12)
IBIRA = dict(name="Areia do Ibira", place="Areia · Ibirapuera", wd="SÁB", d="31", day="Sáb, 31/10",
             hours="09h00 às 11h00", time="09h00", going=6, cap=8)
VILA = dict(name="Futevôlei da Vila", place="Futevôlei · Vila Mariana")
TERCA = dict(name="Vôlei de terça", place="Quadra · Mooca", wd="TER", d="03", day="Ter, 03/11",
             hours="20h00 às 22h00", time="20h00", going=3, cap=12)
DEADLINE = "Prioridade dos mensalistas até hoje, 22h"
CERET_WAITING = [("user_plus", "2 pedidos para entrar", None), ("megaphone", "4 pessoas sem resposta", "Avisar")]
TERCA_WAITING = [("clipboard", "Acertar o jogo de ter, 27/10", None), ("wallet", "5 mensalidades a receber", None)]


# ---------------------------------------------------------------- A · cartão que responde
def card_a(group, meta, status=None, admin=False, waiting=(), deadline=None):
    admin_chip = chip("Organizador") if admin else ""
    top = (
        f'<button style="display: flex; align-items: center; gap: 12px; width: 100%; padding: 0; border: 0; '
        f'background: transparent; text-align: left; font-family: inherit; color: {NAVY};">'
        f'{gavatar(group["name"])}'
        f'<span style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px;">'
        f'<span style="display: flex; align-items: center; flex-wrap: wrap; gap: 4px 8px;">'
        f'<span style="font-size: 16px; line-height: 22px; font-weight: 700;">{group["name"]}</span>{admin_chip}</span>'
        f'<span style="font-size: 13px; line-height: 18px; color: {MUTED};">{meta}</span></span>'
        f'{icon("chevron", 20, MUTED)}</button>'
    )
    if "hours" in group:
        parts = [game_info(group, status)]
        if status == "pending":
            if deadline:
                parts.append(deadline_line(deadline))
            parts.append(answer_buttons())
        game = f'<div style="display: flex; flex-direction: column; gap: 10px;">{"".join(parts)}</div>'
    else:
        game = no_game()
    extra = waiting_block(waiting) if waiting else ""
    return (f'<article style="display: flex; flex-direction: column; gap: 14px; padding: 16px; background: {WHITE}; '
            f'border: 1px solid {BORDER}; border-radius: 12px;">{top}'
            f'<div style="height: 1px; background: {BORDER};"></div>{game}{extra}</article>')


def board_a_athlete():
    return header("3 grupos · 1 esperando sua resposta", create_pill()) + stack([
        card_a(CERET, f'{CERET["place"]} · Mensalista', status="pending", deadline=DEADLINE),
        card_a(IBIRA, f'{IBIRA["place"]} · Avulso', status="going"),
        card_a(VILA, f'{VILA["place"]} · Avulso'),
    ])


def board_a_admin():
    return header("3 grupos · 4 coisas esperando você", create_pill()) + stack([
        card_a(CERET, CERET["place"], status="going", admin=True, waiting=CERET_WAITING),
        card_a(TERCA, TERCA["place"], admin=True, waiting=TERCA_WAITING),
        card_a(IBIRA, f'{IBIRA["place"]} · Avulso', status="going"),
    ])


# ---------------------------------------------------------------- B · lista como a do WhatsApp
def badge(count):
    return (f'<span style="flex: none; min-width: 22px; height: 22px; box-sizing: border-box; padding: 0 6px; '
            f'border-radius: 11px; background: {BLUE}; color: {WHITE}; font-size: 12px; line-height: 22px; '
            f'font-weight: 700; text-align: center;">{count}</span>')


def b_row(group, status=None, count=None, pending_items="", first=False):
    """Linha do grupo: avatar, nome (com o contador do organizador), horário e estado, vagas e, à direita, o selo do
    dia no lugar da hora do WhatsApp."""
    top = "" if first else f" border-top: 1px solid {BORDER};"
    count_html = badge(count) if count else ""
    lines = [
        f'<span style="display: flex; align-items: center; gap: 8px; min-height: 22px;">'
        f'<span style="flex-grow: 1; min-width: 0; font-size: 16px; line-height: 22px; font-weight: 600; '
        f'white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">{group["name"]}</span>{count_html}</span>'
    ]
    if "hours" in group:
        state = {
            "pending": f'<span>·</span><span style="color: {BLUE}; font-weight: 600;">Falta sua resposta</span>',
            "going": f'<span>·</span>{line_dot(SUCCESS)}<span style="color: {SUCCESS_FG}; font-weight: 600;">Você vai</span>',
        }.get(status, "")
        lines.append(
            f'<span style="display: flex; align-items: center; gap: 6px; font-size: 14px; line-height: 20px; '
            f'color: {MUTED}; white-space: nowrap; overflow: hidden;">{icon("clock", 14, MUTED)}'
            f'<span style="color: {NAVY}; font-weight: 600;">{group["time"]}</span>{state}</span>')
        lines.append(slots(group["going"], group["cap"]))
        tile = day_tile(group["wd"], group["d"], 48)
    else:
        lines.append(f'<span style="font-size: 14px; line-height: 20px; color: {MUTED};">Nenhum jogo marcado</span>')
        tile = ""
    if pending_items:
        lines.append(f'<span style="font-size: 13px; line-height: 18px; font-weight: 600; color: {NAVY}; '
                     f'white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">{pending_items}</span>')
    return (
        f'<button style="display: flex; align-items: stretch; width: 100%; padding: 0 0 0 16px; border: 0; '
        f'background: {WHITE}; text-align: left; font-family: inherit; color: {NAVY};">'
        f'<span style="display: flex; align-items: flex-start; flex: none; padding-top: 14px;">'
        f'{gavatar(group["name"], 52)}</span>'
        f'<span style="flex-grow: 1; min-width: 0; display: flex; align-items: flex-start; gap: 12px; margin-left: 12px; '
        f'padding: 14px 16px 14px 0; min-height: 80px; box-sizing: border-box;{top}">'
        f'<span style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px;">'
        f'{"".join(lines)}</span>{tile}</span></button>'
    )


def line_dot(color):
    return f'<span style="width: 8px; height: 8px; flex: none; border-radius: 4px; background: {color};"></span>'


def b_list(rows):
    return (f'<div style="display: flex; flex-direction: column; background: {WHITE}; border-top: 1px solid {BORDER}; '
            f'border-bottom: 1px solid {BORDER};">{"".join(rows)}</div>')


def b_section(title):
    return (f'<h2 style="margin: 0; padding: 18px 16px 8px; font-size: 15px; line-height: 20px; font-weight: 700; '
            f'color: {NAVY};">{title}</h2>')


def dashed_row(icon_name, label, sub, first=False):
    top = "" if first else f" border-top: 1px solid {BORDER};"
    return (
        f'<button style="display: flex; align-items: stretch; width: 100%; padding: 0 0 0 16px; border: 0; '
        f'background: {WHITE}; text-align: left; font-family: inherit;">'
        f'<span style="display: flex; align-items: center; flex: none;"><span style="width: 52px; height: 52px; '
        f'box-sizing: border-box; border-radius: 14px; border: 1.5px dashed {BLUE}; display: flex; align-items: center; '
        f'justify-content: center; color: {BLUE};">{icon(icon_name, 22)}</span></span>'
        f'<span style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; '
        f'gap: 2px; margin-left: 12px; padding: 12px 16px 12px 0; min-height: 76px; box-sizing: border-box;{top}">'
        f'<span style="font-size: 16px; line-height: 22px; font-weight: 600; color: {BLUE};">{label}</span>'
        f'<span style="font-size: 14px; line-height: 20px; color: {MUTED};">{sub}</span></span></button>'
    )


SHORTCUTS = [
    dashed_row("plus", "Criar um grupo", "Para quem organiza os jogos", first=True),
    dashed_row("link", "Entrar com convite", "Cole o link que mandaram no WhatsApp"),
]


def board_b_athlete():
    rows = [
        b_row(CERET, status="pending", first=True),
        b_row(IBIRA, status="going"),
        b_row(VILA),
    ]
    return (header("3 grupos · 1 esperando sua resposta") + b_list(rows) + '<div style="height: 24px;"></div>'
            + b_list(SHORTCUTS))


def board_b_admin():
    organized = [
        b_row(CERET, status="going", count=2, pending_items="2 pedidos para entrar · 4 sem resposta", first=True),
        b_row(TERCA, count=2, pending_items="Acertar o jogo de ter, 27/10 · 5 mensalidades"),
    ]
    playing = [b_row(IBIRA, status="going", first=True)]
    return (header("3 grupos · 4 coisas esperando você") + b_section("Você organiza") + b_list(organized)
            + b_section("Você joga") + b_list(playing) + '<div style="height: 24px;"></div>' + b_list(SHORTCUTS))


# ---------------------------------------------------------------- C · capa do grupo
def cover(group, admin=False):
    role = chip("Organizador", "on_blue") if admin else ""
    return (
        f'<button style="position: relative; overflow: hidden; display: flex; align-items: center; gap: 12px; '
        f'width: 100%; box-sizing: border-box; padding: 16px; border: 0; border-radius: 16px 16px 0 0; '
        f'background: {BLUE}; text-align: left; font-family: inherit; color: {WHITE};">'
        f'{ball(150, -34, -44)}'
        f'<span style="position: relative; display: flex;">{gavatar(group["name"], 44, 12, on_blue=True)}</span>'
        f'<span style="position: relative; flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px;">'
        f'<span style="display: flex; align-items: center; justify-content: space-between; gap: 8px; min-height: 24px;">'
        f'<span style="display: flex; align-items: center; gap: 8px; min-width: 0;">'
        f'<span style="width: 14px; height: 14px; flex: none; border-radius: 7px; background: {LIME18}; display: flex; '
        f'align-items: center; justify-content: center;"><span style="width: 7px; height: 7px; border-radius: 4px; '
        f'background: {LIME};"></span></span>'
        f'<span style="font-size: 11px; line-height: 16px; font-weight: 600; letter-spacing: 0.08em; '
        f'text-transform: uppercase; color: {W82}; white-space: nowrap;">{group["place"]}</span></span>{role}</span>'
        f'<span style="{DISPLAY}; font-size: 24px; line-height: 26px; letter-spacing: -0.03em; color: {WHITE};">'
        f'{group["name"]}</span></span></button>'
    )


def card_c(group, status=None, admin=False, waiting=(), deadline=None):
    if "hours" in group:
        parts = [game_info(group, status)]
        if status == "pending":
            if deadline:
                parts.append(deadline_line(deadline))
            parts.append(answer_buttons())
    else:
        parts = [no_game()]
    if waiting:
        parts.append(waiting_block(waiting))
    return (f'<article style="display: flex; flex-direction: column;">{cover(group, admin)}'
            f'<div style="display: flex; flex-direction: column; gap: 12px; padding: 14px 16px 16px; background: {WHITE}; '
            f'border: 1px solid {BORDER}; border-top: 0; border-radius: 0 0 16px 16px;">{"".join(parts)}</div></article>')


def board_c_athlete():
    return header("3 grupos · 1 esperando sua resposta", create_round()) + stack([
        card_c(CERET, status="pending", deadline=DEADLINE),
        card_c(IBIRA, status="going"),
        card_c(VILA),
    ], gap=16)


def board_c_admin():
    return header("3 grupos · 4 coisas esperando você", create_round()) + stack([
        card_c(CERET, status="going", admin=True, waiting=CERET_WAITING),
        card_c(TERCA, admin=True, waiting=TERCA_WAITING),
        card_c(IBIRA, status="going"),
    ], gap=16)


# ---------------------------------------------------------------- D · bloco do jogo
# Pedido do dono em 28/10: a foto ao lado do selo da data dava dois destaques brigando. Aqui cada grupo tem um destaque
# só, o bloco do jogo (dia da semana, dia e, embaixo, o horário); o resto do cartão é o grupo. Só entra o que a tela de
# hoje já mostra: nome, modalidade e cidade, papel, próximo jogo, vagas e a sua presença.
PLACEHOLDER = "#98A2B3"
# 0,78 de azul: na parede branca da foto o branco das letras ainda passa de 4,5:1.
BLUE_VEIL = "rgba(6,56,223,0.78)"
TABULAR = "font-variant-numeric: tabular-nums;"
D_BLOCK_W = 84
PHOTO_BLOB = "/_blob/69e000aec6ed1fdbf1958756a9bd8801"
PHOTO_LOCAL = "file://" + os.path.normpath(os.path.join(OUT, "..", "..", "landing-page", "assets", "img",
                                                         "quadra-900.jpg"))
FIRMA = dict(name="Vôlei da firma", place="Quadra · Pinheiros", wd="SEX", d="30", hours="18h00 às 20h00",
             time="18h00", going=13, cap=12)


def ball_low(size, right, bottom, opacity=0.16):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 1254 1254" fill="#FFFFFF" aria-hidden="true" '
            f'style="position: absolute; right: {right}px; bottom: {bottom}px; opacity: {opacity};">{BALL_PATHS}</svg>')


def d_block(group, live=False, photo=False):
    if "hours" not in group:
        return (
            f'<div style="flex: none; width: {D_BLOCK_W}px; box-sizing: border-box; display: flex; flex-direction: column; '
            f'align-items: center; justify-content: center; gap: 6px; padding: 14px 0; background: {ICE}; '
            f'border-right: 1px solid {BORDER};">'
            f'<svg width="30" height="30" viewBox="0 0 1254 1254" fill="{PLACEHOLDER}" aria-hidden="true">{BALL_PATHS}'
            f'</svg>'
            f'<span style="font-size: 11px; line-height: 14px; font-weight: 700; letter-spacing: 0.1em; color: {MUTED}; '
            f'text-align: center;">SEM<br>JOGO</span></div>')
    if photo:
        # A foto do grupo entra atrás do bloco, coberta de azul: identidade e data viram uma peça só.
        backdrop = (f'<img src="{PHOTO_BLOB}" alt="Foto do grupo {group["name"]}" style="position: absolute; top: 0; '
                    f'left: 0; width: 100%; height: 100%; object-fit: cover; object-position: 46% 30%;">'
                    f'<span style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; '
                    f'background: {BLUE_VEIL};"></span>')
    else:
        backdrop = ball_low(120, -46, -44)
    if live:
        label = (f'<span style="position: relative; display: flex; align-items: center; gap: 5px; font-size: 11px; '
                 f'line-height: 16px; font-weight: 700; letter-spacing: 0.06em; color: {LIME};">'
                 f'<span style="width: 6px; height: 6px; border-radius: 3px; background: {LIME};"></span>EM JOGO</span>')
    else:
        # Sobre a foto o dia da semana vai em branco cheio: o branco a 82% não passava de 4,5:1 nas partes claras.
        label = (f'<span style="position: relative; font-size: 12px; line-height: 16px; font-weight: 700; '
                 f'letter-spacing: 0.12em; color: {WHITE if photo else W82};">{group["wd"]}</span>')
    return (
        f'<div style="position: relative; overflow: hidden; flex: none; width: {D_BLOCK_W}px; box-sizing: border-box; '
        f'display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 14px 0; '
        f'background: {BLUE}; color: {WHITE};">{backdrop}{label}'
        f'<span style="position: relative; margin-top: 2px; {DISPLAY}; font-size: 38px; line-height: 38px; '
        f'letter-spacing: -0.03em; {TABULAR}">{group["d"]}</span>'
        f'<span style="position: relative; width: 16px; height: 3px; margin: 8px 0; border-radius: 2px; '
        f'background: {LIME};"></span>'
        f'<span style="position: relative; font-size: 15px; line-height: 20px; font-weight: 700; {TABULAR}">'
        f'{group["time"]}</span></div>'
    )


def d_vagas(going, cap):
    bar = "".join(
        f'<span style="height: 6px; border-radius: 3px; background: {BLUE};"></span>' if i < going else
        f'<span style="height: 6px; box-sizing: border-box; border-radius: 3px; background: {ICE}; '
        f'border: 1px solid {BORDER};"></span>'
        for i in range(cap)
    )
    bar = (f'<div aria-hidden="true" style="flex-grow: 1; display: grid; grid-template-columns: repeat({cap}, '
           f'minmax(0, 1fr)); gap: 3px;">{bar}</div>')
    label_style = f'font-size: 13px; line-height: 16px; font-weight: 600; color: {NAVY}; {TABULAR}'
    if going > cap:
        # Acima da lotação a tela de hoje diz "13 confirmados para 12 vagas": a frase desce para baixo da barra cheia.
        return (f'<div style="display: flex; flex-direction: column; gap: 6px;"><div style="display: flex;">{bar}</div>'
                f'<span style="{label_style}">{going} confirmados para {cap} vagas</span></div>')
    return (f'<div style="display: flex; align-items: center; gap: 10px;">{bar}'
            f'<span style="flex: none; {label_style}">{going} de {cap} vão</span></div>')


def d_status(status):
    if status == "pending":
        # Anel vazio = ainda sem resposta; ponto cheio = respondeu. Um "Responder" ao lado do texto quebrava a frase em
        # duas linhas, então a resposta vem direto, embaixo.
        text = (f'<span style="display: flex; align-items: center; gap: 8px; font-size: 14px; line-height: 20px; '
                f'font-weight: 600; color: {BLUE};"><span style="width: 8px; height: 8px; flex: none; '
                f'box-sizing: border-box; border-radius: 4px; border: 2px solid {BLUE};"></span>Falta sua resposta</span>')
        return (f'<div style="display: flex; flex-direction: column; gap: 10px;">{text}'
                f'<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px;">'
                f'<button style="height: 40px; border: 0; border-radius: 999px; background: {BLUE}; color: {WHITE}; '
                f'font-family: inherit; font-size: 15px; line-height: 20px; font-weight: 600;">Vou</button>'
                f'<button style="height: 40px; box-sizing: border-box; border: 1.5px solid {BLUE}; border-radius: 999px; '
                f'background: {WHITE}; color: {BLUE}; font-family: inherit; font-size: 15px; line-height: 20px; '
                f'font-weight: 600;">Não vou</button></div></div>')
    dot, color, label = {
        "going": (SUCCESS, SUCCESS_FG, "Você vai"),
        "wait": (WARN, WARN_FG, "Na espera"),
        "out": (PLACEHOLDER, MUTED, "Não vai"),
    }[status]
    return (f'<span style="display: flex; align-items: center; gap: 8px; min-height: 24px; font-size: 14px; '
            f'line-height: 20px; font-weight: 600; color: {color};">{line_dot(dot)}{label}</span>')


def d_card(group, status=None, admin=False, live=False, photo=False):
    # "Admin", como o WhatsApp marca quem administra o grupo: "Administrador" quebrava a linha em 360 px.
    role = f' · <span style="color: {BLUE}; font-weight: 600;">Admin</span>' if admin else ""
    head = (
        f'<button style="display: flex; align-items: flex-start; gap: 8px; width: 100%; padding: 0; border: 0; '
        f'background: transparent; text-align: left; font-family: inherit; color: {NAVY};">'
        f'<span style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px;">'
        f'<span style="font-size: 17px; line-height: 22px; font-weight: 700;">{group["name"]}</span>'
        f'<span style="font-size: 13px; line-height: 18px; color: {MUTED};">{group["place"]}{role}</span></span>'
        f'<span style="display: flex; padding-top: 1px;">{icon("chevron", 20, MUTED)}</span></button>'
    )
    if "hours" in group:
        body = d_vagas(group["going"], group["cap"]) + d_status(status)
    else:
        body = (f'<p style="margin: 0; font-size: 14px; line-height: 20px; color: {MUTED}; text-wrap: balance;">'
                f'Nenhum jogo marcado por enquanto.</p>')
    return (
        f'<article style="display: flex; align-items: stretch; overflow: hidden; background: {WHITE}; '
        f'border: 1px solid {BORDER}; border-radius: 12px;">{d_block(group, live, photo)}'
        f'<div style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 12px; '
        f'padding: 14px 14px 14px 16px;">{head}{body}</div></article>'
    )


def board_d_list(photo=False):
    return header("3 grupos · 1 esperando sua resposta", create_round()) + stack([
        d_card(CERET, status="pending", admin=True, photo=photo),
        d_card(IBIRA, status="going"),
        d_card(VILA),
    ])


def board_d_states():
    live = dict(CERET, wd="QUA", d="28", going=12)
    return header("4 grupos", create_round()) + stack([
        d_card(live, status="going", admin=True, live=True),
        d_card(FIRMA, status="going"),
        d_card(dict(IBIRA, going=8), status="wait"),
        d_card(TERCA, status="out"),
    ])


# ---------------------------------------------------------------- E · foto em destaque, duas linhas
# Pedido do dono em 28/10, depois da D: tela de grupos tem a foto como destaque; data e hora do próximo jogo vão para
# outra parte do item, sem cartões, em duas linhas.
PHOTO2_BLOB = "/_blob/214a147d3b4337e701660e54f1ab274c"
PHOTO2_LOCAL = "file://" + os.path.normpath(os.path.join(OUT, "..", "..", "landing-page", "assets", "img",
                                                          "hero-1200.jpg"))
E_AVATAR = 56


def e_avatar(group, photo=None, pos="50% 50%"):
    box = (f'width: {E_AVATAR}px; height: {E_AVATAR}px; flex: none; box-sizing: border-box; border-radius: 16px; '
           f'overflow: hidden;')
    if photo:
        return (f'<span style="{box} display: flex; background: {ICE};"><img src="{photo}" alt="Foto do grupo '
                f'{group["name"]}" style="width: 100%; height: 100%; object-fit: cover; object-position: {pos};"></span>')
    # Sem foto o lugar dela não fica vazio: escudo azul com as iniciais na Anybody e a bola ao fundo.
    return (f'<span aria-hidden="true" style="{box} position: relative; display: flex; align-items: center; '
            f'justify-content: center; background: {BLUE};">{ball(62, -22, 18)}'
            f'<span style="position: relative; {DISPLAY}; font-size: 20px; line-height: 20px; letter-spacing: -0.02em; '
            f'color: {WHITE};">{initials(group["name"])}</span></span>')


def e_status(status):
    if status == "pending":
        # Botão de verdade: abre o jogo para responder (o "Confirme" de hoje parecia botão e só abria o grupo).
        return (f'<button style="flex: none; min-height: 30px; box-sizing: border-box; padding: 0 12px; '
                f'border: 1.5px solid {BLUE}; border-radius: 999px; background: {WHITE}; color: {BLUE}; '
                f'font-family: inherit; font-size: 13px; line-height: 18px; font-weight: 600; white-space: nowrap;">'
                f'Responder</button>')
    return {
        "going": chip("Você vai", "success"),
        "wait": chip("Na espera", "warning"),
        "out": chip("Não vai", "neutral"),
        None: "",
    }[status]


def e_row(group, status=None, photo=None, pos="50% 50%", first=False, second=None):
    top = "" if first else f" border-top: 1px solid {BORDER};"
    if second is None:
        second = (f'{group["day"]} · {group["time"]} · {group["going"]} de {group["cap"]} vão'
                  if "hours" in group else "Nenhum jogo marcado")
    return (
        f'<div style="display: flex; align-items: center; padding-left: 16px; background: {WHITE};">'
        f'{e_avatar(group, photo, pos)}'
        f'<div style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; '
        f'gap: 2px; margin-left: 12px; padding: 12px 16px 12px 0; min-height: 80px; box-sizing: border-box;{top}">'
        f'<div style="display: flex; align-items: center; gap: 8px; min-height: 30px;">'
        f'<button style="flex-grow: 1; min-width: 0; padding: 0; border: 0; background: transparent; text-align: left; '
        f'font-family: inherit; font-size: 17px; line-height: 22px; font-weight: 600; color: {NAVY}; white-space: nowrap; '
        f'overflow: hidden; text-overflow: ellipsis;">{group["name"]}</button>{e_status(status)}</div>'
        f'<span style="font-size: 14px; line-height: 20px; color: {MUTED}; white-space: nowrap; overflow: hidden; '
        f'text-overflow: ellipsis; {TABULAR}">{second}</span></div></div>'
    )


def board_e_list():
    return (header("3 grupos · 1 esperando sua resposta", create_round())
            + b_section("Você administra") + b_list([e_row(CERET, "pending", PHOTO_BLOB, "48% 30%", first=True)])
            + b_section("Você joga") + b_list([e_row(IBIRA, "going", first=True), e_row(VILA)]))


def board_e_states():
    firma = dict(FIRMA, day="Sex, 30/10")  # FIRMA nasceu na D, que não usava a data por extenso
    live = (f'<span style="color: {BLUE}; font-weight: 600;">Em andamento</span> · {CERET["time"]} · 12 de 12 vão')
    return (header("4 grupos", create_round())
            + b_section("Você administra")
            + b_list([e_row(dict(CERET, going=12), "going", PHOTO_BLOB, "48% 30%", first=True, second=live)])
            + b_section("Você joga")
            + b_list([
                # Acima da lotação a tela de hoje nunca escreve "13 de 12": diz quantos confirmados para quantas vagas.
                e_row(firma, "going", PHOTO2_BLOB, "57% 18%", first=True,
                      second=f'{firma["day"]} · {firma["time"]} · 13 para 12 vagas'),
                e_row(dict(IBIRA, going=8), "wait"),
                e_row(TERCA, "out"),
            ]))


# ---------------------------------------------------------------- F · a D em lista, com a foto no lugar do azul
# Pedido do dono em 28/10, depois da E: voltar à D, mas sem cartão (só lista), sem o azul por cima da foto, a foto mais
# larga e a data e o horário em duas linhas separadas.
F_MEDIA_W = 112


def f_media(group, photo=None, pos="50% 50%"):
    # Sangrada: encosta no topo, na base e na borda esquerda da linha, sem canto arredondado (pedido do dono em 28/10).
    base = (f'position: relative; flex: none; width: {F_MEDIA_W}px; min-height: 96px; box-sizing: border-box; '
            f'overflow: hidden;')
    if photo:
        return (f'<span style="{base} display: flex; background: {ICE};"><img src="{photo}" alt="Foto do grupo '
                f'{group["name"]}" style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; '
                f'object-fit: cover; object-position: {pos};"></span>')
    # Sem foto, o lugar dela fica no azul da D com a bola; as iniciais do grupo entram onde estava a data.
    return (f'<span aria-hidden="true" style="{base} display: flex; align-items: center; justify-content: center; '
            f'background: {BLUE};">{ball_low(124, -42, -38)}'
            f'<span style="position: relative; {DISPLAY}; font-size: 30px; line-height: 30px; letter-spacing: -0.03em; '
            f'color: {WHITE};">{initials(group["name"])}</span></span>')


def f_info(icon_name, text):
    """Uma informação por linha, com ícone, como data e duração no cabeçalho da tela de jogo."""
    return (f'<div style="display: flex; align-items: center; gap: 8px; min-height: 22px;">{icon(icon_name, 16, BLUE)}'
            f'<span style="min-width: 0; font-size: 15px; line-height: 20px; font-weight: 600; color: {NAVY}; '
            f'{TABULAR}">{text}</span></div>')


def f_row(group, status=None, admin=False, photo=None, pos="50% 50%", live=False):
    role = f' · <span style="color: {BLUE}; font-weight: 600;">Admin</span>' if admin else ""
    head = (
        f'<button style="display: flex; align-items: flex-start; gap: 8px; width: 100%; padding: 0; border: 0; '
        f'background: transparent; text-align: left; font-family: inherit; color: {NAVY};">'
        f'<span style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px;">'
        f'<span style="font-size: 17px; line-height: 22px; font-weight: 700;">{group["name"]}</span>'
        f'<span style="font-size: 13px; line-height: 18px; color: {MUTED};">{group["place"]}{role}</span></span>'
        f'<span style="display: flex; padding-top: 1px;">{icon("chevron", 20, MUTED)}</span></button>'
    )
    if "hours" in group:
        now = f' · <span style="color: {BLUE};">Em andamento</span>' if live else ""
        when = (f'<div style="display: flex; flex-direction: column; gap: 4px;">'
                f'{f_info("calendar", group["long"])}{f_info("clock", group["time"] + now)}</div>')
        body = when + d_vagas(group["going"], group["cap"]) + d_status(status)
    else:
        body = (f'<p style="margin: 0; font-size: 14px; line-height: 20px; color: {MUTED}; text-wrap: balance;">'
                f'Nenhum jogo marcado por enquanto.</p>')
    # A linha não tem respiro à esquerda, em cima nem embaixo: a foto ocupa a altura toda; o respiro fica no texto.
    # Cada grupo é uma faixa branca com fio em cima e embaixo; f_list separa uma faixa da outra.
    return (
        f'<div style="display: flex; align-items: stretch; gap: 14px; padding: 0 16px 0 0; background: {WHITE}; '
        f'border-top: 1px solid {BORDER}; border-bottom: 1px solid {BORDER};">'
        f'{f_media(group, photo, pos)}'
        f'<div style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 12px; '
        f'padding: 14px 0;">{head}{body}</div></div>'
    )


def f_list(rows):
    """Faixas de ponta a ponta com 8 px do fundo entre um grupo e outro: com a foto sangrada, só a divisória de 1 px
    deixava as fotos coladas (pedido do dono em 28/10)."""
    return f'<div style="display: flex; flex-direction: column; gap: 8px;">{"".join(rows)}</div>'


def board_f_list():
    return header("3 grupos · 1 esperando sua resposta", create_round()) + f_list([
        f_row(dict(CERET, long="Quinta, 29/10"), "pending", admin=True, photo=PHOTO_BLOB, pos="48% 30%"),
        f_row(dict(IBIRA, long="Sábado, 31/10"), "going"),
        f_row(VILA),
    ])


def board_f_states():
    return header("4 grupos", create_round()) + f_list([
        f_row(dict(CERET, long="Quarta, 28/10", going=12), "going", admin=True, photo=PHOTO_BLOB, pos="48% 30%",
              live=True),
        f_row(dict(FIRMA, long="Sexta, 30/10"), "going", photo=PHOTO2_BLOB, pos="57% 18%"),
        f_row(dict(IBIRA, long="Sábado, 31/10", going=8), "wait"),
        f_row(dict(TERCA, long="Terça, 03/11"), "out"),
    ])


# ---------------------------------------------------------------- G · refino da F
# Pedido do dono em 28/10: refinar só o layout da F (tamanho de ícone e de texto, cor). Três tamanhos (16 o nome, 14 a
# data e o horário, 13 o resto) e dois pesos (400 e 600); espaços na grade de 4; azul só no que pede ação (a sua
# resposta, o Vou) e nas vagas; ícones e Admin saem do azul.
def g_media(group, photo=None, pos="50% 50%", light=True):
    base = f'position: relative; flex: none; width: {F_MEDIA_W}px; min-height: 96px; overflow: hidden;'
    if photo:
        return (f'<span style="{base} display: flex; background: {ICE};"><img src="{photo}" alt="Foto do grupo '
                f'{group["name"]}" style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; '
                f'object-fit: cover; object-position: {pos};"></span>')
    if light:
        # Sem foto, o lugar fica claro: a foto de quem tem vira o destaque e o azul sobra para o que pede ação.
        return (f'<span aria-hidden="true" style="{base} display: flex; align-items: center; justify-content: center; '
                f'background: {ICE}; border-right: 1px solid {BORDER};">'
                f'<svg width="124" height="124" viewBox="0 0 1254 1254" fill="{BLUE}" aria-hidden="true" '
                f'style="position: absolute; right: -42px; bottom: -38px; opacity: 0.07;">{BALL_PATHS}</svg>'
                f'<span style="position: relative; {DISPLAY}; font-size: 26px; line-height: 26px; letter-spacing: -0.03em; '
                f'color: {BLUE};">{initials(group["name"])}</span></span>')
    return (f'<span aria-hidden="true" style="{base} display: flex; align-items: center; justify-content: center; '
            f'background: {BLUE};">{ball_low(124, -42, -38)}'
            f'<span style="position: relative; {DISPLAY}; font-size: 26px; line-height: 26px; letter-spacing: -0.03em; '
            f'color: {WHITE};">{initials(group["name"])}</span></span>')


def g_info(icon_name, text):
    return (f'<div style="display: flex; align-items: center; gap: 8px; min-height: 20px;">{icon(icon_name, 14, MUTED)}'
            f'<span style="min-width: 0; font-size: 14px; line-height: 20px; color: {NAVY}; {TABULAR}">{text}</span></div>')


def g_vagas(going, cap):
    bar = "".join(
        f'<span style="height: 4px; border-radius: 2px; background: {BLUE if i < going else BORDER};"></span>'
        for i in range(cap)
    )
    bar = (f'<div aria-hidden="true" style="flex-grow: 1; display: grid; grid-template-columns: repeat({cap}, '
           f'minmax(0, 1fr)); gap: 2px;">{bar}</div>')
    if going > cap:
        label = f'<b style="font-weight: 600; color: {NAVY};">{going}</b> confirmados para {cap} vagas'
        return (f'<div style="display: flex; flex-direction: column; gap: 4px;"><div style="display: flex;">{bar}</div>'
                f'<span style="font-size: 13px; line-height: 16px; color: {MUTED}; {TABULAR}">{label}</span></div>')
    return (f'<div style="display: flex; align-items: center; gap: 8px;">{bar}'
            f'<span style="flex: none; font-size: 13px; line-height: 16px; color: {MUTED}; {TABULAR}">'
            f'<b style="font-weight: 600; color: {NAVY};">{going}</b> de {cap} vão</span></div>')


def g_status(status):
    if status == "pending":
        text = (f'<span style="display: flex; align-items: center; gap: 8px; font-size: 13px; line-height: 18px; '
                f'font-weight: 600; color: {BLUE};"><span style="width: 8px; height: 8px; flex: none; '
                f'box-sizing: border-box; border-radius: 4px; border: 2px solid {BLUE};"></span>Falta sua resposta</span>')
        button = ("height: 36px; box-sizing: border-box; border-radius: 999px; font-family: inherit; font-size: 14px; "
                  "line-height: 20px; font-weight: 600;")
        return (f'<div style="display: flex; flex-direction: column; gap: 8px;">{text}'
                f'<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px;">'
                f'<button style="{button} border: 0; background: {BLUE}; color: {WHITE};">Vou</button>'
                f'<button style="{button} border: 1.5px solid {BLUE}; background: {WHITE}; color: {BLUE};">Não vou</button>'
                f'</div></div>')
    dot, color, label = {
        "going": (SUCCESS, SUCCESS_FG, "Você vai"),
        "wait": (WARN, WARN_FG, "Na espera"),
        "out": (PLACEHOLDER, MUTED, "Não vai"),
    }[status]
    return (f'<span style="display: flex; align-items: center; gap: 8px; font-size: 13px; line-height: 18px; '
            f'font-weight: 600; color: {color};"><span style="width: 6px; height: 6px; flex: none; border-radius: 3px; '
            f'background: {dot};"></span>{label}</span>')


def g_row(group, status=None, admin=False, photo=None, pos="50% 50%", live=False, light=True):
    role = f' · <span style="color: {NAVY}; font-weight: 600;">Admin</span>' if admin else ""
    # Sem a seta: a faixa inteira abre o grupo, e a seta só disputava espaço com o nome.
    head = (
        f'<button style="display: flex; flex-direction: column; gap: 2px; width: 100%; padding: 0; border: 0; '
        f'background: transparent; text-align: left; font-family: inherit;">'
        f'<span style="font-size: 16px; line-height: 22px; font-weight: 600; color: {NAVY};">{group["name"]}</span>'
        f'<span style="font-size: 13px; line-height: 18px; color: {MUTED};">{group["place"]}{role}</span></button>'
    )
    if "hours" in group:
        now = f' · <span style="color: {BLUE}; font-weight: 600;">Em andamento</span>' if live else ""
        when = (f'<div style="display: flex; flex-direction: column; gap: 4px;">'
                f'{g_info("calendar", group["long"])}{g_info("clock", group["time"] + now)}</div>')
        game = (f'<div style="display: flex; flex-direction: column; gap: 8px;">'
                f'{g_vagas(group["going"], group["cap"])}{g_status(status)}</div>')
        body = when + game
    else:
        body = (f'<p style="margin: 0; font-size: 14px; line-height: 20px; color: {MUTED}; text-wrap: balance;">'
                f'Nenhum jogo marcado por enquanto.</p>')
    return (
        f'<div style="display: flex; align-items: stretch; gap: 16px; padding: 0 16px 0 0; background: {WHITE}; '
        f'border-top: 1px solid {BORDER}; border-bottom: 1px solid {BORDER};">'
        f'{g_media(group, photo, pos, light)}'
        f'<div style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 12px; '
        f'padding: 16px 0;">{head}{body}</div></div>'
    )


def board_g_list(light=True):
    return header("3 grupos · 1 esperando sua resposta", create_round()) + f_list([
        g_row(dict(CERET, long="Quinta, 29/10"), "pending", admin=True, photo=PHOTO_BLOB, pos="48% 30%", light=light),
        g_row(dict(IBIRA, long="Sábado, 31/10"), "going", light=light),
        g_row(VILA, light=light),
    ])


def board_g_states():
    return header("4 grupos", create_round()) + f_list([
        g_row(dict(CERET, long="Quarta, 28/10", going=12), "going", admin=True, photo=PHOTO_BLOB, pos="48% 30%",
              live=True),
        g_row(dict(FIRMA, long="Sexta, 30/10"), "going", photo=PHOTO2_BLOB, pos="57% 18%"),
        g_row(dict(IBIRA, long="Sábado, 31/10", going=8), "wait"),
        g_row(dict(TERCA, long="Terça, 03/11"), "out"),
    ])


# ---------------------------------------------------------------- H · dois caminhos para o refino
# O dono não gostou da G (28/10). Hipóteses: o grupo virou uma pilha de 6 a 7 linhas iguais; data e horário perderam
# a Anybody que fazia a beleza da D; a altura de cada grupo muda muito. A H separa o item em três colunas com papel
# próprio (foto = quem, texto = o grupo e as vagas, coluna da direita = quando) e traz de volta o dia em Anybody.
H_WHEN_W = 29


def h_when(group, live=False):
    """Selo de calendário pequeno no canto de cima (pedido do dono em 28/10: a coluna grande pesava): faixa azul com
    o dia da semana, o dia em Anybody e o horário embaixo."""
    # Em jogo, a faixa diz "AGORA" em lima: detalhe pequeno sobre o azul, como pede a marca.
    label, label_color = ("AGORA", LIME) if live else (group["wd"], WHITE)
    # Dois cortes pedidos pelo dono em 28/10 (uns 30%, depois mais 20%): 29 px de largura, fontes 6,5/11/7,5. O
    # horário fica no marinho para continuar legível nesse tamanho.
    return (f'<div style="flex: none; width: {H_WHEN_W}px; box-sizing: border-box; display: flex; '
            f'flex-direction: column; align-items: center; overflow: hidden; border: 1px solid {BORDER}; '
            f'border-radius: 6px; background: {WHITE};">'
            f'<span style="align-self: stretch; padding: 1px 0; background: {BLUE}; text-align: center; '
            f'font-size: 6.5px; line-height: 8px; font-weight: 700; letter-spacing: {"0" if live else "0.05em"}; '
            f'color: {label_color};">{label}</span>'
            f'<span style="margin-top: 2px; {DISPLAY}; font-size: 11px; line-height: 13px; letter-spacing: -0.03em; '
            f'color: {NAVY}; {TABULAR}">{group["d"]}</span>'
            f'<span style="margin-bottom: 2px; font-size: 7.5px; line-height: 9px; font-weight: 600; color: {NAVY}; '
            f'{TABULAR}">{group["time"]}</span></div>')


def h_vagas_section(going, cap, style="panel"):
    """As vagas como seção própria do item (pedido do dono em 28/10). "panel": bloco de fundo claro; "divider": só um
    fio em cima. Barra em cima; embaixo, quantos vão à esquerda e, à direita, quantas vagas sobram."""
    bar = "".join(
        f'<span style="height: 4px; border-radius: 2px; background: {BLUE if i < going else BORDER};"></span>'
        for i in range(cap)
    )
    count_style = f'font-size: 13px; line-height: 16px; color: {MUTED}; {TABULAR}'
    if going > cap:
        # A frase de hoje para acima da lotação ocupa a linha toda: não sobra lugar para "vagas abertas".
        footer = (f'<span style="{count_style}"><b style="font-weight: 600; color: {NAVY};">{going}</b> confirmados '
                  f'para {cap} vagas</span>')
    else:
        left = cap - going
        rest = (f'<span style="font-size: 13px; line-height: 16px; font-weight: 600; color: {BLUE}; {TABULAR}">'
                f'{left} {"vaga aberta" if left == 1 else "vagas abertas"}</span>' if left else
                f'<span style="font-size: 13px; line-height: 16px; font-weight: 600; color: {MUTED};">Completo</span>')
        footer = (f'<div style="display: flex; align-items: baseline; justify-content: space-between; gap: 8px;">'
                  f'<span style="{count_style}"><b style="font-weight: 600; color: {NAVY};">{going}</b> de {cap} vão'
                  f'</span>{rest}</div>')
    frame = (f'padding: 10px 12px; border-radius: 10px; background: {ICE};' if style == "panel"
             else f'padding-top: 12px; border-top: 1px solid {BORDER};')
    return (f'<div style="display: flex; flex-direction: column; gap: 6px; {frame}">'
            f'<div aria-hidden="true" style="display: grid; grid-template-columns: repeat({cap}, minmax(0, 1fr)); '
            f'gap: 2px;">{bar}</div>{footer}</div>')


def h_venue(text):
    """Onde é: o pino e a quadra do próximo jogo (ou o bairro do grupo, sem jogo). Nome longo quebra em duas linhas,
    só no travessão: "Quadra 1" não se separa (em 360 px o "1" ficava sozinho)."""
    if " — " in text:
        place, court = text.split(" — ", 1)
        text = f'{place} — <span style="white-space: nowrap;">{court}</span>'
    return (f'<span style="display: flex; align-items: flex-start; gap: 4px; min-width: 0; font-size: 13px; '
            f'line-height: 18px; color: {MUTED};"><span style="display: flex; padding-top: 2px;">'
            f'{icon("pin", 13, MUTED)}</span><span style="min-width: 0;">{text}</span></span>')


# As etiquetas da foto têm o fundo a 70% (pedido do dono em 28/10): a foto aparece por trás; o texto fica sólido.
PHOTO_TAG_BG = "rgba(255,255,255,0.7)"
PHOTO_BUTTON_BG = "rgba(6,56,223,0.7)"  # azul da marca: o marinho foi testado e recusado (sem destaque)


def h_admin_tag():
    """"Admin" como etiqueta no canto de cima da foto (pedido do dono em 28/10), fora da linha do texto."""
    return (f'<span style="position: absolute; top: 8px; left: 8px; padding: 2px 6px; border-radius: 999px; '
            f'background: {PHOTO_TAG_BG}; font-size: 10px; line-height: 12px; font-weight: 700; color: {NAVY};">'
            f'Admin</span>')


def h_photo_status(status):
    """A sua resposta na base da foto (pedido do dono em 28/10): etiqueta branca com o ponto da cor do estado ou, sem
    resposta, o botão azul "Confirmar" (escolhido pelo dono no lugar de "Responder"), que abre a folha com Vou e Não vou."""
    place = "position: absolute; left: 8px; right: 8px; bottom: 8px; box-sizing: border-box; border-radius: 999px;"
    if status == "pending":
        return (f'<button style="{place} height: 28px; border: 0; background: {PHOTO_BUTTON_BG}; color: {WHITE}; '
                f'font-family: inherit; font-size: 12px; line-height: 16px; font-weight: 700;">Confirmar</button>')
    if status is None:
        return ""
    dot, color, label = {
        "going": (SUCCESS, SUCCESS_FG, "Você vai"),
        "wait": (WARN, WARN_FG, "Na espera"),
        "out": (PLACEHOLDER, MUTED, "Não vai"),
    }[status]
    return (f'<span style="{place} display: flex; align-items: center; justify-content: center; gap: 6px; '
            f'height: 24px; background: {PHOTO_TAG_BG}; font-size: 11px; '
            f'line-height: 14px; font-weight: 700; color: {color};"><span style="width: 6px; height: 6px; flex: none; '
            f'border-radius: 3px; background: {dot};"></span>{label}</span>')


def h_row(group, status=None, admin=False, photo=None, pos="50% 50%", live=False, vagas="panel"):
    # Uma linha só de "onde": a quadra do jogo já diz o bairro, então "Quadra · Tatuapé" saiu (redundante, pelo dono).
    where = group.get("venue") or group["place"].split(" · ")[-1]
    head = (
        f'<button style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; padding: 0; '
        f'border: 0; background: transparent; text-align: left; font-family: inherit;">'
        f'<span style="font-size: 16px; line-height: 22px; font-weight: 600; color: {NAVY};">{group["name"]}</span>'
        f'{h_venue(where)}</button>'
    )
    media = g_media(group, photo, pos)
    # Admin no canto de cima e a sua resposta na base da foto: o texto fica só com o grupo, o local e as vagas.
    badges = (h_admin_tag() if admin else "") + (h_photo_status(status) if "hours" in group else "")
    media = media[: -len("</span>")] + badges + "</span>"
    if "hours" in group:
        top = f'<div style="display: flex; align-items: flex-start; gap: 12px;">{head}{h_when(group, live)}</div>'
        body = h_vagas_section(group["going"], group["cap"], vagas)
    else:
        top = f'<div style="display: flex;">{head}</div>'
        body = (f'<p style="margin: 0; font-size: 14px; line-height: 20px; color: {MUTED}; text-wrap: balance;">'
                f'Nenhum jogo marcado por enquanto.</p>')
    return (
        f'<div style="display: flex; align-items: stretch; gap: 16px; padding: 0 16px 0 0; background: {WHITE}; '
        f'border-top: 1px solid {BORDER}; border-bottom: 1px solid {BORDER};">'
        f'{media}'
        # 16 entre "quem e onde" e o bloco do jogo (vagas e resposta): com 12 a barra parecia colada no local.
        f'<div style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 16px; '
        f'padding: 16px 0;">{top}{body}</div></div>'
    )


# Local do próximo jogo (Game.venue), dado de cena: o mesmo CERET da tela de jogo. O CERET tem 18 vagas, o maior
# jogo que o app marca hoje, para ver a barra no caso mais cheio de traços.
H_CERET = dict(CERET, venue="CERET — Quadra 2", going=14, cap=18)
H_IBIRA = dict(IBIRA, venue="Parque Ibirapuera — Quadra 3")
H_FIRMA = dict(FIRMA, venue="Sesc Pinheiros — Quadra 1")
H_TERCA = dict(TERCA, venue="Clube Atlético Mooca — Ginásio")


def board_h_list(vagas="panel"):
    return header("3 grupos · 1 esperando sua resposta", create_round()) + f_list([
        h_row(H_CERET, "pending", admin=True, photo=PHOTO_BLOB, pos="48% 30%", vagas=vagas),
        h_row(H_IBIRA, "going", vagas=vagas),
        h_row(VILA, vagas=vagas),
    ])


def board_h_states():
    return header("4 grupos", create_round()) + f_list([
        h_row(dict(H_CERET, wd="QUA", d="28", going=18), "going", admin=True, photo=PHOTO_BLOB, pos="48% 30%",
              live=True),
        h_row(H_FIRMA, "going", photo=PHOTO2_BLOB, pos="57% 18%"),
        h_row(dict(H_IBIRA, going=8), "wait"),
        h_row(H_TERCA, "out"),
    ])


def h_sheet():
    """A folha que o "Confirmar" abre: o jogo em resumo e Vou / Não vou, como a resposta da Início."""
    photo = (f'<span style="width: 48px; height: 48px; flex: none; border-radius: 12px; overflow: hidden; display: flex;">'
             f'<img src="{PHOTO_BLOB}" alt="Foto do grupo Vôlei do CERET" style="width: 100%; height: 100%; '
             f'object-fit: cover; object-position: 48% 30%;"></span>')
    button = ("height: 48px; box-sizing: border-box; border-radius: 999px; font-family: inherit; font-size: 16px; "
              "line-height: 20px; font-weight: 600;")
    return (
        f'<div style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; background: rgba(14,23,56,0.46);">'
        f'</div>'
        f'<section role="dialog" aria-label="Confirmar presença" style="position: absolute; left: 0; right: 0; '
        f'bottom: 0; display: flex; flex-direction: column; gap: 16px; padding: 8px 16px 28px; '
        f'border-radius: 20px 20px 0 0; background: {WHITE};">'
        f'<span style="align-self: center; width: 36px; height: 4px; border-radius: 2px; background: {BORDER};"></span>'
        f'<div style="display: flex; align-items: center; gap: 12px;">{photo}'
        f'<div style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px;">'
        f'<span style="font-size: 17px; line-height: 22px; font-weight: 600; color: {NAVY};">Vôlei do CERET</span>'
        f'{h_venue(H_CERET["venue"])}</div>{h_when(H_CERET)}</div>'
        f'{h_vagas_section(H_CERET["going"], H_CERET["cap"])}'
        f'<h2 style="margin: 4px 0 0; font-size: 18px; line-height: 24px; font-weight: 700; color: {NAVY};">'
        f'Você vai nesse jogo?</h2>'
        f'<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px;">'
        f'<button style="{button} border: 0; background: {BLUE}; color: {WHITE};">Vou</button>'
        f'<button style="{button} border: 1.5px solid {BLUE}; background: {WHITE}; color: {BLUE};">Não vou</button>'
        f'</div></section>'
    )


# ---------------------------------------------------------------- primeiro acesso
def board_empty():
    hero = (
        f'<section style="position: relative; overflow: hidden; display: flex; flex-direction: column; gap: 12px; '
        f'padding: 20px; border-radius: 20px; background: {BLUE}; color: {WHITE};">{ball(210, -58, -62)}'
        f'<span style="position: relative; display: flex; align-items: center; gap: 9px; min-height: 26px;">'
        f'<span style="width: 16px; height: 16px; flex: none; border-radius: 8px; background: {LIME18}; display: flex; '
        f'align-items: center; justify-content: center;"><span style="width: 8px; height: 8px; border-radius: 4px; '
        f'background: {LIME};"></span></span>'
        f'<span style="font-size: 12px; line-height: 16px; font-weight: 600; letter-spacing: 0.08em; '
        f'text-transform: uppercase; color: {W82};">Convite do grupo</span></span>'
        f'<h2 style="position: relative; margin: 0; {DISPLAY}; font-size: 34px; line-height: 36px; letter-spacing: -0.03em; '
        f'text-wrap: balance; color: {WHITE};">Entre no seu grupo</h2>'
        f'<p style="position: relative; margin: 0; font-size: 15px; line-height: 22px; color: {W82};">'
        f'Recebeu o link do grupo no WhatsApp? Toque nele ou cole aqui.</p>'
        f'<button style="position: relative; display: flex; align-items: center; justify-content: center; gap: 8px; '
        f'height: 52px; margin-top: 4px; border: 0; border-radius: 999px; background: {WHITE}; color: {NAVY}; '
        f'font-family: inherit; font-size: 16px; line-height: 20px; font-weight: 600;">{icon("link", 20)}'
        f'Colar o convite</button></section>'
    )
    create = (f'<div style="overflow: hidden; border: 1px solid {BORDER}; border-radius: 12px;">'
              f'{dashed_row("plus", "Criar um grupo", "Para quem organiza os jogos", first=True)}</div>')
    return header() + stack([hero, create], gap=16, pad="8px 16px 0")


# ---------------------------------------------------------------- hoje (prints do Roborazzi)
def today(src, alt):
    return (f'<div style="width: 390px; height: 868px; background: {CANVAS};">'
            f'<img src="{src}" alt="{alt}" style="width: 390px; height: 868px; display: block;"></div>')


TODAY = {
    "Main": ("Lista de grupos · hoje", "/_blob/c88691b2914d77f138e2a89e5e049e8f",
             "Lista de grupos de hoje: título Grupos com botão + azul, três cartões brancos iguais com ícone de pessoas, "
             "chip Administrador e Confirme, e o bloco Convite recebido"),
    "Hoje-vazio": ("Lista de grupos · hoje, primeiro acesso", "/_blob/bc76341524f8868ead1bfa369a127216",
                   "Primeiro acesso de hoje: título Grupos, ícone, Você ainda não participa de nenhum grupo, botão "
                   "Criar grupo e link Tenho um convite"),
}

BOARDS = {
    "H-cheia": ("Lista de grupos · H, resposta na foto", board_h_list),
    "H-enxuta": ("Lista de grupos · H, folha do Confirmar", lambda: board_h_list() + OVERLAY + h_sheet()),
    "H-estados": ("Lista de grupos · H, outros estados", board_h_states),
    "H-divisoria": ("Lista de grupos · H, vagas com divisória", lambda: board_h_list(vagas="divider")),
    "G-lista": ("Lista de grupos · G, refino da F", board_g_list),
    "G-azul": ("Lista de grupos · G, refino com o lugar da foto em azul", lambda: board_g_list(light=False)),
    "G-estados": ("Lista de grupos · G, outros estados", board_g_states),
    "F-lista": ("Lista de grupos · F, a D em lista com foto", board_f_list),
    "F-estados": ("Lista de grupos · F, outros estados", board_f_states),
    "E-lista": ("Lista de grupos · E, foto em destaque", board_e_list),
    "E-estados": ("Lista de grupos · E, outros estados", board_e_states),
    "D-lista": ("Lista de grupos · D, bloco do jogo", board_d_list),
    "D-estados": ("Lista de grupos · D, outros estados", board_d_states),
    "D-foto": ("Lista de grupos · D, grupo com foto", lambda: board_d_list(photo=True)),
    "A-atleta": ("Lista de grupos · A, atleta", board_a_athlete),
    "A-organizador": ("Lista de grupos · A, organizador", board_a_admin),
    "B-atleta": ("Lista de grupos · B, atleta", board_b_athlete),
    "B-organizador": ("Lista de grupos · B, organizador", board_b_admin),
    "C-atleta": ("Lista de grupos · C, atleta", board_c_athlete),
    "C-organizador": ("Lista de grupos · C, organizador", board_c_admin),
    "Vazio-proposta": ("Lista de grupos · primeiro acesso proposto", board_empty),
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
ROW_GAP = 120


def canvas(heights):
    boards, order, notes = {}, [], {}

    def add(key, x, y, h, title):
        boards[f"{key}.dc.html"] = {"x": x, "y": y, "w": 390, "h": h, "title": title}
        order.append(f"{key}.dc.html")

    def sticky(note_id, x, y, text, fill, w=420):
        notes[note_id] = {"x": x, "y": y, "w": w, "text": text, "fill": fill}

    def title(note_id, y, text):
        notes[note_id] = {"x": 0, "y": y - ROW_TITLE, "text": text, "kind": "title1", "maxW": 1360}

    y = 0
    title("tHoje", y, "Hoje")
    add("Main", 0, y, 868, "Hoje · lista cheia (dados de amostra)")
    add("Hoje-vazio", 390 + GAP_X, y, 868, "Hoje · primeiro acesso")
    sticky("nHoje", 2 * (390 + GAP_X), y,
           "O que pega hoje:\n"
           "1. \"Confirme\" parece botão, mas o toque abre o grupo: responder leva três toques (grupo, jogo, Vou).\n"
           "2. Os três cartões pesam igual; nada diz qual grupo precisa de você.\n"
           "3. Sem foto, todo grupo ganha o mesmo ícone de pessoas (a Início usa as iniciais).\n"
           "4. \"Administrador\" no fim da linha espreme o local em duas linhas (a Início já pôs o chip ao lado do nome).\n"
           "5. \"9 de 12\" não diz de quê.\n"
           "6. Quem organiza não vê nenhuma pendência aqui, só na Início.\n"
           "7. \"Convite recebido\" nunca aparece em produção: nada preenche esse estado, só a amostra do print.\n"
           "8. O + azul cheio é o elemento mais forte da tela e, para o atleta sem plano, leva aos planos.",
           "gray", w=460)

    rows = [
        ("A", "A · Cartão que responde (evolução do cartão de hoje)",
         "Mesmo cartão, mais claro: iniciais no lugar do ícone, \"Organizador\" ao lado do nome, Vou e Não vou de "
         "verdade no grupo que espera sua resposta, barra de vagas com \"9 de 12 vão\" e, para quem organiza, o bloco "
         "\"Esperando você\" de cada grupo (os mesmos itens da Início). Grupo que pede algo sobe para o topo. "
         "\"Criar grupo\" ganha nome e contorno no lugar do + azul cheio.\n\n"
         "Contra: o cartão do organizador fica alto; o Vou repete o da Início."),
        ("B", "B · Lista como a do WhatsApp (índice enxuto)",
         "Sem cartões: uma linha por grupo, como as conversas do WhatsApp, onde esses grupos vivem hoje. Na coluna da "
         "direita, onde o WhatsApp põe a hora, vai o selo do dia do próximo jogo; \"Falta sua resposta\" em azul avisa "
         "o atleta e o número azul conta o que espera o organizador. Quem organiza e joga vê duas seções. Criar grupo e Entrar com "
         "convite viram as linhas tracejadas do fim, como as vagas abertas da tela de jogo; o convite ganha uma porta "
         "também para quem já tem grupo.\n\n"
         "Contra: responder continua na tela do jogo (a linha só avisa); é a que menos usa a marca."),
        ("C", "C · Capa do grupo (identidade)",
         "Cada grupo ganha uma capa no azul do cabeçalho do jogo: bola ao fundo, local no kicker com o ponto lima e o "
         "nome em Anybody. Embaixo, o próximo jogo no mesmo bloco das outras (selo do dia, horário, vagas) e o Vou e Não vou quando falta sua "
         "resposta; para o organizador, o \"Esperando você\".\n\n"
         "Contra: três capas azuis seguidas pesam e o azul deixa de destacar uma coisa só; é a maior mudança."),
    ]
    # A direção escolhida fica logo abaixo de Hoje, para comparar com a tela atual sem rolar o canvas: G (o refino da
    # F), a F, depois a D, e a E (recusada) mais abaixo.
    y = 868 + ROW_GAP + ROW_TITLE
    h_heights = [heights.get(key, 844) for key in ("H-cheia", "H-enxuta", "H-estados", "H-divisoria")]
    title("tH", y, "H · Selo de calendário pequeno no canto (nova, 28/10)")
    add("H-cheia", 0, y, h_heights[0], "H · resposta na base da foto")
    add("H-enxuta", 390 + GAP_X, y, h_heights[1], "H · Confirmar abre a folha com Vou e Não vou")
    add("H-estados", 2 * (390 + GAP_X), y, h_heights[2], "H2 · em jogo, acima da lotação, na espera, não vai")
    add("H-divisoria", 3 * (390 + GAP_X), y, h_heights[3], "H2 · vagas separadas só por divisória (alternativa)")
    sticky("nH", 4 * (390 + GAP_X), y,
           "Por que a G não convenceu (hipóteses):\n"
           "• cada grupo virou uma pilha de 6 a 7 linhas iguais, com cara de formulário;\n"
           "• data e horário perderam o dia em Anybody, que era a beleza da D;\n"
           "• a altura muda muito de um grupo para o outro.\n\n"
           "A H dá um papel a cada parte: a foto diz quem, o texto diz o grupo e as vagas, e um selo de calendário "
           "pequeno (29 px) no canto de cima à direita diz quando: faixa azul com o dia da semana, o dia em Anybody e "
           "o horário embaixo. Em jogo, a faixa diz AGORA em lima.\n"
           "Embaixo do nome, uma linha só de onde: o pino com a quadra do próximo jogo (sem jogo, o bairro do grupo); "
           "\"Quadra · Tatuapé\" saiu por repetir o lugar. \"Admin\" virou etiqueta no canto de cima da foto.\n"
           "As vagas viraram uma seção do item: bloco de fundo claro com a barra e, embaixo, quantos vão e quantas "
           "vagas sobram (\"4 vagas abertas\" em azul, \"Completo\" quando lota). A resposta fica fora do bloco, porque "
           "é sobre você. A 4ª tela separa as vagas só com uma divisória, para comparar.\n"
           "A sua resposta foi para a base da foto: etiqueta branca com o ponto da cor (Você vai, Na espera, Não vai) "
           "ou, sem resposta, o botão azul Confirmar. Ele abre uma folha com o jogo em resumo e Vou / Não vou (2ª "
           "tela). O texto do grupo fica só com nome, local e vagas.", "blue", w=460)

    y += max(h_heights) + ROW_GAP + ROW_TITLE
    g_heights = [heights.get(key, 844) for key in ("G-lista", "G-azul", "G-estados")]
    title("tG", y, "G · Refino da F: texto menor, menos azul (28/10)")
    add("G-lista", 0, y, g_heights[0], "G · refino, lugar da foto claro (sugerido)")
    add("G-azul", 390 + GAP_X, y, g_heights[1], "G · refino, lugar da foto em azul")
    add("G-estados", 2 * (390 + GAP_X), y, g_heights[2], "G · em andamento, acima da lotação, na espera, não vai")
    sticky("nG", 3 * (390 + GAP_X), y,
           "O refino, comparado com a F logo abaixo:\n"
           "1. Texto: três tamanhos e dois pesos. Nome 16 (era 17, negrito 700); data e horário 14 normal (eram 15 "
           "seminegrito, competindo com o nome); o resto 13.\n"
           "2. Ícones de 14 em cinza (eram 16 em azul).\n"
           "3. Azul só no que pede ação, a sua resposta e o Vou, e nas vagas. \"Admin\" vai para o marinho.\n"
           "4. Vagas mais finas (4 px) e o número em destaque: \"9 de 12 vão\".\n"
           "5. Vou e Não vou com 36 de altura (eram 40); a seta saiu, a faixa inteira abre o grupo.\n"
           "6. Respiro de 16 no texto (era 14), na grade de 4.\n"
           "7. Sem foto, o lugar fica claro com as iniciais em azul (1ª tela): a foto de quem tem vira o destaque. "
           "A 2ª tela mantém o azul da F para comparar.", "blue", w=460)

    y += max(g_heights) + ROW_GAP + ROW_TITLE
    f_heights = [heights.get(key, 844) for key in ("F-lista", "F-estados")]
    title("tF", y, "F · A D em lista, com a foto no lugar do azul (28/10)")
    add("F-lista", 0, y, f_heights[0], "F · mesmos dados da tela de hoje")
    add("F-estados", 390 + GAP_X, y, f_heights[1], "F · em andamento, acima da lotação, na espera, não vai")
    sticky("nF", 2 * (390 + GAP_X), y,
           "A D, sem cartão: cada grupo é uma faixa de ponta a ponta, com 8 px de respiro até a próxima.\n"
           "À esquerda, a foto do grupo, mais larga (112 px, a D tinha 84), sem o azul por cima e sangrada: encosta no "
           "topo, na base e na borda da tela; a data saiu dela. Ela acompanha a altura do grupo, como o bloco da D. "
           "Sem foto, o lugar fica no azul da D com a bola e as iniciais do grupo.\n"
           "À direita: nome, modalidade e bairro (e Admin), a data e o horário em duas linhas, cada uma com o seu "
           "ícone, como no cabeçalho da tela de jogo; depois as vagas e a sua resposta, como na D.\n\n"
           "As fotos são as da landing, só de exemplo.", "blue", w=460)

    y += max(f_heights) + ROW_GAP + ROW_TITLE
    d_heights = [heights.get(key, 844) for key in ("D-lista", "D-estados", "D-foto")]
    title("tD", y, "D · Bloco do jogo: um destaque por grupo (28/10)")
    add("D-lista", 0, y, d_heights[0], "D · mesmos dados da tela de hoje")
    add("D-estados", 390 + GAP_X, y, d_heights[1], "D · em jogo, acima da lotação, na espera, não vai")
    add("D-foto", 2 * (390 + GAP_X), y, d_heights[2], "D · grupo com foto (foto de exemplo da landing)")
    sticky("nD", 3 * (390 + GAP_X), y,
           "Um destaque só por grupo. O bloco azul é o jogo: dia da semana, dia e, embaixo, o horário; os números usam "
           "a Anybody do placar e ficam na mesma coluna de um grupo para o outro, então dá para ler a semana descendo a "
           "tela. Do lado fica o grupo: nome, modalidade e bairro, as vagas e a sua resposta. A foto sai de perto da "
           "data: quando o grupo tem foto, ela vai para dentro do bloco, coberta de azul (terceira tela). Sem jogo, o "
           "bloco fica cinza com a bola.\n\n"
           "Só entra o que a tela de hoje já mostra. Duas trocas de texto: \"Admin\", como no WhatsApp, no lugar de "
           "\"Administrador\" (que quebrava a linha em 360 px), e \"Falta sua resposta\" com Vou e Não vou no lugar do "
           "chip \"Confirme\", que parecia botão e não era.", "blue", w=460)

    y += max(d_heights) + ROW_GAP + ROW_TITLE
    e_heights = [heights.get(key, 844) for key in ("E-lista", "E-estados")]
    title("tE", y, "E · Foto em destaque, duas linhas, sem cartões (recusada em 28/10: voltar à D)")
    add("E-lista", 0, y, e_heights[0], "E · mesmos dados da tela de hoje")
    add("E-estados", 390 + GAP_X, y, e_heights[1], "E · em andamento, acima da lotação, na espera, não vai")
    sticky("nE", 2 * (390 + GAP_X), y,
           "Tela de grupos: o destaque é a foto do grupo. Sem cartões, uma lista só, como as conversas do WhatsApp, "
           "com duas linhas por grupo.\n"
           "1ª linha: o nome e, à direita, a sua resposta. \"Responder\" é botão de verdade e abre o jogo; \"Você vai\", "
           "\"Na espera\" e \"Não vai\" ficam como hoje.\n"
           "2ª linha: o próximo jogo, em texto discreto: dia, data, horário e quantos vão.\n"
           "Sem foto, o lugar dela vira um escudo azul com as iniciais na Anybody e a bola.\n"
           "\"Administrador\" vira seção (Você administra, Você joga) e só aparece para quem tem os dois papéis; "
           "quem só joga vê a lista direto.\n\n"
           "Sai da linha: modalidade e bairro, que continuam na tela do grupo. As fotos são as da landing, só de exemplo.",
           "blue", w=460)

    y += max(e_heights) + ROW_GAP + ROW_TITLE
    sticky("nAjuste", 3 * (390 + GAP_X) + 20, y,
           "Ajuste pedido em 28/10, nas três propostas: o calendário virou um selo só com o dia (dia da semana em cima, "
           "como o selo de \"Próximos jogos\" da Início, que hoje mostra o mês), o horário ganhou linha própria com o "
           "fim do jogo e a barra de vagas fica logo embaixo. O próximo jogo é sempre o mesmo bloco: selo à esquerda, "
           "horário e vagas à direita.", "teal", w=400)
    for letter, row_title, text in rows:
        athlete, admin = heights.get(f"{letter}-atleta", 844), heights.get(f"{letter}-organizador", 844)
        title(f"t{letter}", y, row_title)
        add(f"{letter}-atleta", 0, y, athlete, f"{letter} · atleta")
        add(f"{letter}-organizador", 390 + GAP_X, y, admin, f"{letter} · organizador que também joga")
        sticky(f"n{letter}", 2 * (390 + GAP_X), y, text, "blue", w=460)
        y += max(athlete, admin) + ROW_GAP + ROW_TITLE

    empty = heights.get("Vazio-proposta", 844)
    title("tVazio", y, "Primeiro acesso (vale para as três)")
    add("Vazio-proposta", 0, y, empty, "Primeiro acesso · proposta")
    sticky("nVazio", 390 + GAP_X, y,
           "Quem chega sem grupo quase sempre tem um convite no WhatsApp, então o convite vira o destaque (azul com "
           "botão branco) e Criar grupo fica embaixo, tracejado. Para quem já tem plano, inverte: Criar grupo no azul e "
           "o convite tracejado.", "blue", w=460)
    sticky("nDados", 2 * (390 + GAP_X), y,
           "De onde vêm os dados: presença, prazo, vagas e as pendências do organizador (pedidos para entrar, "
           "mensalidades, jogo para acertar) já chegam ao app; as pendências vêm do mesmo resumo da Início. "
           "\"4 pessoas sem resposta\" hoje só sai do detalhe do grupo: na lista pede uma leitura a mais por grupo ou "
           "um resumo no backend. A lista já faz duas leituras por grupo (grupo e jogos).", "teal", w=460)

    return {
        "v": 3,
        # A data em que o canvas nasceu: o índice guarda a mesma em toda regeração.
        "createdOnFiles": {"v": 1, "at": "2026-10-28T13:20:15Z"},
        "title": "Lista de grupos · hoje e propostas",
        "launch": {"view": "canvas"},
        "pages": [],
        "boards": boards,
        "order": order,
        "notes": notes,
        "designSystems": [],
    }


if __name__ == "__main__":
    heights = json.loads(sys.argv[1]) if len(sys.argv) > 1 else {}
    for key, (page_title, src, alt) in TODAY.items():
        with open(os.path.join(PROJECT, f"{key}.dc.html"), "w") as f:
            f.write(dc_html(page_title, today(src, alt), 390, 868))
    for key, (page_title, make) in BOARDS.items():
        h = max(844, heights.get(key, 844))
        heights[key] = h
        root = page(make(), h)
        with open(os.path.join(PROJECT, f"{key}.dc.html"), "w") as f:
            f.write(dc_html(page_title, root, 390, h))
        with open(os.path.join(RENDER, f"{key}.html"), "w") as f:
            # No Brave a foto vem do arquivo da landing; no canvas, do asset enviado.
            f.write(preview_html(root.replace(PHOTO_BLOB, PHOTO_LOCAL).replace(PHOTO2_BLOB, PHOTO2_LOCAL)))
    with open(os.path.join(PROJECT, "canvas.json"), "w") as f:
        json.dump(canvas(heights), f, ensure_ascii=False)
    print("ok")
