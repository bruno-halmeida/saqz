package br.com.saqz.access.application.group.read

interface GroupReadRepository {
    fun find(key: GroupReadKey): GroupReadSnapshot?
}
