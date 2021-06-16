package net.sergeych.tools

import net.sergeych.tools.ResourceHandle.Companion.list
import java.io.File
import java.io.FileNotFoundException
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString

/**
 * Handler to retrieve stored resource in a classpath, also in JAR. Use [list] to get a list if handles.
 * Each handle as a [name] and some accessors to get the contents. Hadnles provdie lazy loading and
 * caching of the respective resources.
 */
class ResourceHandle(val name: String, file: File) {

    /**
     * Retrieve the whole resource as a string
     */
    val text: String by lazy { file.readText() }

    /**
     * retrieve resource as array of lines, same as split text to lines
     */
    val lines: List<String> by lazy { file.readLines() }

    /**
     * retrieve resource as a binary data
     */
    val bytes: ByteArray by lazy { file.readBytes() }

    companion object {

        /**
         * Enumerate resource in a given root folder, using either this class loader or a loader of the
         * specified class.
         *
         * @param root the root folder of the resources to enumerate
         * @param klass class to get a loader from (can't use use library's loader here as resources are in caller's
         *              unit). We use java Class instead of kotlin to not to require kotlin.reflection here.
         * @return list of resource handles (possibly empty) to access found resources.
         */
        fun list(klass: Class<*>,root: String): List<ResourceHandle> {
            val uri: URI = klass.getResource(root)?.toURI()
                ?: throw FileNotFoundException("resource not found: $root")
            val myPath = if (uri.getScheme().equals("jar"))
                FileSystems.newFileSystem(uri, emptyMap<String, Any>()).use { it.getPath(root) }
            else
                Paths.get(uri)
            return Files.walk(myPath, 1).use {
                it.toList().mapNotNull { r ->
                    val index = r.pathString.indexOf(root)
                    if (r.isDirectory() || index < 0)
                        null
                    else {
                        val name = r.pathString.substring(index + root.length + 1)
                        if (name.isEmpty())
                            null
                        else {
                            ResourceHandle(name, r.toFile())
                        }
                    }
                }
            }
        }
    }
}