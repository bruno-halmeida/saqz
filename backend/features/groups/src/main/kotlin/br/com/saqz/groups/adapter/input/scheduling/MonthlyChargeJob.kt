package br.com.saqz.groups.adapter.input.scheduling

import br.com.saqz.groups.application.finance.charge.MonthlyChargeSchedule
import org.springframework.scheduling.annotation.Scheduled

/** Trigger diario do MonthlyChargeSchedule. Horario e fuso vem de configuracao. */
class MonthlyChargeJob(private val schedule:MonthlyChargeSchedule){
    @Scheduled(cron="\${saqz.finance.monthly-charges.cron}",zone="\${saqz.finance.monthly-charges.zone}")
    fun run(){schedule.run()}
}
