// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** An HTTP response: status [code] and the body as text (also for error statuses). */
data class HttpResponse(val code: Int, val body: String)

/** Plain GET requests. Network failures throw [IOException]; any status is returned. */
fun interface HttpTransport {
    suspend fun get(url: String): HttpResponse
}

/**
 * [HttpTransport] on `HttpURLConnection` (no extra dependency), off the main thread, with timeouts and a
 * descriptive User-Agent as the FOSSGIS and Open-Meteo usage policies ask. Cancelling the caller interrupts
 * the blocking read.
 */
class UrlConnectionTransport(
    private val userAgent: String,
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 20_000,
) : HttpTransport {
    override suspend fun get(url: String): HttpResponse = runInterruptible(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** "StravaFit/0.1.0 +https://github.com/warpirate/stravafit". */
        fun userAgent(version: String): String = "StravaFit/$version +https://github.com/warpirate/stravafit"
    }
}

/**
 * Spaces calls at least [minIntervalMillis] apart and runs them one at a time (the FOSSGIS servers allow
 * at most one request per second and no parallel requests).
 *
 * @param now monotonic milliseconds; tests pass the virtual clock.
 */
class RateLimiter(
    private val minIntervalMillis: Long,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val mutex = Mutex()
    private var lastStart: Long? = null

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        lastStart?.let { last ->
            val wait = last + minIntervalMillis - now()
            if (wait > 0) delay(wait)
        }
        lastStart = now()
        block()
    }
}

/** The service answered with an error status that is not a "no route here" answer. */
class RoutingServiceException(message: String) : IOException(message)
