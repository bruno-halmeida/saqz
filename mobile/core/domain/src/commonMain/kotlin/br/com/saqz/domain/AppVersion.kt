package br.com.saqz.domain

/**
 * A versão instalada como a loja mostra, "0.0.5 (7)": nome e número do build. Vem de cada
 * plataforma (Gradle no Android, Info.plist no iOS); é o que o suporte pede num print do Perfil.
 */
data class AppVersion(val label: String)
