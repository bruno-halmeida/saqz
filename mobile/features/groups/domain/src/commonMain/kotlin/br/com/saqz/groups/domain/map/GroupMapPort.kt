package br.com.saqz.groups.domain.map

fun interface GroupMapCallback { fun complete(opened: Boolean) }
fun interface GroupMapPort { fun open(url: String, done: GroupMapCallback) }
