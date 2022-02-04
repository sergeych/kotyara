package net.sergeych.tools

import net.sergeych.tools.ResourceHandle.Companion.list
import java.io.FileNotFoundException
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.readText

/**
 * Handler to retrieve stored resource in a classpath, also in JAR. Use [list] to get a list if handles.
 * Each handle as a [name] and some accessors to get the contents. Hadnles provdie lazy loading and
 * caching of the respective resources.
 */
class ResourceHandle(val name: String, val text: String) {

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
        fun list(klass: Class<*>, root: String): List<ResourceHandle> {

            fun walk(myPath: Path): List<ResourceHandle> =
                Files.walk(myPath, 1).use {
                    // important. Stream.toList often produces very strange exceptions (like it is missing on the
                    // platform where it must exists) so ugly but...
//                    it.toList().mapNotNull { r: Path ->
                    val all = mutableListOf<Path>()
                    for( x in it) all.add(x)
                    all.mapNotNull { r: Path ->
                        val index = r.pathString.indexOf(root)
                        if (r.isDirectory() || index < 0)
                            null
                        else {
                            val name = r.pathString.substring(index + root.length + 1)
                            if (name.isEmpty())
                                null
                            else {
                                ResourceHandle(name, r.readText())
                            }
                        }
                    }
                }

            val uri: URI = klass.getResource(root)?.toURI()
                ?: throw FileNotFoundException("resource not found: $root")
            return if (uri.getScheme().equals("jar"))
                FileSystems.newFileSystem(uri, emptyMap<String, Any>()).use {
                    walk(it.getPath(root))
                }
            else
                walk(Paths.get(uri))
        }
    }
}

