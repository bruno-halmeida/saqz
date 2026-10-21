package br.com.saqz.bootstrap

import br.com.saqz.adminweb.http.AdminCouponsController
import br.com.saqz.adminweb.http.AdminPartnersController
import br.com.saqz.adminweb.http.CreateCouponRequest
import br.com.saqz.adminweb.http.CreatePartnerRequest
import br.com.saqz.subscriptions.application.AdminCoupon
import br.com.saqz.subscriptions.application.AdminCouponCreate
import br.com.saqz.subscriptions.application.AdminCouponCreateResult
import br.com.saqz.subscriptions.application.AdminCouponDirectory
import br.com.saqz.subscriptions.application.AdminPartnerDirectory
import br.com.saqz.subscriptions.application.Partner
import br.com.saqz.subscriptions.application.PartnerCreate
import br.com.saqz.subscriptions.application.PartnerCreateResult
import br.com.saqz.subscriptions.application.PartnerReport
import br.com.saqz.subscriptions.application.PartnerSummary
import br.com.saqz.subscriptions.domain.Coupon
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class AdminPartnersControllerTest {
    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val known = UUID.randomUUID()

    private inner class Partners : AdminPartnerDirectory {
        val created = mutableListOf<PartnerCreate>()
        override fun list(now: Instant) = emptyList<PartnerSummary>()
        override fun create(command: PartnerCreate): PartnerCreateResult {
            if (command.name == "Arena") return PartnerCreateResult.DuplicateName
            created += command
            return PartnerCreateResult.Created(Partner(UUID.randomUUID(), command.name, command.contactName, command.contactPhone, command.contactEmail, command.notes, true, now))
        }
        override fun exists(partnerId: UUID) = partnerId == known
        override fun end(partnerId: UUID, now: Instant) = partnerId == known
        override fun report(partnerId: UUID, from: Instant?, to: Instant?, now: Instant): PartnerReport? = null
    }

    @Test
    fun `cria parceiro com contato normalizado e recusa o inválido`() {
        val partners = Partners()
        val controller = AdminPartnersController(partners, now = { now })
        fun req(name: String?, phone: String? = null, email: String? = null) =
            CreatePartnerRequest().apply { this.name = name; contactPhone = phone; contactEmail = email }
        assertEquals(201, controller.create(req(" Arena Moema ", "(11) 99999-0000", "a@b.co")).statusCode.value())
        assertEquals("11999990000", partners.created.single().contactPhone)
        assertEquals("Arena Moema", partners.created.single().name)
        assertEquals(409, controller.create(req("Arena")).statusCode.value())
        assertEquals(400, controller.create(req("A")).statusCode.value())
        assertEquals(400, controller.create(req("Arena 2", "123")).statusCode.value())
        assertEquals(400, controller.create(req("Arena 3", email = "sem-arroba")).statusCode.value())
        assertEquals(404, controller.report(UUID.randomUUID(), null, null).statusCode.value())
        assertEquals(400, controller.report(known, now, now.minusSeconds(1)).statusCode.value())
        assertEquals(204, controller.end(known).statusCode.value())
    }

    @Test
    fun `cupom com parceiro inexistente é recusado, com parceiro conhecido passa`() {
        val seen = mutableListOf<AdminCouponCreate>()
        val directory = object : AdminCouponDirectory {
            override fun list() = emptyList<AdminCoupon>()
            override fun create(command: AdminCouponCreate): AdminCouponCreateResult {
                seen += command
                return AdminCouponCreateResult.Created(Coupon(UUID.randomUUID(), command.code, command.discountPercent, partnerId = command.partnerId))
            }
            override fun deactivate(couponId: UUID, now: Instant) = false
        }
        val controller = AdminCouponsController(directory, { now }, partnerExists = Partners()::exists)
        fun req(partner: UUID) = CreateCouponRequest().apply { code = "ARENA10"; discountPercent = 10; partnerId = partner }
        assertEquals(400, controller.create(req(UUID.randomUUID())).statusCode.value())
        assertEquals(201, controller.create(req(known)).statusCode.value())
        assertEquals(known, seen.single().partnerId)
    }

    @Test
    fun `regra de comissão - valida percentual, duração e meses - fechar só mês válido`() {
        val saved = mutableListOf<br.com.saqz.subscriptions.application.CommissionRule?>()
        val store = object : br.com.saqz.subscriptions.application.PartnerCommissionStore {
            override fun rule(partnerId: UUID) = null
            override fun setRule(partnerId: UUID, rule: br.com.saqz.subscriptions.application.CommissionRule?) = (partnerId == known).also { saved += rule }
            override fun months(partnerId: UUID, now: Instant) = null
            override fun totals(partnerId: UUID, now: Instant) = br.com.saqz.subscriptions.application.PartnerCommissionTotals(0, 0)
            override fun close(partnerId: UUID, month: java.time.YearMonth, now: Instant) =
                br.com.saqz.subscriptions.application.CloseCommissionResult.NotEnded
            override fun markPaid(partnerId: UUID, month: java.time.YearMonth, paidOn: java.time.LocalDate, note: String?) = true
        }
        val controller = AdminPartnersController(Partners(), store) { now }
        fun rule(p: String?, d: String?, m: Int? = null) = br.com.saqz.adminweb.http.CommissionRuleRequest().apply {
            percent = p?.let { java.math.BigDecimal(it) }; duration = d; months = m
        }
        assertEquals(204, controller.setRule(known, rule("12.5", "MONTHS", 6)).statusCode.value())
        assertEquals(java.math.BigDecimal("12.50"), saved.last()?.percent)
        assertEquals(6, saved.last()?.months)
        assertEquals(204, controller.setRule(known, rule(null, null)).statusCode.value())
        assertEquals(null, saved.last())
        assertEquals(400, controller.setRule(known, rule("12.555", "LIFETIME")).statusCode.value())
        assertEquals(400, controller.setRule(known, rule("0", "LIFETIME")).statusCode.value())
        assertEquals(400, controller.setRule(known, rule("10", "MONTHS", 61)).statusCode.value())
        assertEquals(400, controller.setRule(known, rule("10", "SEMPRE")).statusCode.value())
        assertEquals(404, controller.setRule(UUID.randomUUID(), rule("10", "LIFETIME")).statusCode.value())
        assertEquals(400, controller.close(known, "2026-13").statusCode.value())
        assertEquals(400, controller.close(known, "2026-10").statusCode.value())
        assertEquals(400, controller.markPaid(known, "2026-09", br.com.saqz.adminweb.http.MarkCommissionPaidRequest()).statusCode.value())
    }
}
