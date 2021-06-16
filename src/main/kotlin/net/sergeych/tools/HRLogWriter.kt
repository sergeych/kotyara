package net.sergeych.tools

import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.logging.Level

class HRLogWriter(output: OutputStream, var level: Logger.Severity) : Closeable {
    private val out = OutputStreamWriter(output, "UTF-8")
    var last: Calendar? = null
    var isClosed: Boolean = false
        private set

    private fun writeDateHeader(instant: Instant) =
        writeDateHeader(Date.from(instant))

    private fun writeDateHeader(d: Date) {
        val c = Calendar.getInstance()
        c.time = d
        val l = last
        if (l == null || (
                    c.get(Calendar.YEAR) != l.get(Calendar.YEAR) ||
                            c.get(Calendar.MONTH) != l.get(Calendar.MONTH) ||
                            c.get(Calendar.DAY_OF_MONTH) != l.get(Calendar.DAY_OF_MONTH)
                    )
        ) {
            last = c
            out.write("---- log continues on ${d.iso8601} ----\n")
        }
    }

    override fun close() {
        if (!isClosed) {
            try {
                out.close()
            } catch (x: IOException) {
            }
            isClosed = true
        }
    }

    fun write(entry: Logger.Entry) {
//        println("E:${entry.severity} < ${level} => ${entry.severity <= level}")
        if (!isClosed && entry.severity >= level) {
            try {
                writeDateHeader(entry.createdAt)
                var msg = "${shortInstanceTime.format(entry.createdAt)} ${entry.severity.name[0]} ${entry.tag} ${entry.message}\n"
                entry.throwable?.let { t ->
                    msg += "\tException: ${t.javaClass.name}: ${t.message}\n"
                    msg += t.stackTrace.joinToString("\n") { "\t\t$it" }
                }
                out.write(msg)
            } catch (x: IOException) {
                isClosed = true
            }
        }
    }

    fun flush() {
        if (!isClosed)
            out.flush()
    }

    companion object {
        val shortTimeFormat = SimpleDateFormat("HH:mm:ss")
        val shortInstanceTime =
            DateTimeFormatter.ofPattern("HH:mm:ss")
                .withZone(ZoneId.from(ZoneOffset.UTC))

        fun startLogPump(out: OutputStream,level: Logger.Severity=Logger.Severity.DEBUG) {
            val queue = LinkedBlockingQueue<Logger.Entry>()
            val lw = HRLogWriter(out,level)
            Thread() {
                while (true) {
                    lw.write(queue.take())
                    while (queue.isNotEmpty()) queue.poll()?.let { lw.write(it) }
                    lw.flush()
                }
            }.also {
                it.isDaemon = true
                it.start()
            }
            Logger.onMessage.addListener { queue.put(it) }
        }
    }
}