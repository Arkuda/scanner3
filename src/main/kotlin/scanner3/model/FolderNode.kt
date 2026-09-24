package scanner3.model

data class FolderNode(
    val path: String,
    val name: String,
    val depth: Int,
    val size: Long,
    val children: List<FolderNode>,
    val truncated: Boolean = false,
)
