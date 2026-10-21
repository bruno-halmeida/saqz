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
}
