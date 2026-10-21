package br.com.saqz.adminweb.http

import br.com.saqz.subscriptions.application.AdminRevenueOriginRow
import br.com.saqz.subscriptions.application.AdminRevenueReader
import br.com.saqz.subscriptions.application.AdminRevenueReport
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Receita real x teste por mês e origem (adm-web · Assinaturas). Fora do component scan; fiação em PlatformAdminConfiguration. */
@RestController
class AdminRevenueController(
    private val reader: AdminRevenueReader,
) {
    @GetMapping("/admin/revenue")
    fun revenue(@RequestParam(defaultValue = "12") months: Int): ResponseEntity<AdminRevenueReport> {
        if (months !in 1..MAX_MONTHS) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(reader.monthly(months))
    }

    /** Receita real por origem do cliente ("Sem cupom" e cada cupom/campanha) e plataforma. */
    @GetMapping("/admin/revenue/origins")
    fun origins(@RequestParam(defaultValue = "12") months: Int): ResponseEntity<List<AdminRevenueOriginRow>> {
        if (months !in 1..MAX_MONTHS) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(reader.byOrigin(months))
    }

    private companion object {
        const val MAX_MONTHS = 60
    }
}
