@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.winlator.star.ui.screens


import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.star.R
import com.winlator.star.container.Container
import com.winlator.star.container.ContainerManager
import com.winlator.star.container.DefaultContainerManager
import com.winlator.star.core.ImageUtils
import com.winlator.star.core.WineThemeManager
import com.winlator.star.ui.theme.AppThemeState
import com.winlator.star.ui.theme.OnSurface
import com.winlator.star.ui.theme.OnSurfaceVariant
import com.winlator.star.xenvironment.ImageFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Personalization screen (real, not cosmetic-only):
 *  - Wallpaper: applies to the REAL Windows environment (Wine desktop) via
 *    WineThemeManager + the default container's desktopTheme setting, so the
 *    choice survives restarts and is used by the actual engine.
 *  - Desktop theme: Light/Dark -> written into the Wine registry on next engine start.
 *  - Accent color: changes the WinNext interface theme (AppThemeState, persisted).
 * Only options whose underlying function is really implemented are shown.
 */
private data class BuiltinWallpaper(val resId: Int, val key: String)

private val builtinWallpapers = listOf(
    BuiltinWallpaper(R.drawable.wallpaper_winnext_default, "winnext_default"),
    BuiltinWallpaper(R.drawable.wallpaper_winnext_light,   "winnext_light"),
    BuiltinWallpaper(R.drawable.wallpaper_winnext_dark,    "winnext_dark"),
    BuiltinWallpaper(R.drawable.wallpaper_winnext_nature,  "winnext_nature"),
    BuiltinWallpaper(R.drawable.wallpaper_winnext_colorful,"winnext_colorful"),
)

/** Builds a desktopTheme string in the exact format WineThemeManager.ThemeInfo parses:
 *  "THEME,BGTYPE,#RRGGBB" (+ ",millis" for IMAGE backgrounds). */
private fun buildThemeString(theme: WineThemeManager.Theme, info: WineThemeManager.ThemeInfo,
                             wallpaperStamp: Long?): String {
    val color = String.format("#%06X", 0xFFFFFF and info.backgroundColor)
    return if (info.backgroundType == WineThemeManager.BackgroundType.IMAGE)
        "${theme},${info.backgroundType},$color,${wallpaperStamp ?: 0}"
    else
        "${theme},${info.backgroundType},$color"
}

@Composable
fun PersonalizationScreen() {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    // Default container (created on the desktop boot; may still be null on very first run)
    var container by remember { mutableStateOf<Container?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val manager = ContainerManager(context)
            container = DefaultContainerManager.findDefault(manager)
        }
    }

    // Parse current theme string: "LIGHT,IMAGE,#0277bd,<millis>"
    val themeInfo = remember(container) {
        try { WineThemeManager.ThemeInfo(container?.getDesktopTheme()
            ?: WineThemeManager.DEFAULT_DESKTOP_THEME) }
        catch (e: Exception) { WineThemeManager.ThemeInfo(WineThemeManager.DEFAULT_DESKTOP_THEME) }
    }
    var selectedTheme by remember(themeInfo) {
        mutableIntStateOf(if (themeInfo.theme == WineThemeManager.Theme.DARK) 1 else 0)
    }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val applyWallpaper: (Bitmap) -> Unit = { bitmap ->
        val c = container
        if (c == null) {
            statusMessage = context.getString(R.string.desktop_error_engine_missing)
        } else {
            scope.launch {
                withContext(Dispatchers.IO) {
                    // Save as the user wallpaper — WineThemeManager picks this file up
                    // and installs it into the Wine desktop on the next engine start.
                    val file = WineThemeManager.getUserWallpaperFile(context)
                    ImageUtils.save(bitmap, file, Bitmap.CompressFormat.PNG, 95)
                    val theme = if (selectedTheme == 1) WineThemeManager.Theme.DARK
                                else WineThemeManager.Theme.LIGHT
                    c.setDesktopTheme(buildThemeString(theme, themeInfo, file.lastModified()))
                    c.putExtra("desktopTheme", null) // force re-apply on next start
                    c.saveData()
                }
                statusMessage = context.getString(R.string.personalization_wallpaper_applied)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            val bitmap = ImageUtils.getBitmapFromUri(context, uri, 1920)
            if (bitmap != null) applyWallpaper(bitmap)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(stringResource(R.string.screen_personalization),
             style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        Text(stringResource(R.string.personalization_note),
             fontSize = 13.sp, color = OnSurfaceVariant)

        // ── Wallpaper ────────────────────────────────────────────────────────
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.personalization_wallpaper),
                     fontWeight = FontWeight.SemiBold, color = OnSurface)
                Text(stringResource(R.string.personalization_wallpaper_builtin),
                     fontSize = 13.sp, color = OnSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    builtinWallpapers.forEach { wp ->
                        val bitmap = remember(wp.resId) {
                            BitmapFactory.decodeResource(context.resources, wp.resId)
                        }
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = wp.key,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(84.dp, 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, OnSurfaceVariant, RoundedCornerShape(8.dp))
                                .clickable { applyWallpaper(bitmap) },
                        )
                    }
                }
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    galleryLauncher.launch(Intent.createChooser(intent,
                        context.getString(R.string.personalization_pick_image)))
                }) { Text(stringResource(R.string.personalization_wallpaper_gallery)) }
            }
        }

        // ── Desktop theme ────────────────────────────────────────────────────
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.personalization_theme),
                     fontWeight = FontWeight.SemiBold, color = OnSurface)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTheme == 0,
                        onClick = {
                            selectedTheme = 0
                            val c = container ?: return@SegmentedButton
                            c.setDesktopTheme(buildThemeString(WineThemeManager.Theme.LIGHT, themeInfo, null))
                            c.putExtra("desktopTheme", null)
                            c.saveData()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(stringResource(R.string.theme_light)) }
                    SegmentedButton(
                        selected = selectedTheme == 1,
                        onClick = {
                            selectedTheme = 1
                            val c = container ?: return@SegmentedButton
                            c.setDesktopTheme(buildThemeString(WineThemeManager.Theme.DARK, themeInfo, null))
                            c.putExtra("desktopTheme", null)
                            c.saveData()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text(stringResource(R.string.theme_dark)) }
                }
            }
        }

        // ── Accent color for the WinNext interface ───────────────────────────
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.personalization_accent),
                     fontWeight = FontWeight.SemiBold, color = OnSurface)
                val currentAccent by AppThemeState.customAccent.collectAsState()
                val accents = listOf(
                    Color(0xFF2B8CFF), Color(0xFF8B6BE0), Color(0xFF00A884),
                    Color(0xFFE25555), Color(0xFFE8A33D), Color(0xFF5A9BD8))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    accents.forEach { color ->
                        val selected = currentAccent == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(color)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) Color.White else OnSurfaceVariant,
                                    shape = RoundedCornerShape(18.dp))
                                .clickable { AppThemeState.setCustomAccent(color) }
                        )
                    }
                }
            }
        }

        // ── Reset ────────────────────────────────────────────────────────────
        var showResetConfirm by remember { mutableStateOf(false) }
        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text(stringResource(R.string.personalization_reset)) },
                text = { Text(stringResource(R.string.personalization_note)) },
                confirmButton = {
                    TextButton(onClick = {
                        showResetConfirm = false
                        val c = container
                        if (c != null) {
                            c.setDesktopTheme(WineThemeManager.DEFAULT_DESKTOP_THEME)
                            c.putExtra("desktopTheme", null)
                            c.saveData()
                            val f = WineThemeManager.getUserWallpaperFile(context)
                            if (f.isFile) f.delete()
                        }
                        selectedTheme = 0
                        AppThemeState.setPreset(1)
                        statusMessage = context.getString(R.string.personalization_reset_done)
                    }) { Text(stringResource(R.string.personalization_reset)) }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false })
                    { Text(stringResource(R.string.cancel)) }
                },
            )
        }
        TextButton(onClick = { showResetConfirm = true })
        { Text(stringResource(R.string.personalization_reset)) }

        statusMessage?.let {
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
    }
}
