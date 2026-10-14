package br.com.saqz.access.application.clientapp

import br.com.saqz.sharedkernel.client.ClientApp
import java.time.Duration
import java.time.Instant

enum class ClientAppRecordResult {
    /** A linha do usuário e da plataforma foi criada, ou atualizada porque algo mudou ou ficou velho. */
    WRITTEN,

    /** A linha já estava igual e recente: o banco não escreveu nada. */
    UNCHANGED,

    /** Ainda não existe conta ativa para o subject (a sessão nasce no primeiro PUT /api/session). */
    UNKNOWN_USER,
}

/** Última versão do app vista por usuário e plataforma. */
interface ClientAppVersionStore {
    /**
     * Guarda [app] para a conta ativa de [subject]. Só escreve quando a versão ou o build diferem do que
     * está guardado, ou quando a última vez guardada tem mais de [refreshAfter]; fora isso é uma leitura.
     */
    fun record(
        subject: String,
        app: ClientApp.Identified,
        now: Instant,
        refreshAfter: Duration,
    ): ClientAppRecordResult
}
