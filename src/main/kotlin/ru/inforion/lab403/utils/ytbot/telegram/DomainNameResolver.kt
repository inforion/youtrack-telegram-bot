package ru.inforion.lab403.utils.ytbot.telegram

import ru.inforion.lab403.common.extensions.*
import ru.inforion.lab403.utils.ytbot.asIPAddress
import java.io.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Domain name resolver (for Telegram proxying)
 *
 * based on https://github.com/Njunwa1/java-dns-query/blob/master/src/main/java/com/company/app/Main.java
 *
 * @param dnsServerIP querying DNS server IP
 * @param dnsServerPort querying DNS server port
 */
class DomainNameResolver(val dnsServerIP: String, val dnsServerPort: Int = DNS_DEFAULT_PORT) {
    companion object {
        private const val TIME_OUT = 10_000
        private const val BUF_SIZE = 8192
        private const val DNS_DEFAULT_PORT = 53
    }

    /**
     * Resolve domain name [domainName] using specified DNS server IP [dnsServerIP] and port [dnsServerPort]
     *
     * @param domainName domain name to be resolved
     *
     * @return collection of IP addresses resolved using DNS server
     */
    fun query(domainName: String): Collection<String> =
        DatagramSocket(0).use { socket ->
            socket.soTimeout = TIME_OUT

            val message = encodeDNSMessage(domainName)
            sendRequest(socket, dnsServerIP, dnsServerPort, message)

            val response = recvResponse(socket)
            decodeDNSMessage(response)
        }

    private fun sendRequest(socket: DatagramSocket, address: String, port: Int, stream: ByteArrayOutputStream) {
        val data = stream.toByteArray()
        val host = InetAddress.getByName(address)
        val request = DatagramPacket(data, data.size, host, port)
        return socket.send(request)
    }

    private fun recvResponse(socket: DatagramSocket): ByteArrayInputStream {
        val buffer = ByteArray(BUF_SIZE)
        val bais = ByteArrayInputStream(buffer)
        val response = DatagramPacket(buffer, buffer.size)
        socket.receive(response)
        return bais
    }

    private fun encodeDNSMessage(domainName: String): ByteArrayOutputStream {
        val baos = ByteArrayOutputStream(BUF_SIZE)
        val dos = DataOutputStream(baos)

        // transaction id
        dos.writeShort(1)
        // flags
        dos.writeShort(0x100)
        // number of queries
        dos.writeShort(1)
        // answer, auth, other
        dos.writeShort(0)
        dos.writeShort(0)
        dos.writeShort(0)

        writeEncodedDomainName(dos, domainName)

        // query type
        dos.writeShort(1)
        // query class
        dos.writeShort(1)

        dos.flush()

        return baos
    }

    private fun writeEncodedDomainName(output: DataOutputStream, domainName: String) {
        domainName
            .split(".")
            .forEach {
                val len = it.length.asByte.asUInt
                val name = it.convertToBytes()
                output.writeByte(len)
                output.write(name)
            }
        output.writeByte(0)
    }

    private fun decodeDNSMessage(stream: ByteArrayInputStream): Collection<String> {
        val input = DataInputStream(stream)

        // header
        // transaction id
        input.skip(2)
        // flags
        input.skip(2)
        // number of queries
        input.skip(2)
        // answer, auth, other
        val numberOfAnswer = input.readShort().asInt
        input.skip(2)
        input.skip(2)

        // question record
        skipDomainName(input)
        // query type
        input.skip(2)
        // query class
        input.skip(2)

        val addresses = mutableListOf<Int>()

        // answer records
        repeat(numberOfAnswer) {
            input.mark(1)
            val ahead = input.readByte().asUInt
            input.reset()
            if (ahead and 0xc0 == 0xc0) {
                // compressed name
                input.skip(2)
            } else {
                skipDomainName(input)
            }

            // query type
            val type = input.readShort().asUInt
            // query class
            input.skip(2)
            // ttl
            input.skip(4)
            val addrLen = input.readShort().asULong
            if (type == 1 && addrLen == 4L) {
                addresses.add(input.readInt())
            } else {
                input.skip(addrLen)
            }
        }

        return addresses.map { it.asIPAddress }
    }

    private fun skipDomainName(input: DataInputStream) {
        do {
            val labelLength = input.readByte().asULong
            input.skip(labelLength)
        } while (labelLength != 0L)
    }
}