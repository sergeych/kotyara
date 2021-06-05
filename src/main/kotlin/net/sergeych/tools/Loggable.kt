package net.sergeych.tools

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface Loggable {
    fun debug(text: String)
    fun info(text: String)
    fun error(text: String, t: Throwable?)
    fun warning(text: String)

    fun <T>reportExceptions(from: String?=null,f:()->T): T {
        return try { f() }
        catch(x: Throwable) {
            error(
                "${from ?: this::class.qualifiedName ?: "?"}: exception thrown: $x",
                x
            )
            throw x
        }
    }

    fun <T>ignoreExceptions(from: String?=null,f:()->T): Result<T> {
        return try { Result.success(f()) }
        catch(x: Throwable) {
            error(
                "${from ?: this::class.qualifiedName ?: "?"}: exception thrown: $x",
                x
            )
            if( x is InterruptedException)
                throw x
            Result.failure(x)
        }
    }
}



/**
 * Default logger emits messages that subscribers can process. It uses simple blocking queue and background thread
 * to speed up logging, so its methods only push data to the queue while the background thread removes them
 * and emits events.
 *
 * Use [[TaggedLogger]] to delegate actial logging to your class.
 *
 * Call [DefaultLogger.connectStdout]] early to get logs to teh stdoout, or suvscribe to logged events with
 * [[DefaultLogger.onMessage]].
 */
object DefaultLogger {

    enum class Severity {
        DEBUG, INFO, ERROR, WARNING
    }

    data class Entry(
        val severity: Severity,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null,
        val createdAt: Instant = Instant.now()
    ) {
        val severityChar by lazy { severity.name[0].uppercaseChar() }

        val shortFormat by lazy {
            "$severityChar $tag $message ${throwable?.let { " (${throwable.toString()})" } ?: ""}"
        }
        val longFormat by lazy {
            "${isoFormatter.format(createdAt.truncatedTo(ChronoUnit.SECONDS))} $severityChar $tag $message ${throwable?.let { " (${throwable.toString()})" } ?: ""}"
        }

        companion object {
            val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.from(ZoneOffset.UTC))
        }
    }

    val onMessage = Emitter<Entry>()

    fun debug(tag: String, text: String) {
        push(Entry(Severity.DEBUG, tag, text))
    }

    fun info(tag: String, text: String) {
        push(Entry(Severity.INFO, tag, text))
    }

    fun error(tag: String, text: String, t: Throwable?) {
        push(Entry(Severity.ERROR, tag, text, t))
    }

    fun warning(tag: String, text: String) {
        push(Entry(Severity.WARNING, tag, text))
    }

    private val queue = ArrayBlockingQueue<Entry>(1000, true)

    fun push(entry: Entry) {
        queue.add(entry)
    }

    private val stdoutInstalled = AtomicBoolean(false)

    /**
     * Add stdout log target. Safe to call it any number of times. Ince connected, this target could not
     * be disconnected
     */
    fun connectStdout() {
        if (!stdoutInstalled.getAndSet(true)) {
            HRLogWriter.startLogPump(System.out)
        }
    }

    private val connectedFiles = mutableSetOf<String>()

    fun connectFile(fileName: String) {
        synchronized(connectedFiles) {
            if( fileName !in connectedFiles) {
                val f = File(fileName)
                if( f.exists() ) f.delete()
                f.appendText("----------------- Starting new session log: ${ZonedDateTime.now()} -----------------\n")
                onMessage.addListener {
                    f.appendText(it.longFormat+"\n")
                }
                connectedFiles.add(fileName)
            }
        }
    }


    init {
        Thread {
            while (true) {
                onMessage.fire(queue.poll(Long.MAX_VALUE, TimeUnit.SECONDS)!!)
            }
        }.start()
    }
}

/**
 * Chained DefaultLogger that adds prefix to all passing messages
 */
class TaggedLogger(private val prefix: String) : Loggable {
    override fun debug(text: String) { DefaultLogger.debug(prefix, text)}
    override fun info(text: String) { DefaultLogger.info(prefix, text)}
    override fun error(text: String, t: Throwable?) { DefaultLogger.error(prefix, text, t)}
    override fun warning(text: String) { DefaultLogger.warning(prefix, text)}
}
