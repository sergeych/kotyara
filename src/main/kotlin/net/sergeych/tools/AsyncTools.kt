package net.sergeych.tools

import java.util.concurrent.Executors
import java.util.concurrent.Future

private val es = Executors.newCachedThreadPool()

fun <T>inBackground(closure: ()->T): Future<T> {
    return es.submit(closure)
}



