package scanner3.platform

import java.io.File
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

data class DiskInfo(
    val path: String,
    val label: String,
    val usedBytes: Long,
    val totalBytes: Long,
)

object OS {
    fun name(): String {
        val value = System.getProperty("os.name", "unknown").lowercase()
        return when {
            "mac" in value -> "macos"
            "win" in value -> "windows"
            else -> "linux"
        }
    }

    private fun diskInfo(file: File, label: String): DiskInfo {
        val total = file.totalSpace.coerceAtLeast(0L)
        val free = file.freeSpace.coerceIn(0L, total)
        return DiskInfo(
            path = file.absolutePath,
            label = label,
            usedBytes = total - free,
            totalBytes = total,
        )
    }

    fun listDisks(): List<DiskInfo> = when (name()) {
        "macos" -> File("/Volumes").listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?.map { diskInfo(it, it.name) }
            ?.toList()
            .orEmpty()
            .ifEmpty { listOf(diskInfo(File("/"), "Macintosh HD")) }

        "windows" -> File.listRoots()
            .filter { it.isDirectory }
            .map { root ->
                val label = runCatching { root.canonicalPath.removeSuffix("\\") }.getOrDefault(root.path)
                diskInfo(root, label)
            }

        else -> listOf(diskInfo(File("/"), "Root"))
    }

    fun openFolder(path: String) {
        when (name()) {
            "macos" -> ProcessBuilder("open", path).start()
            "windows" -> ProcessBuilder("explorer.exe", path).start()
            else -> ProcessBuilder("xdg-open", path).start()
        }
    }

    fun moveToTrash(path: String) {
        val target = Path.of(path).toAbsolutePath().normalize()
        require(target.parent != null) { "Moving a disk root to trash is not allowed" }
        if (!Files.exists(target)) return
        check(Desktop.isDesktopSupported()) { "The system trash is not available" }

        val desktop = Desktop.getDesktop()
        check(desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) { "The system trash is not available" }
        check(desktop.moveToTrash(target.toFile())) { "The operating system refused to move this folder to trash" }
    }

    fun deleteRecursively(path: String) {
        val target = Path.of(path).toAbsolutePath().normalize()
        require(target.parent != null) { "Deleting a disk root is not allowed" }
        if (!Files.exists(target)) return

        Files.walk(target).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }
}
