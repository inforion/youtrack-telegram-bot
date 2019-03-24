package ru.inforion.lab403.utils.ytbot.telegram

import org.simplejavamail.mailer.internal.socks.socks5client.ProxyCredentials
import org.simplejavamail.mailer.internal.socks.socks5client.Socks5
import org.simplejavamail.mailer.internal.socks.socks5client.SocksSocket
import ru.inforion.lab403.utils.ytbot.config.ProxyConfig
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

class SSLSocks5Factory(private val proxy: ProxyConfig) : SocketFactory() {
    override fun createSocket(): Socket {
        val proxyAuth = Socks5(InetSocketAddress(proxy.host, proxy.port))
        if (proxy.auth != null)
            proxyAuth.credentials = ProxyCredentials(proxy.auth.username, proxy.auth.password)

        return SocksSocket(proxyAuth, proxyAuth.createProxySocket())
    }

    override fun createSocket(host: InetAddress, port: Int) =
        throw NotImplementedError("Won't be implemented")

    override fun createSocket(address: String, port: Int, localAddress: InetAddress, localPort: Int) =
        throw NotImplementedError("Won't be implemented")

    override fun createSocket(host: String, port: Int) =
        throw NotImplementedError("Won't be implemented")

    override fun createSocket(host: InetAddress, port: Int, localHost: InetAddress, localPort: Int) =
        throw NotImplementedError("Won't be implemented")
}