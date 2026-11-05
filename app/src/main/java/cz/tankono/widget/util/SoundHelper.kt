package cz.tankono.widget.util

import android.content.Context
import android.media.MediaPlayer
import cz.tankono.widget.R

data class SoundItem(
    val resId: Int,
    val fileName: String,
    val displayName: String,
    val durationSec: Int
)

object SoundHelper {

    fun getAvailableSounds(context: Context): List<SoundItem> {
        val sounds = mutableListOf<SoundItem>()
        val fields = R.raw::class.java.fields
        
        for (field in fields) {
            if (field.type == Int::class.java) {
                try {
                    val resId = field.getInt(null)
                    val rawName = field.name
                    
                    if (rawName == "keep") {
                        continue
                    }

                    // Vždy nastavíme výchozí délku, aby se soubor zobrazil
                    var duration = 1 
                    try {
                        val mp = MediaPlayer.create(context, resId)
                        if (mp != null) {
                            val realDuration = mp.duration / 1000
                            if (realDuration > 0) duration = realDuration
                            mp.release()
                        }
                    } catch (_: Throwable) { }

                    val displayName = rawName.replace(".", " ").replace("_", " ")
                        .split(" ")
                        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

                    sounds.add(SoundItem(resId, rawName, displayName, duration))
                } catch (_: Throwable) { }
            }
        }
        return sounds.sortedBy { it.displayName }
    }
}