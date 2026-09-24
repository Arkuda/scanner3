package scanner3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import scanner3.platform.DiskInfo
import scanner3.platform.OS
import scanner3.render.SunburstModel
import scanner3.scan.scan
import scanner3.ui.DiskPicker
import scanner3.ui.SunburstView
import scanner3.util.formatSize

private val AppBackground = Color(0xFF0D1117)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Scanner3",
        state = rememberWindowState(width = 1280.dp, height = 860.dp),
    ) {
        MaterialTheme(
            colors = darkColors(
                primary = Color(0xFF42C8FF),
                secondary = Color(0xFFD86BFF),
                background = AppBackground,
                surface = Color(0xFF121923),
            ),
        ) {
            val scope = rememberCoroutineScope()
            var disks by remember { mutableStateOf(OS.listDisks()) }
            var selected by remember { mutableStateOf<DiskInfo?>(null) }
            var model by remember { mutableStateOf<SunburstModel?>(null) }
            var scanning by remember { mutableStateOf(false) }
            var status by remember {
                mutableStateOf(if (disks.isEmpty()) "No disks found" else "Select a disk to scan")
            }

            suspend fun readDisk(disk: DiskInfo) = withContext(Dispatchers.IO) {
                scan(Path.of(disk.path)) { files, bytes ->
                    SwingUtilities.invokeLater {
                        status = "$files files  •  ${formatSize(bytes)}"
                    }
                }
            }

            fun startScan(disk: DiskInfo) {
                if (scanning) return
                selected = disk
                scanning = true
                status = "Scanning ${disk.label}…"

                scope.launch {
                    try {
                        val root = readDisk(disk)
                        model = SunburstModel.build(root)
                        status = "${formatSize(root.size)}  •  click any folder for actions"
                    } catch (exception: Exception) {
                        status = exception.message ?: "Scan failed"
                    } finally {
                        scanning = false
                    }
                }
            }

            fun mutateAndRescan(
                path: String,
                action: String,
                completed: String,
                operation: () -> Unit,
            ) {
                val currentDisk = selected ?: return
                if (scanning) return
                scanning = true
                status = "$action $path…"

                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { operation() }
                        val refreshedDisks = OS.listDisks()
                        val refreshedDisk = refreshedDisks.firstOrNull { it.path == currentDisk.path } ?: currentDisk
                        disks = refreshedDisks
                        selected = refreshedDisk
                        status = "$completed. Rescanning ${refreshedDisk.label}…"
                        val root = readDisk(refreshedDisk)
                        model = SunburstModel.build(root)
                        status = "${formatSize(root.size)}  •  $completed"
                    } catch (exception: Exception) {
                        status = exception.message ?: "$action failed"
                    } finally {
                        scanning = false
                    }
                }
            }

            fun deleteAndRescan(path: String) = mutateAndRescan(
                path = path,
                action = "Deleting",
                completed = "Folder deleted",
                operation = { OS.deleteRecursively(path) },
            )

            fun trashAndRescan(path: String) = mutateAndRescan(
                path = path,
                action = "Moving to Trash",
                completed = "Folder moved to Trash",
                operation = { OS.moveToTrash(path) },
            )

            Column(Modifier.fillMaxSize().background(AppBackground)) {
                DiskPicker(
                    disks = disks,
                    selected = selected,
                    scanning = scanning,
                    status = status,
                    onPick = ::startScan,
                    onRefresh = {
                        disks = OS.listDisks()
                        selected = null
                        model = null
                        status = if (disks.isEmpty()) "No disks found" else "Select a disk to scan"
                    },
                )

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val currentModel = model
                    if (currentModel == null) {
                        Text(
                            text = if (scanning) "Reading the filesystem…" else "Choose a disk to reveal its storage map",
                            color = Color(0xFF748196),
                            fontSize = 18.sp,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    } else {
                        SunburstView(
                            model = currentModel,
                            onOpen = OS::openFolder,
                            onTrash = ::trashAndRescan,
                            onDelete = ::deleteAndRescan,
                        )
                    }
                }
            }
        }
    }
}
