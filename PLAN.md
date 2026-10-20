# Newt Maps Plan

A private Android app for GrapheneOS, part of the Newt family. It follows the shared
[Newt house style](NEWT_STYLE.md).

**Stage 1 (this plan): a link opener.** Google Maps and Apple Maps links go in, and an
exact location goes out to a map app I choose. It replaces GeoShare. Later stages grow
this same app into a real maps app (offline maps, spoken street names, stops, OSM editing,
search on zeus). The bigger picture is in the vault note `personal/AI/newt-maps-idea.md`.
It is called Newt Maps from day one so the real app arrives as an update, not a second
install.

## Goals

- **Tap a maps link, land in the map app.** No GeoShare, no copy and paste.
- **Pick the map app each time** with big buttons, one per installed map app (Organic
  Maps, CoMaps, Magic Earth, OsmAnd…). The picker is temporary: it goes away once Newt
  Maps has its own map.
- **Low vision first:** the place name and coordinates in big type, big buttons, 7:1
  contrast, works at 200% text.
- **Private by default.** Nothing goes to Google unless I tap **Ask Google**. Short links
  only ever contact the link's own company (Google or Apple), and only to follow the
  redirect.

## Most links have no location in them

The Google links Claude gives me are **text only**:
`google.com/maps/search/?api=1&query=The Catch 5949 Broadway Blvd Garland TX` or
`maps.google.com/?q=4402 Broadway Blvd`. There are no coordinates to read, so the app has
to look the text up. Apple links are the opposite: even the short `maps.apple/p/…` kind
redirects to an address with exact coordinates and the place name.

So a link goes through up to four steps, stopping as soon as it has a place:

1. **Read it.** Coordinates in the link → done, no network.
2. **Short link** (Google or Apple) → follow the redirects → back to step 1, or on to
   step 3 if the real address is text only.
3. **Text only** → look it up with **Nominatim** (OpenStreetMap's search server; only the
   search text is sent). If nothing comes back, try again with the business name dropped,
   starting from the house number ("The Catch 5949 Broadway Blvd…" → "5949 Broadway
   Blvd…"). The place name from the link is kept for the picker.
4. **Still nothing** → "Couldn't find it", with **Ask Google** and **Open in browser**.

**Tested 2026-10-19 on my real links:** Nominatim found 4 of 5 as-is and the 5th once the
business name was dropped. House-number results were 55–140 m from the county's address
point, on the right block. Later, step 3 switches to search on zeus with the exact county
points.

## What it accepts

| Link | Example | Network |
|---|---|---|
| Full Google link | `https://www.google.com/maps/place/…/@32.91,-96.63,17z/data=…!3d32.91!4d-96.63` | None |
| Google search link | `https://www.google.com/maps/search/?api=1&query=…` | Nominatim if text only |
| Old Google link | `https://maps.google.com/?q=…` | Nominatim if text only |
| Google short link | `https://maps.app.goo.gl/AbCd…`, `https://goo.gl/maps/…` | Redirects, then as above |
| Apple link | `https://maps.apple.com/place?coordinate=32.75,-96.68&name=…` and the older `?ll=…&q=…` | None |
| Apple short link | `https://maps.apple/p/w~VGC-HHBheq~7` | Redirects, then as above |
| `geo:` link | `geo:32.91,-96.63?q=32.91,-96.63(Name)` | None |
| Shared text | Any text containing one of the links above (Share → Newt Maps) | Depends on the link |

**Reading order for Google links**, most exact first: `!3d…!4d…` (the pin itself), then
`@lat,lng` (the map's centre), then a `q=` or `query=` value that is coordinates, then the
text of `q=`, `query=` or `/place/<name>/` for step 3.

**Reading Apple links:** `coordinate=lat,lng` or `ll=lat,lng` for the location; `name=` or
`q=` for the name; `address=` shown under the name.

**Opening links by default.** Android only verifies link ownership for the site's own
apps, so a third-party app can't take google.com or apple.com links on its own. On first
launch the app shows a big **Turn on link opening** button. It opens the app's "Open by
default" settings page (`Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS`), where I add the
links once. The card hides once every host is turned on (`DomainVerificationManager`).

## How it works

Small pieces, each testable on its own:

1. **`LinkReader`** (plain Kotlin, no Android). Text in, one result out:
   - `Place(lat, lng, name?, address?)`
   - `Search(text, name?)`: no coordinates, only text to look up
   - `Short(url)`: a short link that needs resolving
   - `Unreadable`
   Coordinates are checked: latitude −90…90, longitude −180…180, otherwise `Unreadable`.
2. **`ShortLinkResolver`** follows redirects by hand (redirect following off), reading
   only the `Location` header. Up to 5 hops, `https` only, and every hop must stay with
   the link's company: Google (`maps.app.goo.gl`, `goo.gl`, `google.com`,
   `www.google.com`, `maps.google.com`) or Apple (`maps.apple`, `maps.apple.com`).
   Anything else stops it. It never reads the response body. 10-second timeout.
3. **`Geocoder`**: the step 3 lookup. An interface, so Nominatim can later be swapped for
   zeus. The Nominatim version sends `q`, `format=jsonv2`, `limit=1`, `countrycodes=us`,
   with a User-Agent naming the app (Nominatim's usage policy), at most one request per
   second, and no other data. It includes the retry without the business name.
4. **`GoogleLookup`**: the **Ask Google** button. It loads the link in a hidden `WebView`
   (no saved cookies, cleared afterwards), waits for Google's page to rewrite its own
   address into the `…/@lat,lng` or `!3d…!4d…` form, reads the coordinates from that
   address, and closes. It gives up after 15 seconds. **To prove first:** a plain fetch of
   Google's page has no place coordinates in it (tested 2026-10-19: the only coordinates
   in the page were Google's guess at *my* location from my IP), so this only works if the
   address rewrite really happens in a `WebView`. Prove it on the emulator before building
   the screens around it. If it doesn't work, drop the button and keep **Open in browser**.
5. **`MapApps`** asks Android which installed apps open `geo:` links (`<queries>` for the
   `geo` scheme), so whatever build is installed shows up (`app.organicmaps`,
   `app.comaps` / `app.comaps.fdroid`, `com.generalmagic.magicearth`, OsmAnd). Known map
   apps are listed first; Newt Maps leaves itself out. The handoff is a
   `geo:lat,lng?q=lat,lng(name)` intent aimed at the chosen app.

Permissions: `INTERNET`, and nothing else.

## The screens

- **Picker** (opened from a link): place name (or "Dropped pin"), the address if known,
  the coordinates, then one full-width button per map app, then **Copy coordinates** and
  **Open in browser**. While a link is being resolved or looked up: "Reading link…" or
  "Looking up the address…".
- **Couldn't find it:** says why in plain words ("No location in this link", "No
  internet", "Nothing found for this address"), then **Ask Google** (says it sends the
  link to Google) and **Open in browser**, so there's always a way out.
- **No map app installed:** says so, and still offers Copy and Open in browser.
- **Home** (opened from the launcher): the **Turn on link opening** card while it's
  needed, and a short "how to use this" line.

## Testing

- **`LinkReader`, test first**, against my real links (the list below), plus every link
  type above and the messy ones: links with no coordinates, out-of-range numbers, junk text
  around the link.
- **`ShortLinkResolver`** against a fake HTTP server: a good redirect chain, a hop to
  another company (must stop), too many hops, a timeout, and an `http` hop (must stop).
- **`Geocoder`** against a fake server: a hit, a miss then a hit on the retry, two misses,
  no internet. The retry's "drop the business name" rule gets its own unit tests.
- **`GoogleLookup`** proven by hand on the emulator first (see above).
- **Screenshots** of every screen in light, dark and 200% text (`newtScreenshot()`).
- **On the emulator:** each handoff lands in the map app at the right place, at least
  with Organic Maps.

**Real links** (from my texts and Claude chats, 2026-10-19):

```
https://maps.apple/p/w~VGC-HHBheq~7
  → maps.apple.com/place?address=8121 Bruton Rd, Dallas, TX 75217…&coordinate=32.749302,-96.681877&name=Gonzalez Restaurants
https://www.google.com/maps/search/?api=1&query=The%20Catch%205949%20Broadway%20Blvd%20Garland%20TX&query_place_id=ChIJ8TWioYqnToYR03QYbJ8D4QE
https://www.google.com/maps/search/?api=1&query=Dickies+Arena+1911+Montgomery+St+Fort+Worth+TX+76107
https://maps.google.com/?q=Clements+University+Hospital+Parking+Garage+B,+6201+Harry+Hines+Blvd,+Dallas,+TX+75390
https://maps.google.com/?q=4402+Broadway+Blvd
```

## Not in Stage 1

- Typing a search into the app ("Whataburger on Garland Rd"). That waits for search on zeus.
- Remembering a default map app. The picker is meant to be replaced, not polished.
- Its own map, routing, or anything offline.
