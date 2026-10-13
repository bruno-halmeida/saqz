package br.com.saqz.groups.presentation

import br.com.saqz.groups.domain.communication.AppDownloadPort

class FakeAppDownload(private val url: String? = "https://play.example/saqz") : AppDownloadPort {
    val opened = mutableListOf<String>()
    override fun appDownloadUrl(): String? = url
    override fun openAppDownload(url: String) { opened += url }
}
