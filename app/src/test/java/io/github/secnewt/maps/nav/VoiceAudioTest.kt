package io.github.secnewt.maps.nav

import android.media.AudioAttributes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class VoiceAudioTest {

    // Spoken as music, the turns were turned down by the app's own request to quiet other
    // audio while speaking (badly so over car Bluetooth). As navigation speech they aren't.
    @Test fun turnsAreSpokenAsNavigationSpeech() {
        val attributes = VoiceAudio.attributes()
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, attributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, attributes.contentType)
    }
}
