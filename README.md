# Newt Maps

A private Android app, part of the Newt family (built from
[newt-android-template](https://github.com/sec-newt/newt-android-template)).
It follows the [house style](NEWT_STYLE.md).

> **A personal project, in early development.**

## Install

Releases are built and signed by GitHub Actions. Install and update with
Obtainium from this repository's Releases page.

## What it does

Tap a Google Maps or Apple Maps link (or Share → Newt Maps), and it shows the place in
big type with one button per installed map app (Organic Maps, CoMaps, Magic Earth,
OsmAnd). Text-only Google links are looked up on OpenStreetMap's Nominatim,
not Google. Short links are followed only through Google's or Apple's own servers.

If Nominatim can't find the place, the "Couldn't find it" screen offers **Ask Google
(test)** for Google links. That sends the link to Google, so it only happens when you tap it.

**Driving directions:** a route always starts from where you are right now. If the
phone hasn't had a new location in the last 30 seconds, it waits for one (up to 15
seconds) instead of using an old one. Turns are spoken as directions, like other map
apps, so music from other apps gets quieter while it talks, and its own voice doesn't.
Over car Bluetooth, the phone's media volume sets how loud it is.

**One-time setup:** Android won't let a third-party app take google.com or apple.com links
by itself. Open Newt Maps → **Turn on link opening** → Add links → turn on every link.
