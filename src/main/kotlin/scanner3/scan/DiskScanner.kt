package scanner3.scan

import java.nio.file.Files
import java.nio.file.Path
import scanner3.model.FolderNode

fun scan(
    root: Path,
    maxNodes: Int = 100_000,
    onProgress: (files: Long, bytes: Long) -> Unit = { _, _ -> },
): FolderNode {
    require(maxNodes > 0) { "maxNodes must be positive" }

    var files = 0L
    var bytes = 0L
    var nodes = 0
    var entriesSeen = 0L

    fun report(force: Boolean = false) {
        if (force || entriesSeen % 1_024L == 0L) onProgress(files, bytes)
    }

    fun walk(directory: Path, depth: Int): FolderNode {
        nodes++
        var ownBytes = 0L
        var truncated = false
        val children = mutableListOf<FolderNode>()

        try {
            Files.newDirectoryStream(directory).use { entries ->
                for (entry in entries) {
                    entriesSeen++
                    report()

                    try {
                        when {
                            Files.isSymbolicLink(entry) -> Unit
                            Files.isDirectory(entry) -> {
                                if (nodes >= maxNodes) {
                                    truncated = true
                                    continue
                                }
                                val child = walk(entry, depth + 1)
                                if (child.size > 0L || child.truncated) children += child
                            }
                            Files.isRegularFile(entry) -> {
                                val size = Files.size(entry)
                                ownBytes += size
                                bytes += size
                                files++
                            }
                        }
                    } catch (_: Exception) {
                        // Files can disappear or become unreadable while a disk is scanned.
                    }
                }
            }
        } catch (_: Exception) {
            truncated = true
        }

        children.sortWith(compareByDescending<FolderNode> { it.size }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val name = directory.fileName?.toString()?.takeIf { it.isNotBlank() } ?: directory.toString()
        return FolderNode(
            path = directory.toAbsolutePath().normalize().toString(),
            name = name,
            depth = depth,
            size = ownBytes + children.sumOf { it.size },
            children = children,
            truncated = truncated || children.any { it.truncated },
        )
    }

    val result = walk(root.toAbsolutePath().normalize(), 0)
    report(force = true)
    return result
}
