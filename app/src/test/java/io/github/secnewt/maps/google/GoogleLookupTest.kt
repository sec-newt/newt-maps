package io.github.secnewt.maps.google

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleLookupTest {

    @Test fun googleOwnLinksAreAsked() {
        assertTrue(GoogleLookup.isGoogleLink("https://maps.app.goo.gl/AbCdEf123"))
        assertTrue(GoogleLookup.isGoogleLink("https://goo.gl/maps/AbCdEf123"))
        assertTrue(GoogleLookup.isGoogleLink("https://www.google.com/maps/place/Example+Park"))
        assertTrue(GoogleLookup.isGoogleLink("https://maps.google.com/?q=Example+Park"))
        assertTrue(GoogleLookup.isGoogleLink("HTTPS://Maps.Google.COM/?q=Example"))
    }

    @Test fun lookAlikesAreNot() {
        assertFalse(GoogleLookup.isGoogleLink("https://notgoogle.com/maps"))
        assertFalse(GoogleLookup.isGoogleLink("https://evilgoo.gl/x"))
        assertFalse(GoogleLookup.isGoogleLink("https://sites.google.com/view/example"))
        assertFalse(GoogleLookup.isGoogleLink("https://google.com.example.net/maps"))
        assertFalse(GoogleLookup.isGoogleLink("https://google.com@example.net/maps"))
        assertFalse(GoogleLookup.isGoogleLink("javascript://google.com/%0Aalert(1)"))
        assertFalse(GoogleLookup.isGoogleLink("file:///google.com/maps"))
        assertFalse(GoogleLookup.isGoogleLink("geo:0,0?q=Example"))
        assertFalse(GoogleLookup.isGoogleLink("not a link"))
    }
}
