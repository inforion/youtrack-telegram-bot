package ru.inforion.lab403.utils.ytbot.config

data class Proxy(
    val host: String,
    val port: Int,
    val auth: ProxyAuth?
)