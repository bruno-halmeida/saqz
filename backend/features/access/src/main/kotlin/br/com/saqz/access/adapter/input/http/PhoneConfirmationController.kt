package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.emailverification.ConfirmAccountPhone
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Destino do link que vai pelo WhatsApp. O GET só mostra o botão: preview de link e
 * antivírus abrem URLs sozinhos, e confirmar num GET deixaria o robô confirmar a conta.
 * Quem confirma é o POST do formulário.
 */
@RestController
class PhoneConfirmationController(
    private val confirmation: ConfirmAccountPhone,
) {
    @GetMapping(PATH, produces = [MediaType.TEXT_HTML_VALUE])
    fun page(): ResponseEntity<String> = html(
        "Confirme sua conta",
        """
        <p>Toque no botão para confirmar sua conta no Saqz.</p>
        <form method="post"><button type="submit">Confirmar conta</button></form>
        """,
    )

    @PostMapping(PATH, produces = [MediaType.TEXT_HTML_VALUE])
    fun confirm(@PathVariable code: String): ResponseEntity<String> = if (confirmation.confirm(code)) {
        html("Conta confirmada", "<p>Pronto! Pode voltar ao app.</p>")
    } else {
        html("Link inválido", "<p>Este link venceu ou já foi usado. Peça outro no app.</p>")
    }

    private fun html(title: String, body: String): ResponseEntity<String> = ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("Referrer-Policy", "no-referrer")
        .contentType(MediaType(MediaType.TEXT_HTML, Charsets.UTF_8))
        .body(
            """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>$title · Saqz</title>
            <style>
            body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#F5F5F7;font-family:Arial,Helvetica,sans-serif;color:#0E1738}
            main{max-width:420px;margin:16px;padding:28px;background:#fff;border-radius:16px;border:1px solid #D8DDE8}
            h1{margin:0 0 12px;font-size:24px}p{font-size:16px;line-height:1.5}
            button{margin-top:12px;padding:14px 28px;border:0;border-radius:12px;background:#C7F300;color:#0E1738;font-size:16px;font-weight:700}
            </style></head>
            <body><main><h1>$title</h1>$body</main></body>
            </html>
            """.trimIndent(),
        )

    companion object {
        const val PATH_PREFIX = "/public/account-confirmation"
        const val PATH = "$PATH_PREFIX/{code}"
    }
}
