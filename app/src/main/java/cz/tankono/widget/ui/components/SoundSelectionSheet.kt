package cz.tankono.widget.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.tankono.widget.ui.OnoRed
import cz.tankono.widget.ui.OnoYellow
import cz.tankono.widget.util.AppLogger
import cz.tankono.widget.util.SoundHelper
import cz.tankono.widget.util.SoundItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundSelectionSheet(
    currentSound: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sounds by remember { mutableStateOf<List<SoundItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val list = SoundHelper.getAvailableSounds(context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                sounds = list
                isLoading = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { mediaPlayer?.release() } catch (_: Throwable) { }
            mediaPlayer = null
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OnoYellow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Zvuk při aktualizaci ceníku",
                color = OnoRed,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(OnoRed)
            )
            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                CircularProgressIndicator(
                    color = OnoRed,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sounds, key = { it.fileName }) { sound ->
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, OnoRed.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            try {
                                                try { mediaPlayer?.release() } catch (_: Throwable) { }
                                                mediaPlayer = null
                                                val mp = android.media.MediaPlayer()
                                                context.resources.openRawResourceFd(sound.resId).use { afd ->
                                                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                                    mp.prepare()
                                                    mp.start()
                                                }
                                                mp.setOnCompletionListener {
                                                    it.release()
                                                    if (mediaPlayer == it) mediaPlayer = null
                                                }
                                                mediaPlayer = mp
                                            } catch (t: Throwable) {
                                                AppLogger.e("Chyba při přehrávání zvuku ${sound.fileName}", t)
                                                Toast.makeText(context, "Tento zvuk nelze přehrát", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        sound.displayName,
                                        color = OnoRed,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "(${sound.durationSec}s)",
                                        color = OnoRed.copy(alpha = 0.7f),
                                        fontSize = 13.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .border(2.dp, OnoRed, RoundedCornerShape(4.dp))
                                        .background(
                                            if (currentSound == sound.fileName) OnoRed else Color.Transparent,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { onSelect(sound.fileName) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (currentSound == sound.fileName) {
                                        Text("✓", color = OnoYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}