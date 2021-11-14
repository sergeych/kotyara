package net.sergeych.kotyara

suspend fun bm(block: suspend ()->Unit) {
    val start = System.currentTimeMillis()
    block()
    val t = System.currentTimeMillis()-start
    println("Elapsed time.ms: $t")
}