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
- **Pick the map app each time** with big buttons: Organic Maps, Magic Earth, CoMaps.
  Only apps that are actually installed are shown. The picker is temporary: it goes away
  once Newt Maps has its own map.
- **Low vision first:** the place name and coordinates in big type, big buttons, 7:1
  contrast, works at 200% text.
- **Private by default.** The only network request is unshortening a Google short link,
  sent only to Google's own hosts. The Google page itself is never downloaded.

## What it accepts

| Link | Example | Needs the network? |
|---|---|---|
| Full Google link | `https://www.google.com/maps/place/…/@32.91,-96.63,17z/data=…!3d32.91!4d-96.63` | No |
| Google search link | `https://www.google.com/maps/search/?api=1&query=32.91,-96.63` or `query=Whataburger` | No |
| Old Google link | `https://maps.google.com/?q=32.91,-96.63` | No |
| Google short link | `https://maps.app.goo.gl/AbCd…`, `https://goo.gl/maps/…` | **Yes**, follows the redirects only |
| Apple link | `https://maps.apple.com/?ll=32.91,-96.63&q=Name` | No |
| `geo:` link | `geo:32.91,-96.63?q=32.91,-96.63(Name)` | No |
| Shared text | Any text containing one of the links above (Share → Newt Maps) | Depends on the link |

**Reading order for Google links**, most exact first: `!3d…!4d…` (the pin itself), then
`@lat,lng` (the map's centre), then a `q=` or `query=` value that is coordinates, then a
place name from `/place/<name>/` or `q=` text.

**Opening links by default.** Android only verifies link ownership for the site's own
apps, so a third-party app can't take google.com links on its own. On first launch the
app shows a big **Turn on link opening** button. It opens the app's "Open by default"
settings page (`Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS`), where I add the links
once. The card hides once every host is turned on (`DomainVerificationManager`).

## How it works

Three small pieces, each testable on its own:

1. **`LinkReader`** (plain Kotlin, no Android). Text in, one of three results out:
   - `Place(lat, lng, name?)`: coordinates, plus a name if the link has one
   - `Search(text)`: the link has only a name, e.g. `q=Whataburger`
   - `Unreadable`
   It also answers "is this a short link?" so the next piece knows to step in. Coordinates
   are checked: latitude −90…90, longitude −180…180, otherwise `Unreadable`.
2. **`ShortLinkResolver`** follows redirects by hand (redirect following off), reading
   only the `Location` header. Up to 5 hops, `https` only, and every hop must be on a
   Google host (`maps.app.goo.gl`, `goo.gl`, `google.com`, `www.google.com`,
   `maps.google.com`). Anything else stops it. It never reads the response body. The
   final address goes back through `LinkReader`. 10-second timeout.
3. **`MapApps`** lists which of the target apps are installed, and builds the handoff: a
   `geo:lat,lng?q=lat,lng(name)` intent (or `geo:0,0?q=<text>` for a search) aimed at
   that app's package.

| App | Package |
|---|---|
| Organic Maps | `app.organicmaps` |
| CoMaps | `app.comaps` *(verify the exact id)* |
| Magic Earth | `com.generalmagic.magicearth` |

The manifest declares those packages under `<queries>` so Android 11+ lets the app see
them. It needs the `INTERNET` permission, and nothing else.

## The screens

- **Picker** (opened from a link): place name (or "Dropped pin"), the coordinates under
  it, then one full-width button per installed map app, then **Copy coordinates** and
  **Open in browser**. While a short link resolves: a "Reading link…" line.
- **Can't read it:** a plain message saying why ("No location in this link", "No
  internet", "Google didn't answer"), plus **Open in browser** so there's always a way out.
- **No map app installed:** says so, and still offers Copy and Open in browser.
- **Home** (opened from the launcher): the **Turn on link opening** card while it's
  needed, and a short "how to use this" line.

## Testing

- **`LinkReader`, test first**, against a list of real links (each link type above, plus
  the messy ones: links with no coordinates, out-of-range numbers, junk text around the
  link). Jerry supplies real links from his Claude chats.
- **`ShortLinkResolver`** against a fake HTTP server: a good redirect chain, a non-Google
  hop (must stop), too many hops, a timeout, and an `http` hop (must stop).
- **Screenshots** of every screen in light, dark and 200% text (`newtScreenshot()`).
- **On the emulator:** each handoff lands in the map app at the right place, at least
  with Organic Maps.

## Not in Stage 1

- Text search ("Whataburger on Garland Rd"). That waits for search on zeus.
- Remembering a default map app. The picker is meant to be replaced, not polished.
- Its own map, routing, or anything offline.
