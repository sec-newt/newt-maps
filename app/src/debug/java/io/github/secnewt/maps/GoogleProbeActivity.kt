package io.github.secnewt.maps

import android.app.Activity
import android.os.Bundle
import android.util.Log
import io.github.secnewt.maps.google.GoogleLookup

/** Debug builds only: `am start -n …/.GoogleProbeActivity -d <link>` logs what GoogleLookup found. */
class GoogleProbeActivity : Activity() {
    private lateinit var lookup: GoogleLookup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lookup = GoogleLookup(this)
        val link = intent.dataString ?: return finish()
        lookup.start(link) { place ->
            Log.i("NewtGoogleProbe", if (place == null) "RESULT none" else "RESULT ${place.lat},${place.lng}")
            finish()
        }
    }

    override fun onDestroy() { lookup.cancel(); super.onDestroy() }
}
