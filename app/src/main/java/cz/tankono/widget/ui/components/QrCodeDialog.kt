package cz.tankono.widget.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import cz.tankono.widget.ui.OnoRed
import cz.tankono.widget.ui.OnoYellow

private const val GITHUB_URL = "https://github.com/birgis-cze/ONOupdate"

@Composable
fun QrCodeDialog(onDismiss: () -> Unit) {
    val qrBitmap = remember { generateQrCode(GITHUB_URL) }
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Naskenuj a sdílej",
                color = OnoRed,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Naskenuj tento QR kód telefonem.",
                    color = OnoRed.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "QR kód pro stažení aplikace",
                    modifier = Modifier
                        .size(240.dp)
                        .border(4.dp, OnoRed, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = GITHUB_URL,
                    color = OnoRed,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try { uriHandler.openUri(GITHUB_URL) } catch (_: Throwable) { }
                        }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Zavřít", color = OnoRed, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = OnoYellow
    )
}

private fun generateQrCode(text: String, size: Int = 512): ImageBitmap {
    val bitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap.asImageBitmap()
}