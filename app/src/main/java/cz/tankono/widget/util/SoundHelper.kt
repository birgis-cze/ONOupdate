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

    /**
     * Projde složku res/raw a vrátí seznam všech zvuků s jejich délkou.
     * Seřazeno abecedně.
     */
    fun getAvailableSounds(context: Context): List<SoundItem> {
        val sounds = mutableListOf<SoundItem>()
        
        // Získáme všechna ID z R.raw pomocí reflexe
        val fields = R.raw::class.java.fields
        for (field in fields) {
            if (field.type == Int::class.java) {
                try {
                    val resId = field.getInt(null)
                    val rawName = field.name
                    
                    // Zjištění délky zvuku
                    var duration = 0
                    try {
                        val mp = MediaPlayer.create(context, resId)
                        if (mp != null) {
                            duration = mp.duration / 1000 // převod na sekundy
                            mp.release()
                        }
                    } catch (_: Throwable) { }

                    // Formátování názvu: "Audi.welcome" -> "Audi welcome"
                    val displayName = rawName.replace(".", " ").replace("_", " ")
                        .split(" ")
                        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

                    sounds.add(SoundItem(resId, rawName, displayName, duration))
                } catch (_: Throwable) { }
            }
        }
        
        // Seřadíme podle názvu
        return sounds.sortedBy { it.displayName }
    }
}