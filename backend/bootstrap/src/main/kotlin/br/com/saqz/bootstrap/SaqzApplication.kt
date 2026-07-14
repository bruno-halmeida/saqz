package br.com.saqz.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(proxyBeanMethods = false)
class SaqzApplication

fun main(args: Array<String>) {
    runApplication<SaqzApplication>(*args)
}
