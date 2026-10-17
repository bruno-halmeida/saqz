package br.com.saqz.groups.presentation

/**
 * Vagas de um jogo (futevôlei e vôlei de praia). O piso vale ao criar ou salvar; jogo que já existe com
 * 2 ou 3 vagas segue como está até alguém abrir a edição, e o backend recusa salvar abaixo disto.
 */
internal object GameCapacityLimits {
    const val Min = 4
    const val Max = 100
}
