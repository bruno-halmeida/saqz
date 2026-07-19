package br.com.saqz.groups.application.read

interface GroupReadRepository {
    fun find(key: GroupReadKey): GroupReadSnapshot?
}
