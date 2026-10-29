package com.winlator.star.ui.screens

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun FileManagerScreen() {
    val home = remember { Environment.getExternalStorageDirectory() }
    var current by remember { mutableStateOf(home) }
    var refresh by remember { mutableStateOf(0) }

    val entries = remember(current, refresh) {
        current.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }
                .thenBy { it.name.lowercase() })
            ?: emptyList()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("This PC", style = MaterialTheme.typography.headlineSmall)
        Text(
            current.absolutePath,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "⬆ Up",
                modifier = Modifier.clickable {
                    current.parentFile?.let { current = it }
                }.padding(vertical = 8.dp)
            )
            Text(
                "↻ Refresh",
                modifier = Modifier.clickable { refresh++ }
                    .padding(vertical = 8.dp)
            )
            Text(
                "Home",
                modifier = Modifier.clickable { current = home }
                    .padding(vertical = 8.dp)
            )
        }

        if (entries.isEmpty()) {
            Text(
                "Folder is empty or access is denied.",
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.absolutePath }) { file ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (file.isDirectory) current = file
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(
                            (if (file.isDirectory) "📁  " else "📄  ") + file.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
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
