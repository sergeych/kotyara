package net.sergeych.tools

import net.sergeych.mp_logger.Log
import net.sergeych.mp_logger.Loggable

/**
 * LogTag with tag connected to this instance id, it is JVM specific sugar
 */
open class TaggedLogger(private val _prefix: String) : Loggable {
    val prefix by lazy { }

    override var logLevel: Log.Level? = Log.Level.INFO

    override var logTag: String = "${System.identityHashCode(this).toString(16)} $_prefix"
}
