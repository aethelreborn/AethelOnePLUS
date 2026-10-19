package org.polyfrost.polyplus.client.utils

import io.ktor.client.network.sockets.ConnectTimeoutException
import org.polyfrost.polyplus.client.PolyPlusConfig
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.URI
import java.net.UnknownHostException
import java.nio.channels.ClosedChannelException
import java.nio.channels.UnresolvedAddressException

/**
 * True when [url] points at this machine. The AethelONE launcher serves the
 * PolyPlus API from a loopback address while it is running.
 */
fun isLoopbackUrl(url: String): Boolean {
    val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
    return host == "localhost" || host.startsWith("127.") || host == "::1" || host == "[::1]"
}

/**
 * True when [error] (or anything it wraps) means the request never reached a
 * server: the connection was refused or dropped, the address did not resolve,
 * or connecting timed out. HTTP-level failures are not included, the server
 * answered for those.
 */
fun isServerUnreachableFailure(error: Throwable): Boolean {
    var cause: Throwable? = error
    var guard = 0
    while (cause != null && guard++ < 12) {
        when (cause) {
            is ConnectException,
            is UnknownHostException,
            is SocketException,
            is ClosedChannelException,
            is UnresolvedAddressException,
            is EOFException,
            is ConnectTimeoutException,
            -> return true
        }
        cause = cause.cause
    }
    return false
}

/**
 * True when [error] means the backend hosted on this machine could not be
 * reached: the AethelONE launcher that runs it is most likely not running.
 */
fun isLocalBackendDown(error: Throwable): Boolean =
    isLoopbackUrl(PolyPlusConfig.apiUrl.url) && isServerUnreachableFailure(error)
