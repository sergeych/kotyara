package net.sergeych.tools

import java.io.File
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Logs to file catcher. Keeps previous log and packs it to Zip on request.
 * deletes older log files.
 *
 */
class FileLogCatcher(root: File) {

    private var prevLog: File?
    private var currentLog: File

    private var queue = LinkedBlockingQueue<DefaultLogger.Entry>()

    /**
     * Export current logs to zip, with optional metadata. It exports current
     * log as `current.log` and, if exists, `previous.log`.
     *
     * @param metadata if present, will be put as `metadata.txt` file
     * @param output if present, zip wile will be written to it (Existing file will be deleted).
     *          Otherwise new temp wile in delete-on-exit mode will be created.
     * @return ready zip file
     */
    fun exportZip(metadata: String? = null, output: File? = null): File {
        val f = if (output != null) {
            if (output.exists()) output.delete()
            output
        } else {
            val f = File.createTempFile("exprort", "log")
            f.deleteOnExit()
            f
        }
        val zos = ZipOutputStream(f.outputStream())
        zos.setLevel(9)
        if (metadata != null) {
            zos.putNextEntry(ZipEntry("metadata.txt"))
            zos.write(metadata.encodeToByteArray())
        }
        prevLog?.let {
            zos.putNextEntry(ZipEntry("previous.log"))
            zos.write(it.readBytes())
        }
        zos.putNextEntry(ZipEntry("current.log"))
        zos.write(currentLog.readBytes())
        zos.closeEntry()
        zos.close()
        return f
    }

    init {
        if (!root.exists()) root.mkdirs()
        val logFiles = root.listFiles().filter { it.endsWith(".log") }.sorted()
        logFiles.dropLast(1).forEach { it.delete() }
        prevLog = logFiles.lastOrNull()
        currentLog = File(root, "${Date().iso8601}.log")
        HRLogWriter(currentLog.outputStream()).use { lw ->
            Thread() {
                while (true) {
                    lw.write(queue.take())
                    while (queue.isNotEmpty()) queue.poll()?.let { lw.write(it) }
                    lw.flush()
                }
            }.start()
        }
    }
}