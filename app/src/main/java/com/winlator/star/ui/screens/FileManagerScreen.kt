package com.winlator.star.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.winlator.star.R
import com.winlator.star.container.Container
import com.winlator.star.container.ContainerManager
import com.winlator.star.container.DefaultContainerManager
import com.winlator.star.core.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "This PC" file browser. Two real sources are supported:
 *  - Android storage (public external storage)
 *  - Drive C of the default container (the real Wine prefix), so users can browse
 *    the files of their Windows environment.
 *
 * Windows executables (.exe/.msi) are NOT handed to Android — the app shows an honest
 * notice instead. Deletion always asks for confirmation.
 */
@Composable
fun FileManagerScreen() {
    val context = LocalContext.current
    var source by remember { mutableIntStateOf(0) } // 0 = Android, 1 = Drive C
    var container by remember { mutableStateOf<Container?>(null) }
    var containerMissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            container = DefaultContainerManager.findDefault(ContainerManager(context))
            containerMissing = container == null
        }
    }

    val androidHome = remember { android.os.Environment.getExternalStorageDirectory() }
    val driveC = remember(container) {
        container?.getRootDir()?.let { File(it, ".wine/drive_c") }
    }

    val start: File? = when (source) {
        0 -> androidHome
        else -> driveC?.takeIf { it.isDirectory }
    }

    var current by remember(source) { mutableStateOf(start) }
    var refresh by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }

    val entries = remember(current, refresh) {
        current?.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }
                .thenBy { it.name.lowercase() })
            ?.toList()
            ?: emptyList()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(stringResource(R.string.fm_this_pc), style = MaterialTheme.typography.headlineSmall)
        Text(
            current?.absolutePath ?: stringResource(R.string.fm_empty),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
        )

        // Source switcher
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = source == 0,
                onClick = { source = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.fm_source_android)) }
            SegmentedButton(
                selected = source == 1,
                onClick = {
                    if (driveC?.isDirectory == true) source = 1
                    else Toast.makeText(context,
                        if (containerMissing) context.getString(R.string.desktop_error_engine_missing)
                        else context.getString(R.string.desktop_error_create_failed),
                        Toast.LENGTH_LONG).show()
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.fm_source_container)) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.fm_up),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    val parent = current?.parentFile
                    // Do not leave the chosen root (matches the old behavior for /storage).
                    val root = if (source == 0) androidHome else driveC
                    if (parent != null && root != null && parent.absolutePath.startsWith(root.absolutePath)) {
                        current = parent
                    }
                }.padding(vertical = 8.dp)
            )
            Text(
                stringResource(R.string.fm_refresh),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { refresh++ }
                    .padding(vertical = 8.dp)
            )
            Text(
                stringResource(R.string.fm_home),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { current = start }
                    .padding(vertical = 8.dp)
            )
        }

        when {
            source == 1 && containerMissing -> {
                Text(stringResource(R.string.desktop_error_engine_missing),
                    modifier = Modifier.padding(top = 16.dp))
            }
            entries.isEmpty() -> {
                Text(stringResource(R.string.fm_empty),
                    modifier = Modifier.padding(top = 16.dp))
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries, key = { it.absolutePath }) { file ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (file.isDirectory) current = file
                                    else if (file.name.lowercase().endsWith(".exe")
                                          || file.name.lowercase().endsWith(".msi")) {
                                        // Honest behavior: never hand Windows binaries to Android.
                                        Toast.makeText(context,
                                            context.getString(R.string.fm_exe_hint),
                                            Toast.LENGTH_LONG).show()
                                    }
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Row {
                                Text(
                                    (if (file.isDirectory) "📁  " else "📄  ") + file.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!file.isDirectory) {
                                    Text(
                                        "🗑",
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .clickable { deleteTarget = file }
                                            .padding(horizontal = 8.dp)
                                    )
                                }
                            }
                            if (!file.isDirectory) {
                                Text(
                                    "${file.length()} bytes",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.fm_delete)) },
            text = { Text(stringResource(R.string.fm_delete_confirm, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    FileUtils.delete(target)
                    deleteTarget = null
                    refresh++
                }) { Text(stringResource(R.string.fm_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null })
                { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
