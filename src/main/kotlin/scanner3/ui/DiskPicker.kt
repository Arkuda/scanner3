package scanner3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import scanner3.platform.DiskInfo
import scanner3.util.formatSize

@Composable
fun DiskPicker(
    disks: List<DiskInfo>,
    selected: DiskInfo?,
    scanning: Boolean,
    status: String,
    onPick: (DiskInfo) -> Unit,
    onRefresh: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF121923)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            Button(onClick = { expanded = true }, enabled = !scanning && disks.isNotEmpty()) {
                Text(selected?.label ?: "Choose a disk")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                disks.forEach { disk ->
                    DropdownMenuItem(onClick = {
                        expanded = false
                        onPick(disk)
                    }) {
                        Text("${disk.label}   ${formatSize(disk.usedBytes)} / ${formatSize(disk.totalBytes)}")
                    }
                }
            }
        }

        selected?.let { disk ->
            Text(
                "Used ${formatSize(disk.usedBytes)}  •  Total ${formatSize(disk.totalBytes)}",
                color = Color(0xFF73D7FF),
            )
        }
        if (scanning) LinearProgressIndicator(Modifier.width(150.dp))
        Text(status, color = Color(0xFFA9B6C8), modifier = Modifier.weight(1f))
        Button(onClick = onRefresh, enabled = !scanning) { Text("Refresh") }
    }
}
