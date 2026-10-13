package br.com.saqz.subscriptions.application

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

val TRIAL_ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")

/**
 * Dias de calendário (no fuso de São Paulo) entre hoje e o dia em que o teste termina: 0 no dia do fim, 1 na véspera.
 * É a mesma conta do aviso do job e da folha do app, para os dois dizerem o mesmo número.
 */
fun trialDaysLeft(now: Instant, endsAt: Instant, zone: ZoneId = TRIAL_ZONE): Int =
    ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), endsAt.atZone(zone).toLocalDate()).toInt()
