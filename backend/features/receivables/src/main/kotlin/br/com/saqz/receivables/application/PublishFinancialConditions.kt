package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.FeeSchedule
import java.time.Instant

data class PublishedFinancialCondition(val kind: String, val resourceId: String,
                                       val effectiveAt: Instant, val publishedAt: Instant)

interface FinancialConditionsPublisher {
    fun terms(request: FinancialRequest, version: String, content: String, effectiveAt: Instant,
              now: Instant): FinancialResult<PublishedFinancialCondition>
    fun fees(request: FinancialRequest, schedule: FeeSchedule, effectiveAt: Instant,
             now: Instant): FinancialResult<PublishedFinancialCondition>
}
