package com.lostsierra.chorequest.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import com.lostsierra.chorequest.data.local.GamePreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gamePreferencesManager: GamePreferencesManager
) {
    private var toneGenerator: ToneGenerator? = null

    enum class SoundType {
        CLICK,
        WIN,
        LOSE,
        DRAW,
        MOVE
    }

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            // Silently fail if tone generator can't be created
            toneGenerator = null
        }
    }

    fun playSound(type: SoundType) {
        if (!gamePreferencesManager.isSoundEnabled() || toneGenerator == null) {
            return
        }

        try {
            when (type) {
                SoundType.CLICK -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
                }
                SoundType.WIN -> {
                    // Play a victory sequence
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 100)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 100)
                    }, 150)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                    }, 300)
                }
                SoundType.LOSE -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                }
                SoundType.DRAW -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                }
                SoundType.MOVE -> {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
                }
            }
        } catch (e: Exception) {
            // Silently fail if sound can't be played
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
