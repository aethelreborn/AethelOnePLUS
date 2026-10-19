package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.utils.isLoopbackUrl
import org.polyfrost.polyplus.client.utils.isServerUnreachableFailure
import java.io.EOFException
import java.net.ConnectException
import java.net.UnknownHostException
import java.nio.channels.ClosedChannelException

class NetworkErrorsTest {
    @Test
    fun `loopback backends are recognized`() {
        assertTrue(isLoopbackUrl("http://127.0.0.1:8777"))
        assertTrue(isLoopbackUrl("http://127.0.0.2:8777"))
        assertTrue(isLoopbackUrl("http://localhost:8777"))
        assertTrue(isLoopbackUrl("http://LOCALHOST:8777"))
        assertTrue(isLoopbackUrl("http://[::1]:8777"))
        assertFalse(isLoopbackUrl("https://plus.polyfrost.org"))
        assertFalse(isLoopbackUrl("https://plus-staging.polyfrost.org"))
        assertFalse(isLoopbackUrl("not a url"))
    }

    @Test
    fun `connection level failures are detected through wrappers`() {
        assertTrue(isServerUnreachableFailure(ConnectException("Connection refused: getsockopt")))
        assertTrue(isServerUnreachableFailure(ClosedChannelException()))
        assertTrue(isServerUnreachableFailure(EOFException("Connection closed")))
        assertTrue(
            isServerUnreachableFailure(
                IllegalStateException("while equipping", UnknownHostException("plus.polyfrost.org")),
            ),
        )
    }

    @Test
    fun `application level failures are not treated as unreachable`() {
        assertFalse(isServerUnreachableFailure(IllegalArgumentException("Cosmetic #1 cannot be equipped")))
        assertFalse(isServerUnreachableFailure(RuntimeException("Response body is not JSON")))
    }
}
