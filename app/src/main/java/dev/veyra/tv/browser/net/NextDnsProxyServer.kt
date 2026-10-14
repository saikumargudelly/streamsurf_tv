package dev.veyra.tv.browser.net

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight, zero-dependency loopback HTTP CONNECT proxy server.
 * Intercepts WebView connection requests and routes domain resolution through the user's personal NextDNS resolver.
 */
class NextDnsProxyServer(private val resolver: NextDnsResolver) {

    companion object {
        private const val TAG = "NextDnsProxyServer"
        private const val BUFFER_SIZE = 32_768 // 32KB buffer for 4K video streams
        private const val SOCKET_TIMEOUT_MS = 30_000 // 30s idle timeout
        private const val CONNECT_TIMEOUT_MS = 6_000 // 6s connection timeout
    }

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()

    var localPort: Int = 0
        private set

    @Synchronized
    fun start(): Int {
        if (isRunning.get() && serverSocket != null && !serverSocket!!.isClosed) {
            return localPort
        }

        val ss = ServerSocket(0, 100, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        localPort = ss.localPort
        isRunning.set(true)

        executor.submit {
            while (isRunning.get() && !ss.isClosed) {
                try {
                    val client = ss.accept()
                    client.tcpNoDelay = true
                    client.soTimeout = SOCKET_TIMEOUT_MS
                    executor.submit {
                        handleClient(client)
                    }
                } catch (_: Throwable) {
                    // Socket closed on stop()
                }
            }
        }
        Log.i(TAG, "NextDNS loopback proxy running on 127.0.0.1:$localPort")
        return localPort
    }

    @Synchronized
    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Throwable) {}
        serverSocket = null
        localPort = 0
        Log.i(TAG, "NextDNS loopback proxy stopped")
    }

    private fun handleClient(client: Socket) {
        try {
            val inStream = client.getInputStream()
            val outStream = client.getOutputStream()

            val firstLine = readLine(inStream) ?: run {
                client.close()
                return
            }

            if (firstLine.startsWith("CONNECT ", ignoreCase = true)) {
                handleConnectTunnel(client, inStream, outStream, firstLine)
            } else {
                handleHttpDirect(client, inStream, outStream, firstLine)
            }
        } catch (_: Throwable) {
            try { client.close() } catch (_: Throwable) {}
        }
    }

    private fun handleConnectTunnel(
        client: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        firstLine: String
    ) {
        // e.g. "CONNECT www.youtube.com:443 HTTP/1.1"
        val parts = firstLine.split(" ")
        if (parts.size < 2) {
            client.close()
            return
        }

        val target = parts[1]
        val host: String
        val port: Int
        if (target.contains(":")) {
            val colonIdx = target.lastIndexOf(":")
            host = target.substring(0, colonIdx)
            port = target.substring(colonIdx + 1).toIntOrNull() ?: 443
        } else {
            host = target
            port = 443
        }

        // Drain client headers until empty line (\r\n)
        while (true) {
            val line = readLine(clientIn)
            if (line.isNullOrEmpty()) break
        }

        // Resolve destination domain through NextDNS DoH
        val resolveResult = resolver.resolve(host)
        if (resolveResult.isBlocked || resolveResult.address == null) {
            // Blocked by user's NextDNS profile
            val blockResponse = "HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain\r\nContent-Length: 22\r\nConnection: close\r\n\r\nBlocked by NextDNS\r\n"
            try {
                clientOut.write(blockResponse.toByteArray())
                clientOut.flush()
            } catch (_: Throwable) {}
            client.close()
            return
        }

        val targetSocket = Socket()
        try {
            targetSocket.connect(InetSocketAddress(resolveResult.address, port), CONNECT_TIMEOUT_MS)
            targetSocket.tcpNoDelay = true
            targetSocket.soTimeout = SOCKET_TIMEOUT_MS

            // Confirm tunnel to WebView
            clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            clientOut.flush()

            // Stream bidirectionally
            pipeBidirectional(client, targetSocket)
        } catch (_: Throwable) {
            try {
                clientOut.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                clientOut.flush()
            } catch (_: Throwable) {}
            closeQuietly(client, targetSocket)
        }
    }

    private fun handleHttpDirect(
        client: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        firstLine: String
    ) {
        val parts = firstLine.split(" ")
        if (parts.size < 2) {
            client.close()
            return
        }

        val headers = mutableListOf<String>()
        headers.add(firstLine)
        var host = ""
        var port = 80

        while (true) {
            val line = readLine(clientIn) ?: break
            if (line.isEmpty()) break
            headers.add(line)
            if (line.startsWith("Host:", ignoreCase = true)) {
                val hostPart = line.substring(5).trim()
                if (hostPart.contains(":")) {
                    host = hostPart.substringBefore(":")
                    port = hostPart.substringAfter(":").toIntOrNull() ?: 80
                } else {
                    host = hostPart
                }
            }
        }

        if (host.isEmpty()) {
            client.close()
            return
        }

        val resolveResult = resolver.resolve(host)
        if (resolveResult.isBlocked || resolveResult.address == null) {
            val blockMsg = "HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            try {
                clientOut.write(blockMsg.toByteArray())
                clientOut.flush()
            } catch (_: Throwable) {}
            client.close()
            return
        }

        val targetSocket = Socket()
        try {
            targetSocket.connect(InetSocketAddress(resolveResult.address, port), CONNECT_TIMEOUT_MS)
            targetSocket.tcpNoDelay = true
            val targetOut = targetSocket.getOutputStream()

            for (h in headers) {
                targetOut.write((h + "\r\n").toByteArray())
            }
            targetOut.write("\r\n".toByteArray())
            targetOut.flush()

            pipeBidirectional(client, targetSocket)
        } catch (_: Throwable) {
            closeQuietly(client, targetSocket)
        }
    }

    private fun pipeBidirectional(s1: Socket, s2: Socket) {
        val in1 = s1.getInputStream()
        val out1 = s1.getOutputStream()
        val in2 = s2.getInputStream()
        val out2 = s2.getOutputStream()

        executor.submit {
            try {
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (in1.read(buffer).also { bytesRead = it } != -1) {
                    out2.write(buffer, 0, bytesRead)
                    out2.flush()
                }
            } catch (_: Throwable) {
            } finally {
                closeQuietly(s1, s2)
            }
        }

        try {
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (in2.read(buffer).also { bytesRead = it } != -1) {
                out1.write(buffer, 0, bytesRead)
                out1.flush()
            }
        } catch (_: Throwable) {
        } finally {
            closeQuietly(s1, s2)
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (sb.isEmpty()) null else sb.toString()
            }
            if (b == '\n'.code) {
                var s = sb.toString()
                if (s.endsWith("\r")) {
                    s = s.substring(0, s.length - 1)
                }
                return s
            }
            sb.append(b.toChar())
        }
    }

    private fun closeQuietly(vararg sockets: Socket) {
        for (s in sockets) {
            try { s.close() } catch (_: Throwable) {}
        }
    }
}
