package br.com.saqz.groups.domain.group

/** O que cobrar de quem vira mensalista depois do dia de vencimento do mês em que entrou. */
enum class MonthlyLateJoinPolicy {
    /** Cobra o mês atual com o vencimento do grupo: a mensalidade já nasce em atraso. */
    GROUP_DUE_DATE,

    /**
     * Cobra o mês atual com vencimento no dia em que a cobrança é gerada, nunca no passado: o dia em que a
     * pessoa virou mensalista, ou o da geração se o job só rodou depois (ele roda de madrugada).
     */
    JOIN_DATE,

    /** A primeira mensalidade é a do mês seguinte. */
    NEXT_MONTH,
}
